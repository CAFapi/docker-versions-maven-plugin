/*
 * Copyright 2024-2025 Open Text.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cafapi.docker_versions.plugins.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthConfig;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryRestClient;
import com.github.cafapi.docker_versions.docker.client.DockerRegistrySchema;
import com.github.cafapi.docker_versions.docker.client.ImageNotFoundException;

final class DockerRegistryRestClientTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRegistryRestClientTest.class);

    @BeforeEach
    void init(final TestInfo testInfo) throws IOException
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());
    }

    @Test
    public void testGetSchema() throws XMLStreamException, URISyntaxException, IOException
    {
        final String registryUrl = "docker.io";
        final DockerRegistrySchema schema = DockerRegistryRestClient.getSchema(registryUrl);
        Assertions.assertTrue("https".equals(schema.getSchema()), "Https schema supported: " + registryUrl);

    }

    @Test
    public void testGetDigestDockerHubNoCredentials() throws DockerRegistryException, ImageNotFoundException
    {
        final DockerRegistryAuthConfig authConfig = null;
        final String registry = "docker.io";
        final String repository = "cafapi/opensuse-jre17";
        final String tag = "latest";
        final DockerRegistrySchema schema = DockerRegistryRestClient.getSchema(registry);
        final String authToken = DockerRegistryRestClient.getAuthToken(schema.getAuthUrl(), registry, repository, authConfig);
        final String digest = DockerRegistryRestClient.getDigest(authToken, schema.getSchema(), registry, repository, tag);

        Assertions.assertNotNull(digest, "Got digest");

        LOGGER.info("Got digest for {}/{}:{} - {}", registry, repository, tag, digest);
    }

    @Test
    public void testGetTagsDockerHubNoCredentials() throws DockerRegistryException
    {
        final DockerRegistryAuthConfig authConfig = null;
        final String registry = "docker.io";
        final String repository = "cafapi/opensuse-jre17";
        final DockerRegistrySchema schema = DockerRegistryRestClient.getSchema(registry);
        final String authToken = DockerRegistryRestClient.getAuthToken(schema.getAuthUrl(), registry, repository, authConfig);
        final List<String> tags = DockerRegistryRestClient.getTags(authToken, schema.getSchema(), registry, repository);

        Assertions.assertNotNull(tags, "Got tags");

        LOGGER.info("Got tags for {}/{} - {}", registry, repository, tags);
    }

    @Test
    public void testGetDigestNoCredentialsNonDockerHubRegistry() throws DockerRegistryException, ImageNotFoundException
    {
        final DockerRegistryAuthConfig authConfig = null;
        final String registry = "docker.elastic.co";
        final String repository = "elasticsearch/elasticsearch-oss";
        final String tag = "7.10.2";
        final DockerRegistrySchema schema = DockerRegistryRestClient.getSchema(registry);
        final String authToken = DockerRegistryRestClient.getAuthToken(schema.getAuthUrl(), registry, repository, authConfig);
        final String digest = DockerRegistryRestClient.getDigest(authToken, schema.getSchema(), registry, repository, tag);

        Assertions.assertNotNull(digest, "Got digest");

        LOGGER.info("Got digest for {}/{}:{} - {}", registry, repository, tag, digest);
    }
}
