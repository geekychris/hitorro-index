# AGENTS.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

This is a Lucene integration library for the Hitorro JSON Type System (JVS), providing full-text search, fielded search, faceting, and multi-language tokenization with streaming NDJson support. The library bridges JVS (a Jackson JsonNode-based type system) with Apache Lucene.

## Build System & Commands

**Build & Install:**
```bash
./build.sh
# or
mvn clean install
```

**Run Tests:**
```bash
mvn test
```

**Skip Tests During Build:**
```bash
mvn clean install -DskipTests
```

**Build Sources JAR:**
```bash
mvn source:jar
```

**Build Javadoc:**
```bash
mvn javadoc:jar
```

**Important:** This module depends on `hitorro-util:3.0.0`. If it's not in your local Maven repository, you must build it first from the sibling directory `../hitorro-util` using `mvn clean install`.

## Architecture

### Core Components

The library is organized into five main packages:

**1. `com.hitorro.index.config`** - Configuration and Field Type System
- `LuceneFieldType`: Maps JVS field types to Lucene field configurations
  - Supports i18n, stored, indexed, tokenized, docValues flags
  - Generates field names using convention: `path.type_lang_m/s`
- `LuceneFieldTypes`: Registry for field type configurations
  - Loads from `config/jsonconfigs/lucene_fields.json`
  - Caches field type mappings
- `LuceneAnalyzerRegistry`: Maps field types and languages to Lucene Analyzers
  - 30+ language-specific analyzers (EnglishAnalyzer, GermanAnalyzer, CJKAnalyzer, etc.)
  - Type-specific analyzers (KeywordAnalyzer for identifiers, StandardAnalyzer for text)
  - Extensible registration API
- `IndexConfig`: Index configuration with builder pattern
  - Storage: in-memory (ByteBuffersDirectory) or filesystem (FSDirectory)
  - IndexWriter settings: RAM buffer size, max buffered docs, auto-commit

**2. `com.hitorro.index.indexer`** - Document Indexing
- `LuceneIndexerAction`: ExecutorAction implementation for Lucene
  - Projects JVS fields to Lucene Document fields
  - Uses field type configuration to determine field properties
  - Handles multi-valued fields, stored vs indexed fields, docValues
- `LuceneProjectionContext`: Context for JVS → Lucene Document projection
  - Extends ProjectionContext from hitorro-util
  - Builds Lucene Document with appropriate field types
  - Adds TextField, StringField, StoredField, NumericDocValuesField, etc.
- `LuceneIndexerFactory`: Factory for creating LuceneIndexerAction instances
- `LuceneExecutionBuilderMapper`: Integrates with Type System's ExecutionBuilder
- `JVSLuceneIndexWriter`: Main indexing API
  - Wraps Lucene IndexWriter with JVS-aware methods
  - Thread-safe operations
  - Batch indexing support
  - Update/delete operations

**3. `com.hitorro.index.query`** - Query Parsing
- `JVSQueryParser`: Extends Lucene QueryParser
  - Supports dotted field names (e.g., `title.mls:search`)
  - Resolves JVS field paths to actual indexed field names
  - Uses Type System to determine field types and analyzers
  - Maps `title.mls` → `title.mls.text_en_s` based on type configuration

**4. `com.hitorro.index.search`** - Search and Faceting
- `SearchResult`: Encapsulates search results
  - Documents, total hits, query metadata
  - Facet results
  - Conversion to JVS format for NDJson output
- `FacetResult`: Represents facet results for a dimension
  - List of FacetValue (value + count)
  - Conversion to JVS format
- `JVSLuceneSearcher`: Main search API
  - Basic search with query string
  - Fielded search (map of field → query)
  - Faceting support (SortedSetDocValuesFacetCounts)
  - Streaming results (Flux<JVS>)
  - Thread-safe operations

**5. `com.hitorro.index.stream`** - NDJson Streaming
- `SearchResponseStream`: Converts SearchResult to NDJson
  - Three object types: metadata, facets, documents
  - Flux<String> for reactive streaming
  - Single string output with newlines
- `IndexerStream`: Consumes NDJson input for indexing
  - Batch processing with configurable size
  - Error handling and reporting
  - Multiple input formats: InputStream, Flux<String>, Flux<JVS>

### Key Design Patterns

**Field Naming Convention:**
- Inherited from SOLR implementation
- Format: `path.type_lang_m/s`
- Examples:
  - `title.mls.text_en_s` - Single-valued English text field at path title.mls
  - `tags.text_en_m` - Multi-valued English text field
  - `price.double_s` - Single-valued double field (no language)

