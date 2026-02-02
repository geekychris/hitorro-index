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

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.util.basefile.Name2JsonMapper;
import com.hitorro.util.core.Env;
import com.hitorro.util.core.events.cache.HashCache;
import com.hitorro.util.core.events.cache.SingletonCache;
import com.hitorro.util.core.iterator.Mapper;
import com.hitorro.util.json.keys.JsonInitableProperty;
import com.hitorro.util.json.keys.MapProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for Lucene field types, similar to SolrFieldTypes.
 * Loads configuration from lucene_fields.json with lazy initialization.
 */
public class LuceneFieldTypes {
    private static LuceneFieldTypes instance;
    private static final Object lock = new Object();
    
    public static JsonInitableProperty<LuceneFieldType> LuceneFieldTypeKey = 
            new JsonInitableProperty("", "", null, LuceneFieldType.class, LuceneFieldType.class);

    public static MapProperty<String, LuceneFieldType> LuceneFields = 
            LuceneFieldTypeKey.mapProperty("fields", "", null, LuceneFieldType.Name);

    protected Map<String, LuceneFieldType> map = new HashMap<>();

    /**
     * Get the singleton instance with lazy initialization.
     */
    public static LuceneFieldTypes getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LuceneFieldTypes();
                    instance.loadConfiguration();
                }
            }
        }
        return instance;
    }
    
    /**
     * Load configuration from JSON file.
     * Fails gracefully if configuration is not available (e.g., in test environments).
     */
    private void loadConfiguration() {
        try {
            // Try to load from Hitorro config system
            HashCache<String, JsonNode> luceneFieldTypesConfig =
                    new HashCache<>(0, true,
                            null, "luceneconfig",
                            new Name2JsonMapper(Env.getBinConfigBaseFile().getChild("jsonconfigs"), "lucene"));
            
            JsonNode node = luceneFieldTypesConfig.get("lucene_fields");
            if (node != null) {
                map = LuceneFields.apply(node);
            }
        } catch (Throwable t) {
            // Configuration not available - use empty map
            // This can happen in test environments without full Hitorro setup
            // Tests that don't use type-based projection will work fine
        }
    }

    public LuceneFieldType get(String name) {
        return map.get(name);
    }
}
