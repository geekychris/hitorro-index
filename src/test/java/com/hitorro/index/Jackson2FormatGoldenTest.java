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

import tools.jackson.databind.*; import tools.jackson.databind.node.*;
import com.hitorro.index.*; import com.hitorro.index.config.*; import com.hitorro.index.indexer.*; import com.hitorro.index.search.*; import com.hitorro.index.stream.*;
import com.hitorro.util.core.iterator.sinks.index.LuceneSink; import com.hitorro.util.core.iterator.sources.index.LuceneSearchSource;
import com.hitorro.jsontypesystem.JVS;
import org.apache.lucene.document.*; import org.apache.lucene.index.*; import org.apache.lucene.store.FSDirectory; import org.apache.lucene.util.BytesRef;
import java.io.*; import java.nio.file.*; import java.util.*;
/**
 * Pins what hitorro-index puts into Lucene and sends to what 3.0.2 did on Jackson 2.18: the
 * typed projection (LuceneProjectionContext.addField for every LuceneFieldType and string, number,
 * boolean, null, object, array values), untyped JVS indexing through IndexManager read back from
 * the index files (stored fields incl. _source, _uid, every indexed term), the NDJSON search
 * response stream, and LuceneSink rows read back by LuceneSearchSource.
 * {@code jackson2-index.txt} is the output of the same code run on the 3.0.2 sources
 * (generator: ~/hitorro-j3/tools/Jackson2IndexGolden.java.txt in the migration workspace).
 * Jackson 3 node and exception names are mapped back before comparing (StringNode = TextNode).
 */