**Type System Integration:**
- Uses ExecutionBuilder projection mechanism from hitorro-util
- Type definitions declare which fields are indexed (via Groups)
- ExecutionBuilder creates execution plan for a Type
- LuceneIndexerAction executes the plan to build Lucene Documents

**Query Field Resolution:**
- User queries use JVS field paths: `title.mls:search`
- JVSQueryParser resolves to indexed field: `title.mls.text_en_s`
- Type System provides field metadata (type, multi-valued, language)
- Appropriate analyzer selected based on field configuration

**Faceting:**
- Uses SortedSetDocValuesFacetField for string facets
- Requires docValues=true in field configuration
- FacetsConfig handles multi-valued facet fields
- Supports multiple facet dimensions per query

**Streaming:**
- NDJson format for interoperability
- Three-phase output: metadata → facets → documents
- Batch processing for efficient indexing
- Project Reactor for reactive streams

## Configuration Files

**Lucene Field Types:** `/Users/chris/hitorro/config/jsonconfigs/lucene_fields.json`

Field type configuration example:
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
    },
    {
      "name": "identifier",
      "i18n": false,
      "isid": false,
      "stored": true,
      "indexed": true,
      "tokenized": false,
      "docValues": true
    }
  ]
}
```

**Field Configuration Properties:**
- `name`: Field type name (text, identifier, long, int, double, date, textmarkup)
- `i18n`: Whether field supports internationalization (language-specific analyzers)
- `isid`: Whether field can be used as document ID
- `stored`: Whether field value is stored in index (for retrieval)
- `indexed`: Whether field is indexed for searching
- `tokenized`: Whether field is tokenized (false for exact match fields)
- `docValues`: Whether field has doc values for faceting/sorting

## Testing

### Test Framework
**Frameworks:** JUnit 5 (Jupiter)
- Uses in-memory indexes (ByteBuffersDirectory) - fast, no cleanup needed
- Test files follow `*Test.java` naming convention
- AssertJ available for fluent assertions
- Mockito available for mocking
- reactor-test available for testing reactive streams

### Running Tests

**Run All Tests:**
```bash
mvn test
```

**Run Single Test Class:**
```bash
mvn test -Dtest=LuceneIndexIntegrationTest
```

**Run Specific Test Method:**
```bash
mvn test -Dtest=LuceneIndexIntegrationTest#testBasicIndexingAndSearch
```

**Run Tests with Debug Output:**
```bash
mvn test -X
```

### Writing Tests

**Basic Test Structure:**
```java
import com.hitorro.index.config.IndexConfig;
import com.hitorro.index.indexer.JVSLuceneIndexWriter;
import com.hitorro.index.search.JVSLuceneSearcher;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.Test;

class MyLuceneTest {
    
    @Test
    void testIndexAndSearch() throws Exception {
        // Create in-memory index
        IndexConfig config = IndexConfig.inMemory().build();
        
        try (JVSLuceneIndexWriter indexWriter = new JVSLuceneIndexWriter(config)) {
            // Index document
            JVS doc = new JVS();
            doc.set("title", "Test");
            indexWriter.indexDocument(doc);
            indexWriter.commit();
        }
        
        // Search
        try (JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
                .config(config)
                .build()) {
            SearchResult result = searcher.search("title:Test", 0, 10);
            assertEquals(1, result.getTotalHits());
        }
    }
}
```

## API Reference

### IndexConfig - Index Configuration

**Create Configuration:**
```java
// In-memory (for testing)
IndexConfig config = IndexConfig.inMemory().build();

// Filesystem
IndexConfig config = IndexConfig.filesystem("/path/to/index").build();

// Custom configuration
IndexConfig config = IndexConfig.builder()
    .filesystem("/path/to/index")
    .ramBufferSize(32.0)
    .maxBufferedDocs(1000)
    .autoCommit(true)
    .commitInterval(60)
    .build();
```

### JVSLuceneIndexWriter - Indexing Operations

**Index Documents:**
```java
// Single document
indexWriter.indexDocument(jvs);

// Batch indexing
List<JVS> docs = Arrays.asList(doc1, doc2, doc3);
indexWriter.indexDocuments(docs);

// Update by ID
indexWriter.updateDocument("id.identifier_s", "doc123", updatedJVS);

// Delete by ID
indexWriter.deleteDocument("id.identifier_s", "doc123");

// Delete all
indexWriter.deleteAll();

// Commit changes
indexWriter.commit();

