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

package org.opensearch.common;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PriorityTests extends OpenSearchTestCase {

    public void testValueOf() {
        for (Priority p : Priority.values()) {
            assertSame(p, Priority.valueOf(p.toString()));
        }

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> {
            Priority.valueOf("foobar");
        });
        assertEquals("No enum constant org.opensearch.common.Priority.foobar", exception.getMessage());
    }

    public void testToString() {
        assertEquals("IMMEDIATE", Priority.IMMEDIATE.toString());
        assertEquals("HIGH", Priority.HIGH.toString());
        assertEquals("LANGUID", Priority.LANGUID.toString());
        assertEquals("LOW", Priority.LOW.toString());
        assertEquals("URGENT", Priority.URGENT.toString());
        assertEquals("NORMAL", Priority.NORMAL.toString());
        assertEquals(6, Priority.values().length);
    }

    public void testSerialization() throws IOException {
        for (Priority p : Priority.values()) {
            BytesStreamOutput out = new BytesStreamOutput();
            Priority.writeTo(p, out);
            Priority priority = Priority.readFrom(out.bytes().streamInput());
            assertSame(p, priority);
        }
        assertSame(Priority.IMMEDIATE, Priority.fromByte((byte) 0));
        assertSame(Priority.HIGH, Priority.fromByte((byte) 2));
        assertSame(Priority.LANGUID, Priority.fromByte((byte) 5));
        assertSame(Priority.LOW, Priority.fromByte((byte) 4));
        assertSame(Priority.NORMAL, Priority.fromByte((byte) 3));
        assertSame(Priority.URGENT,Priority.fromByte((byte) 1));
        assertEquals(6, Priority.values().length);
    }

    public void testCompareTo() {
        assertTrue(Priority.IMMEDIATE.compareTo(Priority.URGENT) < 0);
        assertTrue(Priority.URGENT.compareTo(Priority.HIGH) < 0);
        assertTrue(Priority.HIGH.compareTo(Priority.NORMAL) < 0);
        assertTrue(Priority.NORMAL.compareTo(Priority.LOW) < 0);
        assertTrue(Priority.LOW.compareTo(Priority.LANGUID) < 0);

        assertTrue(Priority.URGENT.compareTo(Priority.IMMEDIATE) > 0);
        assertTrue(Priority.HIGH.compareTo(Priority.URGENT) > 0);
        assertTrue(Priority.NORMAL.compareTo(Priority.HIGH) > 0);
        assertTrue(Priority.LOW.compareTo(Priority.NORMAL) > 0);
        assertTrue(Priority.LANGUID.compareTo(Priority.LOW) > 0);

        for (Priority p : Priority.values()) {
            assertEquals(0, p.compareTo(p));
        }
        List<Priority> shuffeledAndSorted = Arrays.asList(Priority.values());
        Collections.shuffle(shuffeledAndSorted, random());
        Collections.sort(shuffeledAndSorted);
        for (List<Priority> priorities : Arrays.asList(shuffeledAndSorted,
            Arrays.asList(Priority.values()))) { // #values() guarantees order!
            assertSame(Priority.IMMEDIATE, priorities.get(0));
            assertSame(Priority.URGENT, priorities.get(1));
            assertSame(Priority.HIGH, priorities.get(2));
            assertSame(Priority.NORMAL, priorities.get(3));
            assertSame(Priority.LOW, priorities.get(4));
            assertSame(Priority.LANGUID, priorities.get(5));
        }
    }
}
