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
package com.hitorro.index.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.index.config.LuceneAnalyzerRegistry;
import com.hitorro.index.config.LuceneFieldType;
import com.hitorro.jsontypesystem.JVS;
import com.hitorro.jsontypesystem.executors.ProjectionContext;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

/**
 * Context for projecting JVS documents to Lucene Documents.
 * Extends ProjectionContext to work with the ExecutionBuilder mechanism.
 */
public class LuceneProjectionContext extends ProjectionContext {
    public Document document;

    public LuceneProjectionContext(JVS source) {
        this.source = source;
        this.target = new JVS(); // Not used for Lucene but required by parent
        this.document = new Document();
    }

    /**
     * Add a field to the Lucene document based on the field type configuration.
     *
     * @param fieldName The fully qualified field name
     * @param value     The JSON value
     * @param fieldType The field type configuration
     * @param lang      The language code (may be null)
     */
    public void addField(String fieldName, JsonNode value, LuceneFieldType fieldType, String lang) {
        if (value == null || value.isNull()) {
            return;
        }

        String textValue = getTextValue(value);
        if (textValue == null) {
            return;
        }

        // Determine if this is a multi-valued field based on field name suffix
        boolean isMultiValued = fieldName.endsWith("_m");

        // Determine field storage and indexing options
        Field.Store store = fieldType.isStored() ? Field.Store.YES : Field.Store.NO;

        // Add the appropriate field type based on configuration
        if (fieldType.isTokenized() && fieldType.isIndexed()) {
            // Tokenized text field - for indexing
            document.add(new TextField(fieldName, textValue, Field.Store.NO));
            // Store separately to support multi-valued fields
            if (fieldType.isStored()) {
                document.add(new StoredField(fieldName, textValue));
            }
        } else if (fieldType.isIndexed() && !fieldType.isTokenized()) {
            // Non-tokenized string field (exact match)
            document.add(new StringField(fieldName, textValue, Field.Store.NO));
            // Store separately to support multi-valued fields  
            if (fieldType.isStored()) {
                document.add(new StoredField(fieldName, textValue));
            }
        } else if (fieldType.isStored()) {
            // Stored-only field
            document.add(new StoredField(fieldName, textValue));
        }

        // Add docValues for faceting/sorting if configured
        if (fieldType.hasDocValues()) {
            addDocValuesField(fieldName, value, fieldType, isMultiValued);
            // Note: Not adding SortedSetDocValuesFacetField as it conflicts with regular fields
            // and requires FacetsConfig which we're avoiding. The SortedDocValuesField added
            // by addDocValuesField is sufficient for faceting via SortedSetDocValuesReaderState.
        }
    }

    /**
     * Add appropriate DocValues field based on the value type.
     * 
     * @param fieldName The field name
     * @param value The value
     * @param fieldType The field type configuration
     * @param isMultiValued Whether this field can have multiple values
     */
    private void addDocValuesField(String fieldName, JsonNode value, LuceneFieldType fieldType, boolean isMultiValued) {
        String indexType = fieldType.getIndexType();

        if (indexType != null) {
            switch (indexType.toLowerCase()) {
                case "long":
                    if (value.isNumber()) {
                        if (isMultiValued) {
                            // Multi-valued numeric field
                            document.add(new SortedNumericDocValuesField(fieldName, value.asLong()));
                        } else {
                            // Single-valued numeric field
                            document.add(new NumericDocValuesField(fieldName, value.asLong()));
                        }
                    }
                    break;
                case "int":
                    if (value.isNumber()) {
                        if (isMultiValued) {
                            // Multi-valued numeric field (store as long)
                            document.add(new SortedNumericDocValuesField(fieldName, value.asInt()));
                        } else {
                            // Single-valued numeric field
                            document.add(new NumericDocValuesField(fieldName, value.asInt()));
                        }
                    }
                    break;
                case "double":
                    if (value.isNumber()) {
                        if (isMultiValued) {
                            // Multi-valued double field (encode as sortable long)
                            document.add(new SortedNumericDocValuesField(fieldName, 
                                Double.doubleToRawLongBits(value.asDouble())));
                        } else {
                            // Single-valued double field
                            document.add(new DoubleDocValuesField(fieldName, value.asDouble()));
                        }
                    }
                    break;
                case "date":
                    if (value.isNumber()) {
                        if (isMultiValued) {
                            // Multi-valued date field (timestamps as longs)
                            document.add(new SortedNumericDocValuesField(fieldName, value.asLong()));
                        } else {
                            // Single-valued date field
                            document.add(new NumericDocValuesField(fieldName, value.asLong()));
                        }
                    }
                    break;
                default:
                    // For text and other string types
                    if (isMultiValued) {
                        // Multi-valued string field
                        document.add(new SortedSetDocValuesField(fieldName, 
                            new org.apache.lucene.util.BytesRef(getTextValue(value))));
                    } else {
                        // Single-valued string field
                        document.add(new SortedDocValuesField(fieldName, 
                            new org.apache.lucene.util.BytesRef(getTextValue(value))));
                    }
                    break;
            }
        }
    }

    /**
     * Extract text value from JsonNode.
     */
    private String getTextValue(JsonNode value) {
        if (value.isTextual()) {
            return value.textValue();
        } else if (value.isNumber()) {
            return value.asText();
        } else if (value.isBoolean()) {
            return Boolean.toString(value.booleanValue());
        }
        return value.asText();
    }

    /**
     * Get the built Lucene document.
     */
    public Document getDocument() {
        return document;
    }
}
