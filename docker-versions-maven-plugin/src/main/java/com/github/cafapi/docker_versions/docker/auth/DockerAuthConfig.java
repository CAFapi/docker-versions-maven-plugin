/*
 * Copyright 2024 Open Text.
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
package com.github.cafapi.docker_versions.docker.auth;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DockerAuthConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerAuthConfig.class);

    private DockerAuthConfig()
    {
    }

    public static DockerRegistryAuthConfig getRegistryAuthConfig(final String registry)
    {
        final ObjectNode dockerConfig;
        try {
            dockerConfig = readDockerConfig();
        } catch (final IOException e) {
            LOGGER.warn("Error reading docker config", e);
            return null;
        }
        if (dockerConfig == null) {
            return null;
        }
        final String registryToLookup;
        if (registry != null
            && Arrays.stream(Constants.DEFAULT_REGISTRIES).noneMatch(r -> r.equalsIgnoreCase(registry))) {
            registryToLookup = registry;
        } else {
            registryToLookup = Constants.DEFAULT_DOCKER_REGISTRY;
        }

        if (dockerConfig.has("auths")) {
            return extractAuthConfigFromDockerConfigAuths(registryToLookup, dockerConfig.get("auths"));
        }

        return null;
    }

    private static DockerRegistryAuthConfig extractAuthConfigFromDockerConfigAuths(
        final String registryToLookup,
        final JsonNode auths)
    {
        final JsonNode credentials = getCredentialsNode(auths, registryToLookup);

        if (credentials == null) {
            return null;
        }

        final String username = getNodeValue(credentials, Constants.AUTH_USERNAME);

        final String password = getNodeValue(credentials, Constants.AUTH_PASSWORD);

        final String auth = getNodeValue(credentials, Constants.AUTH_AUTH);

        final String identityToken = getNodeValue(credentials, Constants.AUTH_IDENTITY_TOKEN);

        final String email = getNodeValue(credentials, Constants.AUTH_EMAIL);

        return new DockerRegistryAuthConfig(username, password, email, auth, identityToken);
    }

    private static String getNodeValue(final JsonNode node, final String field)
    {
        return node.has(field)
            ? node.get(field).asText()
            : null;
    }

    private static JsonNode getCredentialsNode(final JsonNode auths, final String registryToLookup)
    {
        if (auths.has(registryToLookup)) {
            return auths.get(registryToLookup);
        }
        final String registryWithScheme = DockerAuthUtil.ensureRegistryHttpUrl(registryToLookup);
        if (auths.has(registryWithScheme)) {
            return auths.get(registryWithScheme);
        }
        return null;
    }

    private static ObjectNode readDockerConfig() throws IOException
    {
        final String dockerConfig = System.getenv("DOCKER_CONFIG");

        final Reader reader = dockerConfig == null
            ? DockerAuthUtil.getFileReaderFromDir(new File(DockerAuthUtil.getHomeDir(), ".docker/config.json"))
            : DockerAuthUtil.getFileReaderFromDir(new File(dockerConfig, "config.json"));
        return reader != null ? Constants.MAPPER.readValue(reader, ObjectNode.class) : null;
    }

}
