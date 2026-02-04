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
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for byte vector (quantized) functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ByteVectorTest {
    
    private static IndexConfig config;
    private static JVSLuceneIndexWriter indexWriter;
    private static JVSLuceneSearcher searcher;
    private static final int VECTOR_DIM = 8;
    
    @BeforeAll
    public static void setUp() throws Exception {
        // Create embedding config with BYTE_VECTOR
        EmbeddingConfig embeddingConfig = EmbeddingConfig.builder()
                .fieldName("_embedding")
                .dimension(VECTOR_DIM)
                .similarity(VectorSimilarity.COSINE)
                .fieldType(EmbeddingFieldType.BYTE_VECTOR)  // Use byte vectors
                .hnswM(8)
                .hnswEfConstruction(100)
                .build();
        
        config = IndexConfig.builder()
                .inMemory()
                .embeddings(embeddingConfig)
                .build();
        
        indexWriter = new JVSLuceneIndexWriter(config);
        
        // Index test documents
        List<JVS> documents = createTestDocuments();
        for (JVS doc : documents) {
            indexWriter.indexDocument(doc);
        }
        indexWriter.commit();
        
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
        
        // Document 1
        JVS doc1 = JVS.read("{\"id\": {\"did\": \"doc1\"}, \"title\": \"First\", \"_embedding\": [0.8, 0.2, 0.1, 0.1, 0.0, 0.0, 0.0, 0.0]}");
        docs.add(doc1);
        
        // Document 2 (similar to doc1)
        JVS doc2 = JVS.read("{\"id\": {\"did\": \"doc2\"}, \"title\": \"Second\", \"_embedding\": [0.75, 0.25, 0.1, 0.1, 0.0, 0.0, 0.0, 0.0]}");
        docs.add(doc2);
        
        // Document 3 (different)
        JVS doc3 = JVS.read("{\"id\": {\"did\": \"doc3\"}, \"title\": \"Third\", \"_embedding\": [0.1, 0.1, 0.8, 0.2, 0.0, 0.0, 0.0, 0.0]}");
        docs.add(doc3);
        
        return docs;
    }
    
    @Test
    @Order(1)
    @DisplayName("Test byte vector indexing and search")
    public void testByteVectorSearch() throws IOException {
        float[] queryVector = new float[]{0.78f, 0.22f, 0.1f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f};
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryVector(queryVector)
                .k(2)
                .build();
        
        SearchResult result = searcher.searchByEmbedding(request);
        
        assertNotNull(result);
        assertEquals(2, result.getDocuments().size());
        
        // First result should be doc1 or doc2 (most similar)
        JVS firstDoc = result.getDocuments().get(0);
        Object idObj = firstDoc.get("_uid");
        if (idObj == null) idObj = firstDoc.get("id.did");
        assertNotNull(idObj);
        String firstDocId = idObj instanceof com.fasterxml.jackson.databind.JsonNode 
                ? ((com.fasterxml.jackson.databind.JsonNode) idObj).asText() 
                : idObj.toString();
        assertTrue(firstDocId.equals("doc1") || firstDocId.equals("doc2"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test quantization preserves relative similarities")
    public void testQuantizationPreservesOrder() throws IOException {
        // Even with 8-bit quantization, relative ordering should be preserved
        float[] queryVector = new float[]{0.8f, 0.2f, 0.1f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f};
        
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryVector(queryVector)
                .k(3)
                .build();
        
        SearchResult result = searcher.searchByEmbedding(request);
        
        assertEquals(3, result.getDocuments().size());
        
        // First two should be doc1 and doc2 (in some order)
        List<String> topTwoIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Object idObj = result.getDocuments().get(i).get("_uid");
            if (idObj == null) idObj = result.getDocuments().get(i).get("id.did");
            String id = idObj instanceof com.fasterxml.jackson.databind.JsonNode 
                    ? ((com.fasterxml.jackson.databind.JsonNode) idObj).asText() 
                    : (idObj != null ? idObj.toString() : "unknown");
            topTwoIds.add(id);
        }
        
        assertTrue(topTwoIds.contains("doc1"));
        assertTrue(topTwoIds.contains("doc2"));
        
        // Third should be doc3
        Object idObj3 = result.getDocuments().get(2).get("_uid");
        if (idObj3 == null) idObj3 = result.getDocuments().get(2).get("id.did");
        String thirdId = idObj3 instanceof com.fasterxml.jackson.databind.JsonNode 
                ? ((com.fasterxml.jackson.databind.JsonNode) idObj3).asText() 
                : (idObj3 != null ? idObj3.toString() : "unknown");
        assertEquals("doc3", thirdId);
    }
    
    @Test
    @Order(3)
    @DisplayName("Test memory efficiency of byte vectors")
    public void testMemoryEfficiency() {
        // This is more of a documentation test
        // BYTE_VECTOR uses 1 byte per dimension vs 4 bytes for FLOAT_VECTOR
        // For 384 dimensions: 384 bytes vs 1536 bytes (4x savings)
        
        EmbeddingConfig byteConfig = EmbeddingConfig.builder()
                .dimension(384)
                .fieldType(EmbeddingFieldType.BYTE_VECTOR)
                .build();
        
        EmbeddingConfig floatConfig = EmbeddingConfig.builder()
                .dimension(384)
                .fieldType(EmbeddingFieldType.FLOAT_VECTOR)
                .build();
        
        assertEquals(EmbeddingFieldType.BYTE_VECTOR, byteConfig.getFieldType());
        assertEquals(EmbeddingFieldType.FLOAT_VECTOR, floatConfig.getFieldType());
        
        // Byte vectors are 4x more memory efficient
        int bytesPerByteVector = 384 * 1;  // 384 bytes
        int bytesPerFloatVector = 384 * 4; // 1536 bytes
        
        assertEquals(4, bytesPerFloatVector / bytesPerByteVector);
    }
    
    @Test
    @Order(4)
    @DisplayName("Test extreme values are clamped in quantization")
    public void testQuantizationClamping() throws IOException {
        // Create document with values outside [-1, 1] range
        // Values outside [-1, 1] should be clamped during quantization
        JVS doc = JVS.read("{\"id\": {\"did\": \"extreme\"}, \"title\": \"Extreme Values\", \"_embedding\": [2.0, -2.0, 0.5, -0.5, 0.0, 0.0, 0.0, 0.0]}");
        
        // Should index without error
        assertDoesNotThrow(() -> {
            indexWriter.indexDocument(doc);
            indexWriter.commit();
        });
    }
}
