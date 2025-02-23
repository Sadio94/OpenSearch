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

package org.opensearch.index.reindex.remote;

import org.opensearch.common.bytes.BytesArray;
import org.opensearch.index.reindex.RemoteInfo;
import org.opensearch.test.OpenSearchTestCase;

import static java.util.Collections.emptyMap;

public class RemoteInfoTests extends OpenSearchTestCase {
    private RemoteInfo newRemoteInfo(String scheme, String prefixPath, String username, String password) {
        return new RemoteInfo(scheme, "testhost", 12344, prefixPath,new BytesArray("{ \"foo\" : \"bar\" }"), username, password,
            emptyMap(), RemoteInfo.DEFAULT_SOCKET_TIMEOUT, RemoteInfo.DEFAULT_CONNECT_TIMEOUT);
    }

    public void testToString() {
        assertEquals("host=testhost port=12344 query={ \"foo\" : \"bar\" }",
            newRemoteInfo("http", null, null, null).toString());
        assertEquals("host=testhost port=12344 query={ \"foo\" : \"bar\" } username=testuser",
            newRemoteInfo("http", null, "testuser", null).toString());
        assertEquals("host=testhost port=12344 query={ \"foo\" : \"bar\" } username=testuser password=<<>>",
            newRemoteInfo("http", null, "testuser", "testpass").toString());
        assertEquals("scheme=https host=testhost port=12344 query={ \"foo\" : \"bar\" } username=testuser password=<<>>",
            newRemoteInfo("https", null, "testuser", "testpass").toString());
        assertEquals("scheme=https host=testhost port=12344 pathPrefix=prxy query={ \"foo\" : \"bar\" } username=testuser password=<<>>",
            newRemoteInfo("https", "prxy", "testuser", "testpass").toString());
    }
}
