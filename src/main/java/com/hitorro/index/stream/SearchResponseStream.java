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
package com.hitorro.index.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.index.search.SearchResult;
import com.hitorro.jsontypesystem.JVS;
import reactor.core.publisher.Flux;

/**
 * Streams search results as NDJson (newline-delimited JSON).
 * Outputs three types of objects:
 * 1. Metadata object (search stats)
 * 2. Facets object (facet results)
 * 3. Document objects (search results)
 */
public class SearchResponseStream {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert SearchResult to NDJson stream.
     * First line: metadata
     * Second line: facets (if present)
     * Remaining lines: documents
     *
     * @param result SearchResult to stream
     * @return Flux of JSON strings (one per line)
     */
    public static Flux<String> toNDJson(SearchResult result) {
        return Flux.concat(
                // First emit metadata
                Flux.just(result.toMetadataJVS())
                        .map(SearchResponseStream::toJson),
                
                // Then emit facets if present
                result.hasFacets() 
                    ? Flux.just(result.toFacetsJVS()).map(SearchResponseStream::toJson)
                    : Flux.empty(),
                
                // Finally emit all documents
                Flux.fromIterable(result.getDocuments())
                        .map(SearchResponseStream::toJson)
        );
    }

    /**
     * Convert SearchResult to a single NDJson string.
     *
     * @param result SearchResult to convert
     * @return NDJson string with newlines
     */
    public static final String toNDJsonString(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        
        // Metadata line
        sb.append(toJson(result.toMetadataJVS())).append('\n');
        
        // Facets line (if present)
        if (result.hasFacets()) {
            sb.append(toJson(result.toFacetsJVS())).append('\n');
        }
        
        // Document lines
        for (JVS doc : result.getDocuments()) {
            sb.append(toJson(doc)).append('\n');
        }
        
        return sb.toString();
    }

    /**
     * Stream search results as Flux of JVS objects (not serialized).
     * Useful for downstream processing before serialization.
     *
     * @param result SearchResult to stream
     * @return Flux of JVS objects
     */
    public static Flux<JVS> toJVSStream(SearchResult result) {
        return Flux.concat(
                Flux.just(result.toMetadataJVS()),
                result.hasFacets() 
                    ? Flux.just(result.toFacetsJVS())
                    : Flux.empty(),
                Flux.fromIterable(result.getDocuments())
        );
    }

    /**
     * Convert JVS to JSON string.
     */
    private static String toJson(JVS jvs) {
        if (jvs == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(jvs.getJsonNode());
        } catch (Exception e) {
            return "{}";
        }
    }
}
