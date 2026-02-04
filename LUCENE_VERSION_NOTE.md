# Lucene Version Compatibility Note

## Current Version: Lucene 9.11.1

### KNN/Vector Search Limitations

Lucene 9.11.1 includes `KnnFloatVectorField` and `KnnByteVectorField` for indexing vectors, but **does NOT include** the high-level `KnnFloatVectorQuery` and `KnnByteVectorQuery` classes that were added in Lucene 9.5+.

### Current Implementation

The current embedding search implementation in `JVSLuceneSearcher.searchByEmbedding()` is a **placeholder** that:
1. Accepts embedding search requests
2. Indexes vector fields correctly
3. Returns results, but WITHOUT proper KNN similarity scoring

### Recommended Solutions

#### Option 1: Upgrade to Lucene 9.5+ (Recommended)

Upgrade to Lucene 9.5 or later to get native KNN query support:

```xml
<lucene.version>9.12.0</lucene.version>
```

With Lucene 9.5+, the `KnnFloatVectorQuery` and `KnnByteVectorQuery` classes are available and the implementation will work as designed.

#### Option 2: Implement Custom KNN Scoring (Current Workaround)

For Lucene 9.11.1, you need to implement manual vector similarity computation:

1. **Retrieve all candidate documents** (or use a filter to narrow down)
2. **Extract vector values** from each document
3. **Compute similarity** manually (cosine, euclidean, etc.)
4. **Sort by similarity** and return top-k

Example pseudocode:
```java
// Get candidates
TopDocs candidates = searcher.search(filterQuery, maxCandidates);

// Score each candidate
List<ScoredDoc> scoredDocs = new ArrayList<>();
for (ScoreDoc scoreDoc : candidates.scoreDocs) {
    Document doc = searcher.doc(scoreDoc.doc);
    float[] docVector = extractVector(doc, fieldName);
    float similarity = computeSimilarity(queryVector, docVector, similarityFunction);
    scoredDocs.add(new ScoredDoc(doc, similarity));
}

// Sort by similarity and take top-k
scoredDocs.sort(Comparator.comparingDouble(ScoredDoc::getScore).reversed());
return scoredDocs.subList(0, Math.min(k, scoredDocs.size()));
```

#### Option 3: Use Lucene's Lower-Level API

Use `FloatVectorValues` API directly (available in Lucene 9.11.1):

```java
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.LeafReader;

// Access vector values directly from the index
for (LeafReaderContext context : reader.leaves()) {
    LeafReader leafReader = context.reader();
    FloatVectorValues vectorValues = leafReader.getFloatVectorValues(fieldName);
    // Iterate and compute similarities
}
```

### Testing Impact

**Current Test Results (as of last run):**

✅ **Passing Tests (15 total)**:
- `EmbeddingConfigTest`: 11/11 tests pass - validates configuration and builder
- `VectorSimilarityTest`: 4/4 tests pass - validates enum conversions

❌ **Failing Tests (7 total)**:
All failures are due to placeholder implementation returning ALL documents instead of K nearest neighbors:
- `EmbeddingSearchTest.testBasicEmbeddingSearch`: expects 3 results, gets 5 (all docs)
- `EmbeddingSearchTest.testKnnParameter`: expects 2 results, gets 5 (all docs)
- `EmbeddingSearchTest.testHybridSearchRRF`: expects ≤ 3 results, gets more
- `EmbeddingSearchTest.testHybridSearchWeightedSum`: similar K-limit issue
- `ByteVectorTest.testByteVectorSearch`: expects 2 results, gets 3 (all docs)
- `ByteVectorTest.testQuantizationClamping`: similar issue
- Additional test errors due to JVS field access patterns

**Root Cause**: Placeholder uses `MatchAllDocsQuery` instead of actual KNN search, returning all indexed documents rather than the K nearest neighbors

### Migration Path

1. **Short term**: Tests validate the API and configuration - index structure is correct
2. **Medium term**: Implement Option 2 (custom scoring) for Lucene 9.11.1
3. **Long term**: Upgrade to Lucene 9.12+ for native KNN support

### Related Files

- `/Users/chris/hitorro/hitorro-index/src/main/java/com/hitorro/index/search/JVSLuceneSearcher.java` - Search implementation
- `EMBEDDINGS.md` - User documentation (should note version requirements)

### Action Items

- [ ] Add version check warning in `searchByEmbedding()` method
- [ ] Update EMBEDDINGS.md to document Lucene version requirement
- [ ] Either implement custom KNN scoring OR upgrade Lucene version
- [ ] Add integration test that validates actual KNN behavior (once implemented)
