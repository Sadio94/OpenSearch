/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.ingest.common;

import java.util.Locale;
import java.util.Map;

/**
 * Processor that converts the content of string fields to lowercase.
 * Throws exception is the field is not of type string.
 */

public final class LowercaseProcessor extends AbstractStringProcessor<String> {

    public static final String TYPE = "lowercase";

    LowercaseProcessor(String processorTag, String description, String field, boolean ignoreMissing, String targetField) {
        super(processorTag, description, ignoreMissing, targetField, field);
    }

    public static String apply(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    protected String process(String value) {
        return apply(value);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory extends AbstractStringProcessor.Factory {

        public Factory() {
            super(TYPE);
        }

        @Override
        protected LowercaseProcessor newProcessor(String tag, String description, Map<String, Object> config, String field,
                                                  boolean ignoreMissing, String targetField) {
            return new LowercaseProcessor(tag, description, field, ignoreMissing, targetField);
        }
    }
}
