package com.hitorro.index.analysis;

/**
 * Information about a single token produced by an analyzer chain.
 */
public record TokenInfo(
        int position,
        String term,
        String type,
        int startOffset,
        int endOffset,
        int positionIncrement,
        int positionLength
) {}
