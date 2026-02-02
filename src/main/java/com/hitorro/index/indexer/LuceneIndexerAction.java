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
package com.hitorro.index.indexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.index.config.LuceneFieldType;
import com.hitorro.index.config.LuceneFieldTypes;
import com.hitorro.jsontypesystem.Field;
import com.hitorro.jsontypesystem.Group;
import com.hitorro.jsontypesystem.executors.ExecutionBuilder;
import com.hitorro.jsontypesystem.executors.ExecutorAction;
import com.hitorro.jsontypesystem.executors.ProjectionContext;
import com.hitorro.util.json.keys.propaccess.Propaccess;
import com.hitorro.util.json.keys.propaccess.PropaccessError;

/**
 * Executor action for projecting JVS fields to Lucene document fields.
 * Similar to IndexerAction but creates Lucene-specific field information.
 */
public class LuceneIndexerAction implements ExecutorAction<ExecutionBuilder> {
    protected Group group;
    protected Field field;
    private LuceneFieldType lft;
    private String method;

    public LuceneIndexerAction(final Field field, Group group, final Propaccess path) {
        this.group = group;
        this.field = field;
        LuceneFieldTypes lfts = LuceneFieldTypes.getInstance();
        method = group.getMethod();
        lft = lfts.get(method);
    }

    public void project(ProjectionContext pc, Propaccess path, final boolean isMulti, final String lang) {
        // Cast to LuceneProjectionContext - safe because we control the creation
        if (!(pc instanceof LuceneProjectionContext)) {
            throw new IllegalArgumentException("ProjectionContext must be LuceneProjectionContext for Lucene indexing");
        }
        LuceneProjectionContext lpc = (LuceneProjectionContext) pc;
        
        // Skip if field type not configured
        if (lft == null) {
            return;
        }
        
        try {
            JsonNode val = lpc.source.get(path);

            if (val != null) {
                if (val.isNull()) {
                    return;
                }
                lpc.sb.setLength(0);
                path.getPathSansIndex(lpc.sb);
                lft.get(lpc.sb, lang, isMulti);
                String fieldName = lpc.sb.toString();

                // Add field to the Lucene document being built
                lpc.addField(fieldName, val, lft, lang);
            }
        } catch (PropaccessError propaccessError) {
            propaccessError.printStackTrace();
        }
    }

    public LuceneFieldType getFieldType() {
        return lft;
    }

    public Group getGroup() {
        return group;
    }

    public Field getField() {
        return field;
    }
}
