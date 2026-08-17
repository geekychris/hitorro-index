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
import com.hitorro.util.core.events.cache.HashCache;
import com.hitorro.util.core.events.cache.SingletonCache;
import com.hitorro.util.core.iterator.Mapper;
import com.hitorro.util.json.keys.JsonInitableProperty;
import com.hitorro.util.json.keys.MapProperty;
import com.hitorro.util.basefile.tools.EnvBaseFiles;

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
            new MapProperty<String, LuceneFieldType>(
                    new com.hitorro.util.json.keys.propaccess.Propaccess("fields"),
                    "", null, LuceneFieldTypeKey, LuceneFieldType.Name);

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
                            new Name2JsonMapper(EnvBaseFiles.getBinConfigBaseFile().getChild("jsonconfigs"), "lucene"));

            JsonNode node = luceneFieldTypesConfig.get("lucene_fields");
            if (node != null) {
                loadFrom(node);
            }
        } catch (Throwable t) {
            // Configuration not available - use empty map
            // This can happen in test environments without full Hitorro setup
            // Tests that don't use type-based projection will work fine
        }
    }

    /**
     * Populate the registry from an already-parsed JSON node. Handles both
     * shapes the config has appeared in over time:
     * <ul>
     *   <li>{@code {"fields": {"identifier": {...}, "text": {...}}}} —
     *       the current shipped {@code lucene_fields.json} shape.</li>
     *   <li>{@code {"fields": [{"name": "identifier", ...}]}} — the
     *       array shape assumed by the {@link MapProperty}-based loader.</li>
     * </ul>
     *
     * <p>Public so callers that already have the node in hand (index sinks,
     * standalone tests, application bootstrap) can drive initialization
     * directly and don't need to hit {@code EnvBaseFiles} or hack around
     * the shipped loader's object-shape gap.</p>
     */
    public synchronized void loadFrom(JsonNode node) {
        if (node == null || !node.has("fields")) return;
        JsonNode fields = node.get("fields");
        Map<String, LuceneFieldType> loaded = new HashMap<>();
        if (fields.isArray()) {
            // {"fields": [{"name": "identifier", ...}, ...]}
            var it = fields.elements();
            while (it.hasNext()) {
                JsonNode entry = it.next();
                LuceneFieldType lft = new LuceneFieldType();
                lft.init(entry);
                String name = entry.has("name") ? entry.get("name").asText() : null;
                if (name != null) loaded.put(name, lft);
            }
        } else if (fields.isObject()) {
            // {"fields": {"identifier": {...}, ...}}
            var it = fields.fields();
            while (it.hasNext()) {
                var entry = it.next();
                LuceneFieldType lft = new LuceneFieldType();
                lft.init(entry.getValue());
                loaded.put(entry.getKey(), lft);
            }
        }
        if (!loaded.isEmpty()) map = loaded;
    }

    public LuceneFieldType get(String name) {
        return map.get(name);
    }
}
