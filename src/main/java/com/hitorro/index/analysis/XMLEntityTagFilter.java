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
import java.util.Set;

/**
 * Converts NER XML entity tags into positionally-overlaid NE_ tokens for spanning queries.
 *
 * <p>Input (from StandardTokenizer on {@code <person>RAM</person> works at <organization>Acme</organization>}):
 * <pre>
 * person, RAM, person, works, at, organization, Acme, organization
 * </pre>
 *
 * <p>Output (with positional overlay):
 * <pre>
 * Position 0: NE_Person (increment=1, keyword=true)
 * Position 0: RAM       (increment=0)
 * Position 1: works     (increment=1)
 * Position 2: at        (increment=1)
 * Position 3: NE_Organization (increment=1, keyword=true)
 * Position 3: Acme      (increment=0)
 * </pre>
 *
 * <p>This enables:
 * <ul>
 *   <li>{@code field:NE_Person} — finds all person entities</li>
 *   <li>{@code field:RAM} — finds entity text</li>
 *   <li>Spanning queries: NE_Person and RAM share the same position</li>
 * </ul>
 */
public class XMLEntityTagFilter extends TokenFilter {

    private static final Set<String> ENTITY_TYPES = Set.of(
            "person", "organization", "location", "date", "money", "time", "percentage"
    );

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final KeywordAttribute keywordAtt = addAttribute(KeywordAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    // Pending NE_ type token to emit before the next content token
    private String pendingEntityType = null;
    // State queue for emitting the content token after the NE_ type
    private final Queue<State> stateQueue = new LinkedList<>();
    private boolean insideEntity = false;

    public XMLEntityTagFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        // If we have a queued content token to emit (after emitting the NE_ type)
        if (!stateQueue.isEmpty()) {
            restoreState(stateQueue.poll());
            posIncrAtt.setPositionIncrement(0); // same position as the NE_ type
            return true;
        }

        while (input.incrementToken()) {
            String term = termAtt.toString();
            String lower = term.toLowerCase();

            if (ENTITY_TYPES.contains(lower)) {
                if (!insideEntity) {
                    // Opening tag (e.g., "person" from <person>)
                    insideEntity = true;
                    pendingEntityType = "NE_" + Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
                } else {
                    // Closing tag (e.g., "person" from </person>) — skip it
                    insideEntity = false;
                    pendingEntityType = null;
                }
                continue; // don't emit the raw tag name
            }

            if (pendingEntityType != null) {
                // We have a pending NE_ type. Emit it first, queue the content token.
                // Save the current content token state
                stateQueue.add(captureState());

                // Emit the NE_ type token at this position
                clearAttributes();
                termAtt.setEmpty().append(pendingEntityType);
                keywordAtt.setKeyword(true);
                posIncrAtt.setPositionIncrement(1);
                pendingEntityType = null;
                return true;
                // On the next call, stateQueue will emit the content token at increment=0
            }

            // Regular content token outside any entity — pass through
            return true;
        }

        return false;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingEntityType = null;
        insideEntity = false;
        stateQueue.clear();
    }
}
