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
package com.hitorro.index.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.core.string.Fmt;
import com.hitorro.util.json.JsonInitable;
import com.hitorro.util.json.keys.BooleanProperty;
import com.hitorro.util.json.keys.StringProperty;

/**
 * Configuration for Lucene field types, defining how JVS fields are mapped to Lucene fields.
 * Similar to SolrFieldType but with Lucene-specific settings.
 */
public class LuceneFieldType implements JsonInitable {
    public static BooleanProperty Is18N = new BooleanProperty("i18n", "", false);
    public static BooleanProperty IsId = new BooleanProperty("isid", "", false);
    public static BooleanProperty Stored = new BooleanProperty("stored", "", true);
    public static BooleanProperty Indexed = new BooleanProperty("indexed", "", true);
    public static BooleanProperty Tokenized = new BooleanProperty("tokenized", "", true);
    public static BooleanProperty DocValues = new BooleanProperty("docValues", "", false);
    public static StringProperty Name = new StringProperty("name", "", null);

    private boolean is_18n;
    private boolean isId;
    private boolean stored;
    private boolean indexed;
    private boolean tokenized;
    private boolean docValues;
    private String indexType;

    public String toString() {
        return Fmt.S("LuceneFieldType: i18n:%s isid:%s type:%s stored:%s indexed:%s tokenized:%s docValues:%s",
                Boolean.toString(is_18n), Boolean.toString(isId), indexType,
                Boolean.toString(stored), Boolean.toString(indexed),
                Boolean.toString(tokenized), Boolean.toString(docValues));
    }

    /**
     * Generates the Lucene field name using the same convention as SOLR:
     * - For i18n fields: path.indexType_lang_m/s
     * - For non-i18n fields: path.indexType_m/s
     *
     * @param fieldPath StringBuilder containing the field path
     * @param lang      ISO language code (null for non-i18n fields)
     * @param isMulti   true if the field is multi-valued
     */
    public void get(StringBuilder fieldPath, String lang, boolean isMulti) {
        if (is_18n) {
            // this.is.my.path.indexType_lang_m/s
            Fmt.f(fieldPath, ".%s_%s_%s", indexType, lang, getMulti(isMulti));
        } else {
            // this.is.my.path.indexType_m/s
            Fmt.f(fieldPath, ".%s_%s", indexType, getMulti(isMulti));
        }
    }

    private char getMulti(boolean multi) {
        if (multi) {
            return 'm';
        }
        return 's';
    }

    @Override
    public boolean init(final JsonNode node) {
        is_18n = Is18N.apply(node);
        isId = IsId.apply(node);
        stored = Stored.apply(node);
        indexed = Indexed.apply(node);
        tokenized = Tokenized.apply(node);
        docValues = DocValues.apply(node);
        indexType = Name.apply(node);
        return true;
    }

    public boolean isI18n() {
        return is_18n;
    }

    public boolean isId() {
        return isId;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public boolean isTokenized() {
        return tokenized;
    }

    public boolean hasDocValues() {
        return docValues;
    }

    public String getIndexType() {
        return indexType;
    }
}
