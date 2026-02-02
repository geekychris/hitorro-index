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
package com.hitorro.index.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.jsontypesystem.JVS;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes NDJson input stream and indexes JVS documents.
 * Supports batch processing and error handling.
 */
public class IndexerStream {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final JVSLuceneIndexWriter indexWriter;
    private final int batchSize;
    private final boolean commitAfterBatch;

    /**
     * Create an indexer stream.
     *
     * @param indexWriter      The index writer to use
     * @param batchSize        Number of documents to batch before indexing
     * @param commitAfterBatch Whether to commit after each batch
     */
    public IndexerStream(JVSLuceneIndexWriter indexWriter, int batchSize, boolean commitAfterBatch) {
        this.indexWriter = indexWriter;
        this.batchSize = batchSize;
        this.commitAfterBatch = commitAfterBatch;
    }

    /**
     * Index documents from NDJson input stream.
     *
     * @param inputStream NDJson input stream
     * @return Flux of indexing results (indexed count per batch)
     */
    public Flux<IndexingResult> indexFromStream(InputStream inputStream) {
        return Flux.using(
                () -> new BufferedReader(new InputStreamReader(inputStream)),
                reader -> Flux.fromStream(reader.lines())
                        .map(this::parseJVS)
                        .filter(jvs -> jvs != null)
                        .buffer(batchSize)
                        .flatMap(this::indexBatch),
                reader -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
        );
    }

    /**
     * Index documents from Flux of JSON strings.
     *
     * @param jsonFlux Flux of JSON strings (one document per item)
     * @return Flux of indexing results
     */
    public Flux<IndexingResult> indexFromJsonFlux(Flux<String> jsonFlux) {
        return jsonFlux
                .map(this::parseJVS)
                .filter(jvs -> jvs != null)
                .buffer(batchSize)
                .flatMap(this::indexBatch);
    }

    /**
     * Index documents from Flux of JVS objects.
     *
     * @param jvsFlux Flux of JVS documents
     * @return Flux of indexing results
     */
    public Flux<IndexingResult> indexFromJVSFlux(Flux<JVS> jvsFlux) {
        return jvsFlux
                .buffer(batchSize)
                .flatMap(this::indexBatch);
    }

    /**
     * Index a batch of documents.
     */
    private Mono<IndexingResult> indexBatch(List<JVS> batch) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();
            int successCount = 0;
            int errorCount = 0;
            List<String> errors = new ArrayList<>();

            try {
                indexWriter.indexDocuments(batch);
                successCount = batch.size();
                
                if (commitAfterBatch) {
                    indexWriter.commit();
                }
            } catch (IOException e) {
                errorCount = batch.size();
                errors.add("Batch indexing failed: " + e.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;
            return new IndexingResult(successCount, errorCount, errors, duration);
        });
    }

    /**
     * Parse JSON string to JVS.
     */
    private JVS parseJVS(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            return new JVS(node);
        } catch (Exception e) {
            // Return null for invalid JSON
            return null;
        }
    }

    /**
     * Result of indexing a batch of documents.
     */
    public static class IndexingResult {
        private final int successCount;
        private final int errorCount;
        private final List<String> errors;
        private final long durationMs;

        public IndexingResult(int successCount, int errorCount, List<String> errors, long durationMs) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errors = errors;
            this.durationMs = durationMs;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public boolean hasErrors() {
            return errorCount > 0;
        }

        /**
         * Convert to JVS for reporting.
         */
        public JVS toJVS() {
            JVS result = new JVS();
            try {
                result.set("successCount", successCount);
                result.set("errorCount", errorCount);
                result.set("durationMs", durationMs);
                if (!errors.isEmpty()) {
                    result.set("errors", String.join("; ", errors));
                }
            } catch (Exception e) {
                // Ignore
            }
            return result;
        }
    }

    /**
     * Builder for IndexerStream.
     */
    public static class Builder {
        private JVSLuceneIndexWriter indexWriter;
        private int batchSize = 100;
        private boolean commitAfterBatch = true;

        public Builder indexWriter(JVSLuceneIndexWriter indexWriter) {
            this.indexWriter = indexWriter;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder commitAfterBatch(boolean commitAfterBatch) {
            this.commitAfterBatch = commitAfterBatch;
            return this;
        }

        public IndexerStream build() {
            if (indexWriter == null) {
                throw new IllegalStateException("IndexWriter must be provided");
            }
            return new IndexerStream(indexWriter, batchSize, commitAfterBatch);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
