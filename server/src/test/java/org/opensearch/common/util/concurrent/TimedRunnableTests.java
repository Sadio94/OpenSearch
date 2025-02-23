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

package org.opensearch.common.util.concurrent;

import org.opensearch.ExceptionsHelper;
import org.opensearch.test.OpenSearchTestCase;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;

public final class TimedRunnableTests extends OpenSearchTestCase {

    public void testTimedRunnableDelegatesToAbstractRunnable() {
        final boolean isForceExecution = randomBoolean();
        final AtomicBoolean onAfter = new AtomicBoolean();
        final AtomicReference<Exception> onRejection = new AtomicReference<>();
        final AtomicBoolean doRun = new AtomicBoolean();

        final AbstractRunnable runnable = new AbstractRunnable() {
            @Override
            public boolean isForceExecution() {
                return isForceExecution;
            }

            @Override
            public void onAfter() {
                onAfter.set(true);
            }

            @Override
            public void onRejection(final Exception e) {
                onRejection.set(e);
            }

            @Override
            public void onFailure(final Exception e) {
            }

            @Override
            protected void doRun() throws Exception {
                doRun.set(true);
            }
        };

        final TimedRunnable timedRunnable = new TimedRunnable(runnable);

        assertThat(timedRunnable.isForceExecution(), equalTo(isForceExecution));

        final Exception rejection = new RejectedExecutionException();
        timedRunnable.onRejection(rejection);
        assertThat(onRejection.get(), equalTo(rejection));

        timedRunnable.run();
        assertTrue(doRun.get());
        assertTrue(onAfter.get());
    }

    public void testTimedRunnableDelegatesRunInFailureCase() {
        final AtomicBoolean onAfter = new AtomicBoolean();
        final AtomicReference<Exception> onFailure = new AtomicReference<>();
        final AtomicBoolean doRun = new AtomicBoolean();

        final Exception exception = new Exception();

        final AbstractRunnable runnable = new AbstractRunnable() {
            @Override
            public void onAfter() {
                onAfter.set(true);
            }

            @Override
            public void onFailure(final Exception e) {
                onFailure.set(e);
            }

            @Override
            protected void doRun() throws Exception {
                doRun.set(true);
                throw exception;
            }
        };

        final TimedRunnable timedRunnable = new TimedRunnable(runnable);
        timedRunnable.run();
        assertTrue(doRun.get());
        assertThat(onFailure.get(), equalTo(exception));
        assertTrue(onAfter.get());
    }

    public void testTimedRunnableRethrowsExceptionWhenNotAbstractRunnable() {
        final AtomicBoolean hasRun = new AtomicBoolean();
        final RuntimeException exception = new RuntimeException();

        final Runnable runnable = () -> {
            hasRun.set(true);
            throw exception;
        };

        final TimedRunnable timedRunnable = new TimedRunnable(runnable);
        final RuntimeException thrown = expectThrows(RuntimeException.class, () -> timedRunnable.run());
        assertTrue(hasRun.get());
        assertSame(exception, thrown);
    }

    public void testTimedRunnableRethrowsRejectionWhenNotAbstractRunnable() {
        final AtomicBoolean hasRun = new AtomicBoolean();
        final RuntimeException exception = new RuntimeException();

        final Runnable runnable = () -> {
            hasRun.set(true);
            throw new AssertionError("should not run");
        };

        final TimedRunnable timedRunnable = new TimedRunnable(runnable);
        final RuntimeException thrown = expectThrows(RuntimeException.class, () -> timedRunnable.onRejection(exception));
        assertFalse(hasRun.get());
        assertSame(exception, thrown);
    }

    public void testTimedRunnableExecutesNestedOnAfterOnce() {
        final AtomicBoolean afterRan = new AtomicBoolean(false);
        new TimedRunnable(new AbstractRunnable() {

            @Override
            public void onFailure(final Exception e) {
            }

            @Override
            protected void doRun() {
            }

            @Override
            public void onAfter() {
                if (afterRan.compareAndSet(false, true) == false) {
                    fail("onAfter should have only been called once");
                }
            }
        }).run();
        assertTrue(afterRan.get());
    }

    public void testNestedOnFailureTriggersOnlyOnce() {
        final Exception expectedException = new RuntimeException();
        final AtomicBoolean onFailureRan = new AtomicBoolean(false);
        RuntimeException thrown = expectThrows(RuntimeException.class, () -> new TimedRunnable(new AbstractRunnable() {

            @Override
            public void onFailure(Exception e) {
                if (onFailureRan.compareAndSet(false, true) == false) {
                    fail("onFailure should have only been called once");
                }
                ExceptionsHelper.reThrowIfNotNull(e);
            }

            @Override
            protected void doRun() throws Exception {
                throw expectedException;
            }

        }).run());
        assertTrue(onFailureRan.get());
        assertSame(thrown, expectedException);
    }
}
