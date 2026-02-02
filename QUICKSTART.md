# Hitorro Index Quick Start

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.hitorro</groupId>
    <artifactId>hitorro-index</artifactId>
    <version>3.0.0</version>
</dependency>
```

Build from source:
```bash
cd hitorro-index
./build.sh
```

## Basic Usage

### 1. Create an Index and Add Documents

```java
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.jsontypesystem.JVS;

// Create in-memory index
IndexConfig config = IndexConfig.inMemory().build();

// Create index writer
try (JVSLuceneIndexWriter indexWriter = new JVSLuceneIndexWriter(config)) {
    // Create a document
    JVS doc = new JVS();
    doc.set("title", "Introduction to Lucene");
    doc.set("author", "John Smith");
    doc.set("content", "Apache Lucene is a powerful search library...");
    doc.set("category", "Technology");
    
    // Index it
    indexWriter.indexDocument(doc);
    indexWriter.commit();
}
```

### 2. Search Documents

```java
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.index.search.SearchResult;

try (JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
        .config(config)
        .build()) {
    
    // Search for documents
    SearchResult result = searcher.search("content:lucene", 0, 10);
    
    System.out.println("Total hits: " + result.getTotalHits());
    
    for (JVS doc : result.getDocuments()) {
        System.out.println("Title: " + doc.getString("title"));
        System.out.println("Score: " + doc.get("_score"));
    }
}
```

### 3. Advanced Query Syntax

```java
// Field-specific search
result = searcher.search("title:lucene", 0, 10);

// Phrase search
result = searcher.search("content:\"search library\"", 0, 10);

// Boolean queries
result = searcher.search("title:lucene AND author:Smith", 0, 10);

// Wildcards
result = searcher.search("title:luce*", 0, 10);

// Range queries
result = searcher.search("date:[20240101 TO 20241231]", 0, 10);
```

### 4. Batch Indexing

```java
List<JVS> documents = new ArrayList<>();

for (int i = 0; i < 1000; i++) {
    JVS doc = new JVS();
    doc.set("id", "doc" + i);
    doc.set("title", "Document " + i);
    doc.set("content", "Content for document " + i);
    documents.add(doc);
}

// Index all at once
indexWriter.indexDocuments(documents);
indexWriter.commit();
```

### 5. Faceted Search

```java
import java.util.Arrays;

// Search with faceting on category field
SearchResult result = searcher.search("content:technology", 0, 10,
    Arrays.asList("category", "author"));

// Display facet results
for (Map.Entry<String, FacetResult> entry : result.getFacets().entrySet()) {
    System.out.println("Facet: " + entry.getKey());
    
    for (FacetResult.FacetValue value : entry.getValue().getValues()) {
        System.out.println("  " + value.getValue() + " (" + value.getCount() + ")");
    }
}
```

### 6. Streaming with NDJson

**Index from NDJson stream:**

```java
import com.hitorro.index.stream.IndexerStream;
import java.io.FileInputStream;

IndexerStream indexerStream = IndexerStream.builder()
    .indexWriter(indexWriter)
    .batchSize(100)
    .commitAfterBatch(true)
    .build();

// From file
FileInputStream fis = new FileInputStream("documents.ndjson");
Flux<IndexingResult> results = indexerStream.indexFromStream(fis);

results.subscribe(result -> {
    System.out.println("Indexed: " + result.getSuccessCount() + 
                       " in " + result.getDurationMs() + "ms");
    if (result.hasErrors()) {
        System.err.println("Errors: " + result.getErrors());
    }
});
```

**Output search results as NDJson:**

```java
import com.hitorro.index.stream.SearchResponseStream;

SearchResult result = searcher.search("content:data", 0, 100);

// Stream as NDJson lines
Flux<String> ndjson = SearchResponseStream.toNDJson(result);
ndjson.subscribe(line -> System.out.println(line));

// Or get as single string
String ndjsonString = SearchResponseStream.toNDJsonString(result);
System.out.println(ndjsonString);
```

### 7. Filesystem-Based Index

```java
// Create filesystem index
IndexConfig config = IndexConfig.filesystem("/path/to/my-index")
    .ramBufferSize(64.0)  // 64MB RAM buffer
    .build();

try (JVSLuceneIndexWriter indexWriter = new JVSLuceneIndexWriter(config)) {
    // Index documents...
    indexWriter.commit();
}

// Later, open for searching
try (JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
        .config(config)
        .build()) {
    SearchResult result = searcher.search("query", 0, 10);
}
```

### 8. Update and Delete

```java
// Update document
JVS updatedDoc = new JVS();
updatedDoc.set("id", "doc123");
updatedDoc.set("title", "Updated Title");
updatedDoc.set("content", "Updated content");

indexWriter.updateDocument("id", "doc123", updatedDoc);
indexWriter.commit();

// Delete document
indexWriter.deleteDocument("id", "doc123");
indexWriter.commit();

// Delete all documents
indexWriter.deleteAll();
indexWriter.commit();
```

## NDJson Format

Search results are returned as newline-delimited JSON with three object types:

**Line 1 - Metadata:**
```json
{"totalHits":150,"query":"content:test","offset":0,"limit":10,"searchTimeMs":23,"returned":10}
```

**Line 2 - Facets (if requested):**
```json
{"category":{"dimension":"category","totalCount":150,"values":[{"value":"Tech","count":80},{"value":"News","count":70}]}}
```

**Lines 3+ - Documents:**
```json
{"_score":0.95,"title":"First Result","content":"...","category":"Tech"}
{"_score":0.87,"title":"Second Result","content":"...","category":"News"}
...
```

## Type-Aware Indexing

When using JVS documents with types, the indexer automatically uses the Type System to determine:
- Which fields to index
- Which analyzer to use (based on field type and language)
- Field naming conventions
- Multi-valued vs single-valued fields

```java
// Create typed document
Type myType = JsonTypeSystem.getMe().getType("article");
JVS doc = new JVS(myType);
doc.set("title.mls", "My Article");  // Multi-language string
doc.set("author.name", "John Smith");

// Index - fields automatically projected based on type definition
indexWriter.indexDocument(doc);
```

## Next Steps

- See README.md for complete feature list
- See AGENTS.md for architecture details and advanced usage
- Check `/config/jsonconfigs/lucene_fields.json` for field type configuration
- Review test files for more examples
