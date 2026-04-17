/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.index.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.embeddings.EmbeddingConfig;
import com.hitorro.index.embeddings.EmbeddingFieldType;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.jsontypesystem.executors.ExecutionBuilder;
import com.hitorro.jsontypesystem.executors.ExecutionNode;
import com.hitorro.util.core.events.cache.HashCache;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JVS-aware Lucene IndexWriter wrapper.
 * Converts JVS documents to Lucene Documents using the ExecutionBuilder projection mechanism.
 */
public class JVSLuceneIndexWriter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JVSLuceneIndexWriter.class);
    
    private final IndexWriter indexWriter;
    private final IndexConfig config;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Cache for execution builders per type
    private static final HashCache<Type, ExecutionBuilder> executionBuilderCache =
            Type.getExecBuilderCache("lucene", LuceneExecutionBuilderMapper.me);

    public JVSLuceneIndexWriter(IndexConfig config) throws IOException {
        this.config = config;
        IndexWriterConfig writerConfig = config.createIndexWriterConfig();
        this.indexWriter = new IndexWriter(config.getDirectory(), writerConfig);
    }

    /**
     * Index a single JVS document.
     * If the document has an id.did field, it will replace any existing document with the same ID.
     * This ensures document uniqueness based on the identifier field.
     *
     * @param jvs The JVS document to index
     * @throws IOException if indexing fails
     */
    public void indexDocument(JVS jvs) throws IOException {
        indexDocument(jvs, null);
    }
    
    /**
     * Index a single JVS document with an out-of-band embedding vector.
     * The embedding is added to the Lucene Document without modifying the JVS JSON.
     * If the document has an id.did field, it will replace any existing document with the same ID.
     *
     * @param jvs The JVS document to index
     * @param embedding Optional out-of-band embedding vector (null if not provided)
     * @throws IOException if indexing fails
     */
    public void indexDocument(JVS jvs, float[] embedding) throws IOException {
        // Ensure id.id is computed from domain:did if not already present
        ensureIdField(jvs);

        Document doc = projectToLuceneDocument(jvs, embedding);

        // Store full document JSON as _source for faithful reconstruction in search results.
        // This happens AFTER ensureIdField so _source includes id.id.
        if (config.isStoreSource()) {
            doc.add(new org.apache.lucene.document.StoredField("_source", jvs.getJsonNode().toString()));
        }

        lock.writeLock().lock();
        try {
            // Try to extract document ID for update/replace logic
            String docId = extractDocumentId(jvs);

            if (docId != null) {
                // Add a special _uid field for guaranteed uniqueness
                // This field is always indexed and can be reliably used for updates
                doc.add(new org.apache.lucene.document.StringField(
                    "_uid", docId, org.apache.lucene.document.Field.Store.YES));

                // Use _uid field for document replacement
                indexWriter.updateDocument(new Term("_uid", docId), doc);
            } else {
                // No ID found, just add document
                indexWriter.addDocument(doc);
            }

            if (config.isAutoCommit()) {
                // Auto-commit will be handled by periodic commit
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Ensures the id.id field is populated on the JVS document.
     * If id.domain and id.did exist but id.id does not, synthesizes id.id as "domain:did".
     * This mirrors the type system's multivalue-merger dynamic field computation on core_id.
     */
    private void ensureIdField(JVS jvs) {
        try {
            // Skip if id.id already exists (e.g. from enrichment)
            Object existing = jvs.get("id.id");
            if (existing != null && existing instanceof com.fasterxml.jackson.databind.JsonNode node
                    && node.isTextual() && !node.asText().isEmpty()) {
                return;
            }

            // Synthesize from domain + did
            Object domainObj = jvs.get("id.domain");
            Object didObj = jvs.get("id.did");
            if (domainObj != null && didObj != null) {
                String domain = extractStringValue(domainObj);
                String did = extractStringValue(didObj);
                if (domain != null && !domain.isEmpty() && did != null && !did.isEmpty()) {
                    jvs.set("id.id", domain + ":" + did);
                }
            }
        } catch (Exception e) {
            // Non-fatal -- id.id is optional
        }
    }
    
    /**
     * Extract document ID from JVS for uniqueness checking.
     * Tries multiple ID field patterns:
     * 1. id.id (combined "domain:did" format -- dynamically computed by type system)
     * 2. id.domain + id.did (synthesize "domain:did" when both parts exist)
     * 3. id.did alone (fallback if no domain)
     * 4. id (simple string ID)
     */
    private String extractDocumentId(JVS jvs) {
        try {
            // Try id.id first (dynamically computed by type system's multivalue-merger)
            Object idId = jvs.get("id.id");
            if (idId != null) {
                String val = extractStringValue(idId);
                if (val != null && !val.isEmpty()) return val;
            }

            // Synthesize domain:did when both parts exist
            Object domainObj = jvs.get("id.domain");
            Object didObj = jvs.get("id.did");
            if (domainObj != null && didObj != null) {
                String domain = extractStringValue(domainObj);
                String did = extractStringValue(didObj);
                if (domain != null && !domain.isEmpty() && did != null && !did.isEmpty()) {
                    return domain + ":" + did;
                }
            }

            // Fallback to id.did alone
            if (didObj != null) {
                String val = extractStringValue(didObj);
                if (val != null && !val.isEmpty()) return val;
            }

            // Fallback to simple string id field
            Object idObj = jvs.get("id");
            if (idObj != null && idObj instanceof JsonNode node && node.isTextual()) {
                return node.asText();
            }
        } catch (Exception e) {
            // ID not found or error accessing it
        }
        return null;
    }
    
    /**
     * Extract string value from various object types.
     * Handles JsonNode properly to avoid extra quotes.
     */
    private String extractStringValue(Object obj) {
        if (obj instanceof JsonNode) {
            JsonNode node = (JsonNode) obj;
            if (node.isTextual()) {
                return node.asText();
            }
            return node.asText();
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        return obj.toString();
    }

    /**
     * Index multiple JVS documents in a batch.
     * Each document with an ID will replace any existing document with the same ID.
     *
     * @param documents List of JVS documents
     * @throws IOException if indexing fails
     */
    public void indexDocuments(List<JVS> documents) throws IOException {
        indexDocuments(documents, null);
    }

    /**
     * Index multiple JVS documents, optionally using separate source documents for _source storage.
     * This allows indexing enriched documents (for searchable NLP fields) while storing
     * clean originals in _source for faithful reconstruction in search results.
     *
     * @param documents     The JVS documents to index (may be enriched for field projection)
     * @param sourceDocs    Optional clean documents for _source storage (same order as documents).
     *                      If null, uses the indexed documents themselves.
     */
    public void indexDocuments(List<JVS> documents, List<com.fasterxml.jackson.databind.JsonNode> sourceDocs) throws IOException {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < documents.size(); i++) {
                JVS jvs = documents.get(i);
                Document doc = projectToLuceneDocument(jvs);

                // Store _source: use clean source doc if provided, otherwise the indexed doc
                if (config.isStoreSource()) {
                    com.fasterxml.jackson.databind.JsonNode sourceNode = (sourceDocs != null && i < sourceDocs.size())
                            ? sourceDocs.get(i) : jvs.getJsonNode();
                    doc.add(new org.apache.lucene.document.StoredField("_source", sourceNode.toString()));
                }

                // Extract document ID and use update logic for each document
                String docId = extractDocumentId(jvs);

                if (docId != null) {
                    // Add _uid field for guaranteed uniqueness
                    doc.add(new org.apache.lucene.document.StringField(
                        "_uid", docId, org.apache.lucene.document.Field.Store.YES));

                    // Use _uid field for document replacement
                    indexWriter.updateDocument(new Term("_uid", docId), doc);
                } else {
                    // No ID found, just add document
                    indexWriter.addDocument(doc);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update a document by ID.
     *
     * @param idField The field name used as document ID
     * @param idValue The ID value
     * @param jvs     The updated JVS document
     * @throws IOException if update fails
     */
    public void updateDocument(String idField, String idValue, JVS jvs) throws IOException {
        Document doc = projectToLuceneDocument(jvs);
        
        lock.writeLock().lock();
        try {
            indexWriter.updateDocument(new Term(idField, idValue), doc);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete a document by ID.
     *
     * @param idField The field name used as document ID
     * @param idValue The ID value
     * @throws IOException if deletion fails
     */
    public void deleteDocument(String idField, String idValue) throws IOException {
        lock.writeLock().lock();
        try {
            indexWriter.deleteDocuments(new Term(idField, idValue));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Delete all documents from the index.
     *
     * @throws IOException if deletion fails
     */
    public void deleteAll() throws IOException {
        lock.writeLock().lock();
        try {
            indexWriter.deleteAll();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Commit all pending changes to the index.
     *
     * @throws IOException if commit fails
     */
    public void commit() throws IOException {
        lock.writeLock().lock();
        try {
            indexWriter.commit();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Flush all pending changes without committing.
     *
     * @throws IOException if flush fails
     */
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            indexWriter.flush();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Project a JVS document to a Lucene Document using the ExecutionBuilder mechanism.
     * If JVS has no type, creates a simple document with all fields as text.
     * Also handles optional embedding fields if configured.
     *
     * @param jvs The JVS document
     * @return Lucene Document
     */
    private Document projectToLuceneDocument(JVS jvs) {
        return projectToLuceneDocument(jvs, null);
    }
    
    /**
     * Project a JVS document to a Lucene Document with optional out-of-band embedding.
     * If JVS has no type, creates a simple document with all fields as text.
     * Also handles optional embedding fields if configured.
     *
     * @param jvs The JVS document
     * @param outOfBandEmbedding Optional embedding vector to add (takes precedence over in-JSON embedding)
     * @return Lucene Document
     */
    private Document projectToLuceneDocument(JVS jvs, float[] outOfBandEmbedding) {
        Type type = jvs.getType();
        Document doc;
        
        if (type == null) {
            // No type - create simple document with all fields as text
            doc = createSimpleDocument(jvs);
        } else {
            // Get or create execution builder for this type
            ExecutionBuilder builder = executionBuilderCache.get(type);
            
            // Create projection context
            LuceneProjectionContext context = new LuceneProjectionContext(jvs);
            
            // Execute projection
            ExecutionNode root = builder.getExecutor();
            try {
                root.project(context);
            } catch (PropaccessError e) {
                throw new RuntimeException("Failed to project JVS to Lucene document", e);
            }
            
            doc = context.getDocument();
        }
        
        // Add embedding field if configured
        if (config.hasEmbeddings()) {
            if (outOfBandEmbedding != null) {
                // Use out-of-band embedding (takes precedence)
                addOutOfBandEmbeddingField(doc, outOfBandEmbedding);
            } else {
                // Try to extract from JVS JSON
                addEmbeddingField(doc, jvs);
            }
        }
        
        return doc;
    }
    
    /**
     * Add out-of-band embedding directly to Lucene document.
     * This bypasses the JVS JSON extraction and adds the embedding vector directly.
     *
     * @param doc The Lucene document
     * @param embedding The embedding vector
     */
    private void addOutOfBandEmbeddingField(Document doc, float[] embedding) {
        if (embedding == null) {
            return;
        }
        
        EmbeddingConfig embeddingConfig = config.getEmbeddingConfig();
        String fieldName = embeddingConfig.getFieldName();
        
        // Validate dimension
        if (embedding.length != embeddingConfig.getDimension()) {
            logger.warn("Out-of-band embedding dimension mismatch: expected {}, got {}. Skipping embedding.",
                       embeddingConfig.getDimension(), embedding.length);
            return;
        }
        
        try {
            // Add appropriate vector field based on configuration
            if (embeddingConfig.getFieldType() == EmbeddingFieldType.FLOAT_VECTOR) {
                KnnFloatVectorField vectorField = new KnnFloatVectorField(
                    fieldName,
                    embedding,
                    embeddingConfig.getSimilarity().toLuceneFunction()
                );
                doc.add(vectorField);
                logger.debug("Added out-of-band FLOAT_VECTOR embedding '{}' with dimension {}",
                            fieldName, embedding.length);
            } else if (embeddingConfig.getFieldType() == EmbeddingFieldType.BYTE_VECTOR) {
                byte[] byteVector = quantizeToBytes(embedding);
                KnnByteVectorField vectorField = new KnnByteVectorField(
                    fieldName,
                    byteVector,
                    embeddingConfig.getSimilarity().toLuceneFunction()
                );
                doc.add(vectorField);
                logger.debug("Added out-of-band BYTE_VECTOR embedding '{}' with dimension {}",
                            fieldName, embedding.length);
            }
        } catch (Exception e) {
            logger.warn("Error adding out-of-band embedding: {}", e.getMessage());
        }
    }
    
    /**
     * Extract and add embedding field to Lucene document if present in JVS.
     */
    private void addEmbeddingField(Document doc, JVS jvs) {
        EmbeddingConfig embeddingConfig = config.getEmbeddingConfig();
        String fieldName = embeddingConfig.getFieldName();
        
        try {
            // Try to get embedding from JVS
            Object embeddingObj = jvs.get(fieldName);
            if (embeddingObj == null) {
                // No embedding field present - this is OK, embeddings are optional
                return;
            }
            
            // Convert to float array
            float[] vector = extractVectorFromObject(embeddingObj);
            if (vector == null) {
                logger.warn("Could not extract embedding vector from field '{}', invalid type: {}", 
                           fieldName, embeddingObj.getClass().getSimpleName());
                return;
            }
            
            // Validate dimension
            if (vector.length != embeddingConfig.getDimension()) {
                logger.warn("Embedding dimension mismatch: expected {}, got {}. Skipping embedding for this document.",
                           embeddingConfig.getDimension(), vector.length);
                return;
            }
            
            // Add appropriate vector field based on configuration
            if (embeddingConfig.getFieldType() == EmbeddingFieldType.FLOAT_VECTOR) {
                // Use KnnFloatVectorField for 32-bit floats
                // In Lucene 9.12, similarity function is specified separately via FieldType
                KnnFloatVectorField vectorField = new KnnFloatVectorField(
                    fieldName,
                    vector,
                    embeddingConfig.getSimilarity().toLuceneFunction()
                );
                doc.add(vectorField);
            } else if (embeddingConfig.getFieldType() == EmbeddingFieldType.BYTE_VECTOR) {
                // Convert to byte vector (quantize)
                byte[] byteVector = quantizeToBytes(vector);
                KnnByteVectorField vectorField = new KnnByteVectorField(
                    fieldName,
                    byteVector,
                    embeddingConfig.getSimilarity().toLuceneFunction()
                );
                doc.add(vectorField);
            }
            
            logger.debug("Added {} embedding field '{}' with dimension {}",
                        embeddingConfig.getFieldType(), fieldName, vector.length);
            
        } catch (Exception e) {
            logger.warn("Error extracting embedding from field '{}': {}", fieldName, e.getMessage());
            // Continue without embedding - don't fail the entire document
        }
    }
    
    /**
     * Extract float vector from various possible object types.
     * Supports: float[], double[], List<Number>, JsonNode array.
     */
    private float[] extractVectorFromObject(Object obj) {
        if (obj == null) {
            return null;
        }
        
        // Direct float array
        if (obj instanceof float[]) {
            return (float[]) obj;
        }
        
        // Double array - convert to float
        if (obj instanceof double[]) {
            double[] doubles = (double[]) obj;
            float[] floats = new float[doubles.length];
            for (int i = 0; i < doubles.length; i++) {
                floats[i] = (float) doubles[i];
            }
            return floats;
        }
        
        // List of numbers
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            float[] floats = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    floats[i] = ((Number) item).floatValue();
                } else {
                    return null; // Invalid list element
                }
            }
            return floats;
        }
        
        // Jackson JsonNode array
        if (obj instanceof JsonNode) {
            JsonNode node = (JsonNode) obj;
            if (node.isArray()) {
                float[] floats = new float[node.size()];
                for (int i = 0; i < node.size(); i++) {
                    JsonNode element = node.get(i);
                    if (element.isNumber()) {
                        floats[i] = (float) element.asDouble();
                    } else {
                        return null; // Invalid array element
                    }
                }
                return floats;
            }
        }
        
        return null; // Unsupported type
    }
    
    /**
     * Quantize float vector to byte vector.
     * Maps float range [-1, 1] to byte range [-128, 127].
     * Values outside [-1, 1] are clamped.
     */
    private byte[] quantizeToBytes(float[] vector) {
        byte[] byteVector = new byte[vector.length];
        for (int i = 0; i < vector.length; i++) {
            // Clamp to [-1, 1] and scale to [-128, 127]
            float clamped = Math.max(-1.0f, Math.min(1.0f, vector[i]));
            byteVector[i] = (byte) Math.round(clamped * 127.0f);
        }
        return byteVector;
    }
    
    /**
     * Create a simple Lucene document from JVS without using type system.
     * All fields are indexed as text fields.
     */
    private Document createSimpleDocument(JVS jvs) {
        Document doc = new Document();
        
        // Iterate through all fields in the JVS
        try {
            com.fasterxml.jackson.databind.JsonNode root = jvs.getJsonNode();
            if (root.isObject()) {
                java.util.Iterator<String> fieldNames = root.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    com.fasterxml.jackson.databind.JsonNode value = root.get(fieldName);
                    
                    if (value != null && !value.isNull()) {
                        if (value.isTextual()) {
                            doc.add(new org.apache.lucene.document.TextField(fieldName, value.textValue(), 
                                org.apache.lucene.document.Field.Store.YES));
                        } else if (value.isNumber()) {
                            doc.add(new org.apache.lucene.document.StringField(fieldName, value.asText(), 
                                org.apache.lucene.document.Field.Store.YES));
                        } else {
                            doc.add(new org.apache.lucene.document.TextField(fieldName, value.asText(), 
                                org.apache.lucene.document.Field.Store.YES));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty document on error
        }
        
        return doc;
    }

    /**
     * Get the underlying IndexWriter.
     *
     * @return IndexWriter instance
     */
    public IndexWriter getIndexWriter() {
        return indexWriter;
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            indexWriter.close();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
