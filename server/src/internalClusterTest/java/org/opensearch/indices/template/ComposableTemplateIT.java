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

package org.opensearch.indices.template;

import org.opensearch.action.admin.indices.template.put.PutComponentTemplateAction;
import org.opensearch.action.admin.indices.template.put.PutComposableIndexTemplateAction;

import org.opensearch.cluster.metadata.ComponentTemplate;
import org.opensearch.cluster.metadata.ComposableIndexTemplate;
import org.opensearch.cluster.metadata.Template;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.collect.List;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

public class ComposableTemplateIT extends OpenSearchIntegTestCase {

    // See: https://github.com/elastic/elasticsearch/issues/58643
    public void testComponentTemplatesCanBeUpdatedAfterRestart() throws Exception {
        ComponentTemplate ct = new ComponentTemplate(new Template(null, new CompressedXContent("{\n" +
            "      \"dynamic\": false,\n" +
            "      \"properties\": {\n" +
            "        \"foo\": {\n" +
            "          \"type\": \"text\"\n" +
            "        }\n" +
            "      }\n" +
            "    }"), null), 3L, Collections.singletonMap("eggplant", "potato"));
        client().execute(PutComponentTemplateAction.INSTANCE, new PutComponentTemplateAction.Request("my-ct").componentTemplate(ct)).get();

        ComposableIndexTemplate cit = new ComposableIndexTemplate(Collections.singletonList("coleslaw"),
            new Template(null, new CompressedXContent("{\n" +
                "      \"dynamic\": false,\n" +
                "      \"properties\": {\n" +
                "        \"foo\": {\n" +
                "          \"type\": \"keyword\"\n" +
                "        }\n" +
                "      }\n" +
                "    }"), null), Collections.singletonList("my-ct"),
            4L, 5L, Collections.singletonMap("egg", "bread"));
        client().execute(PutComposableIndexTemplateAction.INSTANCE,
            new PutComposableIndexTemplateAction.Request("my-it").indexTemplate(cit)).get();

        internalCluster().fullRestart();
        ensureGreen();

        ComponentTemplate ct2 = new ComponentTemplate(new Template(null, new CompressedXContent("{\n" +
            "      \"dynamic\": true,\n" +
            "      \"properties\": {\n" +
            "        \"foo\": {\n" +
            "          \"type\": \"keyword\"\n" +
            "        }\n" +
            "      }\n" +
            "    }"), null), 3L, Collections.singletonMap("eggplant", "potato"));
        client().execute(PutComponentTemplateAction.INSTANCE,
            new PutComponentTemplateAction.Request("my-ct").componentTemplate(ct2)).get();

        ComposableIndexTemplate cit2 = new ComposableIndexTemplate(Collections.singletonList("coleslaw"),
            new Template(null, new CompressedXContent("{\n" +
                "      \"dynamic\": true,\n" +
                "      \"properties\": {\n" +
                "        \"foo\": {\n" +
                "          \"type\": \"integer\"\n" +
                "        }\n" +
                "      }\n" +
                "    }"), null), Collections.singletonList("my-ct"),
            4L, 5L, Collections.singletonMap("egg", "bread"));
        client().execute(PutComposableIndexTemplateAction.INSTANCE,
            new PutComposableIndexTemplateAction.Request("my-it").indexTemplate(cit2)).get();
    }

    public void testUsageOfDataStreamFails() throws IOException {
        // Exception that would happen if a unknown field is provided in a composable template:
        // The thrown exception will be used to compare against the exception that is thrown when providing
        // a composable template with a data stream definition.
        String content = "{\"index_patterns\":[\"logs-*-*\"],\"my_field\":\"bla\"}";
        XContentParser parser =
            XContentHelper.createParser(xContentRegistry(), null, new BytesArray(content), XContentType.JSON);
        Exception expectedException = expectThrows(Exception.class, () -> ComposableIndexTemplate.parse(parser));

        ComposableIndexTemplate template = new ComposableIndexTemplate(List.of("logs-*-*"), null, null, null, null,
            null, new ComposableIndexTemplate.DataStreamTemplate());
        Exception e = expectThrows(IllegalArgumentException.class, () -> client().execute(PutComposableIndexTemplateAction.INSTANCE,
            new PutComposableIndexTemplateAction.Request("my-it").indexTemplate(template)).actionGet());
        Exception actualException = (Exception) e.getCause();
        assertThat(actualException.getMessage(),
            equalTo(expectedException.getMessage().replace("[1:32] ", "").replace("my_field", "data_stream")));
        assertThat(actualException.getMessage(), equalTo("[index_template] unknown field [data_stream]"));
    }
}
