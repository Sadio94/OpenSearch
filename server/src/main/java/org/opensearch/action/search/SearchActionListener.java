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

package org.opensearch.action.search;

import org.opensearch.action.ActionListener;
import org.opensearch.search.SearchPhaseResult;
import org.opensearch.search.SearchShardTarget;

/**
 * A base action listener that ensures shard target and shard index is set on all responses
 * received by this listener.
 */
abstract class SearchActionListener<T extends SearchPhaseResult> implements ActionListener<T> {

    final int requestIndex;
    private final SearchShardTarget searchShardTarget;

    protected SearchActionListener(SearchShardTarget searchShardTarget,
                                   int shardIndex) {
        assert shardIndex >= 0 : "shard index must be positive";
        this.searchShardTarget = searchShardTarget;
        this.requestIndex = shardIndex;
    }

    @Override
    public final void onResponse(T response) {
        response.setShardIndex(requestIndex);
        setSearchShardTarget(response);
        innerOnResponse(response);
    }

    protected void setSearchShardTarget(T response) { // some impls need to override this
        response.setSearchShardTarget(searchShardTarget);
    }

    protected abstract void innerOnResponse(T response);
}
