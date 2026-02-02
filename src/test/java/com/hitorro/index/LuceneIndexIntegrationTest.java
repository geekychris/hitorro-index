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
package com.hitorro.index;

import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Lucene indexing and searching.
 * Uses in-memory index for fast testing.
 */
public class LuceneIndexIntegrationTest {
    private IndexConfig config;
    private JVSLuceneIndexWriter indexWriter;
    private JVSLuceneSearcher searcher;

    @BeforeEach
    public void setUp() throws IOException {
        // Create in-memory index for testing
        config = IndexConfig.inMemory().build();
        indexWriter = new JVSLuceneIndexWriter(config);
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (indexWriter != null) {
            indexWriter.close();
        }
        if (searcher != null) {
            searcher.close();
        }
    }

    @Test
    public void testBasicIndexingAndSearch() throws Exception {
        // Create test documents
        JVS doc1 = new JVS();
        doc1.set("title", "Test Document One");
        doc1.set("content", "This is the content of the first test document");
        doc1.set("id", "doc1");

        JVS doc2 = new JVS();
        doc2.set("title", "Test Document Two");
        doc2.set("content", "This is the content of the second test document");
        doc2.set("id", "doc2");

        // Index documents
        indexWriter.indexDocument(doc1);
        indexWriter.indexDocument(doc2);
        indexWriter.commit();

        // Create searcher
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        // Search for documents
        SearchResult result = searcher.search("content:test", 0, 10);

        // Verify results
        assertNotNull(result);
        assertEquals(2, result.getTotalHits());
        assertEquals(2, result.getDocuments().size());
    }

    @Test
    public void testBatchIndexing() throws Exception {
        // Create test documents
        java.util.List<JVS> documents = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            JVS doc = new JVS();
            doc.set("title", "Document " + i);
            doc.set("content", "Content for document number " + i);
            doc.set("id", "doc" + i);
            documents.add(doc);
        }

        // Batch index
        indexWriter.indexDocuments(documents);
        indexWriter.commit();

        // Create searcher
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        // Search
        SearchResult result = searcher.search("content:document", 0, 20);

        // Verify
        assertNotNull(result);
        assertEquals(10, result.getTotalHits());
    }

    @Test
    public void testUpdate() throws Exception {
        // Create and index initial document
        JVS doc = new JVS();
        doc.set("id", "testdoc");
        doc.set("title", "Original Title");
        doc.set("content", "Original content");

        indexWriter.indexDocument(doc);
        indexWriter.commit();

        // Update document
        JVS updatedDoc = new JVS();
        updatedDoc.set("id", "testdoc");
        updatedDoc.set("title", "Updated Title");
        updatedDoc.set("content", "Updated content");

        indexWriter.updateDocument("id", "testdoc", updatedDoc);
        indexWriter.commit();

        // Search and verify
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        SearchResult result = searcher.search("title:Updated", 0, 10);
        assertEquals(1, result.getTotalHits());
    }

    @Test
    public void testDelete() throws Exception {
        // Index documents
        JVS doc1 = new JVS();
        doc1.set("id", "doc1");
        doc1.set("content", "Document one");

        JVS doc2 = new JVS();
        doc2.set("id", "doc2");
        doc2.set("content", "Document two");

        indexWriter.indexDocument(doc1);
        indexWriter.indexDocument(doc2);
        indexWriter.commit();

        // Delete one document
        indexWriter.deleteDocument("id", "doc1");
        indexWriter.commit();

        // Search and verify
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        SearchResult result = searcher.search("content:Document", 0, 10);
        assertEquals(1, result.getTotalHits());
    }
}
