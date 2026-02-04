/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.embeddings;

import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.index.search.EmbeddingSearchRequest;
import com.hitorro.index.search.HybridSearchRequest;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for embedding indexing and search functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EmbeddingSearchTest {
    
    private static IndexConfig config;
    private static JVSLuceneIndexWriter indexWriter;
    private static JVSLuceneSearcher searcher;
    private static final int VECTOR_DIM = 4; // Small dimension for testing
    
    @BeforeAll
    public static void setUp() throws Exception {
        // Create embedding config
        EmbeddingConfig embeddingConfig = EmbeddingConfig.builder()
                .fieldName("_embedding")
                .dimension(VECTOR_DIM)
                .similarity(VectorSimilarity.COSINE)
                .fieldType(EmbeddingFieldType.FLOAT_VECTOR)
                .hnswM(16)
                .hnswEfConstruction(100)
                .build();
        
        // Create index config with embeddings
        config = IndexConfig.builder()
                .inMemory()
                .embeddings(embeddingConfig)
                .build();
        
        // Create index writer
        indexWriter = new JVSLuceneIndexWriter(config);
        
        // Index test documents with embeddings
        List<JVS> documents = createTestDocuments();
        for (JVS doc : documents) {
            indexWriter.indexDocument(doc);
        }
        indexWriter.commit();
        
        // Create searcher
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();
    }
    
    @AfterAll
    public static void tearDown() throws IOException {
        if (searcher != null) {
            searcher.close();
        }
        if (indexWriter != null) {
            indexWriter.close();
        }
    }
    
    private static List<JVS> createTestDocuments() {
        List<JVS> docs = new ArrayList<>();
        
        // Document 1: Technology
        JVS doc1 = JVS.read("{\"id\": {\"did\": \"doc1\"}, \"title\": \"Machine Learning\", \"content\": \"AI and ML technologies\", \"_embedding\": [0.9, 0.1, 0.1, 0.1]}");
        docs.add(doc1);
        
        // Document 2: Technology (similar to doc1)
        JVS doc2 = JVS.read("{\"id\": {\"did\": \"doc2\"}, \"title\": \"Deep Learning\", \"content\": \"Neural networks and AI\", \"_embedding\": [0.85, 0.15, 0.1, 0.1]}");
        docs.add(doc2);
        
        // Document 3: Sports
        JVS doc3 = JVS.read("{\"id\": {\"did\": \"doc3\"}, \"title\": \"Football\", \"content\": \"Sports and athletics\", \"_embedding\": [0.1, 0.9, 0.1, 0.1]}");
        docs.add(doc3);
        
        // Document 4: Food
        JVS doc4 = JVS.read("{\"id\": {\"did\": \"doc4\"}, \"title\": \"Pizza Recipe\", \"content\": \"Cooking and food\", \"_embedding\": [0.1, 0.1, 0.9, 0.1]}");
        docs.add(doc4);
        
        // Document 5: Travel
        JVS doc5 = JVS.read("{\"id\": {\"did\": \"doc5\"}, \"title\": \"Paris Guide\", \"content\": \"Travel and tourism\", \"_embedding\": [0.1, 0.1, 0.1, 0.9]}");
        docs.add(doc5);
        
        return docs;
    }
    
    @Test
    @Order(1)
    @DisplayName("Test basic embedding search")
    public void testBasicEmbeddingSearch() throws IOException {
        // Query vector similar to technology documents
        float[] queryVector = new float[]{0.88f, 0.12f, 0.1f, 0.1f};
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryVector(queryVector)
                .k(3)
                .build();
        
        SearchResult result = searcher.searchByEmbedding(request);
        
        assertNotNull(result);
        assertEquals(3, result.getDocuments().size());
        
        // First result should be most similar (doc1 or doc2)
        JVS firstDoc = result.getDocuments().get(0);
        Object idObj = firstDoc.get("_uid");
        if (idObj == null) {
            idObj = firstDoc.get("id.did");
        }
        assertNotNull(idObj, "Document should have an ID");
        // Extract text value properly from JsonNode
        String firstDocId = idObj instanceof com.fasterxml.jackson.databind.JsonNode 
                ? ((com.fasterxml.jackson.databind.JsonNode) idObj).asText() 
                : idObj.toString();
        assertTrue(firstDocId.equals("doc1") || firstDocId.equals("doc2"),
                "First result should be a technology document, but got: " + firstDocId);
    }
    
    @Test
    @Order(2)
    @DisplayName("Test KNN with k parameter")
    public void testKnnParameter() throws IOException {
        float[] queryVector = new float[]{0.9f, 0.1f, 0.1f, 0.1f};
        
        // Request top 2
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryVector(queryVector)
                .k(2)
                .build();
        
        SearchResult result = searcher.searchByEmbedding(request);
        
        assertNotNull(result);
        assertEquals(2, result.getDocuments().size(), "Should return exactly k results");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test hybrid search with RRF")
    public void testHybridSearchRRF() throws Exception {
        String textQuery = "Machine Learning";
        float[] queryVector = new float[]{0.88f, 0.12f, 0.1f, 0.1f};
        
        HybridSearchRequest request = HybridSearchRequest.builder()
                .textQuery(textQuery)
                .queryVector(queryVector)
                .k(3)
                .strategy(HybridSearchRequest.CombinationStrategy.RERANK_RRF)
                .build();
        
        SearchResult result = searcher.searchHybrid(request);
        
        assertNotNull(result);
        assertTrue(result.getDocuments().size() > 0, "Should return results");
        assertTrue(result.getDocuments().size() <= 3, "Should not exceed k results");
    }
    
    @Test
    @Order(4)
    @DisplayName("Test hybrid search with weighted sum")
    public void testHybridSearchWeightedSum() throws Exception {
        String textQuery = "Football";
        float[] queryVector = new float[]{0.1f, 0.88f, 0.1f, 0.1f}; // Sports vector
        
        // Alpha = 0.7 means 70% text, 30% vector
        HybridSearchRequest request = HybridSearchRequest.builder()
                .textQuery(textQuery)
                .queryVector(queryVector)
                .k(3)
                .strategy(HybridSearchRequest.CombinationStrategy.MERGE_SUM_SCORE)
                .alpha(0.7)
                .build();
        
        SearchResult result = searcher.searchHybrid(request);
        
        assertNotNull(result);
        assertTrue(result.getDocuments().size() > 0);
    }
    
    @Test
    @Order(5)
    @DisplayName("Test different similarity functions")
    public void testDifferentSimilarityFunctions() throws Exception {
        // This test validates that different similarity functions can be configured
        // For a more comprehensive test, you'd create multiple indexes with different
        // similarity functions and compare results
        
        EmbeddingConfig euclideanConfig = EmbeddingConfig.builder()
                .fieldName("_embedding")
                .dimension(VECTOR_DIM)
                .similarity(VectorSimilarity.EUCLIDEAN)
                .build();
        
        assertNotNull(euclideanConfig);
        assertEquals(VectorSimilarity.EUCLIDEAN, euclideanConfig.getSimilarity());
        
        EmbeddingConfig dotProductConfig = EmbeddingConfig.builder()
                .fieldName("_embedding")
                .dimension(VECTOR_DIM)
                .similarity(VectorSimilarity.DOT_PRODUCT)
                .build();
        
        assertNotNull(dotProductConfig);
        assertEquals(VectorSimilarity.DOT_PRODUCT, dotProductConfig.getSimilarity());
    }
    
    @Test
    @Order(6)
    @DisplayName("Test embedding config validation")
    public void testEmbeddingConfigValidation() {
        // Test dimension validation
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder()
                    .dimension(0)
                    .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder()
                    .dimension(-1)
                    .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder()
                    .dimension(5000) // Too large
                    .build();
        });
        
        // Test HNSW parameter validation
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder()
                    .hnswM(1) // Too small
                    .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder()
                    .hnswEfConstruction(0) // Too small
                    .build();
        });
    }
    
    @Test
    @Order(7)
    @DisplayName("Test search request validation")
    public void testSearchRequestValidation() {
        // Test missing vector
        assertThrows(IllegalStateException.class, () -> {
            EmbeddingSearchRequest.builder()
                    .k(10)
                    .build();
        });
        
        // Test invalid k
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingSearchRequest.builder()
                    .queryVector(new float[]{0.1f, 0.2f})
                    .k(0)
                    .build();
        });
        
        // Test hybrid request validation
        assertThrows(IllegalStateException.class, () -> {
            HybridSearchRequest.builder()
                    .queryVector(new float[]{0.1f, 0.2f})
                    // Missing text query
                    .build();
        });
        
        assertThrows(IllegalStateException.class, () -> {
            HybridSearchRequest.builder()
                    .textQuery("test")
                    // Missing vector
                    .build();
        });
    }
}