// Flush without commit
indexWriter.flush();
```

### JVSLuceneSearcher - Search Operations

**Basic Search:**
```java
try (JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
        .config(config)
        .type(myType)  // Optional: for field resolution
        .defaultLang("en")
        .build()) {
    
    SearchResult result = searcher.search("content:test", 0, 10);
    
    System.out.println("Total: " + result.getTotalHits());
    for (JVS doc : result.getDocuments()) {
        System.out.println(doc);
    }
}
```

**Search with Facets:**
```java
SearchResult result = searcher.search("content:news", 0, 10,
    Arrays.asList("category", "author", "date"));

Map<String, FacetResult> facets = result.getFacets();
for (Map.Entry<String, FacetResult> entry : facets.entrySet()) {
    FacetResult facet = entry.getValue();
    System.out.println(facet.getDimension() + ":");
    for (FacetResult.FacetValue value : facet.getValues()) {
        System.out.println("  " + value.getValue() + ": " + value.getCount());
    }
}
```

**Fielded Search:**
```java
Map<String, String> fieldQueries = new HashMap<>();
fieldQueries.put("title", "important");
fieldQueries.put("author", "Smith");

SearchResult result = searcher.fieldedSearch(fieldQueries, 0, 10);
```

**Streaming Search:**
```java
Flux<JVS> results = searcher.searchStream("content:data", 0, 100);
results.subscribe(doc -> processDocument(doc));
```

### SearchResponseStream - NDJson Output

**Stream Search Results:**
```java
SearchResult result = searcher.search("query", 0, 10);

// As Flux of JSON strings
Flux<String> ndjson = SearchResponseStream.toNDJson(result);
ndjson.subscribe(line -> System.out.println(line));

// As single NDJson string
String ndjsonString = SearchResponseStream.toNDJsonString(result);

// As Flux of JVS objects
Flux<JVS> jvsStream = SearchResponseStream.toJVSStream(result);
```

**NDJson Format:**
```
{"totalHits":100,"query":"test","offset":0,"limit":10,"searchTimeMs":45,"returned":10}
{"facets":{"category":[{"value":"news","count":50},{"value":"blog","count":30}]}}
{"_score":0.95,"title":"First Result","content":"..."}
{"_score":0.87,"title":"Second Result","content":"..."}
...
```

### IndexerStream - NDJson Input

**Index from Stream:**
```java
IndexerStream indexer = IndexerStream.builder()
    .indexWriter(indexWriter)
    .batchSize(100)
    .commitAfterBatch(true)
    .build();

// From InputStream
Flux<IndexingResult> results = indexer.indexFromStream(inputStream);

// From Flux<String>
Flux<String> jsonLines = Flux.just("{...}", "{...}");
Flux<IndexingResult> results = indexer.indexFromJsonFlux(jsonLines);

// From Flux<JVS>
Flux<JVS> jvsFlux = Flux.just(doc1, doc2);
Flux<IndexingResult> results = indexer.indexFromJVSFlux(jvsFlux);

// Process results
results.subscribe(result -> {
    System.out.println("Indexed: " + result.getSuccessCount());
    if (result.hasErrors()) {
        System.err.println("Errors: " + result.getErrors());
    }
});
```

## Dependencies & Versions

- **Java:** 21 (required - both source and target)
- **Apache Lucene:** 9.11.1
  - lucene-core
  - lucene-queryparser
  - lucene-analysis-common (language analyzers)
  - lucene-facet
- **Project Reactor:** 3.6.11
- **Jackson:** 2.18.2 (transitively from hitorro-util)
- **Hitorro Util:** 3.0.0 (contains JVS core and Type System)

## Code Conventions

**Field Naming:**
- Use the `path.type_lang_m/s` convention consistently
- Examples:
  - `title.mls.text_en_s` - Single English text field
  - `tags.text_en_m` - Multi-valued English text
  - `price.double_s` - Single double field
  - `id.identifier_s` - Single identifier (exact match)

**Analyzer Selection:**
- Text fields with i18n=true: Use language-specific analyzer
- Text fields with i18n=false: Use StandardAnalyzer
- Identifier/numeric fields: Use KeywordAnalyzer
- Custom fields: Register via LuceneAnalyzerRegistry

**Error Handling:**
- IndexWriter operations throw IOException
- Search operations throw IOException or ParseException
- Use try-with-resources for AutoCloseable components
- Reactive operations handle errors in Flux/Mono pipeline

**Threading:**
- JVSLuceneIndexWriter is thread-safe (uses ReentrantReadWriteLock)
- JVSLuceneSearcher is thread-safe (IndexSearcher is thread-safe)
- Avoid sharing IndexWriter across threads without synchronization

## Common Patterns

**Type-Aware Indexing:**
```java
// JVS document with type
JVS doc = new JVS(myType);
doc.set("title.mls", "Hello World");
doc.set("author.name", "John Smith");

