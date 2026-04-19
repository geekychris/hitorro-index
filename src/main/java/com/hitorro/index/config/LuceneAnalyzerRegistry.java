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
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ga.IrishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.lt.LithuanianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for Lucene analyzers, mapping language codes and field types to appropriate analyzers.
 * Supports language-specific analyzers for i18n fields and type-specific analyzers.
 */
public class LuceneAnalyzerRegistry {
    private static final Map<String, Analyzer> languageAnalyzers = new HashMap<>();
    private static final Map<String, Analyzer> typeAnalyzers = new HashMap<>();
    private static final Analyzer defaultAnalyzer = new StandardAnalyzer();

    static {
        // Initialize language-specific analyzers using ISO 639-1 codes
        languageAnalyzers.put("ar", new ArabicAnalyzer());
        languageAnalyzers.put("bg", new BulgarianAnalyzer());
        languageAnalyzers.put("ca", new CatalanAnalyzer());
        // Czech analyzer not available in this Lucene version
        languageAnalyzers.put("da", new DanishAnalyzer());
        languageAnalyzers.put("de", new GermanAnalyzer());
        languageAnalyzers.put("el", new GreekAnalyzer());
        languageAnalyzers.put("en", new EnglishAnalyzer());
        languageAnalyzers.put("es", new SpanishAnalyzer());
        languageAnalyzers.put("eu", new BasqueAnalyzer());
        languageAnalyzers.put("fa", new PersianAnalyzer());
        languageAnalyzers.put("fi", new FinnishAnalyzer());
        languageAnalyzers.put("fr", new FrenchAnalyzer());
        languageAnalyzers.put("ga", new IrishAnalyzer());
        languageAnalyzers.put("gl", new GalicianAnalyzer());
        languageAnalyzers.put("hi", new HindiAnalyzer());
        languageAnalyzers.put("hu", new HungarianAnalyzer());
        languageAnalyzers.put("hy", new ArmenianAnalyzer());
        languageAnalyzers.put("id", new IndonesianAnalyzer());
        languageAnalyzers.put("it", new ItalianAnalyzer());
        languageAnalyzers.put("lt", new LithuanianAnalyzer());
        languageAnalyzers.put("lv", new LatvianAnalyzer());
        languageAnalyzers.put("nl", new DutchAnalyzer());
        languageAnalyzers.put("no", new NorwegianAnalyzer());
        languageAnalyzers.put("pt", new PortugueseAnalyzer());
        languageAnalyzers.put("ro", new RomanianAnalyzer());
        languageAnalyzers.put("ru", new RussianAnalyzer());
        languageAnalyzers.put("sv", new SwedishAnalyzer());
        languageAnalyzers.put("th", new ThaiAnalyzer());
        languageAnalyzers.put("tr", new TurkishAnalyzer());
        // CJK for Chinese, Japanese, Korean
        languageAnalyzers.put("zh", new CJKAnalyzer());
        languageAnalyzers.put("ja", new CJKAnalyzer());
        languageAnalyzers.put("ko", new CJKAnalyzer());

        // Initialize type-specific analyzers
        typeAnalyzers.put("text", new StandardAnalyzer());
        typeAnalyzers.put("textmarkup", new com.hitorro.index.analysis.NERMarkupAnalyzer());
        typeAnalyzers.put("identifier", new KeywordAnalyzer());
        typeAnalyzers.put("long", new KeywordAnalyzer());
        typeAnalyzers.put("int", new KeywordAnalyzer());
        typeAnalyzers.put("double", new KeywordAnalyzer());
        typeAnalyzers.put("date", new KeywordAnalyzer());
    }

    /**
     * Get analyzer for a specific language code.
     *
     * @param langCode ISO 639-1 language code (e.g., "en", "fr", "de")
     * @return Language-specific analyzer, or StandardAnalyzer if not found
     */
    public static Analyzer getLanguageAnalyzer(String langCode) {
        if (langCode == null) {
            return defaultAnalyzer;
        }
        return languageAnalyzers.getOrDefault(langCode.toLowerCase(), defaultAnalyzer);
    }

    /**
     * Get analyzer for a specific field type.
     *
     * @param fieldType The field type name (e.g., "text", "identifier", "long")
     * @return Type-specific analyzer, or StandardAnalyzer if not found
     */
    public static Analyzer getTypeAnalyzer(String fieldType) {
        if (fieldType == null) {
            return defaultAnalyzer;
        }
        return typeAnalyzers.getOrDefault(fieldType.toLowerCase(), defaultAnalyzer);
    }

    /**
     * Get the appropriate analyzer for a field based on its configuration.
     *
     * @param fieldType   The LuceneFieldType configuration
     * @param langCode    ISO language code (may be null)
     * @return Appropriate analyzer for the field
     */
    public static Analyzer getAnalyzer(LuceneFieldType fieldType, String langCode) {
        if (fieldType == null) {
            return defaultAnalyzer;
        }

        // If field is not tokenized, use KeywordAnalyzer
        if (!fieldType.isTokenized()) {
            return new KeywordAnalyzer();
        }

        // For i18n fields, use language-specific analyzer
        if (fieldType.isI18n() && langCode != null) {
            return getLanguageAnalyzer(langCode);
        }

        // Otherwise use type-specific analyzer
        return getTypeAnalyzer(fieldType.getIndexType());
    }

    /**
     * Register a custom analyzer for a language code.
     *
     * @param langCode ISO language code
     * @param analyzer Custom analyzer
     */
    public static void registerLanguageAnalyzer(String langCode, Analyzer analyzer) {
        languageAnalyzers.put(langCode.toLowerCase(), analyzer);
    }

    /**
     * Register a custom analyzer for a field type.
     *
     * @param fieldType Field type name
     * @param analyzer  Custom analyzer
     */
    public static void registerTypeAnalyzer(String fieldType, Analyzer analyzer) {
        typeAnalyzers.put(fieldType.toLowerCase(), analyzer);
    }

    /**
     * Get all registered language codes.
     *
     * @return Set of language codes
     */
    public static java.util.Set<String> getSupportedLanguages() {
        return languageAnalyzers.keySet();
    }
}
