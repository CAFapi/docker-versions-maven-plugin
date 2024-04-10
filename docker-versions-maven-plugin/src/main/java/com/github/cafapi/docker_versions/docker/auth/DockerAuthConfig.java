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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class DockerAuthConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerAuthConfig.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private DockerAuthConfig()
    {
    }

    public static DockerRegistryAuthConfig getRegistryAuthConfig(final String registry) throws DockerRegistryAuthException
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

        final String authKey;
        if (registry != null
            && Arrays.stream(Constants.DEFAULT_REGISTRIES).noneMatch(r -> r.equalsIgnoreCase(registry))) {
            authKey = registry;
        } else {
            authKey = Constants.DEFAULT_DOCKER_REGISTRY;
        }

        final JsonNode credHelpers = dockerConfig.get("credHelpers");
        if (credHelpers != null) {
            final JsonNode credsStore = credHelpers.get(authKey);
            if (credsStore != null) {
                return extractAuthConfigFromCredentialsHelper(authKey, credsStore.asText());
            }
        }

        final JsonNode credsStore = dockerConfig.get("credsStore");
        if (credsStore != null) {
            final DockerRegistryAuthConfig authConfig = extractAuthConfigFromCredentialsHelper(authKey, credsStore.asText());
            if (authConfig != null) {
                return authConfig;
            }
        }

        final JsonNode auths = dockerConfig.get("auths");
        if (auths == null) {
            return null;
        }

        return extractAuthConfigFromDockerConfigAuths(auths, authKey);
    }

    private static DockerRegistryAuthConfig extractAuthConfigFromCredentialsHelper(final String authKey, final String credentialsStore)
        throws DockerRegistryAuthException
    {
        final CredentialHelperClient credentialHelper = new CredentialHelperClient(credentialsStore);
        try {
            return credentialHelper.getAuthConfig(authKey);
        } catch (final IOException e) {
            throw new DockerRegistryAuthException(
                "Error getting the credentials for " + authKey + " from the configured credential helper", e);
        }
    }

    private static DockerRegistryAuthConfig extractAuthConfigFromDockerConfigAuths(final JsonNode auths, final String authKey)
    {
        final JsonNode credentials = auths.get(authKey);

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

    private static ObjectNode readDockerConfig() throws IOException
    {
        final String dockerConfig = System.getenv("DOCKER_CONFIG");

        final Reader reader = dockerConfig == null
            ? DockerAuthUtil.getFileReaderFromDir(new File(DockerAuthUtil.getHomeDir(), ".docker/config.json"))
            : DockerAuthUtil.getFileReaderFromDir(new File(dockerConfig, "config.json"));
        return reader != null ? MAPPER.readValue(reader, ObjectNode.class) : null;
    }
}
