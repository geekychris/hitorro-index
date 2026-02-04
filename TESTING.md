# Embedding Support Test Suite

## Overview

Comprehensive test suite for the embedding/vector search functionality in hitorro-index.

## Test Files

### 1. EmbeddingConfigTest.java
**Purpose**: Unit tests for EmbeddingConfig builder and validation

**Coverage**:
- Default configuration values
- Custom configuration
- Field name validation (null, empty, whitespace)
- Dimension validation (negative, zero, too large, valid ranges)
- Similarity function validation
- Field type validation (FLOAT_VECTOR, BYTE_VECTOR)
- HNSW M parameter validation (range [2, 96])
- HNSW efConstruction validation (range [1, 3200])
- Common embedding dimensions (384, 768, 1536, 3072)
- HNSW parameter recommendations for different dataset sizes

**Test Count**: 12 tests

---

### 2. VectorSimilarityTest.java
**Purpose**: Unit tests for VectorSimilarity enum

**Coverage**:
- All similarity functions present (COSINE, DOT_PRODUCT, EUCLIDEAN, MAXIMUM_INNER_PRODUCT)
- Conversion to Lucene VectorSimilarityFunction
- Conversion from Lucene VectorSimilarityFunction
- Round-trip conversion validation

**Test Count**: 4 tests

---

### 3. EmbeddingSearchTest.java
**Purpose**: Integration tests for embedding indexing and search

**Coverage**:
- Basic embedding search (KNN/ANN)
- K parameter behavior
- Hybrid search with RRF (Reciprocal Rank Fusion)
- Hybrid search with weighted sum
- Different similarity functions
- Configuration validation
- Search request validation

**Test Count**: 7 tests

---

### 4. ByteVectorTest.java
**Purpose**: Tests for byte vector (quantized) functionality

**Coverage**:
- Byte vector indexing and search
- Quantization preserves relative similarities
- Memory efficiency (4x savings vs float)
- Extreme value clamping (values outside [-1, 1])

**Test Count**: 4 tests

---

### 5. HybridSearchStrategiesTest.java
**Purpose**: Comparison of different hybrid search strategies

**Coverage**:
- RRF (Reciprocal Rank Fusion) strategy
- Weighted sum with high text weight (alpha=0.9)
- Weighted sum with high vector weight (alpha=0.1)
- Weighted sum with balanced weights (alpha=0.5)
- Max score strategy
- All strategies return reasonable results
- Alpha parameter validation

**Test Count**: 7 tests

---

### 6. EmbeddingErrorHandlingTest.java
**Purpose**: Error handling and edge cases

**Coverage**:
- Embedding search on non-embedding index (throws exception)
- Hybrid search on non-embedding index (throws exception)
- Wrong dimension embedding (graceful degradation)
- Null embedding field (allowed)
- Missing embedding field (allowed)
- Invalid embedding type (graceful degradation)
- Mixed valid/invalid embeddings in batch
- Empty query vector (throws exception)
- Null query vector (throws exception)
- Invalid k values (zero, negative)
- Hybrid search validation (missing text, empty text, missing vector)
- Very large dimension (4096)
- Search on empty index
- Different vector input formats (List<Float>, float[], double[])

**Test Count**: 17 tests

---

## Total Test Coverage

- **51 total tests**
- **6 test classes**
- **Coverage areas**:
  - Configuration validation
  - Indexing (float and byte vectors)
  - Search (pure vector, hybrid)
  - Error handling
  - Edge cases
  - Different input formats

## Running Tests

### Run All Embedding Tests

```bash
cd /Users/chris/hitorro/hitorro-index
mvn test -Dtest="com.hitorro.index.embeddings.*"
```

### Run Specific Test Class

```bash
# Configuration tests
mvn test -Dtest=EmbeddingConfigTest

# Vector similarity tests
mvn test -Dtest=VectorSimilarityTest

# Embedding search integration tests
mvn test -Dtest=EmbeddingSearchTest

# Byte vector tests
mvn test -Dtest=ByteVectorTest

# Hybrid search strategy comparison
mvn test -Dtest=HybridSearchStrategiesTest

# Error handling tests
mvn test -Dtest=EmbeddingErrorHandlingTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=EmbeddingSearchTest#testBasicEmbeddingSearch
```

### Run All Tests in hitorro-index Module

```bash
mvn test
```

### Run Tests with Coverage

```bash
mvn clean test jacoco:report
```

Coverage report will be in `target/site/jacoco/index.html`

## Test Dependencies

Tests require:
- JUnit 5 (Jupiter)
- Lucene 9.11.1
- hitorro-jsontypesystem (for JVS and Type)
- In-memory index configuration (no external dependencies)

## Test Data

Tests use:
- Small vector dimensions (4-8) for fast execution
- Simple synthetic data (technology, sports, food, travel topics)
- In-memory indexes (no filesystem I/O)
- No external embedding models (hardcoded test vectors)

## Expected Behavior

### Successful Tests
- All 51 tests should pass
- Tests are ordered using `@Order` annotations
- Tests are isolated (each test class has its own indexes)

### Test Execution Time
- Full suite: ~5-10 seconds
- Individual test class: ~1-2 seconds
- Fast due to in-memory indexes and small vectors

## Continuous Integration

Tests are suitable for CI/CD pipelines:
- No external dependencies
- Deterministic results
- Fast execution
- No network access required

## Debugging Failed Tests

If tests fail:

1. **Check Lucene version**: Must be 9.11.1
   ```bash
   mvn dependency:tree | grep lucene-core
   ```

2. **Check Java version**: Must be Java 21
   ```bash
   java -version
   ```

3. **Run with verbose output**:
   ```bash
   mvn test -Dtest=FailingTest -X
   ```

4. **Check logs**: Tests use SLF4J logging
   ```bash
   mvn test 2>&1 | grep -A 10 "WARN\|ERROR"
   ```

## Adding New Tests

When adding new embedding features:

1. Add unit tests for new configuration classes
2. Add integration tests for new search methods
3. Add error handling tests for validation
4. Update this document with new test counts

### Test Naming Conventions

- **Class name**: `[Feature]Test.java`
- **Method name**: `test[Behavior]` (camelCase)
- **Display name**: Use `@DisplayName` with clear description

### Example

```java
@Test
@DisplayName("Test feature with specific condition")
public void testFeatureWithCondition() {
    // Arrange
    // Act
    // Assert
}
```

## Coverage Goals

Current coverage (estimated):
- **Configuration classes**: 100%
- **Indexing logic**: 90%
- **Search logic**: 85%
- **Error handling**: 95%

## Known Limitations

Tests do NOT cover:
- Performance/benchmark testing (separate suite needed)
- Recall accuracy measurements (would require labeled data)
- Large-scale testing (millions of vectors)
- Real embedding model integration (only synthetic vectors)
- Filesystem-based indexes (only in-memory)
- Concurrent access patterns

For these scenarios, create separate test suites or use manual testing.

## Test Maintenance

- Update tests when Lucene version changes
- Regenerate test data if default dimensions change
- Review HNSW parameter ranges if Lucene limits change
- Keep test vectors small for fast execution
