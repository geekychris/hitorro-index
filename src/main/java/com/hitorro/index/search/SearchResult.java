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
package com.hitorro.index.search;

import com.hitorro.jsontypesystem.JVS;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates search results including documents, metadata, and facets.
 */
public class SearchResult {
    private final List<JVS> documents;
    private final long totalHits;
    private final Map<String, FacetResult> facets;
    private final String query;
    private final int offset;
    private final int limit;
    private final long searchTimeMs;

    private SearchResult(Builder builder) {
        this.documents = builder.documents;
        this.totalHits = builder.totalHits;
        this.facets = builder.facets;
        this.query = builder.query;
        this.offset = builder.offset;
        this.limit = builder.limit;
        this.searchTimeMs = builder.searchTimeMs;
    }

    public List<JVS> getDocuments() {
        return documents;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public Map<String, FacetResult> getFacets() {
        return facets;
    }

    public String getQuery() {
        return query;
    }

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public long getSearchTimeMs() {
        return searchTimeMs;
    }

    public boolean hasFacets() {
        return facets != null && !facets.isEmpty();
    }

    /**
     * Convert search metadata to JVS format.
     */
    public JVS toMetadataJVS() {
        JVS metadata = new JVS();
        try {
            metadata.set("totalHits", totalHits);
            metadata.set("query", query);
            metadata.set("offset", offset);
            metadata.set("limit", limit);
            metadata.set("searchTimeMs", searchTimeMs);
            metadata.set("returned", documents.size());
        } catch (Exception e) {
            // Ignore
        }
        return metadata;
    }

    /**
     * Convert facets to JVS format.
     */
    public JVS toFacetsJVS() {
        if (facets == null || facets.isEmpty()) {
            return null;
        }
        
        JVS facetsJVS = new JVS();
        try {
            for (Map.Entry<String, FacetResult> entry : facets.entrySet()) {
                facetsJVS.set(entry.getKey(), entry.getValue().toJVS());
            }
        } catch (Exception e) {
            // Ignore
        }
        return facetsJVS;
    }

    public static class Builder {
        private List<JVS> documents;
        private long totalHits;
        private Map<String, FacetResult> facets;
        private String query;
        private int offset;
        private int limit;
        private long searchTimeMs;

        public Builder documents(List<JVS> documents) {
            this.documents = documents;
            return this;
        }

        public Builder totalHits(long totalHits) {
            this.totalHits = totalHits;
            return this;
        }

        public Builder facets(Map<String, FacetResult> facets) {
            this.facets = facets;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder searchTimeMs(long searchTimeMs) {
            this.searchTimeMs = searchTimeMs;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
