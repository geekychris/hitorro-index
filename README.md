# Hitorro Lucene Index Integration

Apache Lucene integration for the Hitorro JSON Type System (JVS), providing full-text search, fielded search, faceting, and multi-language tokenization with streaming NDJson support.

## Features

- **Type-Aware Indexing**: Automatically indexes JVS documents using the Type System's field definitions
- **Multi-Language Support**: Language-specific analyzers for 30+ languages (English, French, German, Chinese, etc.)
- **Fielded Search**: Query specific fields with support for dotted field paths (e.g., `title.mls:search`)
- **Faceting**: String, numeric, and date range facets using Lucene's facet module
- **Streaming API**: NDJson input/output for efficient batch processing
- **Flexible Storage**: In-memory (RAMDirectory) or filesystem-based indexes
- **Reactive Streams**: Built on Project Reactor for streaming search results

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.hitorro</groupId>
    <artifactId>hitorro-index</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2. Create an Index

```java
// Create in-memory index
IndexConfig config = IndexConfig.inMemory().build();

// Or filesystem-based index
IndexConfig config = IndexConfig.filesystem("/path/to/index").build();
```

### 3. Index Documents

```java
try (JVSLuceneIndexWriter indexWriter = new JVSLuceneIndexWriter(config)) {
    // Index single document
    JVS doc = new JVS();
    doc.set("title", "My Document");
    doc.set("content", "This is the document content");
    indexWriter.indexDocument(doc);
    
    // Or batch index
    List<JVS> documents = Arrays.asList(doc1, doc2, doc3);
    indexWriter.indexDocuments(documents);
    
    indexWriter.commit();
}
```

### 4. Search Documents

```java
try (JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
        .config(config)
        .build()) {
    
    // Basic search
    SearchResult result = searcher.search("content:document", 0, 10);
    
    System.out.println("Found " + result.getTotalHits() + " documents");
    for (JVS doc : result.getDocuments()) {
        System.out.println(doc);
    }
}
```

### 5. Search with Facets

```java
SearchResult result = searcher.search("content:news", 0, 10, 
    Arrays.asList("category", "author"));

// Access facet results
Map<String, FacetResult> facets = result.getFacets();
for (Map.Entry<String, FacetResult> entry : facets.entrySet()) {
    System.out.println("Facet: " + entry.getKey());
    for (FacetResult.FacetValue value : entry.getValue().getValues()) {
        System.out.println("  " + value.getValue() + ": " + value.getCount());
    }
}
```

### 6. Streaming NDJson

```java
// Index from NDJson stream
IndexerStream indexerStream = IndexerStream.builder()
    .indexWriter(indexWriter)
    .batchSize(100)
    .build();

Flux<IndexingResult> results = indexerStream.indexFromStream(inputStream);
results.subscribe(result -> 
    System.out.println("Indexed: " + result.getSuccessCount()));

// Output search results as NDJson
SearchResult result = searcher.search("query", 0, 10);
Flux<String> ndjson = SearchResponseStream.toNDJson(result);
ndjson.subscribe(System.out::println);
```

## Architecture

### Field Type System

The module uses a configuration-driven approach to map JVS field types to Lucene field types and analyzers:

- **Configuration**: `config/jsonconfigs/lucene_fields.json`
- **Field Types**: text, identifier, long, int, double, date, textmarkup
- **Field Naming**: Follows SOLR convention: `path.type_lang_m/s`
  - Example: `title.mls.text_en_s` (single-valued English text field)
  - Example: `tags.text_en_m` (multi-valued English text field)

### Language Support

Language-specific analyzers are automatically selected for i18n fields:

- Arabic (ar), Bulgarian (bg), Catalan (ca), Czech (cs), Danish (da)
- Dutch (nl), English (en), Finnish (fi), French (fr), German (de)
- Greek (el), Hindi (hi), Hungarian (hu), Indonesian (id), Italian (it)
- Norwegian (no), Persian (fa), Portuguese (pt), Romanian (ro), Russian (ru)
- Spanish (es), Swedish (sv), Thai (th), Turkish (tr)
- CJK (Chinese, Japanese, Korean): zh, ja, ko

### Projection Mechanism

The indexer uses the Type System's ExecutionBuilder pattern:

1. Type definition declares which fields should be indexed
2. ExecutionBuilder creates an execution plan
3. LuceneIndexerAction projects JVS fields to Lucene Document fields
4. Appropriate analyzers and field types are automatically selected

## API Reference

### IndexConfig

Configure index storage and settings:

```java
IndexConfig config = IndexConfig.builder()
    .filesystem("/path/to/index")
    .ramBufferSize(32.0)  // MB
    .maxBufferedDocs(1000)
    .autoCommit(true)
    .commitInterval(60)  // seconds
    .build();
```

### JVSLuceneIndexWriter

Index, update, and delete documents:

```java
indexWriter.indexDocument(jvs);
indexWriter.indexDocuments(List<JVS>);
indexWriter.updateDocument(idField, idValue, jvs);
indexWriter.deleteDocument(idField, idValue);
indexWriter.commit();
indexWriter.flush();
```

### JVSLuceneSearcher

Search with various strategies:

```java
// Basic search
SearchResult search(String query, int offset, int limit)

// Search with facets
SearchResult search(String query, int offset, int limit, List<String> facetDims)

// Fielded search
SearchResult fieldedSearch(Map<String, String> fieldQueries, int offset, int limit)

// Streaming search
Flux<JVS> searchStream(String query, int offset, int limit)
```

### Query Syntax

Supports standard Lucene query syntax with dotted field names:

```
title:search                    // Search in title field
title.mls:hello                 // Dotted field path
content:("hello world")         // Phrase search
title:test AND content:data     // Boolean query
field:[10 TO 20]                // Range query
content:test*                   // Wildcard
```

## Configuration

### Lucene Field Types

Edit `config/jsonconfigs/lucene_fields.json`:

```json
{
  "fields": [
    {
      "name": "text",
      "i18n": true,
      "isid": true,
      "stored": true,
      "indexed": true,
      "tokenized": true,
      "docValues": false
    }
  ]
}
```

### Custom Analyzers

Register custom analyzers programmatically:

```java
LuceneAnalyzerRegistry.registerLanguageAnalyzer("custom", new MyAnalyzer());
LuceneAnalyzerRegistry.registerTypeAnalyzer("specialfield", new MyAnalyzer());
```

## Dependencies

- Java 21
- Apache Lucene 9.11.1
- Hitorro Util 3.0.0 (contains JVS core)
- Project Reactor 3.6.11
- Jackson 2.18.2

## Building

```bash
./build.sh
# or
mvn clean install
```

## Testing

```bash
mvn test
```

Tests use in-memory indexes (ByteBuffersDirectory) for fast execution.

## License

MIT License - Copyright (c) 2006-2025 Chris Collins
