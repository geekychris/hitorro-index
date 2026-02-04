/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.embeddings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmbeddingConfig builder and validation.
 */
public class EmbeddingConfigTest {
    
    @Test
    @DisplayName("Test default values")
    public void testDefaults() {
        EmbeddingConfig config = EmbeddingConfig.builder().build();
        
        assertEquals("_embedding", config.getFieldName());
        assertEquals(384, config.getDimension());
        assertEquals(VectorSimilarity.COSINE, config.getSimilarity());
        assertEquals(EmbeddingFieldType.FLOAT_VECTOR, config.getFieldType());
        assertEquals(16, config.getHnswM());
        assertEquals(100, config.getHnswEfConstruction());
        assertTrue(config.isEnabled());
    }
    
    @Test
    @DisplayName("Test custom configuration")
    public void testCustomConfig() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .fieldName("custom_embedding")
                .dimension(768)
                .similarity(VectorSimilarity.DOT_PRODUCT)
                .fieldType(EmbeddingFieldType.BYTE_VECTOR)
                .hnswM(32)
                .hnswEfConstruction(200)
                .enabled(false)
                .build();
        
        assertEquals("custom_embedding", config.getFieldName());
        assertEquals(768, config.getDimension());
        assertEquals(VectorSimilarity.DOT_PRODUCT, config.getSimilarity());
        assertEquals(EmbeddingFieldType.BYTE_VECTOR, config.getFieldType());
        assertEquals(32, config.getHnswM());
        assertEquals(200, config.getHnswEfConstruction());
        assertFalse(config.isEnabled());
    }
    
    @Test
    @DisplayName("Test field name validation")
    public void testFieldNameValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().fieldName(null).build();
        }, "Null field name should throw exception");
        
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().fieldName("").build();
        }, "Empty field name should throw exception");
        
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().fieldName("   ").build();
        }, "Whitespace-only field name should throw exception");
    }
    
    @Test
    @DisplayName("Test dimension validation")
    public void testDimensionValidation() {
        // Test negative dimension
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().dimension(-1).build();
        });
        
        // Test zero dimension
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().dimension(0).build();
        });
        
        // Test too large dimension
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().dimension(5000).build();
        });
        
        // Test valid dimensions
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().dimension(1).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().dimension(384).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().dimension(4096).build();
        });
    }
    
    @Test
    @DisplayName("Test similarity validation")
    public void testSimilarityValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().similarity(null).build();
        });
        
        // Test all valid similarity functions
        for (VectorSimilarity sim : VectorSimilarity.values()) {
            assertDoesNotThrow(() -> {
                EmbeddingConfig.builder().similarity(sim).build();
            });
        }
    }
    
    @Test
    @DisplayName("Test field type validation")
    public void testFieldTypeValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().fieldType(null).build();
        });
        
        // Test both field types
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().fieldType(EmbeddingFieldType.FLOAT_VECTOR).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().fieldType(EmbeddingFieldType.BYTE_VECTOR).build();
        });
    }
    
    @Test
    @DisplayName("Test HNSW M parameter validation")
    public void testHnswMValidation() {
        // Test below minimum
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().hnswM(1).build();
        });
        
        // Test above maximum
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().hnswM(97).build();
        });
        
        // Test valid values
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswM(2).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswM(16).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswM(96).build();
        });
    }
    
    @Test
    @DisplayName("Test HNSW efConstruction parameter validation")
    public void testHnswEfConstructionValidation() {
        // Test below minimum
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().hnswEfConstruction(0).build();
        });
        
        // Test above maximum
        assertThrows(IllegalArgumentException.class, () -> {
            EmbeddingConfig.builder().hnswEfConstruction(3201).build();
        });
        
        // Test valid values
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswEfConstruction(1).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswEfConstruction(100).build();
        });
        
        assertDoesNotThrow(() -> {
            EmbeddingConfig.builder().hnswEfConstruction(3200).build();
        });
    }
    
    @Test
    @DisplayName("Test toString")
    public void testToString() {
        EmbeddingConfig config = EmbeddingConfig.builder()
                .fieldName("test_embedding")
                .dimension(512)
                .build();
        
        String str = config.toString();
        assertTrue(str.contains("test_embedding"));
        assertTrue(str.contains("512"));
        assertTrue(str.contains("COSINE"));
    }
    
    @Test
    @DisplayName("Test common dimension configurations")
    public void testCommonDimensions() {
        // all-MiniLM-L6-v2
        EmbeddingConfig config384 = EmbeddingConfig.builder()
                .dimension(384)
                .build();
        assertEquals(384, config384.getDimension());
        
        // BERT-base, all-mpnet-base-v2
        EmbeddingConfig config768 = EmbeddingConfig.builder()
                .dimension(768)
                .build();
        assertEquals(768, config768.getDimension());
        
        // OpenAI text-embedding-ada-002
        EmbeddingConfig config1536 = EmbeddingConfig.builder()
                .dimension(1536)
                .build();
        assertEquals(1536, config1536.getDimension());
        
        // OpenAI text-embedding-3-large
        EmbeddingConfig config3072 = EmbeddingConfig.builder()
                .dimension(3072)
                .build();
        assertEquals(3072, config3072.getDimension());
    }
    
    @Test
    @DisplayName("Test HNSW parameter recommendations")
    public void testHnswRecommendations() {
        // Small dataset
        EmbeddingConfig small = EmbeddingConfig.builder()
                .hnswM(8)
                .hnswEfConstruction(100)
                .build();
        assertEquals(8, small.getHnswM());
        assertEquals(100, small.getHnswEfConstruction());
        
        // Balanced
        EmbeddingConfig balanced = EmbeddingConfig.builder()
                .hnswM(16)
                .hnswEfConstruction(200)
                .build();
        assertEquals(16, balanced.getHnswM());
        assertEquals(200, balanced.getHnswEfConstruction());
        
        // High quality
        EmbeddingConfig highQuality = EmbeddingConfig.builder()
                .hnswM(32)
                .hnswEfConstruction(400)
                .build();
        assertEquals(32, highQuality.getHnswM());
        assertEquals(400, highQuality.getHnswEfConstruction());
    }
}
