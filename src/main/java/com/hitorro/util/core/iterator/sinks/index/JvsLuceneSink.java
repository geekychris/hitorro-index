/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.util.core.iterator.sinks.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.index.IndexManager;
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.config.LuceneFieldTypes;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.jsontypesystem.resources.TypeJsonLoader;
import com.hitorro.util.core.iterator.sinks.JsonNodeSinkBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Type-aware Lucene sink. Every row is wrapped as a {@link JVS} with
 * the loaded {@link Type}, then handed to
 * {@link com.hitorro.index.indexer.JVSLuceneIndexWriter} — which
 * projects each field per its {@code Group.method}
 * (identifier / text / long / mls / …). Physical index at
 * {@code <home>/lucene/<name>/}.
 *
 * <p>Lives in {@code hitorro-index} — any {@code Sink<JsonNode>} caller
 * that also has the JVS type system can use this without adopting a
 * pipeline framework.</p>
 *
 * <p>Type loaded once at {@link #start()} from {@code typeJsonResource}
 * — a Spring-style URL: {@code file:…}, {@code classpath:…}, or
 * {@code http(s)://…}. Each row's JVS gets the loaded Type forcibly
 * attached so we do not depend on JsonTypeSystem being pre-populated.</p>
 */
public final class JvsLuceneSink extends JsonNodeSinkBase {

    private final String name;
    private final String typeJsonResource;
    private final boolean storeSource;
    private final Path indexDir;

    private IndexManager indexManager;
    private Type type;

    public JvsLuceneSink(String name, String typeJsonResource, boolean storeSource, Path home) {
        this.name = name;
        this.typeJsonResource = typeJsonResource;
        this.storeSource = storeSource;
        this.indexDir = home.resolve("lucene").resolve(name);
    }

    public String name()          { return name; }
    public boolean storeSource()  { return storeSource; }
    public Path indexDir()        { return indexDir; }

    @Override
    public boolean start() throws IOException {
        Files.createDirectories(indexDir);
        // Bootstrap LuceneFieldTypes from ${HT_BIN}/config/jsonconfigs/lucene/lucene_fields.json.
        // The shipped MapProperty loader silently no-ops on the object-shaped file;
        // loadFrom() handles both shapes and returns cleanly if the file isn't
        // reachable (dev / test). Idempotent — no-op if the registry is already populated.
        bootstrapLuceneFieldTypes();

        JsonNode typeJson;
        try {
            typeJson = TypeJsonLoader.load(typeJsonResource);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("JvsLuceneSink type load interrupted", e);
        }
        this.type = new Type();
        this.type.init(typeJson);

        IndexConfig cfg = IndexConfig.builder()
                .filesystem(indexDir)
                .storeSource(storeSource)
                .build();
        this.indexManager = new IndexManager("en");
        try { this.indexManager.createIndex(name, cfg, type); }
        catch (Exception e) { throw new IOException("JvsLuceneSink createIndex " + name + ": " + e.getMessage(), e); }
        return true;
    }

    @Override
    protected void writeRow(JsonNode row) throws IOException {
        if (indexManager == null) start();
        JVS jvs = new JVS(row);
        jvs.setType(type);
        try { indexManager.indexDocuments(name, List.of(jvs)); }
        catch (Exception e) { throw new IOException("JvsLuceneSink indexDocuments: " + e.getMessage(), e); }
    }

    @Override
    public void close() throws IOException {
        try { if (indexManager != null) indexManager.commit(name); } catch (Exception ignore) {}
        try { if (indexManager != null) indexManager.close(); }     catch (Exception ignore) {}
        indexManager = null;
    }

    private static void bootstrapLuceneFieldTypes() {
        LuceneFieldTypes lfts = LuceneFieldTypes.getInstance();
        if (lfts.get("identifier") != null) return;
        String bin = System.getProperty("HT_BIN",
                System.getProperty("ht.bin", System.getenv("HT_BIN")));
        if (bin == null) bin = System.getProperty("user.dir");
        Path path = Path.of(bin, "config", "jsonconfigs", "lucene", "lucene_fields.json");
        if (!Files.exists(path)) return;
        try {
            JsonNode node = new ObjectMapper().readTree(path.toFile());
            lfts.loadFrom(node);
        } catch (IOException e) {
            System.err.println("[JvsLuceneSink] bootstrapLuceneFieldTypes failed: " + e);
        }
    }
}
