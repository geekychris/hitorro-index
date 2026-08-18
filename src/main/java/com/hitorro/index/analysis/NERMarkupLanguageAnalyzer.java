/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;

/**
 * Language-aware sibling of {@link NERMarkupAnalyzer}. Does the same
 * NE-bracket extraction (via {@link NERMarkupTokenFilter}) AND applies
 * the language-appropriate stemming step so that a {@code textmarkup_en_m}
 * field produces the same terms as a {@code text_en_m} field for the
 * plain-text portion of its content. Without the language step, a
 * search for "run" wouldn't hit a stored "running" in a textmarkup
 * field — which is the failure documented in
 * {@code FieldPatternAnalyzerWrapperTest.testTextMarkupField}.
 *
 * <p>Chain:</p>
 * <pre>
 *   WhitespaceTokenizer → NERMarkupTokenFilter → LowerCaseFilter →
 *     &lt;language stemmer&gt;
 * </pre>
 *
 * <p>Stemmer table:</p>
 * <ul>
 *   <li>{@code en} → {@link PorterStemFilter} (matches EnglishAnalyzer's
 *       terminal stemming step)</li>
 *   <li>Every other Snowball-supported language ({@code de}, {@code fr},
 *       {@code es}, {@code it}, {@code pt}, {@code nl}, {@code sv},
 *       {@code no}, {@code da}, {@code fi}, {@code ru}, {@code hu},
 *       {@code ro}, {@code tr}, {@code hy}, {@code eu}, {@code ca},
 *       {@code ga}, {@code lt}) → {@link SnowballFilter} with the
 *       language's stemmer name</li>
 *   <li>Anything else → LowerCase-only, no stemming (Chinese / Japanese /
 *       Korean would need CJK-specific handling; Arabic / Hebrew
 *       likewise). Documented gap — plain {@link NERMarkupAnalyzer}
 *       is the fallback until per-language wiring extends.</li>
 * </ul>
 *
 * <p>NE tokens ({@code NE_Person}, {@code NE_Organization}, …) come out
 * of {@link NERMarkupTokenFilter} lowercased downstream and mostly
 * survive stemmer treatment intact (they don't look like English word
 * suffixes). If a specific stemmer starts mangling them, we'd need to
 * exempt the underscore-prefixed forms — hasn't happened in practice.</p>
 */
public final class NERMarkupLanguageAnalyzer extends Analyzer {

    private final String langCode;

    public NERMarkupLanguageAnalyzer(String langCode) {
        this.langCode = langCode == null ? "en" : langCode.toLowerCase();
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream filter = new NERMarkupTokenFilter(tokenizer);
        filter = new LowerCaseFilter(filter);
        filter = applyStemmer(filter, langCode);
        return new TokenStreamComponents(tokenizer, filter);
    }

    /** Attach the language stemmer to the tail of the chain, or return
     *  {@code in} unchanged for languages we don't have a stemmer for. */
    private static TokenStream applyStemmer(TokenStream in, String lang) {
        if ("en".equals(lang)) {
            // Same terminal stemmer as EnglishAnalyzer's chain, so
            // text_en and textmarkup_en produce identical tokens for
            // bracket-free input.
            return new PorterStemFilter(in);
        }
        String snowball = snowballNameFor(lang);
        if (snowball != null) {
            try { return new SnowballFilter(in, snowball); }
            catch (Exception e) { /* fall through — LowerCase-only */ }
        }
        return in;
    }

    /** Map ISO-639-1 → Snowball stemmer name. Null for unsupported. */
    private static String snowballNameFor(String lang) {
        return switch (lang) {
            case "de" -> "German";
            case "fr" -> "French";
            case "es" -> "Spanish";
            case "it" -> "Italian";
            case "pt" -> "Portuguese";
            case "nl" -> "Dutch";
            case "sv" -> "Swedish";
            case "no" -> "Norwegian";
            case "da" -> "Danish";
            case "fi" -> "Finnish";
            case "ru" -> "Russian";
            case "hu" -> "Hungarian";
            case "ro" -> "Romanian";
            case "tr" -> "Turkish";
            case "hy" -> "Armenian";
            case "eu" -> "Basque";
            case "ca" -> "Catalan";
            case "ga" -> "Irish";
            case "lt" -> "Lithuanian";
            default   -> null;
        };
    }
}
