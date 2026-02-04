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
 * Type of vector field to use for embeddings.
 */
public enum EmbeddingFieldType {
    /**
     * 32-bit floating point vectors.
     * - Most precise representation
     * - Uses KnnFloatVectorField
     * - Memory: 4 bytes per dimension
     * - Best for: Most use cases, especially when accuracy is critical
     */
    FLOAT_VECTOR,
    
    /**
     * 8-bit quantized byte vectors.
     * - Compact representation (4x smaller than float)
     * - Uses KnnByteVectorField
     * - Memory: 1 byte per dimension
     * - Best for: Large-scale deployments where memory is constrained
     * - Trade-off: Slight loss in precision vs significant memory savings
     */
    BYTE_VECTOR
}
