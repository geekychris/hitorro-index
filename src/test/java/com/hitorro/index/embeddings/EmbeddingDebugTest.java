/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.embeddings;

import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.index.search.EmbeddingSearchRequest;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to verify embedding indexing and search.
 */
public class EmbeddingDebugTest {
    
    @Test
    public void testEmbeddingIndexAndSearch() throws Exception {
        System.out.println("=== Starting Embedding Debug Test ===");
        
        // Create embedding config
        EmbeddingConfig embeddingConfig = EmbeddingConfig.builder()
                .fieldName("_embedding")
                .dimension(4)
                .similarity(VectorSimilarity.COSINE)
                .fieldType(EmbeddingFieldType.FLOAT_VECTOR)
                .hnswM(16)
                .hnswEfConstruction(100)
                .build();
        
        System.out.println("Embedding config: " + embeddingConfig);
        
        // Create index config
        IndexConfig config = IndexConfig.builder()
                .inMemory()
                .embeddings(embeddingConfig)
                .build();
        
        System.out.println("Has embeddings: " + config.hasEmbeddings());
        
        // Create index writer
        JVSLuceneIndexWriter indexWriter = new JVSLuceneIndexWriter(config);
        
        // Create document with embedding in JSON
        JVS doc = JVS.read("{\"title\": \"Test Document\", \"_embedding\": [0.1, 0.2, 0.3, 0.4]}");
        
        System.out.println("Document title: " + doc.get("title"));
        System.out.println("Document embedding: " + doc.get("_embedding"));
        System.out.println("Embedding class: " + doc.get("_embedding").getClass().getName());
        
        // Index document
        System.out.println("Indexing document...");
        indexWriter.indexDocument(doc);
        indexWriter.commit();
        System.out.println("Document indexed and committed");
        
        // Create searcher
        JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();
        
        // Search
        float[] queryVector = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryVector(queryVector)
                .k(10)
                .build();
        
        System.out.println("Searching with query vector: [0.1, 0.2, 0.3, 0.4]");
        SearchResult result = searcher.searchByEmbedding(request);
        
        System.out.println("Total hits: " + result.getTotalHits());
        System.out.println("Documents returned: " + result.getDocuments().size());
        
        for (JVS resultDoc : result.getDocuments()) {
            System.out.println("  - " + resultDoc.get("title"));
        }
        
        searcher.close();
        indexWriter.close();
        
        // Assert we got results
        assertTrue(result.getDocuments().size() > 0, "Should have found at least one document");
        
        System.out.println("=== Test Complete ===");
    }
}
