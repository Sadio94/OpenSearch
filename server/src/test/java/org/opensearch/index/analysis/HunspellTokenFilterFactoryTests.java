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
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.index.analysis;

import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.index.analysis.AnalysisTestsHelper;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class HunspellTokenFilterFactoryTests extends OpenSearchTestCase {
    public void testDedup() throws IOException {
        Settings settings = Settings.builder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        TestAnalysis analysis =
                AnalysisTestsHelper.createTestAnalysisFromSettings(settings, getDataPath("/indices/analyze/conf_dir"));
        TokenFilterFactory tokenFilter = analysis.tokenFilter.get("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        HunspellTokenFilterFactory hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.dedup(), is(true));

        settings = Settings.builder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.dedup", false)
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        analysis = AnalysisTestsHelper.createTestAnalysisFromSettings(settings, getDataPath("/indices/analyze/conf_dir"));
        tokenFilter = analysis.tokenFilter.get("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.dedup(), is(false));
    }
}
