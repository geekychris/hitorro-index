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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FieldPatternAnalyzerWrapper to verify
 * correct analyzer selection based on field name patterns.
 */
public class FieldPatternAnalyzerWrapperTest {
    
    /**
     * Helper method to extract tokens from an analyzer.
     */
    private List<String> analyze(Analyzer analyzer, String fieldName, String text) throws Exception {
        List<String> tokens = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(fieldName, new StringReader(text))) {
            CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(termAtt.toString());
            }
            ts.end();
        }
        return tokens;
    }
    
    @Test
    public void testEnglishTextFieldAnalysis() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // English text field should use EnglishAnalyzer (stemming, lowercasing, stop words)
        List<String> tokens = analyze(wrapper, "title.mls.text_en_s", "Searching for documents");
        
        // EnglishAnalyzer should:
        // - Lowercase: "Searching" -> "searching"  
        // - Stem: "searching" -> "search"
        // - Remove stop words: "for" might be removed
        // - Keep content words: "documents" -> "document"
        
        assertTrue(tokens.contains("search"), "Should stem 'searching' to 'search'");
        assertTrue(tokens.contains("document"), "Should stem 'documents' to 'document'");
        assertFalse(tokens.contains("Searching"), "Should lowercase tokens");
    }
    
    @Test
    public void testGermanTextFieldAnalysis() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // German text field should use GermanAnalyzer
        List<String> tokens = analyze(wrapper, "description.text_de_m", "Häuser suchen");
        
        // GermanAnalyzer should handle umlauts and stemming
        assertFalse(tokens.isEmpty(), "Should tokenize German text");
    }
    
    @Test
    public void testIdentifierFieldNoAnalysis() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Identifier field should use KeywordAnalyzer (no tokenization)
        List<String> tokens = analyze(wrapper, "id.did.identifier_s", "Test-ID-123");
        
        // KeywordAnalyzer should keep the entire string as one token
        assertEquals(1, tokens.size(), "Identifier should be single token");
        assertEquals("Test-ID-123", tokens.get(0), "Identifier should not be modified");
    }
    
    @Test
    public void testNumericFieldNoAnalysis() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Numeric fields should use KeywordAnalyzer
        List<String> tokens = analyze(wrapper, "price.long_s", "12345");
        
        assertEquals(1, tokens.size(), "Numeric should be single token");
        assertEquals("12345", tokens.get(0), "Numeric should not be modified");
    }
    
    @Test
    public void testMultiValuedFieldSuffix() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Both _s and _m suffixes should use same analyzer
        List<String> tokensSingle = analyze(wrapper, "title.text_en_s", "running");
        List<String> tokensMulti = analyze(wrapper, "title.text_en_m", "running");
        
        // Both should stem "running" to "run"
        assertEquals(tokensSingle, tokensMulti, "Single and multi-valued fields should use same analyzer");
        assertTrue(tokensSingle.contains("run"), "Should stem 'running' to 'run'");
    }
    
    @Test
    public void testCaseInsensitiveAnalysis() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Text fields should lowercase
        List<String> tokens1 = analyze(wrapper, "title.text_en_s", "APACHE LUCENE");
        List<String> tokens2 = analyze(wrapper, "title.text_en_s", "apache lucene");
        
        // Both should produce same tokens after lowercasing
        assertEquals(tokens1, tokens2, "Analysis should be case-insensitive");
        assertTrue(tokens1.contains("apach"), "Should lowercase and stem");
        assertTrue(tokens1.contains("lucen"), "Should lowercase and stem");
    }
    
    @Test
    public void testFallbackToDefaultAnalyzer() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Unknown field pattern should fall back to StandardAnalyzer
        List<String> tokens = analyze(wrapper, "unknown.field.name", "Test Document");
        
        // StandardAnalyzer lowercases and tokenizes
        assertTrue(tokens.contains("test"), "Should use default analyzer");
        assertTrue(tokens.contains("document"), "Should use default analyzer");
    }
    
    @Test
    public void testLanguageCodeExtraction() {
        // Test utility method for extracting language from field name
        String lang1 = FieldPatternAnalyzerWrapper.extractLanguageFromFieldName("title.mls.text_en_s");
        assertEquals("en", lang1, "Should extract 'en' from field name");
        
        String lang2 = FieldPatternAnalyzerWrapper.extractLanguageFromFieldName("description.textmarkup_de_m");
        assertEquals("de", lang2, "Should extract 'de' from field name");
        
        String lang3 = FieldPatternAnalyzerWrapper.extractLanguageFromFieldName("id.identifier_s");
        assertNull(lang3, "Should return null for non-text fields");
    }
    
    @Test
    public void testAnalyzerTypeExtraction() {
        // Test utility method for extracting analyzer type
        String type1 = FieldPatternAnalyzerWrapper.extractAnalyzerTypeFromFieldName("title.text_en_s");
        assertEquals("text", type1, "Should identify text field");
        
        String type2 = FieldPatternAnalyzerWrapper.extractAnalyzerTypeFromFieldName("brand.identifier_m");
        assertEquals("identifier", type2, "Should identify identifier field");
        
        String type3 = FieldPatternAnalyzerWrapper.extractAnalyzerTypeFromFieldName("price.long_s");
        assertEquals("keyword", type3, "Should identify numeric field");
        
        String type4 = FieldPatternAnalyzerWrapper.extractAnalyzerTypeFromFieldName("unknown.field");
        assertNull(type4, "Should return null for unknown pattern");
    }
    
    @Test
    public void testFrenchAnalyzer() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // French text field
        List<String> tokens = analyze(wrapper, "title.text_fr_s", "rechercher des documents");
        
        // FrenchAnalyzer should handle elision and stemming
        assertFalse(tokens.isEmpty(), "Should tokenize French text");
        // The exact tokens depend on FrenchAnalyzer implementation
    }
    
    @Test
    public void testTextMarkupField() throws Exception {
        FieldPatternAnalyzerWrapper wrapper = new FieldPatternAnalyzerWrapper(new StandardAnalyzer());
        
        // Textmarkup should use same analyzer as text
        List<String> tokensText = analyze(wrapper, "content.text_en_m", "running quickly");
        List<String> tokensMarkup = analyze(wrapper, "content.textmarkup_en_m", "running quickly");
        
        // Both should produce same analysis
        assertEquals(tokensText, tokensMarkup, "Text and textmarkup should use same analyzer");
    }
}
