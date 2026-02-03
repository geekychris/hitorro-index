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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Analyzer wrapper that selects analyzers based on field name patterns,
 * similar to Solr's dynamic field system.
 * 
 * <p>Field naming convention (Solr-style):
 * <ul>
 *   <li>{@code *.text_en_s} - English text, single-valued</li>
 *   <li>{@code *.text_en_m} - English text, multi-valued</li>
 *   <li>{@code *.text_de_s} - German text, single-valued</li>
 *   <li>{@code *.identifier_s} - Identifier, single-valued (no analysis)</li>
 *   <li>{@code *.long_s} - Long numeric, single-valued</li>
 * </ul>
 * 
 * <p>The wrapper extracts the analyzer type from the field name suffix
 * and uses the appropriate analyzer from the {@link LuceneAnalyzerRegistry}.
 */
public class FieldPatternAnalyzerWrapper extends DelegatingAnalyzerWrapper {
    
    private final Analyzer defaultAnalyzer;
    private final List<FieldPattern> patterns;
    
    /**
     * Pattern for matching field name suffixes to analyzer types.
     */
    private static class FieldPattern {
        final Pattern pattern;
        final String analyzerType;
        final String language;
        
        FieldPattern(Pattern pattern, String analyzerType, String language) {
            this.pattern = pattern;
            this.analyzerType = analyzerType;
            this.language = language;
        }
    }
    
    public FieldPatternAnalyzerWrapper(Analyzer defaultAnalyzer) {
        super(PER_FIELD_REUSE_STRATEGY);
        this.defaultAnalyzer = defaultAnalyzer;
        this.patterns = buildPatterns();
    }
    
    /**
     * Build field name patterns for all supported analyzer types.
     * Patterns are checked in order, so more specific patterns should come first.
     */
    private List<FieldPattern> buildPatterns() {
        List<FieldPattern> patterns = new ArrayList<>();
        
        // Text fields with language codes (e.g., *.text_en_s, *.text_de_m)
        for (String lang : LuceneAnalyzerRegistry.getSupportedLanguages()) {
            patterns.add(new FieldPattern(
                Pattern.compile(".*\\.text_" + lang + "_[sm]$"),
                "text",
                lang
            ));
            patterns.add(new FieldPattern(
                Pattern.compile(".*\\.textmarkup_" + lang + "_[sm]$"),
                "textmarkup",
                lang
            ));
        }
        
        // Identifier fields (keyword analysis - exact match)
        patterns.add(new FieldPattern(
            Pattern.compile(".*\\.identifier_[sm]$"),
            "identifier",
            null
        ));
        
        // Numeric and other non-analyzed fields
        patterns.add(new FieldPattern(
            Pattern.compile(".*\\.(long|int|double|date|boolean)_[sm]$"),
            "keyword",
            null
        ));
        
        return patterns;
    }
    
    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        // Try to match field name against patterns
        for (FieldPattern fp : patterns) {
            if (fp.pattern.matcher(fieldName).matches()) {
                Analyzer analyzer = getAnalyzerForPattern(fp);
                if (analyzer != null) {
                    return analyzer;
                }
            }
        }
        
        // Fall back to default analyzer
        return defaultAnalyzer;
    }
    
    /**
     * Get the appropriate analyzer for a matched pattern.
     */
    private Analyzer getAnalyzerForPattern(FieldPattern fp) {
        switch (fp.analyzerType) {
            case "text":
            case "textmarkup":
                // Use language-specific analyzer
                if (fp.language != null) {
                    return LuceneAnalyzerRegistry.getLanguageAnalyzer(fp.language);
                }
                break;
                
            case "identifier":
            case "keyword":
                // Use keyword analyzer (no tokenization)
                return LuceneAnalyzerRegistry.getTypeAnalyzer("identifier");
        }
        
        return null;
    }
    
    /**
     * Extract language code from field name.
     * Example: "title.mls.text_en_s" -> "en"
     */
    public static String extractLanguageFromFieldName(String fieldName) {
        // Match pattern: *.text_XX_[sm] or *.textmarkup_XX_[sm]
        java.util.regex.Matcher matcher = Pattern.compile(".*\\.(text|textmarkup)_([a-z]{2})_[sm]$")
                .matcher(fieldName);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return null;
    }
    
    /**
     * Extract analyzer type from field name.
     * Example: "title.mls.text_en_s" -> "text"
     * Example: "brand.identifier_s" -> "identifier"
     */
    public static String extractAnalyzerTypeFromFieldName(String fieldName) {
        // Match known patterns
        if (fieldName.matches(".*\\.text_[a-z]{2}_[sm]$")) {
            return "text";
        } else if (fieldName.matches(".*\\.textmarkup_[a-z]{2}_[sm]$")) {
            return "textmarkup";
        } else if (fieldName.matches(".*\\.identifier_[sm]$")) {
            return "identifier";
        } else if (fieldName.matches(".*\\.(long|int|double|date|boolean)_[sm]$")) {
            return "keyword";
        }
        return null;
    }
}
