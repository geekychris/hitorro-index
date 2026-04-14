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
package com.hitorro.index.search;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.embeddings.EmbeddingConfig;
import com.hitorro.index.query.JVSQueryParser;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.Type;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main search API for JVS documents in Lucene indexes.
 * Supports basic search, fielded search, and faceting with streaming results.
 */
public class JVSLuceneSearcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JVSLuceneSearcher.class);
    
    private final IndexConfig config;
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    private final Type type;
    private final String defaultLang;
    private final FacetsConfig facetsConfig;

    /**
     * Create a searcher for the given index configuration.
     *
     * @param config      Index configuration
     * @param type        JVS Type for field resolution
     * @param defaultLang Default language for i18n fields
     * @throws IOException if index cannot be opened
     */
    public JVSLuceneSearcher(IndexConfig config, Type type, String defaultLang) throws IOException {
        this.config = config;
        this.type = type;
        this.defaultLang = defaultLang != null ? defaultLang : "en";
        this.analyzer = config.getDefaultAnalyzer();
        this.reader = DirectoryReader.open(config.getDirectory());
        this.searcher = new IndexSearcher(reader);
        this.facetsConfig = new FacetsConfig();
        
        // Configure facets
        facetsConfig.setMultiValued("identifier", true);
        facetsConfig.setMultiValued("text", true);
        facetsConfig.setMultiValued("textmarkup", true);
    }

    /**
     * Basic search with query string.
     *
     * @param queryString Lucene query string
     * @param offset      Starting offset
     * @param limit       Maximum number of results
     * @return SearchResult with matching documents
     * @throws IOException    if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult search(String queryString, int offset, int limit) throws IOException, ParseException {
        return search(queryString, offset, limit, null, null);
    }

    /**
     * Search with faceting support.
     *
     * @param queryString Lucene query string
     * @param offset      Starting offset
     * @param limit       Maximum number of results
     * @param facetDims   Dimensions to facet on (null for no faceting)
     * @return SearchResult with matching documents and facets
     * @throws IOException    if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult search(String queryString, int offset, int limit, List<String> facetDims)
            throws IOException, ParseException {
        return search(queryString, offset, limit, facetDims, null);
    }

    /**
     * Search with faceting and explicit language selection.
     *
     * @param queryString Lucene query string
     * @param offset      Starting offset
     * @param limit       Maximum number of results
     * @param facetDims   Dimensions to facet on (null for no faceting)
     * @param lang        Optional ISO 639-1 language code for i18n fields;
     *                    if null or empty, the searcher's defaultLang is used.
     */
    public SearchResult search(String queryString, int offset, int limit, List<String> facetDims, String lang)
            throws IOException, ParseException {
        long startTime = System.currentTimeMillis();

        String effectiveLang = (lang != null && !lang.isEmpty()) ? lang : defaultLang;

        // Parse query using JVS-aware parser
        JVSQueryParser parser = new JVSQueryParser(type, "content", analyzer, effectiveLang);
        Query query = parser.parse(queryString);

        // Execute search
        TopDocs topDocs = searcher.search(query, offset + limit);
        long totalHits = topDocs.totalHits.value;

        // Collect documents
        List<JVS> documents = new ArrayList<>();
        ScoreDoc[] hits = topDocs.scoreDocs;
        int start = Math.min(offset, hits.length);
        int end = Math.min(offset + limit, hits.length);

        for (int i = start; i < end; i++) {
            Document doc = searcher.storedFields().document(hits[i].doc);
            JVS jvs = convertDocumentToJVS(doc, hits[i].score);
            documents.add(jvs);
        }

        // Collect facets if requested
        Map<String, FacetResult> facetResults = null;
        if (facetDims != null && !facetDims.isEmpty()) {
            facetResults = collectFacets(query, facetDims);
        }

        long searchTime = System.currentTimeMillis() - startTime;

        return SearchResult.builder()
                .documents(documents)
                .totalHits(totalHits)
                .facets(facetResults)
                .query(queryString)
                .offset(offset)
                .limit(limit)
                .searchTimeMs(searchTime)
                .build();
    }

    /**
     * Fielded search with map of field->query pairs.
     *
     * @param fieldQueries Map of field names to query strings
     * @param offset       Starting offset
     * @param limit        Maximum number of results
     * @return SearchResult with matching documents
     * @throws IOException    if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult fieldedSearch(Map<String, String> fieldQueries, int offset, int limit) 
            throws IOException, ParseException {
        // Build boolean query combining all field queries
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        JVSQueryParser parser = new JVSQueryParser(type, "content", analyzer, defaultLang);
        
        for (Map.Entry<String, String> entry : fieldQueries.entrySet()) {
            Query fieldQuery = parser.parse(entry.getKey() + ":(" + entry.getValue() + ")");
            builder.add(fieldQuery, BooleanClause.Occur.MUST);
        }
        
        Query query = builder.build();
        
        // Execute search
        long startTime = System.currentTimeMillis();
        TopDocs topDocs = searcher.search(query, offset + limit);
        long totalHits = topDocs.totalHits.value;
        
        // Collect documents
        List<JVS> documents = new ArrayList<>();
        ScoreDoc[] hits = topDocs.scoreDocs;
        int start = Math.min(offset, hits.length);
        int end = Math.min(offset + limit, hits.length);
        
        for (int i = start; i < end; i++) {
            Document doc = searcher.storedFields().document(hits[i].doc);
            JVS jvs = convertDocumentToJVS(doc, hits[i].score);
            documents.add(jvs);
        }
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        return SearchResult.builder()
                .documents(documents)
                .totalHits(totalHits)
                .query(query.toString())
                .offset(offset)
                .limit(limit)
                .searchTimeMs(searchTime)
                .build();
    }

    /**
     * Stream search results as Flux.
     *
     * @param queryString Lucene query string
     * @param offset      Starting offset
     * @param limit       Maximum number of results
     * @return Flux of JVS documents
     */
    public Flux<JVS> searchStream(String queryString, int offset, int limit) {
        return Mono.fromCallable(() -> search(queryString, offset, limit))
                .flatMapMany(result -> Flux.fromIterable(result.getDocuments()));
    }
    
    /**
     * Search by embedding vector (KNN/ANN search).
     *
     * @param request Embedding search request
     * @return SearchResult with nearest neighbors
     * @throws IOException if search fails
     */
    public SearchResult searchByEmbedding(EmbeddingSearchRequest request) throws IOException {
        if (!config.hasEmbeddings()) {
            throw new IllegalStateException("Embedding search not supported - index not configured with embeddings");
        }
        
        EmbeddingConfig embeddingConfig = config.getEmbeddingConfig();
        String fieldName = embeddingConfig.getFieldName();
        
        long startTime = System.currentTimeMillis();
        
        // Create KNN query based on index configuration
        Query knnQuery;
        if (embeddingConfig.getFieldType() == com.hitorro.index.embeddings.EmbeddingFieldType.BYTE_VECTOR) {
            // Index uses byte vectors - convert float query if needed
            byte[] queryBytes;
            if (request.isFloatVector()) {
                queryBytes = quantizeToBytes(request.getQueryVector());
            } else {
                queryBytes = request.getQueryByteVector();
            }
            knnQuery = new org.apache.lucene.search.KnnByteVectorQuery(
                    fieldName, 
                    queryBytes, 
                    request.getK(), 
                    request.getFilter()
            );
        } else {
            // Index uses float vectors
            if (!request.isFloatVector()) {
                throw new IllegalArgumentException("Index uses FLOAT_VECTOR but query provided BYTE_VECTOR");
            }
            knnQuery = new org.apache.lucene.search.KnnFloatVectorQuery(
                    fieldName, 
                    request.getQueryVector(), 
                    request.getK(), 
                    request.getFilter()
            );
        }
        
        // Execute KNN search
        TopDocs topDocs = searcher.search(knnQuery, request.getK());
        long totalHits = topDocs.totalHits.value;
        
        // Collect documents
        List<JVS> documents = new ArrayList<>();
        ScoreDoc[] hits = topDocs.scoreDocs;
        
        for (int i = 0; i < hits.length; i++) {
            Document doc = searcher.storedFields().document(hits[i].doc);
            JVS jvs = convertDocumentToJVS(doc, hits[i].score);
            
            // Add similarity score if requested
            if (request.isIncludeScores()) {
                try {
                    jvs.set("_similarity", hits[i].score);
                } catch (Exception e) {
                    logger.warn("Could not set similarity score", e);
                }
            }
            
            documents.add(jvs);
        }
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        return SearchResult.builder()
                .documents(documents)
                .totalHits(totalHits)
                .query("vector[" + (request.isFloatVector() ? request.getQueryVector().length : request.getQueryByteVector().length) + "]")
                .offset(0)
                .limit(request.getK())
                .searchTimeMs(searchTime)
                .build();
    }
    
    /**
     * Hybrid search combining text and vector search.
     *
     * @param request Hybrid search request
     * @return SearchResult with merged results from text and vector search
     * @throws IOException if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult searchHybrid(HybridSearchRequest request) throws IOException, ParseException {
        if (!config.hasEmbeddings()) {
            throw new IllegalStateException("Hybrid search not supported - index not configured with embeddings");
        }
        
        long startTime = System.currentTimeMillis();
        
        // Execute text search
        SearchResult textResult = search(request.getTextQuery(), 0, request.getK());
        
        // Execute vector search
        EmbeddingSearchRequest.Builder embeddingRequestBuilder = EmbeddingSearchRequest.builder()
                .k(request.getK())
                .includeScores(true);
        
        if (request.isFloatVector()) {
            embeddingRequestBuilder.queryVector(request.getQueryVector());
        } else {
            embeddingRequestBuilder.queryByteVector(request.getQueryByteVector());
        }
        
        SearchResult vectorResult = searchByEmbedding(embeddingRequestBuilder.build());
        
        // Merge results based on strategy
        List<JVS> mergedDocuments;
        long totalHits;
        
        switch (request.getStrategy()) {
            case MERGE_MAX_SCORE:
                mergedDocuments = mergeByMaxScore(textResult.getDocuments(), vectorResult.getDocuments());
                totalHits = Math.max(textResult.getTotalHits(), vectorResult.getTotalHits());
                break;
            
            case MERGE_SUM_SCORE:
                mergedDocuments = mergeByWeightedSum(textResult.getDocuments(), vectorResult.getDocuments(), request.getAlpha());
                totalHits = Math.max(textResult.getTotalHits(), vectorResult.getTotalHits());
                break;
            
            case RERANK_RRF:
            default:
                mergedDocuments = mergeByRRF(textResult.getDocuments(), vectorResult.getDocuments());
                totalHits = Math.max(textResult.getTotalHits(), vectorResult.getTotalHits());
                break;
        }
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        return SearchResult.builder()
                .documents(mergedDocuments)
                .totalHits(totalHits)
                .query("hybrid: text=\"" + request.getTextQuery() + "\" + vector[" + 
                       (request.isFloatVector() ? request.getQueryVector().length : request.getQueryByteVector().length) + "]")
                .offset(0)
                .limit(request.getK())
                .searchTimeMs(searchTime)
                .build();
    }
    
    /**
     * Merge results by taking maximum score per document.
     */
    private List<JVS> mergeByMaxScore(List<JVS> textDocs, List<JVS> vectorDocs) {
        Map<String, JVS> docMap = new HashMap<>();
        Map<String, Float> scoreMap = new HashMap<>();
        
        // Add text search results
        for (JVS doc : textDocs) {
            String docId = extractDocId(doc);
            float score = extractScore(doc);
            docMap.put(docId, doc);
            scoreMap.put(docId, score);
        }
        
        // Merge with vector search results (take max score)
        for (JVS doc : vectorDocs) {
            String docId = extractDocId(doc);
            float score = extractScore(doc);
            
            if (!docMap.containsKey(docId) || score > scoreMap.get(docId)) {
                docMap.put(docId, doc);
                scoreMap.put(docId, score);
            }
        }
        
        // Sort by score descending
        return docMap.values().stream()
                .sorted((a, b) -> Float.compare(scoreMap.get(extractDocId(b)), scoreMap.get(extractDocId(a))))
                .collect(Collectors.toList());
    }
    
    /**
     * Merge results by weighted sum of normalized scores.
     */
    private List<JVS> mergeByWeightedSum(List<JVS> textDocs, List<JVS> vectorDocs, double alpha) {
        // Normalize scores for each result set
        Map<String, Float> textScores = normalizeScores(textDocs);
        Map<String, Float> vectorScores = normalizeScores(vectorDocs);
        
        // Combine all unique document IDs
        Set<String> allDocIds = new HashSet<>();
        Map<String, JVS> docMap = new HashMap<>();
        
        for (JVS doc : textDocs) {
            String docId = extractDocId(doc);
            allDocIds.add(docId);
            docMap.put(docId, doc);
        }
        
        for (JVS doc : vectorDocs) {
            String docId = extractDocId(doc);
            allDocIds.add(docId);
            if (!docMap.containsKey(docId)) {
                docMap.put(docId, doc);
            }
        }
        
        // Calculate weighted scores
        Map<String, Float> finalScores = new HashMap<>();
        for (String docId : allDocIds) {
            float textScore = textScores.getOrDefault(docId, 0.0f);
            float vectorScore = vectorScores.getOrDefault(docId, 0.0f);
            float combinedScore = (float) (alpha * textScore + (1.0 - alpha) * vectorScore);
            finalScores.put(docId, combinedScore);
        }
        
        // Sort by combined score descending
        return docMap.values().stream()
                .sorted((a, b) -> Float.compare(finalScores.get(extractDocId(b)), finalScores.get(extractDocId(a))))
                .collect(Collectors.toList());
    }
    
    /**
     * Merge results using Reciprocal Rank Fusion (RRF).
     */
    private List<JVS> mergeByRRF(List<JVS> textDocs, List<JVS> vectorDocs) {
        final int K = 60; // RRF constant
        
        Map<String, JVS> docMap = new HashMap<>();
        Map<String, Float> rrfScores = new HashMap<>();
        
        // Add text search results with RRF scoring
        for (int i = 0; i < textDocs.size(); i++) {
            JVS doc = textDocs.get(i);
            String docId = extractDocId(doc);
            float rrfScore = 1.0f / (K + i + 1); // rank starts at 0
            
            docMap.put(docId, doc);
            rrfScores.put(docId, rrfScore);
        }
        
        // Add vector search results with RRF scoring
        for (int i = 0; i < vectorDocs.size(); i++) {
            JVS doc = vectorDocs.get(i);
            String docId = extractDocId(doc);
            float rrfScore = 1.0f / (K + i + 1);
            
            if (!docMap.containsKey(docId)) {
                docMap.put(docId, doc);
                rrfScores.put(docId, rrfScore);
            } else {
                // Add to existing RRF score
                rrfScores.put(docId, rrfScores.get(docId) + rrfScore);
            }
        }
        
        // Sort by RRF score descending
        return docMap.values().stream()
                .sorted((a, b) -> Float.compare(rrfScores.get(extractDocId(b)), rrfScores.get(extractDocId(a))))
                .collect(Collectors.toList());
    }
    
    /**
     * Normalize scores to [0, 1] range using min-max normalization.
     */
    private Map<String, Float> normalizeScores(List<JVS> docs) {
        if (docs.isEmpty()) {
            return new HashMap<>();
        }
        
        // Find min and max scores
        float minScore = Float.MAX_VALUE;
        float maxScore = Float.MIN_VALUE;
        
        Map<String, Float> scores = new HashMap<>();
        for (JVS doc : docs) {
            String docId = extractDocId(doc);
            float score = extractScore(doc);
            scores.put(docId, score);
            minScore = Math.min(minScore, score);
            maxScore = Math.max(maxScore, score);
        }
        
        // Normalize to [0, 1]
        Map<String, Float> normalized = new HashMap<>();
        float range = maxScore - minScore;
        
        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            float normalizedScore = range > 0 ? (entry.getValue() - minScore) / range : 1.0f;
            normalized.put(entry.getKey(), normalizedScore);
        }
        
        return normalized;
    }
    
    /**
     * Extract document ID from JVS.
     * Tries _uid, id.did, id fields.
     */
    private String extractDocId(JVS doc) {
        try {
            Object uid = doc.get("_uid");
            if (uid != null) return uid.toString();
            
            Object did = doc.get("id.did");
            if (did != null) return did.toString();
            
            Object id = doc.get("id");
            if (id != null) return id.toString();
        } catch (Exception e) {
            // Fall through
        }
        
        // Use hash code as last resort
        return String.valueOf(doc.hashCode());
    }
    
    /**
     * Extract score from JVS document.
     * Tries _score, _similarity fields.
     */
    private float extractScore(JVS doc) {
        try {
            Object score = doc.get("_score");
            if (score instanceof Number) {
                return ((Number) score).floatValue();
            }
            
            Object similarity = doc.get("_similarity");
            if (similarity instanceof Number) {
                return ((Number) similarity).floatValue();
            }
        } catch (Exception e) {
            // Fall through
        }
        
        return 0.0f;
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
     * Stream embedding search results as Flux.
     *
     * @param request Embedding search request
     * @return Flux of JVS documents
     */
    public Flux<JVS> searchByEmbeddingStream(EmbeddingSearchRequest request) {
        return Mono.fromCallable(() -> searchByEmbedding(request))
                .flatMapMany(result -> Flux.fromIterable(result.getDocuments()));
    }

    /**
     * Collect facets for the given query and dimensions.
     */
    private Map<String, FacetResult> collectFacets(Query query, List<String> facetDims) throws IOException {
        try {
            SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader);
            FacetsCollector fc = new FacetsCollector();
            FacetsCollector.search(searcher, query, 10, fc);
            
            Facets facets = new SortedSetDocValuesFacetCounts(state, fc);
            Map<String, FacetResult> results = new HashMap<>();
            
            for (String dim : facetDims) {
                try {
                    org.apache.lucene.facet.FacetResult luceneFacetResult = facets.getTopChildren(100, dim);
                    if (luceneFacetResult != null) {
                        results.put(dim, convertLuceneFacetResult(luceneFacetResult));
                    }
                } catch (Exception e) {
                    // Dimension may not exist or have no values
                }
            }
            
            return results;
        } catch (Exception e) {
            // Faceting failed, return empty map
            return new HashMap<>();
        }
    }

    /**
     * Convert Lucene FacetResult to our FacetResult.
     */
    private FacetResult convertLuceneFacetResult(org.apache.lucene.facet.FacetResult luceneFacetResult) {
        List<FacetResult.FacetValue> values = new ArrayList<>();
        
        if (luceneFacetResult.labelValues != null) {
            for (LabelAndValue lv : luceneFacetResult.labelValues) {
                values.add(new FacetResult.FacetValue(lv.label, lv.value.longValue()));
            }
        }
        
        return new FacetResult(luceneFacetResult.dim, values, luceneFacetResult.value.longValue());
    }

    /**
     * Convert Lucene Document to JVS.
     * Uses _source field (full original JSON) when available for faithful reconstruction.
     * Falls back to field-by-field reconstruction from stored Lucene fields.
     */
    private JVS convertDocumentToJVS(Document doc, float score) {
        // Try _source first - contains the full original document JSON
        String source = doc.get("_source");
        if (source != null) {
            try {
                JVS jvs = JVS.read(source);
                jvs.set("_score", score);
                // Preserve internal Lucene fields (e.g. _uid) on the reconstructed doc
                String uid = doc.get("_uid");
                if (uid != null) jvs.set("_uid", uid);
                return jvs;
            } catch (Exception e) {
                // Fall through to field-by-field reconstruction
            }
        }

        // Fallback: reconstruct from individual stored fields
        JVS jvs = new JVS();

        try {
            // Add score
            jvs.set("_score", score);

            // Collect all field values (handling multi-valued fields)
            // Key = original field path (with index type suffix stripped)
            // Value = list of values
            Map<String, List<String>> fieldValues = new HashMap<>();

            for (org.apache.lucene.index.IndexableField field : doc.getFields()) {
                String name = field.name();
                String value = field.stringValue();

                if (value != null && !"_source".equals(name)) {
                    // Strip index type suffixes like .text_en_s, .text_en_m, .identifier_s, .long_m, etc.
                    String cleanPath = stripIndexTypeSuffix(name);
                    fieldValues.computeIfAbsent(cleanPath, k -> new ArrayList<>()).add(value);
                }
            }

            // Add fields to JVS using proper path notation
            for (Map.Entry<String, List<String>> entry : fieldValues.entrySet()) {
                String fieldPath = entry.getKey();
                List<String> values = entry.getValue();

                try {
                    if (values.size() == 1) {
                        jvs.set(fieldPath, values.get(0));
                    } else {
                        // Multi-valued field - set as array
                        jvs.set(fieldPath, values);
                    }
                } catch (Exception e) {
                    // Field path may not be settable, skip it
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return jvs;
    }
    
    /**
     * Strip index type suffixes from field names.
     * Examples:
     *   title.mls.text_en_s -> title.mls
     *   title.mls.segmented.text_en_m -> title.mls.segmented
     *   brand.identifier_s -> brand
     *   price.double_s -> price
     */
    private String stripIndexTypeSuffix(String fieldName) {
        // Pattern: .{indexType}_{lang}_{s|m} or .{indexType}_{s|m}
        // Index types: text, textmarkup, identifier, long, int, double, date, boolean
        int lastDot = fieldName.lastIndexOf('.');
        if (lastDot > 0) {
            String lastPart = fieldName.substring(lastDot + 1);
            // Check if it matches index type patterns
            if (lastPart.matches("(text|textmarkup|identifier|long|int|double|date|boolean)(_[a-z]{2})?_[sm]")) {
                return fieldName.substring(0, lastDot);
            }
        }
        return fieldName;
    }

    /**
     * Get the underlying IndexSearcher.
     */
    public IndexSearcher getIndexSearcher() {
        return searcher;
    }

    /**
     * Refresh the reader to see new changes.
     */
    public JVSLuceneSearcher refresh() throws IOException {
        DirectoryReader newReader = DirectoryReader.openIfChanged((DirectoryReader) reader);
        if (newReader != null) {
            return new JVSLuceneSearcher(config, type, defaultLang);
        }
        return this;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /**
     * Builder for JVSLuceneSearcher.
     */
    public static class Builder {
        private IndexConfig config;
        private Type type;
        private String defaultLang = "en";

        public Builder config(IndexConfig config) {
            this.config = config;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder defaultLang(String defaultLang) {
            this.defaultLang = defaultLang;
            return this;
        }

        public JVSLuceneSearcher build() throws IOException {
            if (config == null) {
                throw new IllegalStateException("IndexConfig must be provided");
            }
            return new JVSLuceneSearcher(config, type, defaultLang);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
