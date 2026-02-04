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
package com.hitorro.index.embeddings;

import org.apache.lucene.index.VectorSimilarityFunction;

/**
 * Vector similarity functions for KNN/ANN search.
 * Wraps Lucene's VectorSimilarityFunction for easier configuration.
 */
public enum VectorSimilarity {
    /**
     * Cosine similarity: measures angle between vectors.
     * Range: [-1, 1] where 1 is most similar.
     * Best for: Most embedding models (OpenAI, sentence-transformers, etc.)
     * Formula: (A · B) / (||A|| * ||B||)
     */
    COSINE(VectorSimilarityFunction.COSINE),
    
    /**
     * Dot product similarity: measures projection of one vector onto another.
     * Range: [-∞, +∞] where higher is more similar.
     * Best for: When vectors are normalized, equivalent to cosine.
     * Formula: A · B
     */
    DOT_PRODUCT(VectorSimilarityFunction.DOT_PRODUCT),
    
    /**
     * Euclidean distance (L2): measures straight-line distance between vectors.
     * Range: [0, +∞] where 0 is most similar (identical).
     * Best for: When absolute distance matters more than angle.
     * Formula: sqrt(sum((A_i - B_i)^2))
     * Note: Lucene converts this to similarity score (1 / (1 + distance))
     */
    EUCLIDEAN(VectorSimilarityFunction.EUCLIDEAN),
    
    /**
     * Maximum Inner Product: optimized for dot product with normalization.
     * Range: [-∞, +∞] where higher is more similar.
     * Best for: Maximum Inner Product Search (MIPS) use cases.
     * Formula: -A · B (negated for max-heap optimization)
     */
    MAXIMUM_INNER_PRODUCT(VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT);
    
    private final VectorSimilarityFunction luceneFunction;
    
    VectorSimilarity(VectorSimilarityFunction luceneFunction) {
        this.luceneFunction = luceneFunction;
    }
    
    /**
     * Get the underlying Lucene VectorSimilarityFunction.
     */
    public VectorSimilarityFunction toLuceneFunction() {
        return luceneFunction;
    }
    
    /**
     * Convert from Lucene VectorSimilarityFunction to our enum.
     */
    public static VectorSimilarity fromLuceneFunction(VectorSimilarityFunction function) {
        for (VectorSimilarity similarity : values()) {
            if (similarity.luceneFunction == function) {
                return similarity;
            }
        }
        throw new IllegalArgumentException("Unknown VectorSimilarityFunction: " + function);
    }
}
