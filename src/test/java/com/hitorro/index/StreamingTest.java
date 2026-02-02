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
import com.hitorro.index.stream.IndexerStream;
import com.hitorro.index.stream.SearchResponseStream;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for streaming NDJson functionality.
 */
public class StreamingTest {
    private IndexConfig config;
    private JVSLuceneIndexWriter indexWriter;
    private JVSLuceneSearcher searcher;

    @BeforeEach
    public void setUp() throws IOException {
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
    public void testIndexFromJVSFlux() throws Exception {
        // Create JVS documents
        List<JVS> documents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JVS doc = new JVS();
            doc.set("id", "doc" + i);
            doc.set("title", "Document " + i);
            doc.set("content", "Content " + i);
            documents.add(doc);
        }

        // Create indexer stream
        IndexerStream indexerStream = IndexerStream.builder()
                .indexWriter(indexWriter)
                .batchSize(2)
                .commitAfterBatch(true)
                .build();

        // Index documents
        Flux<IndexerStream.IndexingResult> results = 
                indexerStream.indexFromJVSFlux(Flux.fromIterable(documents));

        // Verify using StepVerifier
        StepVerifier.create(results)
                .expectNextMatches(result -> result.getSuccessCount() == 2)
                .expectNextMatches(result -> result.getSuccessCount() == 2)
                .expectNextMatches(result -> result.getSuccessCount() == 1)
                .verifyComplete();

        // Verify search works
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        SearchResult searchResult = searcher.search("content:Content", 0, 10);
        assertEquals(5, searchResult.getTotalHits());
    }

    @Test
    public void testSearchResponseStream() throws Exception {
        // Index some documents
        for (int i = 0; i < 3; i++) {
            JVS doc = new JVS();
            doc.set("id", "doc" + i);
            doc.set("title", "Title " + i);
            indexWriter.indexDocument(doc);
        }
        indexWriter.commit();

        // Search
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        SearchResult result = searcher.search("title:Title", 0, 10);

        // Convert to NDJson stream
        Flux<String> ndjsonFlux = SearchResponseStream.toNDJson(result);

        // Verify: should have metadata + 3 documents = 4 lines
        StepVerifier.create(ndjsonFlux)
                .expectNextCount(4)
                .verifyComplete();
    }

    @Test
    public void testNDJsonStringOutput() throws Exception {
        // Index documents
        JVS doc = new JVS();
        doc.set("id", "test");
        doc.set("title", "Test Document");
        
        indexWriter.indexDocument(doc);
        indexWriter.commit();

        // Search
        searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build();

        SearchResult result = searcher.search("title:Test", 0, 10);

        // Convert to NDJson string
        String ndjson = SearchResponseStream.toNDJsonString(result);

        // Verify structure
        assertNotNull(ndjson);
        assertTrue(ndjson.contains("totalHits"));
        assertTrue(ndjson.contains("Test Document"));
        
        // Should have at least 2 newlines (metadata + 1 document)
        int newlineCount = ndjson.split("\n").length;
        assertTrue(newlineCount >= 2);
    }
}
