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

import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ExampleDatasetsTest {

    private IndexManager manager;

    @AfterEach
    public void tearDown() throws Exception {
        if (manager != null) {
            manager.close();
        }
    }

    // ========== Raw document access tests ==========

    @Test
    public void testGetArticlesReturns15Documents() {
        assertEquals(15, ExampleDatasets.getArticles().size());
    }

    @Test
    public void testGetProductsReturns15Documents() {
        assertEquals(15, ExampleDatasets.getProducts().size());
    }

    @Test
    public void testGetDocumentsReturns15Documents() {
        assertEquals(15, ExampleDatasets.getDocumentDocs().size());
    }

    @Test
    public void testArticlesFollowJvsTypeSystem() {
        for (JVS article : ExampleDatasets.getArticles()) {
            assertEquals("demo_article", article.get("type").asText(), "type should be demo_article");
            assertNotNull(article.get("id.domain"), "should have id.domain");
            assertNotNull(article.get("id.did"), "should have id.did");
            assertEquals("en", article.get("title.mls[0].lang").asText(), "title MLS should have lang");
            assertFalse(article.get("title.mls[0].text").asText().isEmpty(), "title MLS should have text");
            assertEquals("en", article.get("content.mls[0].lang").asText(), "content MLS should have lang");
            assertNotNull(article.get("author"), "should have author");
            assertTrue(article.get("tags").isArray(), "tags should be array");
        }
    }

    @Test
    public void testProductsFollowJvsTypeSystem() {
        for (JVS product : ExampleDatasets.getProducts()) {
            assertEquals("demo_product", product.get("type").asText(), "type should be demo_product");
            assertNotNull(product.get("id.domain"), "should have id.domain");
            assertNotNull(product.get("id.did"), "should have id.did");
            assertEquals("en", product.get("title.mls[0].lang").asText(), "title MLS should have lang");
            assertEquals("en", product.get("description.mls[0].lang").asText(), "description MLS should have lang");
            assertNotNull(product.get("brand"), "should have brand");
            assertNotNull(product.get("price"), "should have price");
            assertNotNull(product.get("sku"), "should have sku");
            assertTrue(product.get("category").isArray(), "category should be array");
        }
    }

    @Test
    public void testDocumentsFollowJvsTypeSystem() {
        for (JVS doc : ExampleDatasets.getDocumentDocs()) {
            assertEquals("demo_document", doc.get("type").asText(), "type should be demo_document");
            assertNotNull(doc.get("id.domain"), "should have id.domain");
            assertNotNull(doc.get("id.did"), "should have id.did");
            assertEquals("en", doc.get("title.mls[0].lang").asText(), "title MLS should have lang");
            assertEquals("en", doc.get("body.mls[0].lang").asText(), "body MLS should have lang");
            assertNotNull(doc.get("author"), "should have author");
            assertNotNull(doc.get("department"), "should have department");
            assertTrue(doc.get("keywords").isArray(), "keywords should be array");
            assertNotNull(doc.get("classification"), "should have classification");
        }
    }

    @Test
    public void testIdsAreGloballyUnique() {
        Set<String> allIds = new java.util.HashSet<>();
        for (ExampleDatasets.Dataset ds : ExampleDatasets.Dataset.values()) {
            for (JVS doc : ExampleDatasets.getDocuments(ds)) {
                String domain = doc.get("id.domain").asText();
                String did = doc.get("id.did").asText();
                assertTrue(allIds.add(domain + "/" + did), "Duplicate ID: " + domain + "/" + did);
            }
        }
        assertEquals(45, allIds.size());
    }

    @Test
    public void testGetDocumentsByDatasetEnum() {
        assertEquals(15, ExampleDatasets.getDocuments(ExampleDatasets.Dataset.ARTICLES).size());
        assertEquals(15, ExampleDatasets.getDocuments(ExampleDatasets.Dataset.PRODUCTS).size());
        assertEquals(15, ExampleDatasets.getDocuments(ExampleDatasets.Dataset.DOCUMENTS).size());
    }

    @Test
    public void testGetAvailableDatasets() {
        List<ExampleDatasets.Dataset> datasets = ExampleDatasets.getAvailableDatasets();
        assertEquals(3, datasets.size());
        assertTrue(datasets.contains(ExampleDatasets.Dataset.ARTICLES));
        assertTrue(datasets.contains(ExampleDatasets.Dataset.PRODUCTS));
        assertTrue(datasets.contains(ExampleDatasets.Dataset.DOCUMENTS));
    }

    @Test
    public void testDatasetIndexNames() {
        assertEquals("articles", ExampleDatasets.Dataset.ARTICLES.getIndexName());
        assertEquals("products", ExampleDatasets.Dataset.PRODUCTS.getIndexName());
        assertEquals("documents", ExampleDatasets.Dataset.DOCUMENTS.getIndexName());
    }

    // ========== Index loading tests ==========

    @Test
    public void testLoadAllDatasets() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.LoadResult result = ExampleDatasets.loadAll(manager);

        assertEquals(45, result.getTotalLoaded());
        assertEquals(3, result.getLoadedDatasets().size());
        assertTrue(manager.hasIndex("articles"));
        assertTrue(manager.hasIndex("products"));
        assertTrue(manager.hasIndex("documents"));
    }

    @Test
    public void testSearchArticlesIndex() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.loadArticles(manager);
        SearchResult result = manager.search("articles", "*:*", 0, 20, null);
        assertEquals(15, result.getTotalHits());
    }

    @Test
    public void testSearchProductsIndex() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.loadProducts(manager);
        SearchResult result = manager.search("products", "*:*", 0, 20, null);
        assertEquals(15, result.getTotalHits());
    }

    @Test
    public void testSearchDocumentsIndex() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.loadDocuments(manager);
        SearchResult result = manager.search("documents", "*:*", 0, 20, null);
        assertEquals(15, result.getTotalHits());
    }

    @Test
    public void testSearchAcrossMultipleIndices() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.loadAll(manager);
        SearchResult result = manager.searchMultiple(
                List.of("articles", "products", "documents"), "*:*", 0, 50);
        assertEquals(45, result.getTotalHits());
    }

    @Test
    public void testPaginationAcrossIndices() throws Exception {
        manager = new IndexManager("en");
        ExampleDatasets.loadAll(manager);
        List<String> all = List.of("articles", "products", "documents");

        SearchResult page1 = manager.searchMultiple(all, "*:*", 0, 10);
        SearchResult page2 = manager.searchMultiple(all, "*:*", 10, 10);
        assertEquals(10, page1.getDocuments().size());
        assertEquals(10, page2.getDocuments().size());
    }
}
