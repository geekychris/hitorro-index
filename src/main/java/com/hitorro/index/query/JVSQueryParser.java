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
package com.hitorro.index.query;

import com.hitorro.index.config.LuceneAnalyzerRegistry;
import com.hitorro.index.config.LuceneFieldType;
import com.hitorro.index.config.LuceneFieldTypes;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.jsontypesystem.Type;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

import java.util.Collection;

/**
 * Query parser that supports dotted field names and leverages the Type system.
 * Maps user-facing field paths (e.g., "title.mls") to internal indexed field names.
 */
public class JVSQueryParser extends QueryParser {
    private final Type type;
    private final String defaultLang;
    private final LuceneFieldTypes fieldTypes;

    /**
     * Create a JVS query parser.
     *
     * @param type        The JVS Type for field resolution
     * @param defaultField The default field for queries without explicit field
     * @param analyzer    The analyzer to use
     * @param defaultLang Default language code for i18n fields (e.g., "en")
     */
    public JVSQueryParser(Type type, String defaultField, Analyzer analyzer, String defaultLang) {
        super(defaultField, analyzer);
        this.type = type;
        this.defaultLang = defaultLang != null ? defaultLang : "en";
        this.fieldTypes = LuceneFieldTypes.getInstance();
        
        // Allow leading wildcards for more flexible searching
        setAllowLeadingWildcard(true);
    }

    /**
     * Override getFieldQuery to handle dotted field names.
     * Maps JVS field paths to actual indexed field names.
     */
    @Override
    protected Query getFieldQuery(String field, String queryText, boolean quoted) throws org.apache.lucene.queryparser.classic.ParseException {
        // Resolve the field path to actual indexed field name(s)
        String resolvedField = resolveFieldName(field);
        
        // Get appropriate analyzer for this field
        Analyzer fieldAnalyzer = getAnalyzerForField(field);
        
        return super.getFieldQuery(resolvedField, queryText, quoted);
    }

    /**
     * Resolve a JVS field path to the actual indexed field name.
     * For example, "title.mls" might resolve to "title.mls.text_en_s"
     *
     * @param fieldPath The JVS field path (potentially with dots)
     * @return The resolved Lucene field name
     */
    private String resolveFieldName(String fieldPath) {
        if (type == null || fieldPath == null) {
            return fieldPath;
        }

        try {
            // Parse the field path
            Propaccess access = new Propaccess(fieldPath);
            
            // Get the field from the type system
            Field field = type.getField(access);
            if (field == null) {
                // Field not found in type system, use as-is
                return fieldPath;
            }

            // Get the default group for indexing
            Group group = type.getDefaultGroupFor(access, "index");
            if (group == null) {
                // No index group, use as-is
                return fieldPath;
            }

            // Get the field type configuration
            String method = group.getMethod();
            LuceneFieldType lft = fieldTypes.get(method);
            if (lft == null) {
                return fieldPath;
            }

            // Determine if multi-valued
            boolean isMulti = type.isMultiValuedPath(access);
            
            // Build the indexed field name
            StringBuilder sb = new StringBuilder();
            access.getPathSansIndex(sb);
            lft.get(sb, defaultLang, isMulti);
            
            return sb.toString();
            
        } catch (Exception e) {
            // If resolution fails, use the field path as-is
            return fieldPath;
        }
    }

    /**
     * Get the appropriate analyzer for a field based on the type system.
     */
    private Analyzer getAnalyzerForField(String fieldPath) {
        if (type == null || fieldPath == null) {
            return getAnalyzer();
        }

        try {
            Propaccess access = new Propaccess(fieldPath);
            Field field = type.getField(access);
            
            if (field != null) {
                Group group = type.getDefaultGroupFor(access, "index");
                if (group != null) {
                    String method = group.getMethod();
                    LuceneFieldType lft = fieldTypes.get(method);
                    if (lft != null) {
                        return LuceneAnalyzerRegistry.getAnalyzer(lft, defaultLang);
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to default analyzer
        }
        
        return getAnalyzer();
    }

    /**
     * Create a query parser with English as default language.
     */
    public static JVSQueryParser create(Type type, String defaultField, Analyzer analyzer) {
        return new JVSQueryParser(type, defaultField, analyzer, "en");
    }
}
