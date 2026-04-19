/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.analysis;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Splits NER bracket-format tokens into entity text + entity type at the same position.
 *
 * <p>Input token: {@code [{RAM&&NE_Person}]}
 * <p>Output tokens at same position:
 * <ul>
 *   <li>{@code RAM} (position increment 1)</li>
 *   <li>{@code NE_Person} (position increment 0, keyword=true)</li>
 * </ul>
 *
 * <p>Preserves offsets from the upstream tokenizer to avoid Lucene offset errors.
 */
public class NERMarkupTokenFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final KeywordAttribute keywordAtt = addAttribute(KeywordAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    // Queue of extra tokens to emit after the primary token
    private final Queue<QueuedToken> extraTokens = new LinkedList<>();
    // Saved state from the original token (preserves offsets)
    private State savedState = null;

    public NERMarkupTokenFilter(TokenStream in) {
        super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
        // Emit queued tokens (NE_ types and extra entity words)
        if (!extraTokens.isEmpty()) {
            QueuedToken qt = extraTokens.poll();
            // Restore the original state first (preserves offsets from tokenizer)
            restoreState(savedState);
            // Then override just the term, keyword, and position increment
            termAtt.setEmpty().append(qt.text);
            keywordAtt.setKeyword(qt.keyword);
            posIncrAtt.setPositionIncrement(qt.posIncrement);
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String term = termAtt.toString();

        // Check for bracket format: [{text&&NE_Type}] or [{text&&NE_Type&&NE_Type2}]
        if (term.length() > 4 && term.startsWith("[{") && term.endsWith("}]")) {
            String inner = term.substring(2, term.length() - 2);
            String[] parts = inner.split("&&");
            if (parts.length >= 2) {
                // Save state (preserves offsets from WhitespaceTokenizer)
                savedState = captureState();

                String entityText = parts[0];
                String[] words = entityText.split("\\s+");

                // Emit first word as the current token
                termAtt.setEmpty().append(words.length > 0 ? words[0] : entityText);
                keywordAtt.setKeyword(false);
                posIncrAtt.setPositionIncrement(1);

                // Queue NE_ type tokens at position increment 0 (same position as first word)
                for (int i = 1; i < parts.length; i++) {
                    extraTokens.add(new QueuedToken(parts[i], true, 0));
                }

                // Queue additional entity words at increment 1
                for (int i = 1; i < words.length; i++) {
                    if (!words[i].isEmpty()) {
                        extraTokens.add(new QueuedToken(words[i], false, 1));
                    }
                }

                return true;
            }
        }

        // Regular token — pass through unchanged
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        extraTokens.clear();
        savedState = null;
    }

    private record QueuedToken(String text, boolean keyword, int posIncrement) {}
}
