/*
 * Copyright 2024-2026 Open Text.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

final class CredentialHelperClient
{
    // Refer: https://docs.docker.com/reference/cli/docker/login/
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialHelperClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final String SECRET_KEY = "Secret";
    private static final String USERNAME_KEY = "Username";
    private static final String TOKEN_USERNAME = "<token>";

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final String credentialHelperName;

    private int statusCode;

    public CredentialHelperClient(final String credentialsStore)
    {
        credentialHelperName = "docker-credential-" + credentialsStore;
    }

    public String getName()
    {
        return credentialHelperName;
    }

    public DockerRegistryAuthConfig getAuthConfig(final String authKey) throws IOException
    {
        LOGGER.debug("Executing {} {}", getName(), "get");
        // create and start the process
        final Process process = new ProcessBuilder(getName(), "get").start();
        final StringBuffer authDetails = new StringBuffer();
        try {
            // The 'get' command takes a string payload from the standard input.
            // That payload carries the server address that the Docker Engine needs credentials for.
            try (
                final Writer outStreamWriter = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
                final BufferedWriter writer = new BufferedWriter(outStreamWriter)) {
                writer.write(authKey);
                writer.newLine();
                writer.flush();
            }

            // Watch for errors when executing the command
            final Future<IOException> stderrFuture = startWatchingErrorStream(process.getErrorStream());

            // Read the process output from stream
            try (final Reader inStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(inStreamReader)) {
                while (true) {
                    final String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    authDetails.append(line).append("\n");
                }
            }

            stopWatchingErrorStream(stderrFuture);
            checkProcessExit(process);
        } catch (final IOException ex) {
            process.destroy();
            throw ex;
        }

        if (statusCode == 0) {
            return readAuthDetails(authDetails.toString());
        } else if (statusCode == 1) {
            // credentials not found in native keychain
            return null;
        }

        throw new IOException(String.format("'%s' exited with status %d", getName(), statusCode));
    }

    private static DockerRegistryAuthConfig readAuthDetails(final String authDetails) throws IOException
    {
        final JsonNode credentials = MAPPER.readTree(authDetails);
        if (!credentials.has(SECRET_KEY) || !credentials.has(USERNAME_KEY)) {
            return null;
        }
        return toAuthConfig(credentials);
    }

    private static DockerRegistryAuthConfig toAuthConfig(final JsonNode credential)
    {
        if (credential == null) {
            return null;
        }
        final String secret = credential.get(CredentialHelperClient.SECRET_KEY).asText();
        final String username = credential.get(CredentialHelperClient.USERNAME_KEY).asText();
        if (TOKEN_USERNAME.equals(username)) {
            // If userKey is <token>, the password is actually a token
            return new DockerRegistryAuthConfig(null, null, null, null, secret);
        }

        return new DockerRegistryAuthConfig(username, secret, null, null);
    }

    private Future<IOException> startWatchingErrorStream(final InputStream errorStream)
    {
        return executor.submit(() -> {
            try (final Reader inStreamReader = new InputStreamReader(errorStream, StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(inStreamReader)) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    synchronized (LOGGER) {
                        LOGGER.warn(line);
                    }
                }
                return null;
            } catch (final IOException e) {
                return e;
            }
        });
    }

    private void stopWatchingErrorStream(final Future<IOException> future) throws IOException
    {
        try {
            final IOException ioe = future.get(2, TimeUnit.SECONDS);
            if (ioe != null) {
                throw new IOException(String.format("Failed to read process '%s' error stream", getName()), ioe);
            }
        } catch (final InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException | TimeoutException e) {
            throw new IOException(String.format("Failed to stop process '%s' error stream", getName()), e);
        }
    }

    private void checkProcessExit(final Process process)
    {
        try {
            statusCode = process.waitFor();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (final IllegalThreadStateException | InterruptedException e) {
            process.destroy();
            statusCode = -1;
        }
    }
}
