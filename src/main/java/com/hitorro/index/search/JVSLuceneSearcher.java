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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;

/**
 * Main search API for JVS documents in Lucene indexes.
 * Supports basic search, fielded search, and faceting with streaming results.
 */
public class JVSLuceneSearcher implements AutoCloseable {
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
        return search(queryString, offset, limit, null);
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
        long startTime = System.currentTimeMillis();
        
        // Parse query using JVS-aware parser
        JVSQueryParser parser = new JVSQueryParser(type, "content", analyzer, defaultLang);
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
     * This creates a minimal JVS with only the stored fields.
     */
    private JVS convertDocumentToJVS(Document doc, float score) {
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
                
                if (value != null) {
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
