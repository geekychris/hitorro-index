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

/**
 * Request for hybrid search combining traditional text search with vector similarity.
 */
public class HybridSearchRequest {
    private final String textQuery;
    private final float[] queryVector;
    private final byte[] queryByteVector;
    private final int k;
    private final CombinationStrategy strategy;
    private final double alpha;
    
    private HybridSearchRequest(Builder builder) {
        this.textQuery = builder.textQuery;
        this.queryVector = builder.queryVector;
        this.queryByteVector = builder.queryByteVector;
        this.k = builder.k;
        this.strategy = builder.strategy;
        this.alpha = builder.alpha;
    }
    
    public String getTextQuery() {
        return textQuery;
    }
    
    public float[] getQueryVector() {
        return queryVector;
    }
    
    public byte[] getQueryByteVector() {
        return queryByteVector;
    }
    
    public boolean isFloatVector() {
        return queryVector != null;
    }
    
    public boolean isByteVector() {
        return queryByteVector != null;
    }
    
    public int getK() {
        return k;
    }
    
    public CombinationStrategy getStrategy() {
        return strategy;
    }
    
    public double getAlpha() {
        return alpha;
    }
    
    /**
     * Strategy for combining text and vector search results.
     */
    public enum CombinationStrategy {
        /**
         * Take maximum score per document across text and vector results.
         * Final score = max(textScore, vectorScore)
         */
        MERGE_MAX_SCORE,
        
        /**
         * Weighted sum of normalized scores.
         * Final score = alpha * textScore + (1 - alpha) * vectorScore
         * Use alpha parameter to control weighting (0.0 = pure vector, 1.0 = pure text)
         */
        MERGE_SUM_SCORE,
        
        /**
         * Reciprocal Rank Fusion (RRF).
         * Combines rankings from text and vector search.
         * Final score = 1/(k + rank_text) + 1/(k + rank_vector)
         * k is a constant (typically 60) that prevents division by zero
         */
        RERANK_RRF
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String textQuery;
        private float[] queryVector;
        private byte[] queryByteVector;
        private int k = 10;
        private CombinationStrategy strategy = CombinationStrategy.RERANK_RRF;
        private double alpha = 0.5; // Equal weight for text and vector by default
        
        /**
         * Set the text query string for traditional search.
         */
        public Builder textQuery(String textQuery) {
            if (textQuery == null || textQuery.trim().isEmpty()) {
                throw new IllegalArgumentException("Text query cannot be null or empty");
            }
            this.textQuery = textQuery;
            return this;
        }
        
        /**
         * Set the query vector (float array) for semantic search.
         */
        public Builder queryVector(float[] vector) {
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("Query vector cannot be null or empty");
            }
            this.queryVector = vector;
            this.queryByteVector = null;
            return this;
        }
        
        /**
         * Set the query vector (byte array) for semantic search.
         */
        public Builder queryByteVector(byte[] vector) {
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("Query byte vector cannot be null or empty");
            }
            this.queryByteVector = vector;
            this.queryVector = null;
            return this;
        }
        
        /**
         * Set k (number of results to retrieve from each search type).
         * Default: 10
         */
        public Builder k(int k) {
            if (k <= 0) {
                throw new IllegalArgumentException("k must be positive, got: " + k);
            }
            this.k = k;
            return this;
        }
        
        /**
         * Set the combination strategy.
         * Default: RERANK_RRF (Reciprocal Rank Fusion)
         */
        public Builder strategy(CombinationStrategy strategy) {
            if (strategy == null) {
                throw new IllegalArgumentException("Strategy cannot be null");
            }
            this.strategy = strategy;
            return this;
        }
        
        /**
         * Set alpha (weight for text vs vector search).
         * Only used with MERGE_SUM_SCORE strategy.
         * 0.0 = pure vector search
         * 1.0 = pure text search
         * 0.5 = equal weight (default)
         */
        public Builder alpha(double alpha) {
            if (alpha < 0.0 || alpha > 1.0) {
                throw new IllegalArgumentException("Alpha must be in range [0.0, 1.0], got: " + alpha);
            }
            this.alpha = alpha;
            return this;
        }
        
        public HybridSearchRequest build() {
            if (textQuery == null || textQuery.trim().isEmpty()) {
                throw new IllegalStateException("Text query must be set");
            }
            if (queryVector == null && queryByteVector == null) {
                throw new IllegalStateException("Either queryVector or queryByteVector must be set");
            }
            return new HybridSearchRequest(this);
        }
    }
}
