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

package org.opensearch.snapshots;

import org.opensearch.action.admin.cluster.repositories.get.GetRepositoriesResponse;
import org.opensearch.action.admin.cluster.repositories.verify.VerifyRepositoryResponse;
import org.opensearch.action.admin.cluster.state.ClusterStateResponse;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.metadata.RepositoriesMetadata;
import org.opensearch.cluster.metadata.RepositoryMetadata;
import org.opensearch.common.io.FileSystemUtils;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.repositories.RepositoryException;
import org.opensearch.repositories.RepositoryVerificationException;
import org.opensearch.snapshots.mockstore.MockRepository;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.nio.file.Path;
import java.util.List;

import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertAcked;
import static org.opensearch.test.hamcrest.OpenSearchAssertions.assertRequestBuilderThrows;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@OpenSearchIntegTestCase.ClusterScope(minNumDataNodes = 2)
public class RepositoriesIT extends AbstractSnapshotIntegTestCase {
    public void testRepositoryCreation() throws Exception {
        Client client = client();

        Path location = randomRepoPath();

        createRepository("test-repo-1", "fs", location);

        logger.info("--> verify the repository");
        int numberOfFiles = FileSystemUtils.files(location).length;
        VerifyRepositoryResponse verifyRepositoryResponse = client.admin().cluster().prepareVerifyRepository("test-repo-1").get();
        assertThat(verifyRepositoryResponse.getNodes().size(), equalTo(cluster().numDataAndMasterNodes()));

        logger.info("--> verify that we didn't leave any files as a result of verification");
        assertThat(FileSystemUtils.files(location).length, equalTo(numberOfFiles));

        logger.info("--> check that repository is really there");
        ClusterStateResponse clusterStateResponse = client.admin().cluster().prepareState().clear().setMetadata(true).get();
        Metadata metadata = clusterStateResponse.getState().getMetadata();
        RepositoriesMetadata repositoriesMetadata = metadata.custom(RepositoriesMetadata.TYPE);
        assertThat(repositoriesMetadata, notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1").type(), equalTo("fs"));

        logger.info("-->  creating another repository");
        createRepository("test-repo-2", "fs");

        logger.info("--> check that both repositories are in cluster state");
        clusterStateResponse = client.admin().cluster().prepareState().clear().setMetadata(true).get();
        metadata = clusterStateResponse.getState().getMetadata();
        repositoriesMetadata = metadata.custom(RepositoriesMetadata.TYPE);
        assertThat(repositoriesMetadata, notNullValue());
        assertThat(repositoriesMetadata.repositories().size(), equalTo(2));
        assertThat(repositoriesMetadata.repository("test-repo-1"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-1").type(), equalTo("fs"));
        assertThat(repositoriesMetadata.repository("test-repo-2"), notNullValue());
        assertThat(repositoriesMetadata.repository("test-repo-2").type(), equalTo("fs"));

        logger.info("--> check that both repositories can be retrieved by getRepositories query");
        GetRepositoriesResponse repositoriesResponse = client.admin().cluster()
            .prepareGetRepositories(randomFrom("_all", "*", "test-repo-*")).get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(2));
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-1"), notNullValue());
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-2"), notNullValue());

        logger.info("--> check that trying to create a repository with the same settings repeatedly does not update cluster state");
        String beforeStateUuid = clusterStateResponse.getState().stateUUID();
        assertThat(
            client.admin().cluster().preparePutRepository("test-repo-1")
                .setType("fs").setSettings(Settings.builder()
                .put("location", location)
            ).get().isAcknowledged(),
            equalTo(true));
        assertEquals(beforeStateUuid, client.admin().cluster().prepareState().clear().get().getState().stateUUID());

        logger.info("--> delete repository test-repo-1");
        client.admin().cluster().prepareDeleteRepository("test-repo-1").get();
        repositoriesResponse = client.admin().cluster().prepareGetRepositories().get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(1));
        assertThat(findRepository(repositoriesResponse.repositories(), "test-repo-2"), notNullValue());

        logger.info("--> delete repository test-repo-2");
        client.admin().cluster().prepareDeleteRepository("test-repo-2").get();
        repositoriesResponse = client.admin().cluster().prepareGetRepositories().get();
        assertThat(repositoriesResponse.repositories().size(), equalTo(0));
    }

    public void testResidualStaleIndicesAreDeletedByConsecutiveDelete() throws Exception {
        Client client = client();
        Path repositoryPath = randomRepoPath();
        final String repositoryName = "test-repo";
        final String snapshot1Name = "test-snap-1";
        final String snapshot2Name = "test-snap-2";

        logger.info("-->  creating repository at {}", repositoryPath.toAbsolutePath());
        createRepository(repositoryName, "mock", repositoryPath);

        int numberOfFiles = numberOfFiles(repositoryPath);

        logger.info("--> creating index-1 and ingest data");
        createIndex("test-idx-1");
        ensureGreen();
        for (int j = 0; j < 10; j++) {
            index("test-idx-1", "_doc", Integer.toString( 10 + j), "foo", "bar" +  10 + j);
        }
        refresh();

        logger.info("--> creating first snapshot");
        createFullSnapshot(repositoryName, snapshot1Name);

        logger.info("--> creating index-2 and ingest data");
        createIndex("test-idx-2");
        ensureGreen();
        for (int j = 0; j < 10; j++) {
            index("test-idx-2", "_doc", Integer.toString( 10 + j), "foo", "bar" +  10 + j);
        }
        refresh();

        logger.info("--> creating second snapshot");
        createFullSnapshot(repositoryName, snapshot2Name);

        // Make repository to throw exception when trying to delete stale indices
        // This will make sure stale indices stays in repository after snapshot delete
        String masterNode = internalCluster().getMasterName();
        ((MockRepository)internalCluster().getInstance(RepositoriesService.class, masterNode).repository("test-repo")).
            setThrowExceptionWhileDelete(true);

        logger.info("--> delete the second snapshot");
        client.admin().cluster().prepareDeleteSnapshot(repositoryName, snapshot2Name).get();

        // Make repository to work normally
        ((MockRepository)internalCluster().getInstance(RepositoriesService.class, masterNode).repository("test-repo")).
            setThrowExceptionWhileDelete(false);

        // This snapshot should delete last snapshot's residual stale indices as well
        logger.info("--> delete snapshot one");
        client.admin().cluster().prepareDeleteSnapshot(repositoryName, snapshot1Name).get();

        logger.info("--> make sure that number of files is back to what it was when the first snapshot was made");
        assertFileCount(repositoryPath, numberOfFiles + 2);

        logger.info("--> done");
    }

    private RepositoryMetadata findRepository(List<RepositoryMetadata> repositories, String name) {
        for (RepositoryMetadata repository : repositories) {
            if (repository.name().equals(name)) {
                return repository;
            }
        }
        return null;
    }

    public void testMisconfiguredRepository() throws Exception {
        Client client = client();

        logger.info("--> trying creating repository with incorrect settings");
        try {
            client.admin().cluster().preparePutRepository("test-repo").setType("fs").get();
            fail("Shouldn't be here");
        } catch (RepositoryException ex) {
            assertThat(ex.toString(), containsString("missing location"));
        }

        logger.info("--> trying creating fs repository with location that is not registered in path.repo setting");
        Path invalidRepoPath = createTempDir().toAbsolutePath();
        String location = invalidRepoPath.toString();
        try {
            client().admin().cluster().preparePutRepository("test-repo")
                    .setType("fs").setSettings(Settings.builder().put("location", location))
                    .get();
            fail("Shouldn't be here");
        } catch (RepositoryException ex) {
            assertThat(ex.toString(), containsString("location [" + location + "] doesn't match any of the locations specified " +
                "by path.repo"));
        }
    }

    public void testRepositoryAckTimeout() throws Exception {
        logger.info("-->  creating repository test-repo-1 with 0s timeout - shouldn't ack");
        AcknowledgedResponse putRepositoryResponse = client().admin().cluster().preparePutRepository("test-repo-1")
                .setType("fs").setSettings(Settings.builder()
                                .put("location", randomRepoPath())
                                .put("compress", randomBoolean())
                                .put("chunk_size", randomIntBetween(5, 100), ByteSizeUnit.BYTES)
                )
                .setTimeout("0s").get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(false));

        logger.info("-->  creating repository test-repo-2 with standard timeout - should ack");
        putRepositoryResponse = client().admin().cluster().preparePutRepository("test-repo-2")
                .setType("fs").setSettings(Settings.builder()
                                .put("location", randomRepoPath())
                                .put("compress", randomBoolean())
                                .put("chunk_size", randomIntBetween(5, 100), ByteSizeUnit.BYTES)
                ).get();
        assertThat(putRepositoryResponse.isAcknowledged(), equalTo(true));

        logger.info("-->  deleting repository test-repo-2 with 0s timeout - shouldn't ack");
        AcknowledgedResponse deleteRepositoryResponse = client().admin().cluster().prepareDeleteRepository("test-repo-2")
                .setTimeout("0s").get();
        assertThat(deleteRepositoryResponse.isAcknowledged(), equalTo(false));

        logger.info("-->  deleting repository test-repo-1 with standard timeout - should ack");
        deleteRepositoryResponse = client().admin().cluster().prepareDeleteRepository("test-repo-1").get();
        assertThat(deleteRepositoryResponse.isAcknowledged(), equalTo(true));
    }

