/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.indexer;

import com.hitorro.index.config.IndexConfig;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.util.json.JsonMappers;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stored {@code _source} is the document's compact JSON, as on 3.x, also when a value nests
 * 600 levels deep (Jackson 2's limit was 1000; Jackson 3's {@code JsonNode.toString()} throws past
 * 500). Covers both {@code indexDocument} and {@code indexDocuments} with separate source docs.
 */
class SourceFieldDeepNestingTest {

    @Test
    void storesDeepDocumentsAsCompactJson(@TempDir Path dir) throws Exception {
        String deep = "[".repeat(600) + "]".repeat(600);
        String d1 = "{\"id\":\"d1\",\"title\":\"one\",\"deep\":" + deep + "}";
        String d2 = "{\"id\":\"d2\",\"title\":\"two\"}";
        String s2 = "{\"id\":\"d2\",\"raw\":" + deep + "}";
        Path idx = dir.resolve("idx");
        try (JVSLuceneIndexWriter w = new JVSLuceneIndexWriter(IndexConfig.builder().filesystem(idx).build())) {
            w.indexDocument(new JVS(JsonMappers.json().readTree(d1)));
            w.indexDocuments(List.of(new JVS(JsonMappers.json().readTree(d2))), List.of(JsonMappers.json().readTree(s2)));
            w.commit();
        }
        List<String> sources = new ArrayList<>();
        try (FSDirectory fs = FSDirectory.open(idx); DirectoryReader r = DirectoryReader.open(fs)) {
            for (LeafReaderContext lc : r.leaves()) {
                StoredFields sf = lc.reader().storedFields();
                for (int i = 0; i < lc.reader().maxDoc(); i++) sources.add(sf.document(i).get("_source"));
            }
        }
        assertThat(sources).hasSize(2);
        assertThat(sources).anySatisfy(s -> assertThat(s).startsWith("{\"id\":").contains("\"deep\":" + deep));
        assertThat(sources).contains(s2);
    }
}
