/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.embeddings;

import org.apache.lucene.index.VectorSimilarityFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VectorSimilarity enum.
 */
public class VectorSimilarityTest {
    
    @Test
    @DisplayName("Test all similarity functions are present")
    public void testAllFunctionsPresent() {
        assertEquals(4, VectorSimilarity.values().length);
        
        assertNotNull(VectorSimilarity.COSINE);
        assertNotNull(VectorSimilarity.DOT_PRODUCT);
        assertNotNull(VectorSimilarity.EUCLIDEAN);
        assertNotNull(VectorSimilarity.MAXIMUM_INNER_PRODUCT);
    }
    
    @Test
    @DisplayName("Test toLuceneFunction conversion")
    public void testToLuceneFunction() {
        assertEquals(VectorSimilarityFunction.COSINE, 
                    VectorSimilarity.COSINE.toLuceneFunction());
        
        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, 
                    VectorSimilarity.DOT_PRODUCT.toLuceneFunction());
        
        assertEquals(VectorSimilarityFunction.EUCLIDEAN, 
                    VectorSimilarity.EUCLIDEAN.toLuceneFunction());
        
        assertEquals(VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT, 
                    VectorSimilarity.MAXIMUM_INNER_PRODUCT.toLuceneFunction());
    }
    
    @Test
    @DisplayName("Test fromLuceneFunction conversion")
    public void testFromLuceneFunction() {
        assertEquals(VectorSimilarity.COSINE, 
                    VectorSimilarity.fromLuceneFunction(VectorSimilarityFunction.COSINE));
        
        assertEquals(VectorSimilarity.DOT_PRODUCT, 
                    VectorSimilarity.fromLuceneFunction(VectorSimilarityFunction.DOT_PRODUCT));
        
        assertEquals(VectorSimilarity.EUCLIDEAN, 
                    VectorSimilarity.fromLuceneFunction(VectorSimilarityFunction.EUCLIDEAN));
        
        assertEquals(VectorSimilarity.MAXIMUM_INNER_PRODUCT, 
                    VectorSimilarity.fromLuceneFunction(VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT));
    }
    
    @Test
    @DisplayName("Test round-trip conversion")
    public void testRoundTripConversion() {
        for (VectorSimilarity similarity : VectorSimilarity.values()) {
            VectorSimilarityFunction luceneFunc = similarity.toLuceneFunction();
            VectorSimilarity converted = VectorSimilarity.fromLuceneFunction(luceneFunc);
            assertEquals(similarity, converted);
        }
    }
}