    public void testRepositoryVerification() throws Exception {
        disableRepoConsistencyCheck("This test does not create any data in the repository.");

        Client client = client();

        Settings settings = Settings.builder()
                .put("location", randomRepoPath())
                .put("random_control_io_exception_rate", 1.0).build();
        Settings readonlySettings = Settings.builder().put(settings)
            .put("readonly", true).build();
        logger.info("-->  creating repository that cannot write any files - should fail");
        assertRequestBuilderThrows(client.admin().cluster().preparePutRepository("test-repo-1")
                        .setType("mock").setSettings(settings),
                RepositoryVerificationException.class);

        logger.info("-->  creating read-only repository that cannot read any files - should fail");
        assertRequestBuilderThrows(client.admin().cluster().preparePutRepository("test-repo-2")
                .setType("mock").setSettings(readonlySettings),
            RepositoryVerificationException.class);

        logger.info("-->  creating repository that cannot write any files, but suppress verification - should be acked");
        assertAcked(client.admin().cluster().preparePutRepository("test-repo-1")
                .setType("mock").setSettings(settings).setVerify(false));

        logger.info("-->  verifying repository");
        assertRequestBuilderThrows(client.admin().cluster().prepareVerifyRepository("test-repo-1"), RepositoryVerificationException.class);

        logger.info("-->  creating read-only repository that cannot read any files, but suppress verification - should be acked");
        assertAcked(client.admin().cluster().preparePutRepository("test-repo-2")
            .setType("mock").setSettings(readonlySettings).setVerify(false));

        logger.info("-->  verifying repository");
        assertRequestBuilderThrows(client.admin().cluster().prepareVerifyRepository("test-repo-2"), RepositoryVerificationException.class);

        Path location = randomRepoPath();

        logger.info("-->  creating repository");
        try {
            client.admin().cluster().preparePutRepository("test-repo-1")
                    .setType("mock")
                    .setSettings(Settings.builder()
                                    .put("location", location)
                                    .put("localize_location", true)
                    ).get();
            fail("RepositoryVerificationException wasn't generated");
        } catch (RepositoryVerificationException ex) {
            assertThat(ex.getMessage(), containsString("is not shared"));
        }
    }
}
