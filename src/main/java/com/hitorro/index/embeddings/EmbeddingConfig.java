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

/**
 * Configuration for vector embedding support in an index.
 * 
 * Usage:
 * <pre>
 * EmbeddingConfig config = EmbeddingConfig.builder()
 *     .fieldName("_embedding")
 *     .dimension(384)
 *     .similarity(VectorSimilarity.COSINE)
 *     .build();
 * </pre>
 */
public class EmbeddingConfig {
    private final String fieldName;
    private final int dimension;
    private final VectorSimilarity similarity;
    private final EmbeddingFieldType fieldType;
    private final int hnswM;
    private final int hnswEfConstruction;
    private final boolean enabled;
    
    private EmbeddingConfig(Builder builder) {
        this.fieldName = builder.fieldName;
        this.dimension = builder.dimension;
        this.similarity = builder.similarity;
        this.fieldType = builder.fieldType;
        this.hnswM = builder.hnswM;
        this.hnswEfConstruction = builder.hnswEfConstruction;
        this.enabled = builder.enabled;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public int getDimension() {
        return dimension;
    }
    
    public VectorSimilarity getSimilarity() {
        return similarity;
    }
    
    public EmbeddingFieldType getFieldType() {
        return fieldType;
    }
    
    /**
     * Get HNSW M parameter (max connections per node in the graph).
     * Higher values = better recall but more memory and slower indexing.
     * Typical range: 4-48, default 16.
     */
    public int getHnswM() {
        return hnswM;
    }
    
    /**
     * Get HNSW efConstruction parameter (search depth during index build).
     * Higher values = better graph quality but slower indexing.
     * Typical range: 100-800, default 100.
     */
    public int getHnswEfConstruction() {
        return hnswEfConstruction;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String fieldName = "_embedding";
        private int dimension = 384; // Default to all-MiniLM-L6-v2 dimension
        private VectorSimilarity similarity = VectorSimilarity.COSINE;
        private EmbeddingFieldType fieldType = EmbeddingFieldType.FLOAT_VECTOR;
        private int hnswM = 16;
        private int hnswEfConstruction = 100;
        private boolean enabled = true;
        
        /**
         * Set the field name where embeddings are stored in JVS documents.
         * Default: "_embedding"
         */
        public Builder fieldName(String fieldName) {
            if (fieldName == null || fieldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Field name cannot be null or empty");
            }
            this.fieldName = fieldName;
            return this;
        }
        
        /**
         * Set the vector dimension.
         * Common dimensions:
         * - 384: all-MiniLM-L6-v2, all-mpnet-base-v2
         * - 768: BERT-base, sentence-transformers medium models
         * - 1536: OpenAI text-embedding-ada-002
         * - 3072: OpenAI text-embedding-3-large
         */
        public Builder dimension(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("Dimension must be positive, got: " + dimension);
            }
            if (dimension > 4096) {
                throw new IllegalArgumentException("Dimension too large (max 4096), got: " + dimension);
            }
            this.dimension = dimension;
            return this;
        }
        
        /**
         * Set the similarity function for vector comparison.
         * Default: COSINE (works for most embedding models)
         */
        public Builder similarity(VectorSimilarity similarity) {
            if (similarity == null) {
                throw new IllegalArgumentException("Similarity cannot be null");
            }
            this.similarity = similarity;
            return this;
        }
        
        /**
         * Set the vector field type (FLOAT_VECTOR or BYTE_VECTOR).
         * Default: FLOAT_VECTOR
         */
        public Builder fieldType(EmbeddingFieldType fieldType) {
            if (fieldType == null) {
                throw new IllegalArgumentException("Field type cannot be null");
            }
            this.fieldType = fieldType;
            return this;
        }
        
        /**
         * Set HNSW M parameter (max connections per node).
         * Higher = better recall, more memory.
         * Range: 2-96, recommended 4-48, default 16.
         */
        public Builder hnswM(int m) {
            if (m < 2 || m > 96) {
                throw new IllegalArgumentException("HNSW M must be in range [2, 96], got: " + m);
            }
            this.hnswM = m;
            return this;
        }
        
        /**
         * Set HNSW efConstruction parameter (build-time search depth).
         * Higher = better graph quality, slower indexing.
         * Range: 1-3200, recommended 100-800, default 100.
         */
        public Builder hnswEfConstruction(int efConstruction) {
            if (efConstruction < 1 || efConstruction > 3200) {
                throw new IllegalArgumentException("HNSW efConstruction must be in range [1, 3200], got: " + efConstruction);
            }
            this.hnswEfConstruction = efConstruction;
            return this;
        }
        
        /**
         * Enable or disable embedding support.
         * Default: true
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public EmbeddingConfig build() {
            return new EmbeddingConfig(this);
        }
    }
    
    @Override
    public String toString() {
        return "EmbeddingConfig{" +
                "fieldName='" + fieldName + '\'' +
                ", dimension=" + dimension +
                ", similarity=" + similarity +
                ", fieldType=" + fieldType +
                ", hnswM=" + hnswM +
                ", hnswEfConstruction=" + hnswEfConstruction +
                ", enabled=" + enabled +
                '}';
    }
}
