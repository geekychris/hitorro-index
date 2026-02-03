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

import com.hitorro.index.config.IndexConfig;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.jsontypesystem.executors.ExecutionBuilder;
import com.hitorro.jsontypesystem.executors.ExecutionNode;
import com.hitorro.util.core.events.cache.HashCache;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JVS-aware Lucene IndexWriter wrapper.
 * Converts JVS documents to Lucene Documents using the ExecutionBuilder projection mechanism.
 */
public class JVSLuceneIndexWriter implements AutoCloseable {
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
        Document doc = projectToLuceneDocument(jvs);
        
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
     * Extract document ID from JVS for uniqueness checking.
     * Tries multiple ID field patterns:
     * 1. id.did (domain-specific ID)
     * 2. id.id (combined domain:did format)
     * 3. id (simple string ID)
     */
    private String extractDocumentId(JVS jvs) {
        try {
            // Try id.did first (most common pattern)
            Object idObj = jvs.get("id.did");
            if (idObj != null) {
                return idObj.toString();
            }
            
            // Try id.id (combined format like "sysobject:doc001")
            idObj = jvs.get("id.id");
            if (idObj != null) {
                return idObj.toString();
            }
            
            // Try simple id field
            idObj = jvs.get("id");
            if (idObj != null && idObj instanceof String) {
                return idObj.toString();
            }
        } catch (Exception e) {
            // ID not found or error accessing it
        }
        return null;
    }

    /**
     * Index multiple JVS documents in a batch.
     * Each document with an ID will replace any existing document with the same ID.
     *
     * @param documents List of JVS documents
     * @throws IOException if indexing fails
     */
    public void indexDocuments(List<JVS> documents) throws IOException {
        lock.writeLock().lock();
        try {
            for (JVS jvs : documents) {
                Document doc = projectToLuceneDocument(jvs);
                
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
     *
     * @param jvs The JVS document
     * @return Lucene Document
     */
    private Document projectToLuceneDocument(JVS jvs) {
        Type type = jvs.getType();
        
        if (type == null) {
            // No type - create simple document with all fields as text
            return createSimpleDocument(jvs);
        }

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
        
        return context.getDocument();
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
