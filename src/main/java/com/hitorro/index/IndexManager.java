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
package com.hitorro.index;

import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.Type;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple named Lucene indexes with support for:
 * - Creating and accessing individual indexes
 * - Indexing documents to specific indexes
 * - Searching within a single index
 * - Searching across multiple indexes simultaneously
 * - Index lifecycle management
 */
public class IndexManager implements Closeable {
    
    private final Map<String, IndexHandle> indexes = new ConcurrentHashMap<>();
    private final String defaultLang;
    
    /**
     * Handle to an individual index with its writer and searcher.
     */
    public static class IndexHandle {
        private final String name;
        private final IndexConfig config;
        private final JVSLuceneIndexWriter writer;
        private JVSLuceneSearcher searcher;
        private final Type type;
        private final String defaultLang;
        
        IndexHandle(String name, IndexConfig config, Type type, String defaultLang) throws IOException {
            this.name = name;
            this.config = config;
            this.type = type;
            this.defaultLang = defaultLang;
            this.writer = new JVSLuceneIndexWriter(config);
            this.writer.commit(); // Initialize empty index
            this.searcher = new JVSLuceneSearcher(config, type, defaultLang);
        }
        
        public String getName() {
            return name;
        }
        
        public JVSLuceneIndexWriter getWriter() {
            return writer;
        }
        
        public JVSLuceneSearcher getSearcher() {
            return searcher;
        }
        
        public void refreshSearcher() throws IOException {
            JVSLuceneSearcher newSearcher = searcher.refresh();
            if (newSearcher != searcher) {
                // Searcher was refreshed, update reference
                JVSLuceneSearcher oldSearcher = searcher;
                searcher = newSearcher;
                oldSearcher.close();
            }
        }
        
        public void close() throws IOException {
            if (writer != null) {
                writer.close();
            }
            if (searcher != null) {
                searcher.close();
            }
        }
    }
    
    /**
     * Create a new IndexManager.
     * 
     * @param defaultLang Default language for i18n fields (e.g., "en")
     */
    public IndexManager(String defaultLang) {
        this.defaultLang = defaultLang != null ? defaultLang : "en";
    }
    
    /**
     * Create or get an index by name.
     * 
     * @param name Index name
     * @param config Index configuration
     * @param type Optional JVS Type for field resolution
     * @return Index handle
     * @throws IOException if index creation fails
     */
    public IndexHandle createIndex(String name, IndexConfig config, Type type) throws IOException {
        if (indexes.containsKey(name)) {
            throw new IllegalArgumentException("Index '" + name + "' already exists");
        }
        
        IndexHandle handle = new IndexHandle(name, config, type, defaultLang);
        indexes.put(name, handle);
        return handle;
    }
    
    /**
     * Get an existing index by name.
     * 
     * @param name Index name
     * @return Index handle, or null if not found
     */
    public IndexHandle getIndex(String name) {
        return indexes.get(name);
    }
    
    /**
     * Check if an index exists.
     * 
     * @param name Index name
     * @return true if index exists
     */
    public boolean hasIndex(String name) {
        return indexes.containsKey(name);
    }
    
    /**
     * Get all index names.
     * 
     * @return Set of index names
     */
    public Set<String> getIndexNames() {
        return new HashSet<>(indexes.keySet());
    }
    
    /**
     * Index a document to a specific index.
     * 
     * @param indexName Index name
     * @param document JVS document
     * @throws IOException if indexing fails
     */
    public void indexDocument(String indexName, JVS document) throws IOException {
        IndexHandle handle = getRequiredIndex(indexName);
        handle.getWriter().indexDocument(document);
    }
    
    /**
     * Index multiple documents to a specific index.
     * 
     * @param indexName Index name
     * @param documents List of JVS documents
     * @throws IOException if indexing fails
     */
    public void indexDocuments(String indexName, List<JVS> documents) throws IOException {
        IndexHandle handle = getRequiredIndex(indexName);
        handle.getWriter().indexDocuments(documents);
    }
    
    /**
     * Commit changes to a specific index.
     * 
     * @param indexName Index name
     * @throws IOException if commit fails
     */
    public void commit(String indexName) throws IOException {
        IndexHandle handle = getRequiredIndex(indexName);
        handle.getWriter().commit();
        handle.refreshSearcher();
    }
    
