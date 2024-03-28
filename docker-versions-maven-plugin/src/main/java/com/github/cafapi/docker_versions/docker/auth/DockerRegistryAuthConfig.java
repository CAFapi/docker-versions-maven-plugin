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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DockerRegistryAuthConfig
{
    public static final DockerRegistryAuthConfig EMPTY_AUTH_CONFIG = new DockerRegistryAuthConfig("", "", "", "");

    public static final String AUTH_USERNAME = "username";
    public static final String AUTH_PASSWORD = "password";
    public static final String AUTH_EMAIL = "email";
    public static final String AUTH_AUTH = "auth";
    public static final String AUTH_IDENTITY_TOKEN = "identityToken";

    public static final String REGISTRY_DOCKER_IO = "docker.io";
    public static final String REGISTRY_DOCKER_IO_URL = "https://index.docker.io/v1/";
    public static final String REGISTRY_DEFAULT = REGISTRY_DOCKER_IO;

    private final String username;
    private final String password;
    private final String email;
    private final String auth;
    private final String identityToken;
    private String registry;

    private final String authEncoded;

    public DockerRegistryAuthConfig(final Map<String, String> params)
    {
        this(params.get(AUTH_USERNAME), params.get(AUTH_PASSWORD), params.get(AUTH_EMAIL), params.get(AUTH_AUTH),
            params.get(AUTH_IDENTITY_TOKEN));
    }

    public DockerRegistryAuthConfig(final String username, final String password, final String email, final String auth)
    {
        this(username, password, email, auth, null);
    }

    public DockerRegistryAuthConfig(
        final String username,
        final String password,
        final String email,
        final String auth,
        final String identityToken)
    {
        this.username = username;
        this.password = password;
        this.email = email;
        this.auth = auth;
        this.identityToken = identityToken;
        authEncoded = createAuthEncoded();
    }

    /**
     * Constructor which takes a base64 encoded credentials in the form 'user:password'
     *
     * @param credentialsEncoded the docker encoded user and password
     * @param email the email to use for authentication
     */
    public DockerRegistryAuthConfig(final String credentialsEncoded, final String email)
    {
        this(credentialsEncoded, email, null);
    }

    /**
     * Constructor which takes a base64 encoded credentials in the form 'user:password'
     *
     * @param credentialsEncoded the docker encoded user and password
     * @param email the email to use for authentication
     */
    public DockerRegistryAuthConfig(final String credentialsEncoded, final String email, final String identityToken)
    {
        final String credentials = new String(Base64.decodeBase64(credentialsEncoded));
        final String[] parsedCreds = credentials.split(":", 2);
        username = parsedCreds[0];
        password = parsedCreds[1];
        this.email = email;
        this.identityToken = identityToken;
        auth = null;
        authEncoded = createAuthEncoded();
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getEmail()
    {
        return email;
    }

    public String getAuth()
    {
        return auth;
    }

    public String getIdentityToken()
    {
        return identityToken;
    }

    public String toHeaderValue()
    {
        return authEncoded;
    }

    public void setRegistry(final String registry)
    {
        this.registry = registry;
    }

    public String toJson()
    {
        return toJsonObject().toString();
    }

    public JsonNode toJsonObject()
    {
        final ObjectNode creds = Constants.MAPPER.createObjectNode();
        creds.put("auth", encodeBase64(username + ":" + password));
        final ObjectNode auths = Constants.MAPPER.createObjectNode();

        auths.set(getRegistryUrl(registry), creds);
        final ObjectNode root = Constants.MAPPER.createObjectNode();
        root.set("auths", auths);
        return root;
    }

    private String createAuthEncoded()
    {
        final ObjectNode ret = Constants.MAPPER.createObjectNode();
        if (identityToken != null) {
            putNonNull(ret, AUTH_IDENTITY_TOKEN, identityToken);
        } else {
            putNonNull(ret, AUTH_USERNAME, username);
            putNonNull(ret, AUTH_PASSWORD, password);
            putNonNull(ret, AUTH_EMAIL, email);
            putNonNull(ret, AUTH_AUTH, auth);
        }

        return encodeBase64(ret.toString());
    }

    private static String encodeBase64(String value)
    {
        return encodeBase64ChunkedURLSafeString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes the given binaryData in a format that is compatible with the Docker Engine API.
     * That is, base64 encoded, padded, and URL safe.
     *
     * @param binaryData data to encode
     * @return encoded data
     */
    private static String encodeBase64ChunkedURLSafeString(final byte[] binaryData)
    {
        return Base64.encodeBase64String(binaryData).replace('+', '-').replace('/', '_');
    }

    private static void putNonNull(final ObjectNode ret, final String key, final String value)
    {
        if (value != null) {
            ret.put(key, value);
        }
    }

    /**
     * This method returns registry authentication URL.
     * In most cases it will be the same as registry, but for some it could be different.
     * For example for "docker.io" the authentication URL should be exactly "https://index.docker.io/v1/"
     *
     * @param registry registry or null. If registry is null the default one is used (see REGISTRY_DEFAULT).
     * @return authentication URL for the given registry.
     */
    private static String getRegistryUrl(final String registry)
    {
        final String reg = registry != null ? registry : REGISTRY_DEFAULT;
        if (REGISTRY_DOCKER_IO.equals(StringUtils.substringBefore(reg, "/"))) {
            return REGISTRY_DOCKER_IO_URL;
        }
        return reg;
    }
}
