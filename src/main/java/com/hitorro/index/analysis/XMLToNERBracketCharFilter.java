/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.analysis;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts XML NER-tagged text to bracket format before tokenization.
 *
 * <p>Input:  {@code <person>RAM</person> works at <organization>Acme Corp</organization>}
 * <p>Output: {@code [{RAM&&NE_Person}] works at [{Acme Corp&&NE_Organization}]}
 *
 * <p>The bracket format is then consumed by {@link NERMarkupTokenFilter} which
 * splits each {@code [{text&&NE_Type}]} into two tokens at the same position.
 */
public class XMLToNERBracketCharFilter {

    private static final Set<String> ENTITY_TYPES = Set.of(
            "person", "organization", "location", "date", "money", "time", "percentage"
    );

    private static final Map<String, String> TYPE_MAP = Map.of(
            "person", "NE_Person",
            "organization", "NE_Organization",
            "location", "NE_Location",
            "date", "NE_Date",
            "money", "NE_Money",
            "time", "NE_Time",
            "percentage", "NE_Percentage"
    );

    // Pattern: <type>content</type>
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "<(person|organization|location|date|money|time|percentage)>(.*?)</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Convert XML NER tags to bracket format.
     */
    public static String convert(String text) {
        if (text == null) return null;
        Matcher m = TAG_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String type = m.group(1).toLowerCase();
            String content = m.group(2).trim();
            String neType = TYPE_MAP.getOrDefault(type, "NE_" + type);
            // For multi-word entities, emit each word as a separate bracketed token
            // so WhitespaceTokenizer doesn't break the bracket structure.
            // E.g., "Acme Corp" → "[{Acme&&NE_Organization}] Corp"
            // The NE_ type is attached to the first word only.
            String[] words = content.split("\\s+");
            StringBuilder replacement = new StringBuilder();
            replacement.append("[{").append(words[0]).append("&&").append(neType).append("}]");
            for (int i = 1; i < words.length; i++) {
                replacement.append(" ").append(words[i]);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Create a Reader that converts XML NER tags on the fly.
     */
    public static Reader createReader(Reader input) throws IOException {
        // Read all input (NER fields are typically short sentences)
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = input.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return new StringReader(convert(sb.toString()));
    }
}