    /**
     * Search within a single index.
     * 
     * @param indexName Index name
     * @param queryString Lucene query string
     * @param offset Starting offset
     * @param limit Maximum number of results
     * @param facetDims Facet dimensions (optional)
     * @return Search results
     * @throws IOException if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult search(String indexName, String queryString, int offset, int limit, 
                               List<String> facetDims) throws IOException, ParseException {
        IndexHandle handle = getRequiredIndex(indexName);
        handle.refreshSearcher();
        return handle.getSearcher().search(queryString, offset, limit, facetDims);
    }
    
    /**
     * Search across multiple indexes simultaneously.
     * Results are merged and scored together.
     * 
     * @param indexNames List of index names to search
     * @param queryString Lucene query string
     * @param offset Starting offset
     * @param limit Maximum number of results
     * @return Merged search results from all indexes
     * @throws IOException if search fails
     * @throws ParseException if query parsing fails
     */
    public SearchResult searchMultiple(List<String> indexNames, String queryString, 
                                       int offset, int limit) throws IOException, ParseException {
        if (indexNames == null || indexNames.isEmpty()) {
            throw new IllegalArgumentException("Must specify at least one index to search");
        }
        
        // Single index - use regular search
        if (indexNames.size() == 1) {
            return search(indexNames.get(0), queryString, offset, limit, null);
        }
        
        // Multiple indexes - use MultiReader
        List<IndexReader> readers = new ArrayList<>();
        List<JVSLuceneSearcher> searchers = new ArrayList<>();
        
        try {
            // Collect readers from all indexes
            for (String indexName : indexNames) {
                IndexHandle handle = getRequiredIndex(indexName);
                handle.refreshSearcher();
                readers.add(handle.getSearcher().getIndexSearcher().getIndexReader());
                searchers.add(handle.getSearcher());
            }
            
            // Create multi-reader
            IndexReader[] readerArray = readers.toArray(new IndexReader[0]);
            MultiReader multiReader = new MultiReader(readerArray);
            IndexSearcher multiSearcher = new IndexSearcher(multiReader);
            
            // Use first searcher's configuration for query parsing
            JVSLuceneSearcher firstSearcher = searchers.get(0);
            IndexHandle firstHandle = getRequiredIndex(indexNames.get(0));
            
            // Parse query using the config analyzer
            org.apache.lucene.search.Query query = new com.hitorro.index.query.JVSQueryParser(
                null, // type not needed for cross-index search
                "content",
                firstHandle.config.getDefaultAnalyzer(),
                defaultLang
            ).parse(queryString);
            
            // Execute search
            long startTime = System.currentTimeMillis();
            org.apache.lucene.search.TopDocs topDocs = multiSearcher.search(query, offset + limit);
            
            // Collect documents
            List<JVS> documents = new ArrayList<>();
            org.apache.lucene.search.ScoreDoc[] hits = topDocs.scoreDocs;
            int start = Math.min(offset, hits.length);
            int end = Math.min(offset + limit, hits.length);
            
            for (int i = start; i < end; i++) {
                org.apache.lucene.document.Document doc = multiSearcher.storedFields().document(hits[i].doc);
                // Convert to JVS (simplified - no field reconstruction)
                JVS jvs = new JVS();
                jvs.set("_score", hits[i].score);
                jvs.set("_index", getIndexNameForDoc(hits[i].doc, readers));
                
                // Add stored fields
                for (org.apache.lucene.index.IndexableField field : doc.getFields()) {
                    String name = field.name();
                    String value = field.stringValue();
                    if (value != null) {
                        try {
                            jvs.set(name, value);
                        } catch (Exception e) {
                            // Skip field if can't set
                        }
                    }
                }
                
                documents.add(jvs);
            }
            
            long searchTime = System.currentTimeMillis() - startTime;
            
            return SearchResult.builder()
                    .documents(documents)
                    .totalHits(topDocs.totalHits.value)
                    .query(queryString)
                    .offset(offset)
                    .limit(limit)
                    .searchTimeMs(searchTime)
                    .build();
                    
        } finally {
            // Don't close readers - they're managed by IndexHandles
        }
    }
    
    /**
     * Determine which index a document came from in a MultiReader search.
     */
    private String getIndexNameForDoc(int docId, List<IndexReader> readers) {
        int currentBase = 0;
        int index = 0;
        for (IndexReader reader : readers) {
            if (docId < currentBase + reader.maxDoc()) {
                return new ArrayList<>(indexes.keySet()).get(index);
            }
            currentBase += reader.maxDoc();
            index++;
        }
        return "unknown";
    }
    
    /**
     * Delete all documents from an index.
     * 
     * @param indexName Index name
     * @throws IOException if deletion fails
     */
    public void clearIndex(String indexName) throws IOException {
        IndexHandle handle = getRequiredIndex(indexName);
        handle.getWriter().deleteAll();
        handle.getWriter().commit();
        handle.refreshSearcher();
    }
    
    /**
     * Close and remove an index.
     * 
     * @param indexName Index name
     * @throws IOException if closing fails
     */
    public void closeIndex(String indexName) throws IOException {
        IndexHandle handle = indexes.remove(indexName);
        if (handle != null) {
            handle.close();
        }
    }
    
    /**
     * Get index handle, throwing exception if not found.
     */
    private IndexHandle getRequiredIndex(String indexName) {
        IndexHandle handle = indexes.get(indexName);
        if (handle == null) {
            throw new IllegalArgumentException("Index '" + indexName + "' does not exist");
        }
        return handle;
    }
    
    @Override
    public void close() throws IOException {
        IOException firstException = null;
        
        for (IndexHandle handle : indexes.values()) {
            try {
                handle.close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        
        indexes.clear();
        
        if (firstException != null) {
            throw firstException;
        }
    }
}
