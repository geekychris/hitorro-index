/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.index.readonly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.jsontypesystem.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view of on-disk Lucene indexes. Constructs
 * {@link JVSLuceneSearcher} directly via {@link IndexConfig#getDirectory()}
 * (which opens a {@code DirectoryReader}) — no {@code IndexWriter}, no
 * {@code write.lock} contention with pipeline writers landing new
 * documents into the same directory.
 *
 * <p>Use this whenever your reader lifecycle is longer than your writer's
 * commit cadence and both share a filesystem — dashboards, retrieval
 * services, standalone search tools. Call {@link #refresh(String)}
 * periodically to pick up writes that landed since the last open.</p>
 *
 * <p>Not Spring-coupled — callers must invoke {@link #close()} explicitly.
 * Spring hosts can wrap it as a bean with their own {@code @PreDestroy}.</p>
 */
public class ReadOnlyIndexService implements AutoCloseable {

    private final String defaultLanguage;
    private final Map<String, JVSLuceneSearcher> searchers = new ConcurrentHashMap<>();
    private final Map<String, Path>              paths     = new ConcurrentHashMap<>();

    public ReadOnlyIndexService() { this("en"); }

    public ReadOnlyIndexService(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage == null ? "en" : defaultLanguage;
    }

    /** Open (or return cached) read-only searcher for the given on-disk index.
     *  When the index dir has a {@code .jvs-type.json} sidecar (written by
     *  {@code JvsLuceneSink}), the loaded {@link Type} is threaded into the
     *  searcher so {@code JVSQueryParser} can rewrite logical field paths
     *  ({@code body.mls.clean:chase}) into the physical projected names
     *  ({@code body.mls.clean.text_en_m:chase}) at query time. Without this,
     *  shared-mode readers (fleet-retrieval) reject logical paths as unknown
     *  fields and return zero hits, forcing callers to spell out the
     *  language- and cardinality-suffixed physical names. */
    public synchronized JVSLuceneSearcher openOrGet(String name, Path indexDir) throws IOException {
        JVSLuceneSearcher existing = searchers.get(name);
        if (existing != null) return existing;
        if (!Files.isDirectory(indexDir)) throw new IOException("No such index dir: " + indexDir);
        IndexConfig cfg = IndexConfig.builder().filesystem(indexDir).storeSource(true).build();
        Type type = loadTypeSidecar(indexDir);
        JVSLuceneSearcher s = new JVSLuceneSearcher(cfg, type, defaultLanguage);
        searchers.put(name, s);
        paths.put(name, indexDir);
        return s;
    }

    /** Load the JVS type from {@code <indexDir>/.jvs-type.json} if present.
     *  Returns null on any failure — the caller can still search, just
     *  without logical-path expansion. Sidecar filename matches
     *  {@code JvsLuceneSink.TYPE_SIDECAR}; hardcoded here to avoid pulling
     *  in the pipeline-sink module purely for a string constant. */
    private static Type loadTypeSidecar(Path indexDir) {
        Path sidecar = indexDir.resolve(".jvs-type.json");
        if (!Files.exists(sidecar)) return null;
        try {
            JsonNode typeJson = new ObjectMapper().readTree(sidecar.toFile());
            Type type = new Type();
            type.init(typeJson);
            return type;
        } catch (Exception e) {
            System.err.println("[ReadOnlyIndexService] failed to load type sidecar "
                    + sidecar + " — falling back to null type: " + e);
            return null;
        }
    }

    /** Force a refresh so writes since the last open are visible. */
    public synchronized JVSLuceneSearcher refresh(String name) throws IOException {
        JVSLuceneSearcher old = searchers.get(name);
        if (old == null) return null;
        JVSLuceneSearcher refreshed = old.refresh();
        if (refreshed != old) {
            searchers.put(name, refreshed);
            try { old.close(); } catch (IOException ignore) { /* logged by caller if desired */ }
        }
        return refreshed;
    }

    public JVSLuceneSearcher get(String name) { return searchers.get(name); }
    public Path pathOf(String name)           { return paths.get(name); }
    public Set<String> names()                { return searchers.keySet(); }

    public Map<String, Object> describe(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("path", paths.getOrDefault(name, Path.of("?")).toString());
        m.put("open", searchers.containsKey(name));
        return m;
    }

    @Override
    public void close() {
        for (JVSLuceneSearcher s : searchers.values()) {
            try { s.close(); } catch (IOException ignore) { }
        }
        searchers.clear();
    }
}
