/*
 * Copyright (c) 2006-2025 Chris Collins
 */
package com.hitorro.index.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

/**
 * Analyzer for NER-annotated text fields (textmarkup method).
 *
 * <p>Expects text in bracket format: {@code [{RAM&&NE_Person}] works at [{Acme&&NE_Organization}]}
 *
 * <p>The conversion from XML tags ({@code <person>RAM</person>}) to bracket format
 * is done at indexing time by the projection layer via {@link XMLToNERBracketCharFilter#convert(String)},
 * NOT by this analyzer. This avoids offset mapping issues.
 *
 * <p>Processing chain:
 * <ol>
 *   <li>{@link WhitespaceTokenizer} — tokenizes on whitespace, keeping brackets intact</li>
 *   <li>{@link NERMarkupTokenFilter} — splits {@code [{RAM&&NE_Person}]} into:
 *       {@code RAM} (increment=1) and {@code NE_Person} (increment=0, same position)</li>
 * </ol>
 */
public class NERMarkupAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream filter = new NERMarkupTokenFilter(tokenizer);
        filter = new LowerCaseFilter(filter);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