// Index - fields automatically projected based on type definition
indexWriter.indexDocument(doc);
```

**Query with Type Resolution:**
```java
// Parser resolves field paths using type
JVSLuceneSearcher searcher = JVSLuceneSearcher.builder()
    .config(config)
    .type(myType)  // Enables field resolution
    .defaultLang("en")
    .build();

// Query uses JVS field path
SearchResult result = searcher.search("title.mls:hello", 0, 10);
// Internally resolves to: title.mls.text_en_s:hello
```

**Batch Processing:**
```java
// Index in batches for efficiency
List<JVS> batch = new ArrayList<>();
for (JVS doc : allDocuments) {
    batch.add(doc);
    if (batch.size() >= 100) {
        indexWriter.indexDocuments(batch);
        indexWriter.commit();
        batch.clear();
    }
}
// Don't forget remaining documents
if (!batch.isEmpty()) {
    indexWriter.indexDocuments(batch);
    indexWriter.commit();
}
```

**Faceted Search:**
```java
// Configure which dimensions to facet
List<String> facetDims = Arrays.asList("category", "author");

// Search with facets
SearchResult result = searcher.search("content:news", 0, 10, facetDims);

// Access facet results
for (Map.Entry<String, FacetResult> entry : result.getFacets().entrySet()) {
    System.out.println("Dimension: " + entry.getKey());
    for (FacetResult.FacetValue value : entry.getValue().getValues()) {
        System.out.println("  " + value.getValue() + " (" + value.getCount() + ")");
    }
}
```

## Common Pitfalls

**Missing Type on JVS:**
If JVS document doesn't have a type, indexing will fail. Ensure documents have a type set:
```java
JVS doc = new JVS(myType);  // Preferred
// or
JVS doc = new JVS();
doc.set("type", "myTypeName");
```

**Field Type Configuration:**
If field types are not configured in `lucene_fields.json`, indexing may fail or produce unexpected results. Ensure all required field types are defined.

**Commit Required:**
Changes are not visible to searchers until committed:
```java
indexWriter.indexDocument(doc);
indexWriter.commit();  // Must commit!
```

**Reader Refresh:**
If index is updated while searcher is open, refresh to see changes:
```java
searcher = searcher.refresh();
```

**Facet Field Configuration:**
Fields must have `docValues: true` in configuration to support faceting. Ensure facet dimensions match field type names (not full field paths).

**Language Code Format:**
Use ISO 639-1 two-letter codes: "en", "fr", "de", etc. (not "eng", "fra", "deu")

## Project Structure

```
hitorro-index/
├── pom.xml                              # Maven configuration
├── build.sh                             # Build script
├── README.md                            # User documentation
├── AGENTS.md                            # This file
└── src/
    ├── main/java/com/hitorro/index/
    │   ├── config/
    │   │   ├── IndexConfig.java         # Index configuration
    │   │   ├── LuceneAnalyzerRegistry.java  # Analyzer mappings
    │   │   ├── LuceneFieldType.java     # Field type config
    │   │   └── LuceneFieldTypes.java    # Field type registry
    │   ├── indexer/
    │   │   ├── JVSLuceneIndexWriter.java    # Main indexing API
    │   │   ├── LuceneIndexerAction.java     # Projection action
    │   │   ├── LuceneIndexerFactory.java    # Action factory
    │   │   ├── LuceneProjectionContext.java # Projection context
    │   │   └── LuceneExecutionBuilderMapper.java  # Type integration
    │   ├── query/
    │   │   └── JVSQueryParser.java      # Query parser
    │   ├── search/
    │   │   ├── FacetResult.java         # Facet results
    │   │   ├── JVSLuceneSearcher.java   # Main search API
    │   │   └── SearchResult.java        # Search results
    │   └── stream/
    │       ├── IndexerStream.java       # NDJson input streaming
    │       └── SearchResponseStream.java # NDJson output streaming
    └── test/java/com/hitorro/index/
        ├── LuceneIndexIntegrationTest.java  # Integration tests
        └── StreamingTest.java           # Streaming tests
```

## Integration with Hitorro Ecosystem

**Type System Integration:**
- Uses ExecutionBuilder from hitorro-util
- Leverages Group definitions for field indexing rules
- Reuses field type configuration pattern from SOLR integration

**SOLR Compatibility:**
- Field naming convention matches SOLR
- Configuration structure similar to `solr_fields.json`
- Same projection mechanism (ExecutorAction pattern)

**Future Extensions:**
- Integration with hitorro-jsonts-mongo for document storage
- Full document retrieval from external KV store
- Real-time indexing with change streams
- Distributed search support
