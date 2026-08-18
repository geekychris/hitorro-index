/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.core.iterator.sinks.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.util.core.iterator.sinks.JsonNodeSinkBase;
import com.hitorro.util.io.StoreException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Schema-less Lucene index sink. Every scalar top-level field becomes a
 * {@link StringField} (indexed, not analyzed); a {@code _text} catch-all
 * lands as a {@link TextField} for full-text queries. When
 * {@code storeSource} is true, the whole row JSON is stored as
 * {@code _source}.
 *
 * <p>Lives in {@code hitorro-index} — any {@code Sink<JsonNode>} caller
 * with Lucene on the classpath can use this without adopting a
 * pipeline framework. For JVS-typed rows use {@link JvsLuceneSink}
 * which projects fields via each Group's method (identifier / text /
 * long / …) for proper analyzer chain routing.</p>
 */
public final class LuceneSink extends JsonNodeSinkBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Hidden Lucene field name for the exactly-once dedup key.
     * Every doc written via {@link #addIdempotent} gets a
     * {@code _taskseq} StringField with value {@code "<taskId>:<seq>"};
     * subsequent writes for the same (taskId, seq) use
     * {@link IndexWriter#updateDocument(Term, Iterable)} which
     * atomically delete-by-term + add-fresh. Retried mappers stop
     * producing duplicate hits.
     */
    public static final String TASK_SEQ_FIELD = "_taskseq";

    private final String name;
    private final boolean storeSource;
    private final Path dir;

    private FSDirectory fsDir;
    private IndexWriter writer;

    public LuceneSink(String name, boolean storeSource, Path home) {
        this.name        = name;
        this.storeSource = storeSource;
        this.dir         = home.resolve("lucene").resolve(name);
    }

    public String name()          { return name; }
    public boolean storeSource()  { return storeSource; }
    public Path indexDir()        { return dir; }

    @Override
    public boolean start() throws IOException {
        Files.createDirectories(dir);
        fsDir = FSDirectory.open(dir);
        IndexWriterConfig cfg = new IndexWriterConfig(new StandardAnalyzer())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writer = new IndexWriter(fsDir, cfg);
        return true;
    }

    @Override
    protected void writeRow(JsonNode row) throws IOException {
        if (writer == null) start();
        writer.addDocument(buildDoc(row, null));
    }

    /**
     * Exactly-once write via Lucene's native {@code updateDocument(Term, doc)}
     * — atomically delete-any-existing-by-term + add-fresh. A retried
     * mapper that re-sends the same (taskId, seq) row replaces the
     * previous doc instead of adding a duplicate hit.
     *
     * <p>Storage overhead: one small {@link StringField} per doc holding
     * {@code "<taskId>:<seq>"}. Retrieval cost is negligible; the field
     * is stored NO (indexed for the term-delete only).</p>
     *
     * <p>When {@code taskId} is null, falls back to plain
     * {@link #add(JsonNode)} — matches the base-interface contract that
     * a null taskId means "no dedup, at-least-once semantics".</p>
     */
    @Override
    public boolean addIdempotent(String taskId, long seq, JsonNode row) throws IOException, StoreException {
        if (taskId == null) return add(row);
        if (writer == null) start();
        String key = taskId + ":" + seq;
        writer.updateDocument(new Term(TASK_SEQ_FIELD, key), buildDoc(row, key));
        bumpCount();   // keep sink.count() honest — see JsonNodeSinkBase#bumpCount
        return true;
    }

    /** Build a doc from a row, optionally with a {@code _taskseq} StringField
     *  used as the update-by-term key. */
    private Document buildDoc(JsonNode row, String taskSeqKey) throws IOException {
        Document doc = new Document();
        StringBuilder text = new StringBuilder();
        if (row.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = row.fields();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue().isNull()) continue;
                if (e.getValue().isValueNode()) {
                    doc.add(new StringField(e.getKey(), e.getValue().asText(), Field.Store.NO));
                    text.append(e.getValue().asText()).append(' ');
                }
            }
        }
        doc.add(new TextField("_text", text.toString(), Field.Store.NO));
        if (storeSource) {
            doc.add(new StoredField("_source", JSON.writeValueAsString(row)));
        }
        if (taskSeqKey != null) {
            doc.add(new StringField(TASK_SEQ_FIELD, taskSeqKey, Field.Store.NO));
        }
        return doc;
    }

    @Override
    public void close() throws IOException {
        try { if (writer != null) writer.commit(); } catch (Exception ignored) { }
        try { if (writer != null) writer.close(); } catch (Exception ignored) { }
        try { if (fsDir != null) fsDir.close(); }   catch (Exception ignored) { }
        writer = null; fsDir = null;
    }
}
