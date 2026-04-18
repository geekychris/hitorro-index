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
 *
 * <p>Stream ordering:
 * <ol>
 *   <li>Metadata object ({@code _type: "meta"}) -- search stats</li>
 *   <li>Document objects ({@code _type: "doc"}) -- one per line, streamed</li>
 *   <li>Facets object ({@code _type: "facets"}) -- after all documents</li>
 * </ol>
 *
 * <p>This ordering allows consumers to display documents as they arrive,
 * then update facet navigation after the stream completes.
 */
public class SearchResponseStream {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert SearchResult to NDJson stream.
     * Order: metadata, documents, facets (tail).
     */
    public static Flux<String> toNDJson(SearchResult result) {
        return Flux.concat(
                // 1. Metadata
                Flux.just(result.toMetadataJVS())
                        .doOnNext(jvs -> jvs.set("_type", "meta"))
                        .map(SearchResponseStream::toJson),

                // 2. Documents
                Flux.fromIterable(result.getDocuments())
                        .doOnNext(jvs -> jvs.set("_type", "doc"))
                        .map(SearchResponseStream::toJson),

                // 3. Facets at the tail (after all documents)
                result.hasFacets()
                    ? Flux.just(result.toFacetsJVS())
                        .doOnNext(jvs -> jvs.set("_type", "facets"))
                        .map(SearchResponseStream::toJson)
                    : Flux.empty()
        );
    }

    /**
     * Convert SearchResult to a single NDJson string.
     * Order: metadata, documents, facets (tail).
     */
    public static final String toNDJsonString(SearchResult result) {
        StringBuilder sb = new StringBuilder();

        // 1. Metadata
        JVS meta = result.toMetadataJVS();
        meta.set("_type", "meta");
        sb.append(toJson(meta)).append('\n');

        // 2. Documents
        for (JVS doc : result.getDocuments()) {
            doc.set("_type", "doc");
            sb.append(toJson(doc)).append('\n');
        }

        // 3. Facets at the tail
        if (result.hasFacets()) {
            JVS facets = result.toFacetsJVS();
            facets.set("_type", "facets");
            sb.append(toJson(facets)).append('\n');
        }

        return sb.toString();
    }

    /**
     * Stream search results as Flux of JVS objects (not serialized).
     * Order: metadata, documents, facets (tail).
     */
    public static Flux<JVS> toJVSStream(SearchResult result) {
        return Flux.concat(
                Flux.just(result.toMetadataJVS())
                        .doOnNext(jvs -> jvs.set("_type", "meta")),
                Flux.fromIterable(result.getDocuments())
                        .doOnNext(jvs -> jvs.set("_type", "doc")),
                result.hasFacets()
                    ? Flux.just(result.toFacetsJVS())
                        .doOnNext(jvs -> jvs.set("_type", "facets"))
                    : Flux.empty()
        );
    }

    private static String toJson(JVS jvs) {
        if (jvs == null) return "{}";
        try {
            return objectMapper.writeValueAsString(jvs.getJsonNode());
        } catch (Exception e) {
            return "{}";
        }
    }
}
