package com.hitorro.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.apache.lucene.analysis.TokenizerFactory;
import org.apache.lucene.analysis.tokenattributes.*;

import java.util.*;

/**
 * Builds custom analyzer chains from named tokenizers and filters,
 * and analyzes text to produce token details including positions and offsets.
 *
 * <p>Uses Lucene's SPI (ServiceLoader) to discover available tokenizers and filters.
 */
public final class AnalyzerChainBuilder {

    private AnalyzerChainBuilder() {}

    /**
     * Analyze text using a custom tokenizer + filter chain.
     *
     * @param tokenizerName SPI name (e.g., "standard", "whitespace", "keyword")
     * @param filterNames   ordered list of SPI filter names (e.g., ["lowercase", "porterStem"])
     * @param text          input text to analyze
     * @return list of token info with positions, offsets, types
     */
    public static List<TokenInfo> analyze(String tokenizerName,
                                           List<String> filterNames,
                                           String text) throws Exception {
        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = TokenizerFactory.forName(tokenizerName, new HashMap<>())
                        .create();
                TokenStream stream = tokenizer;
                if (filterNames != null) {
                    for (String filterName : filterNames) {
                        try {
                            stream = TokenFilterFactory.forName(filterName, new HashMap<>()).create(stream);
                        } catch (Exception e) {
                            // Filter requires parameters — try with luceneMatchVersion
                            try {
                                var params = new HashMap<String, String>();
                                params.put("luceneMatchVersion", "9.12.0");
                                stream = TokenFilterFactory.forName(filterName, params).create(stream);
                            } catch (Exception e2) {
                                // Skip this filter — will be reported via error
                                throw new RuntimeException("Filter '" + filterName + "' failed: " + e2.getMessage(), e2);
                            }
                        }
                    }
                }
                return new TokenStreamComponents(tokenizer, stream);
            }
        };

        List<TokenInfo> tokens = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("field", text)) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            PositionIncrementAttribute posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
            OffsetAttribute offsetAtt = ts.addAttribute(OffsetAttribute.class);
            TypeAttribute typeAtt = ts.addAttribute(TypeAttribute.class);
            PositionLengthAttribute posLenAtt = ts.hasAttribute(PositionLengthAttribute.class)
                    ? ts.addAttribute(PositionLengthAttribute.class) : null;

            ts.reset();
            int pos = -1;
            while (ts.incrementToken()) {
                int posInc = posIncAtt.getPositionIncrement();
                pos += posInc;
                tokens.add(new TokenInfo(
                        pos,
                        termAtt.toString(),
                        typeAtt.type(),
                        offsetAtt.startOffset(),
                        offsetAtt.endOffset(),
                        posInc,
                        posLenAtt != null ? posLenAtt.getPositionLength() : 1
                ));
            }
            ts.end();
        }
        analyzer.close();

        return tokens;
    }

    /**
     * List all available tokenizer names via Lucene SPI.
     */
    public static List<String> availableTokenizers() {
        return new ArrayList<>(TokenizerFactory.availableTokenizers()).stream().sorted().toList();
    }

    /**
     * List all available token filter names via Lucene SPI.
     */
    public static List<String> availableTokenFilters() {
        return new ArrayList<>(TokenFilterFactory.availableTokenFilters()).stream().sorted().toList();
    }

    /**
     * Returns token filters organized into logical groups.
     */
    public static Map<String, List<String>> availableTokenFiltersGrouped() {
        Set<String> all = TokenFilterFactory.availableTokenFilters();
        Map<String, List<String>> groups = new LinkedHashMap<>();

        groups.put("General", filterMatching(all, "lowercase", "uppercase", "trim", "length",
                "truncate", "capitalization", "asciiFolding", "removeDuplicates", "reverseString",
                "fingerprint", "decimalDigit", "fixBrokenOffsets"));

        groups.put("Stemming", filterMatching(all, "porterStem", "snowballPorter", "kStem",
                "stemmerOverride", "hunspellStem", "keywordMarker", "keywordRepeat", "protectedTerm"));

        groups.put("Stop Words", filterMatching(all, "stop", "keepWord", "commonGrams",
                "commonGramsQuery", "limitTokenCount", "limitTokenOffset", "limitTokenPosition"));

        groups.put("N-Grams & Shingles", filterMatching(all, "nGram", "edgeNGram", "shingle",
                "fixedShingle", "minHash", "codepointCount", "concatenateGraph", "flattenGraph"));

        groups.put("Synonyms", filterMatching(all, "synonym", "synonymGraph", "Word2VecSynonym"));

        groups.put("Word Splitting", filterMatching(all, "wordDelimiter", "wordDelimiterGraph",
                "hyphenatedWords", "hyphenationCompoundWord", "dictionaryCompoundWord"));

        groups.put("Pattern & Type", filterMatching(all, "patternReplace", "patternCaptureGroup",
                "patternTyping", "type", "typeAsPayload", "typeAsSynonym", "dropIfFlagged",
                "dateRecognizer"));

        groups.put("Payloads", filterMatching(all, "delimitedPayload", "delimitedBoost",
                "delimitedTermFrequency", "numericPayload", "tokenOffsetPayload"));

        groups.put("Arabic", filterMatching(all, "arabicNormalization", "arabicStem"));
        groups.put("Bengali", filterMatching(all, "bengaliNormalization", "bengaliStem"));
        groups.put("Brazilian", filterMatching(all, "brazilianStem"));
        groups.put("Bulgarian", filterMatching(all, "bulgarianStem"));
        groups.put("CJK", filterMatching(all, "cjkBigram", "cjkWidth"));
        groups.put("Czech", filterMatching(all, "czechStem"));
        groups.put("English", filterMatching(all, "englishMinimalStem", "englishPossessive", "classic"));
        groups.put("Finnish", filterMatching(all, "finnishLightStem"));
        groups.put("French", filterMatching(all, "frenchLightStem", "frenchMinimalStem", "elision"));
        groups.put("Galician", filterMatching(all, "galicianMinimalStem", "galicianStem"));
        groups.put("German", filterMatching(all, "germanLightStem", "germanMinimalStem",
                "germanNormalization", "germanStem"));
        groups.put("Greek", filterMatching(all, "greekLowercase", "greekStem"));
        groups.put("Hindi", filterMatching(all, "hindiNormalization", "hindiStem", "indicNormalization"));
        groups.put("Hungarian", filterMatching(all, "hungarianLightStem"));
        groups.put("Indonesian", filterMatching(all, "indonesianStem"));
        groups.put("Irish", filterMatching(all, "irishLowercase"));
        groups.put("Italian", filterMatching(all, "italianLightStem"));
        groups.put("Latvian", filterMatching(all, "latvianStem"));
        groups.put("Norwegian", filterMatching(all, "norwegianLightStem", "norwegianMinimalStem",
                "norwegianNormalization"));
        groups.put("Persian", filterMatching(all, "persianNormalization", "persianStem"));
        groups.put("Portuguese", filterMatching(all, "portugueseLightStem", "portugueseMinimalStem",
                "portugueseStem"));
        groups.put("Russian", filterMatching(all, "russianLightStem"));
        groups.put("Scandinavian", filterMatching(all, "scandinavianFolding", "scandinavianNormalization"));
        groups.put("Serbian", filterMatching(all, "serbianNormalization"));
        groups.put("Sorani", filterMatching(all, "soraniNormalization", "soraniStem"));
        groups.put("Spanish", filterMatching(all, "spanishLightStem", "spanishMinimalStem",
                "spanishPluralStem"));
        groups.put("Swedish", filterMatching(all, "swedishLightStem", "swedishMinimalStem"));
        groups.put("Telugu", filterMatching(all, "teluguNormalization", "teluguStem"));
        groups.put("Turkish", filterMatching(all, "turkishLowercase"));

        // Collect any ungrouped filters
        Set<String> grouped = new HashSet<>();
        groups.values().forEach(grouped::addAll);
        List<String> other = all.stream().filter(f -> !grouped.contains(f)).sorted().toList();
        if (!other.isEmpty()) {
            groups.put("Other", other);
        }

        // Remove empty groups
        groups.entrySet().removeIf(e -> e.getValue().isEmpty());

        return groups;
    }

    private static List<String> filterMatching(Set<String> all, String... names) {
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (all.contains(name)) result.add(name);
        }
        return result;
    }
}
