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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;

final class DockerAuthConfig
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerAuthConfig.class);

    private DockerAuthConfig()
    {
    }

    public static DockerRegistryAuthConfig getRegistryAuthConfig(final String registry)
        throws DockerRegistryException
    {
        final ObjectNode dockerConfig;
        try {
            dockerConfig = readDockerConfig();
        } catch (final IOException e) {
            throw new DockerRegistryException("Error reading docker config", e);
        }
        LOGGER.info("getAuthConfigFromDockerConfig...{}", dockerConfig);
        if (dockerConfig == null) {
            return null;
        }
        final String registryToLookup;
        if (registry != null && Arrays.stream(Constants.DEFAULT_REGISTRIES).noneMatch(r -> r.equalsIgnoreCase(registry))) {
            registryToLookup = registry;
        } else {
            registryToLookup = Constants.DEFAULT_DOCKER_REGISTRY;
        }

        /*
        if (dockerConfig.has("credHelpers") || dockerConfig.has("credsStore")) {
            if (dockerConfig.has("credHelpers")) {
                final JsonNode credHelpers = dockerConfig.get("credHelpers");
                if (credHelpers.has(registryToLookup)) {
                    return extractAuthConfigFromCredentialsHelper(registryToLookup,
                        credHelpers.get(registryToLookup).asText());
                }
            }
            if (dockerConfig.has("credsStore")) {
                return extractAuthConfigFromCredentialsHelper(registryToLookup,
                    dockerConfig.get("credsStore").asText());
            }
        }
        */

        if (dockerConfig.has("auths")) {
            return extractAuthConfigFromDockerConfigAuths(registryToLookup, dockerConfig.get("auths"));
        }

        return null;
    }

    /*
    private static DockerRegistryAuthConfig extractAuthConfigFromCredentialsHelper(
        final String registryToLookup,
        final String credConfig)
        throws DockerRegistryException
    {
        final CredentialHelperClient credentialHelper = new CredentialHelperClient(credConfig);
        final String version = credentialHelper.getVersion();
        LOGGER.info("AuthConfig: credentials from credential helper/store {}{}", credentialHelper.getName(),
            version != null ? " version " + version : "");
        return credentialHelper.getAuthConfig(registryToLookup);
    }
    */

    private static DockerRegistryAuthConfig extractAuthConfigFromDockerConfigAuths(
        final String registryToLookup,
        final JsonNode auths)
    {
        final JsonNode credentials = getCredentialsNode(auths, registryToLookup);

        if (credentials == null) {
            return null;
        }

        final String username = getNodeValue(credentials, "username");

        final String password = getNodeValue(credentials, "password");

        final String auth = getNodeValue(credentials, "auth");

        final String identityToken = getNodeValue(credentials, "identitytoken");

        final String email = getNodeValue(credentials, "email");

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
        final String registryWithScheme = ensureRegistryHttpUrl(registryToLookup);
        if (auths.has(registryWithScheme)) {
            return auths.get(registryWithScheme);
        }
        return null;
    }

    private static ObjectNode readDockerConfig() throws IOException
    {
        final String dockerConfig = System.getenv("DOCKER_CONFIG");

        final Reader reader = dockerConfig == null
            ? getFileReaderFromDir(new File(getHomeDir(), ".docker/config.json"))
            : getFileReaderFromDir(new File(dockerConfig, "config.json"));
        return reader != null ? Constants.MAPPER.readValue(reader, ObjectNode.class) : null;
    }

    private static Reader getFileReaderFromDir(final File file)
    {
        if (file.exists() && file.length() != 0) {
            try {
                return new FileReader(file);
            } catch (final FileNotFoundException e) {
                // Unexpected
                throw new IllegalStateException("Cannot find " + file, e);
            }
        }
        return null;
    }

    private static File getHomeDir()
    {
        return new File(getUserHome());
    }

    public static String getUserHome()
    {
        String homeDir = System.getenv("HOME");
        if (homeDir == null) {
            homeDir = System.getProperty("user.home");
        }
        return homeDir;
    }

    private static String ensureRegistryHttpUrl(final String registry) {
        if (registry.toLowerCase().startsWith("http")) {
            return registry;
        }
        // Default to https:// schema
        return "https://" + registry;
    }
}