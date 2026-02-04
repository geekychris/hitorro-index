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

import org.apache.lucene.search.Query;

/**
 * Request for embedding-based (vector) search.
 * Supports both KNN and ANN search with optional filtering.
 */
public class EmbeddingSearchRequest {
    private final float[] queryVector;
    private final byte[] queryByteVector;
    private final int k;
    private final Query filter;
    private final boolean includeScores;
    
    private EmbeddingSearchRequest(Builder builder) {
        this.queryVector = builder.queryVector;
        this.queryByteVector = builder.queryByteVector;
        this.k = builder.k;
        this.filter = builder.filter;
        this.includeScores = builder.includeScores;
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
    
    public Query getFilter() {
        return filter;
    }
    
    public boolean isIncludeScores() {
        return includeScores;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float[] queryVector;
        private byte[] queryByteVector;
        private int k = 10;
        private Query filter = null;
        private boolean includeScores = true;
        
        /**
         * Set the query vector (float array).
         * Use this for FLOAT_VECTOR indexes.
         */
        public Builder queryVector(float[] vector) {
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("Query vector cannot be null or empty");
            }
            this.queryVector = vector;
            this.queryByteVector = null; // Clear byte vector if set
            return this;
        }
        
        /**
         * Set the query vector (byte array).
         * Use this for BYTE_VECTOR indexes.
         */
        public Builder queryByteVector(byte[] vector) {
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("Query byte vector cannot be null or empty");
            }
            this.queryByteVector = vector;
            this.queryVector = null; // Clear float vector if set
            return this;
        }
        
        /**
         * Set k (number of nearest neighbors to retrieve).
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
         * Set optional filter query to pre-filter documents before KNN search.
         * Only documents matching the filter will be considered.
         */
        public Builder filter(Query filter) {
            this.filter = filter;
            return this;
        }
        
        /**
         * Whether to include similarity scores in results.
         * Default: true
         */
        public Builder includeScores(boolean includeScores) {
            this.includeScores = includeScores;
            return this;
        }
        
        public EmbeddingSearchRequest build() {
            if (queryVector == null && queryByteVector == null) {
                throw new IllegalStateException("Either queryVector or queryByteVector must be set");
            }
            return new EmbeddingSearchRequest(this);
        }
    }
}