class Jackson2FormatGoldenTest {
  static final Map<String, String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static boolean jackson(Throwable t){ return t.getClass().getName().startsWith("com.fasterxml.jackson") || t.getClass().getName().startsWith("tools.jackson"); }
  static String desc(Throwable t){ String s = (t instanceof IOException ? "IOE:" : "RTE:") + (jackson(t) ? "jackson" : t.getClass().getSimpleName());
    if (!jackson(t) && t.getCause() != null) s += "<" + (jackson(t.getCause()) ? "jackson" : t.getCause().getClass().getSimpleName()) + ">"; return s; }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { return String.valueOf(c.call()); } catch(Throwable t){ return "!"+desc(t); } }
  static Iterator<String> names(JsonNode n){ return n.propertyNames().iterator(); }
  static String hex(BytesRef b){ StringBuilder s = new StringBuilder(); for (int i = 0; i < b.length; i++) s.append(String.format("%02x", b.bytes[b.offset + i])); return s.toString(); }
  static String field(IndexableField f){ IndexableFieldType t = f.fieldType();
    return f.name() + ":" + f.getClass().getSimpleName() + "[s=" + f.stringValue() + ",n=" + f.numericValue() + ",b=" + (f.binaryValue() == null ? null : hex(f.binaryValue()))
      + ",stored=" + t.stored() + ",tok=" + t.tokenized() + ",idx=" + t.indexOptions() + ",dv=" + t.docValuesType() + "]"; }
  static String fields(Document d){ List<String> l = new ArrayList<>(); for (IndexableField f : d.getFields()) l.add(field(f)); return String.join(" ; ", l); }
  // stored fields of every doc and every indexed term, from the index files
  static String dump(Path dir) throws Exception { StringBuilder sb = new StringBuilder();
    try (FSDirectory fs = FSDirectory.open(dir); DirectoryReader r = DirectoryReader.open(fs)) {
      List<String> docs = new ArrayList<>();
      for (LeafReaderContext lc : r.leaves()) { LeafReader lr = lc.reader(); StoredFields sf = lr.storedFields(); org.apache.lucene.util.Bits live = lr.getLiveDocs();
        for (int i = 0; i < lr.maxDoc(); i++) { if (live != null && !live.get(i)) continue; Document d = sf.document(i); TreeMap<String, List<String>> m = new TreeMap<>();
          for (IndexableField f : d.getFields()) m.computeIfAbsent(f.name(), k -> new ArrayList<>()).add(f.stringValue() != null ? f.stringValue() : String.valueOf(f.numericValue()));
          docs.add(m.toString()); } }
      Collections.sort(docs); sb.append("docs=").append(docs);
      TreeSet<String> names = new TreeSet<>(); for (LeafReaderContext lc : r.leaves()) for (FieldInfo fi : lc.reader().getFieldInfos()) names.add(fi.name + "/" + fi.getIndexOptions() + "/" + fi.getDocValuesType());
      sb.append(" fields=").append(names);
      for (String n : names) { String fn = n.substring(0, n.indexOf('/')); Terms t = MultiTerms.getTerms(r, fn); if (t == null) continue; TermsEnum te = t.iterator(); List<String> ts = new ArrayList<>(); BytesRef br;
        while ((br = te.next()) != null) ts.add(br.utf8ToString() + "(" + te.docFreq() + ")"); sb.append(" terms." + fn + "=").append(ts); } }
    return sb.toString(); }
  static final String FIELD_TYPES = "{\"fields\":{\"text\":{\"name\":\"text\",\"indexed\":true,\"tokenized\":true,\"stored\":true,\"docValues\":false,\"i18n\":true,\"isid\":false},"
    + "\"identifier\":{\"name\":\"identifier\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":true,\"i18n\":false,\"isid\":true},"
    + "\"long\":{\"name\":\"long\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":true,\"i18n\":false,\"isid\":false},"
    + "\"int\":{\"name\":\"int\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":true,\"i18n\":false,\"isid\":false},"
    + "\"double\":{\"name\":\"double\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":true,\"i18n\":false,\"isid\":false},"
    + "\"date\":{\"name\":\"date\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":true,\"i18n\":false,\"isid\":false},"
    + "\"boolean\":{\"name\":\"boolean\",\"indexed\":true,\"tokenized\":false,\"stored\":true,\"docValues\":false,\"i18n\":false,\"isid\":false},"
    + "\"plain\":{\"name\":\"plain\",\"indexed\":true,\"tokenized\":false,\"stored\":false,\"docValues\":true,\"i18n\":false,\"isid\":false}}}";
  public static Map<String, String> compute(Path dir) throws Exception {
    out.clear();
    ObjectMapper om = com.hitorro.util.json.JsonMappers.json();
    // ---- typed projection: LuceneFieldType x value -> Lucene fields
    JsonNode ftJson = om.readTree(FIELD_TYPES);
    LuceneFieldTypes lfts = LuceneFieldTypes.getInstance(); lfts.loadFrom(ftJson);
    String[] values = {"\"Hello World\"", "\"12\"", "12", "12.7", "-0.0", "1e30", "12345678901234567890", "true", "null", "{\"a\":1}", "[1,\"x\"]", "\"\"", "\"\u00e9/\u2028\""};
    Iterator<String> it = names(ftJson.get("fields"));
    while (it.hasNext()) { String ftName = it.next(); LuceneFieldType ft = lfts.get(ftName);
      p("fieldType." + ftName, tryIt(() -> ft + " i18n=" + ft.isI18n() + " id=" + ft.isId() + " stored=" + ft.isStored() + " idx=" + ft.isIndexed() + " tok=" + ft.isTokenized() + " dv=" + ft.hasDocValues() + " type=" + ft.getIndexType()));
      for (String v : values) for (String lang : new String[]{"en", null}) {
        p("project." + ftName + "." + v + "." + lang, tryIt(() -> { LuceneProjectionContext ctx = new LuceneProjectionContext(new JVS()); ctx.addField("f", om.readTree(v), ft, lang); return fields(ctx.getDocument()); })); } }
    // ---- untyped JVS indexing through IndexManager, read back from the index files
    Path idx = dir.resolve("idx");
    IndexManager im = new IndexManager("en");
    im.createIndex("g", IndexConfig.builder().filesystem(idx).build(), null);
    String[] docs = {
      "{\"id\":{\"did\":\"d1\",\"domain\":\"news\"},\"title\":\"First Doc\",\"count\":12,\"score\":1.10,\"flag\":true,\"tags\":[\"a\",\"b\"],\"meta\":{\"k\":\"v\"},\"n\":null,\"big\":12345678901234567890,\"s\":\"\u00e9/\u2028\"}",
      "{\"id\":\"plain-id\",\"title\":\"Second\",\"count\":\"12\",\"score\":-0.0,\"flag\":\"false\"}",
      "{\"id\":7,\"title\":\"Numeric id\"}",
      "{\"id\":{\"did\":5,\"domain\":true},\"title\":\"odd id parts\"}",
      "{\"id\":{\"id\":\"given:id\",\"did\":\"x\",\"domain\":\"y\"},\"title\":\"given id\"}",
      "{\"id\":{\"did\":\"d1\",\"domain\":\"news\"},\"title\":\"First Doc replaced\",\"count\":13}",
      "{\"title\":\"no id\",\"dec\":123456789012345678901234567890.5}"};
    for (String d : docs) p("index." + d.hashCode(), tryIt(() -> { im.indexDocument("g", new JVS(om.readTree(d))); return "ok"; }));
    List<JVS> batch = new ArrayList<>(); batch.add(new JVS(om.readTree("{\"id\":\"b1\",\"title\":\"batch one\"}"))); batch.add(new JVS(om.readTree("{\"id\":\"b2\",\"v\":[1,2]}")));
    p("index.batch", tryIt(() -> { im.indexDocuments("g", batch); return "ok"; }));
    im.commit("g");
    for (String q : new String[]{"*:*", "title:first", "count:12", "id.id:\"news:d1\"", "flag:true"})
      p("search." + q, tryIt(() -> SearchResponseStream.toNDJsonString(im.search("g", q, 0, 20, null)).replaceAll("\"searchTimeMs\":[0-9]+", "\"searchTimeMs\":T")));
    im.close();
    p("index.files", dump(idx));
    // ---- LuceneSink rows -> index -> LuceneSearchSource
    Path home = dir.resolve("home");
    LuceneSink sink = new LuceneSink("rows", true, home); sink.start();
    String[] rows = {"{\"id\":\"r1\",\"b\":1.10,\"a\":[1,{\"x\":null}],\"big\":12345678901234567890,\"s\":\"\u00e9/\u2028\",\"t\":true,\"o\":{\"k\":1}}", "{\"id\":7,\"v\":\"text here\"}", "{\"v\":5}", "{\"id\":\"r1\",\"v\":\"again\"}"};
    for (String r : rows) p("sink.add." + r.hashCode(), tryIt(() -> sink.add(om.readTree(r))));
    p("sink.idem", tryIt(() -> sink.addIdempotent("t", 2, om.readTree("{\"id\":\"i\",\"v\":1}")) + "," + sink.addIdempotent("t", 1, om.readTree("{\"id\":\"i\",\"v\":2}"))));
    sink.close();
    Path sinkDir = home.resolve("lucene").resolve("rows");
    if (!Files.exists(sinkDir)) { try (java.util.stream.Stream<Path> s = Files.walk(home)) { sinkDir = s.filter(x -> x.getFileName().toString().startsWith("segments")).findFirst().map(Path::getParent).orElse(home); } }
    p("sink.files", tryIt(() -> dump(home.resolve("lucene").resolve("rows"))));
    final Path sd = sinkDir;
    for (String q : new String[]{"*:*", "_text:again"})
      p("source." + q, tryIt(() -> { StringBuilder sb = new StringBuilder(); LuceneSearchSource src = new LuceneSearchSource(sd, q); List<String> l = new ArrayList<>(); while (src.hasNext()) { JsonNode n = src.next(); l.add(n.getClass().getSimpleName() + " " + n); } src.close(); Collections.sort(l); return l; }));
    return out;
  }

  static Map<String, String> load(String name) throws IOException {
    Map<String, String> m = new LinkedHashMap<>();
    try (InputStream in = Jackson2FormatGoldenTest.class.getResourceAsStream(name)) {
      if (in == null) return m;
      for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        if (line.isEmpty()) continue;
        int i = line.indexOf('=');
        m.put(line.substring(0, i), line.substring(i + 1));
      }
    }
    return m;
  }

  @org.junit.jupiter.api.Test
  void matchesJackson2Output(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Map<String, String> expected = load("jackson2-index.txt");
    
    Map<String, String> actual = new LinkedHashMap<>();
    compute(dir).forEach((k, v) -> actual.put(k, v.replace("StringNode", "TextNode")));
    org.assertj.core.api.Assertions.assertThat(expected).hasSize(238);
    org.assertj.core.api.Assertions.assertThat(actual).containsExactlyEntriesOf(expected);
  }
}

