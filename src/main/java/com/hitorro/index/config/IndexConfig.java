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
package com.hitorro.index.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.ByteBuffersDirectory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Configuration for Lucene index creation and management.
 * Provides builder pattern for configuring index storage, analyzers, and writer settings.
 */
public class IndexConfig {
    private final Directory directory;
    private final Analyzer defaultAnalyzer;
    private final double ramBufferSizeMB;
    private final int maxBufferedDocs;
    private final boolean autoCommit;
    private final int commitIntervalSeconds;

    private IndexConfig(Builder builder) throws IOException {
        this.directory = builder.directory;
        this.defaultAnalyzer = builder.defaultAnalyzer;
        this.ramBufferSizeMB = builder.ramBufferSizeMB;
        this.maxBufferedDocs = builder.maxBufferedDocs;
        this.autoCommit = builder.autoCommit;
        this.commitIntervalSeconds = builder.commitIntervalSeconds;
    }

    public Directory getDirectory() {
        return directory;
    }

    public Analyzer getDefaultAnalyzer() {
        return defaultAnalyzer;
    }

    public double getRamBufferSizeMB() {
        return ramBufferSizeMB;
    }

    public int getMaxBufferedDocs() {
        return maxBufferedDocs;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public int getCommitIntervalSeconds() {
        return commitIntervalSeconds;
    }

    /**
     * Create an IndexWriterConfig from this configuration.
     *
     * @return Configured IndexWriterConfig
     */
    public IndexWriterConfig createIndexWriterConfig() {
        IndexWriterConfig config = new IndexWriterConfig(defaultAnalyzer);
        config.setRAMBufferSizeMB(ramBufferSizeMB);
        if (maxBufferedDocs > 0) {
            config.setMaxBufferedDocs(maxBufferedDocs);
        }
        return config;
    }

    /**
     * Builder for IndexConfig.
     */
    public static class Builder {
        private Directory directory;
        private Analyzer defaultAnalyzer = new StandardAnalyzer();
        private double ramBufferSizeMB = 16.0;
        private int maxBufferedDocs = -1;
        private boolean autoCommit = true;
        private int commitIntervalSeconds = 60;

        /**
         * Use in-memory storage (RAMDirectory).
         * Good for testing and temporary indexes.
         */
        public Builder inMemory() {
            this.directory = new ByteBuffersDirectory();
            return this;
        }

        /**
         * Use filesystem-based storage.
         *
         * @param indexPath Path to the index directory
         */
        public Builder filesystem(Path indexPath) throws IOException {
            this.directory = FSDirectory.open(indexPath);
            return this;
        }

        /**
         * Use filesystem-based storage.
         *
         * @param indexPath String path to the index directory
         */
        public Builder filesystem(String indexPath) throws IOException {
            return filesystem(Path.of(indexPath));
        }

        /**
         * Use a custom Directory implementation.
         *
         * @param directory Custom Directory
         */
        public Builder directory(Directory directory) {
            this.directory = directory;
            return this;
        }

        /**
         * Set the default analyzer (used when no specific analyzer is configured).
         *
         * @param analyzer Default analyzer
         */
        public Builder defaultAnalyzer(Analyzer analyzer) {
            this.defaultAnalyzer = analyzer;
            return this;
        }

        /**
         * Set RAM buffer size for IndexWriter.
         *
         * @param sizeMB Size in megabytes
         */
        public Builder ramBufferSize(double sizeMB) {
            this.ramBufferSizeMB = sizeMB;
            return this;
        }

        /**
         * Set maximum buffered documents before flush.
         *
         * @param maxDocs Maximum number of documents
         */
        public Builder maxBufferedDocs(int maxDocs) {
            this.maxBufferedDocs = maxDocs;
            return this;
        }

        /**
         * Enable/disable automatic commits.
         *
         * @param autoCommit true to enable auto-commit
         */
        public Builder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        /**
         * Set commit interval for auto-commit.
         *
         * @param seconds Interval in seconds
         */
        public Builder commitInterval(int seconds) {
            this.commitIntervalSeconds = seconds;
            return this;
        }

        /**
         * Build the IndexConfig.
         *
         * @return Configured IndexConfig
         * @throws IOException if directory creation fails
         */
        public IndexConfig build() throws IOException {
            if (directory == null) {
                throw new IllegalStateException("Directory must be specified (use inMemory() or filesystem())");
            }
            return new IndexConfig(this);
        }
    }

    /**
     * Create a new builder for in-memory index.
     */
    public static Builder inMemory() {
        return new Builder().inMemory();
    }

    /**
     * Create a new builder for filesystem index.
     *
     * @param indexPath Path to index directory
     */
    public static Builder filesystem(String indexPath) throws IOException {
        return new Builder().filesystem(indexPath);
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
