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

        // NOTE: We currently compute the field-specific analyzer but
        // Lucene's QueryParser uses the analyzer passed to the
        // constructor. We keep this call for future extension and
        // for symmetry with field resolution, but the returned
        // analyzer is not wired into parsing yet.
        Analyzer fieldAnalyzer = getAnalyzerForField(field);

        return super.getFieldQuery(resolvedField, queryText, quoted);
    }

    /**
     * Resolve a JVS field path to the actual indexed field name.
     * For example, "title.mls" might resolve to "title.mls.text_en_s".
     *
     * This method also supports:
     * - Explicit indexing methods as a trailing segment, e.g.
     *   "title.mls.clean.text" → method "text" on field "title.mls.clean".
     * - Direct use of physical Lucene field names like
     *   "title.mls.clean.text_en_m", which are returned unchanged
     *   for backward compatibility.
     *
     * @param fieldPath The JVS field path or physical field name
     * @return The resolved Lucene field name
     */
    private String resolveFieldName(String fieldPath) {
        if (type == null || fieldPath == null) {
            return fieldPath;
        }

        try {
            // If this already looks like a concrete Lucene field name
            // (e.g. *.text_en_s), preserve it as-is for backwards compatibility.
            if (isPhysicalFieldName(fieldPath)) {
                return fieldPath;
            }

            ResolvedField resolved = resolveField(fieldPath);
            if (resolved == null) {
                // Field not found in type system or no index group; use as-is
                return fieldPath;
            }

            // Build the indexed field name from the base JSON path and
            // the LuceneFieldType suffix using the effective language.
            StringBuilder sb = new StringBuilder();
            resolved.baseAccess.getPathSansIndex(sb);
            resolved.lft.get(sb, resolved.lang, resolved.multiValued);
            return sb.toString();

        } catch (Exception e) {
            // If resolution fails, use the field path as-is
            return fieldPath;
        }
    }

    /**
     * Get the appropriate analyzer for a field based on the type system.
     * Uses the same resolution logic as resolveFieldName so that
     * method overrides (e.g. ".text") are treated consistently.
     */
    private Analyzer getAnalyzerForField(String fieldPath) {
        if (type == null || fieldPath == null) {
            return getAnalyzer();
        }

        try {
            // Physical field names are already fully specified; fall
            // back to the default analyzer in that case.
            if (isPhysicalFieldName(fieldPath)) {
                return getAnalyzer();
            }

            ResolvedField resolved = resolveField(fieldPath);
            if (resolved != null && resolved.lft != null) {
                return LuceneAnalyzerRegistry.getAnalyzer(resolved.lft, resolved.lang);
            }
        } catch (Exception e) {
            // Fall back to default analyzer
        }

        return getAnalyzer();
    }

    /**
     * Helper class capturing the resolution of a logical JVS field
     * path (with optional method suffix) to a base path and Lucene
     * field type.
     */
    private static class ResolvedField {
        final Propaccess baseAccess;
        final LuceneFieldType lft;
        final boolean multiValued;
        final String lang;

        ResolvedField(Propaccess baseAccess, LuceneFieldType lft, boolean multiValued, String lang) {
            this.baseAccess = baseAccess;
            this.lft = lft;
            this.multiValued = multiValued;
            this.lang = lang;
        }
    }

    /**
     * Core resolution logic shared between field name mapping and
     * analyzer selection.
     *
     * @param fieldPath Logical field path, optionally with a trailing
     *                  indexing method (e.g. ".text").
     * @return ResolvedField or null if the path cannot be mapped via
     *         the type system.
     */
    private ResolvedField resolveField(String fieldPath) {
        if (type == null || fieldPath == null) {
            return null;
        }

        Propaccess access = new Propaccess(fieldPath);

        // Determine how much of the path matches the type system.
        int commonDepth = type.getMaxCommonDepth(access);
        if (commonDepth == 0) {
            // No prefix in the type system – treat as unknown.
            return null;
        }

        // Build the base field path from the matching prefix.
        StringBuilder basePathBuilder = new StringBuilder();
        for (int i = 0; i < commonDepth; i++) {
            if (i > 0) {
                basePathBuilder.append('.');
            }
            basePathBuilder.append(access.get(i).name());
        }
        String baseFieldPath = basePathBuilder.toString();
        Propaccess baseAccess = new Propaccess(baseFieldPath);

        // Resolve the base field from the type system.
        Field field = type.getField(baseAccess);
        if (field == null) {
            return null;
        }

        // Determine if we have a trailing method override segment.
        String methodOverride = null;
        if (access.length() > commonDepth) {
            methodOverride = access.get(access.length() - 1).name();
        }

        // Select the appropriate group for indexing.
        Group group = null;
        if (methodOverride != null) {
            Collection<Group> indexGroups = field.getGroup("index");
            if (indexGroups != null) {
                for (Group g : indexGroups) {
                    if (methodOverride.equals(g.getMethod())) {
                        group = g;
                        break;
                    }
                }
            }
        }

        // Fall back to the default index group if no override matched.
        if (group == null) {
            group = type.getDefaultGroupFor(baseAccess, "index");
        }

        if (group == null) {
            return null;
        }

        String method = group.getMethod();
        LuceneFieldType lft = fieldTypes.get(method);
        if (lft == null) {
            return null;
        }

        boolean isMulti = type.isMultiValuedPath(baseAccess);
        return new ResolvedField(baseAccess, lft, isMulti, defaultLang);
    }

    /**
     * Detect whether a field path already looks like a concrete
     * Lucene field name, e.g. "title.mls.text_en_s".
     */
    private boolean isPhysicalFieldName(String fieldPath) {
        int lastDot = fieldPath.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fieldPath.length() - 1) {
            return false;
        }

        String lastPart = fieldPath.substring(lastDot + 1);
        // Matches patterns like:
        //   text_en_s, text_en_m, identifier_s, long_m, double_s, date_s, boolean_m, textmarkup_en_s
        return lastPart.matches("(text|textmarkup|identifier|long|int|double|date|boolean)(_[a-z]{2})?_[sm]");
    }

    /**
     * Create a query parser with English as default language.
     */
    public static JVSQueryParser create(Type type, String defaultField, Analyzer analyzer) {
        return new JVSQueryParser(type, defaultField, analyzer, "en");
    }
}
