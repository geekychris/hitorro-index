/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.core.iterator.sources.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Iterates matching documents from a named on-disk Lucene index as
 * {@link JsonNode} rows. When the index was built with
 * {@code storeSource: true}, the returned row is the original JSON;
 * otherwise a synthesised row of the stored fields is emitted.
 *
 * <p>Lives in {@code hitorro-index} — usable by any {@code Iterator<JsonNode>}
 * caller (streams pipelines, standalone tools, tests). Empty or null
 * query strings mean {@code MatchAllDocsQuery} — a "scan the whole
 * index" source.</p>
 *
 * <p>Untyped variant: uses {@link StandardAnalyzer} and the {@code _text}
 * catch-all field. For JVS-typed queries with proper analyzer routing
 * per {@code Group.method}, wrap {@link com.hitorro.index.search.JVSLuceneSearcher}
 * directly.</p>
 */
public final class LuceneSearchSource implements Iterator<JsonNode>, AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PAGE_SIZE = 1000;

    private final FSDirectory fsDir;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private ScoreDoc[] hits;
    private int pos;

    public LuceneSearchSource(Path indexDir, String queryStr) throws Exception {
        this.fsDir   = FSDirectory.open(indexDir);
        this.reader  = DirectoryReader.open(fsDir);
        this.searcher = new IndexSearcher(reader);
        Query q = (queryStr == null || queryStr.isBlank())
                ? new MatchAllDocsQuery()
                : new QueryParser("_text", new StandardAnalyzer()).parse(queryStr);
        this.hits = searcher.search(q, PAGE_SIZE).scoreDocs;
        this.pos = 0;
    }

    @Override
    public boolean hasNext() { return pos < hits.length; }

    @Override
    public JsonNode next() {
        if (!hasNext()) throw new java.util.NoSuchElementException();
        try {
            Document d = searcher.storedFields().document(hits[pos++].doc);
            String src = d.get("_source");
            if (src != null) return JSON.readTree(src);
            // No stored source — synthesise a row from stored fields.
            ObjectNode row = JSON.createObjectNode();
            for (IndexableField f : d.getFields()) {
                if (f.stringValue() != null) row.put(f.name(), f.stringValue());
            }
            return row;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try { reader.close(); }  catch (Exception ignored) { }
        try { fsDir.close(); }   catch (Exception ignored) { }
    }
}
