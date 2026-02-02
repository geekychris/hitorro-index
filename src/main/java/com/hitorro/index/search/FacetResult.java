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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.jsontypesystem.JVS;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents facet results for a single dimension.
 */
public class FacetResult {
    private final String dimension;
    private final List<FacetValue> values;
    private final long totalCount;

    public FacetResult(String dimension, List<FacetValue> values, long totalCount) {
        this.dimension = dimension;
        this.values = values;
        this.totalCount = totalCount;
    }

    public String getDimension() {
        return dimension;
    }

    public List<FacetValue> getValues() {
        return values;
    }

    public long getTotalCount() {
        return totalCount;
    }

    /**
     * Convert facet result to JVS format.
     */
    public ObjectNode toJVS() {
        ObjectNode facetNode = JsonNodeFactory.instance.objectNode();
        facetNode.put("dimension", dimension);
        facetNode.put("totalCount", totalCount);
        
        ArrayNode valuesArray = JsonNodeFactory.instance.arrayNode();
        for (FacetValue value : values) {
            ObjectNode valueNode = JsonNodeFactory.instance.objectNode();
            valueNode.put("value", value.getValue());
            valueNode.put("count", value.getCount());
            valuesArray.add(valueNode);
        }
        facetNode.set("values", valuesArray);
        
        return facetNode;
    }

    /**
     * Represents a single facet value with its count.
     */
    public static class FacetValue {
        private final String value;
        private final long count;

        public FacetValue(String value, long count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() {
            return value;
        }

        public long getCount() {
            return count;
        }
    }
}
