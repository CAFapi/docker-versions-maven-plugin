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
package com.github.cafapi.docker_versions.docker.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthConfig;
import com.github.cafapi.docker_versions.docker.auth.Constants;
import com.google.common.io.BaseEncoding;
import java.nio.charset.StandardCharsets;

public final class DockerRegistryRestClient
{
    private static final Pattern AUTH_URL_PATTERN = Pattern.compile("Bearer realm=\"(.*?)\",service=\"(.*?)\"");
    private static final Pattern LINK_HEADER_PATTERN = Pattern.compile("<(.*)>; rel=\"next\"");

    private static final String BASE = "%s/v2";
    private static final String MANIFEST = "%s/manifests/%s";
    private static final String TAGS = "%s/tags/list";

    private static final String SCHEMA_HTTP = "http";
    private static final String SCHEMA_HTTPS = "https";

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRegistryRestClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    public static String getDigest(
        final String authToken,
        final String registrySchema,
        final String registry,
        final String repository,
        final String tag)
        throws DockerRegistryException, ImageNotFoundException
    {
        final String registryWithoutTrailingSlash = getRegistryName(registry);

        final String imageNameWithTag = registryWithoutTrailingSlash + "/" + repository + ":" + tag;

        LOGGER.debug("Getting digest for image '{}'", imageNameWithTag);

        final String url = String.format("%s://%s/%s",
                                         registrySchema,
                                         String.format(BASE, registryWithoutTrailingSlash),
                                         String.format(MANIFEST, repository, tag));

        final HttpHead httpHead = new HttpHead(url);

        Optional.ofNullable(authToken).ifPresent(t -> httpHead.addHeader(HttpHeaders.AUTHORIZATION, t));

        httpHead.addHeader(HttpHeaders.ACCEPT, "application/vnd.docker.distribution.manifest.v2+json");

        try (final CloseableHttpClient httpclient = HttpClients.custom().setRedirectStrategy(new DefaultRedirectStrategy()).build()) {
            final Result result = httpclient.execute(
                httpHead, response -> {
                    if (response.getCode() == HttpStatus.SC_OK) {
                        final Header digestHeader = response.getHeader("Docker-Content-Digest");
                        if (digestHeader == null) {
                            return new Result(response.getCode(), null);
                        }
                        return new Result(response.getCode(), digestHeader.getValue());
                    }
                    return new Result(response.getCode(), null);
                }
            );

            if (result.data != null) {
                return result.data;
            }

            if (result.status == HttpStatus.SC_NOT_FOUND) {
                throw new ImageNotFoundException("Image not found in registry " + imageNameWithTag);
            }

            if (result.status == HttpStatus.SC_UNAUTHORIZED) {
                throw new DockerRegistryException("Unauthorized registry access " + imageNameWithTag);
            }

            throw new DockerRegistryException("Docker-Content-Digest header was not set in the response, status: " + result.status);
        } catch (final IOException ex) {
            throw new DockerRegistryException("Error creating http client for getting digest", ex);
        }
    }

    public static List<String> getTags(
        final String authToken,
        final String registrySchema,
        final String registry,
        final String repository)
        throws DockerRegistryException
    {
        final List<String> allTags = new ArrayList<>();
        final String registryWithoutTrailingSlash = getRegistryName(registry);
        LOGGER.debug("Finding image tags '{}/{}'", registryWithoutTrailingSlash, repository);

        final String url = String.format("%s://%s/%s",
                                         registrySchema,
                                         String.format(BASE, registryWithoutTrailingSlash),
                                         String.format(TAGS, repository));

        try {
            // Make the initial request to get the first page of tags
            Map<String, String> nextPageParams = appendPageOfTagsToSpecifiedList(
                url,
                Collections.singletonMap("n", "1000"),
                authToken,
                allTags);

            // Fetch subsequent pages until there are no more tags
            while (nextPageParams != null) {
                nextPageParams = appendPageOfTagsToSpecifiedList(url, nextPageParams, authToken, allTags);
            }

            return allTags;
        } catch (final URISyntaxException e) {
            throw new DockerRegistryException("Error creating http client for getting tags", e);
        }
    }

    private static Map<String, String> appendPageOfTagsToSpecifiedList(
        final String url,
        final Map<String, String> nextPageParams,
        final String authToken,
        final List<String> allTags)
        throws DockerRegistryException, URISyntaxException
    {
        LOGGER.debug("Getting page of tags: {}", nextPageParams);
        final URIBuilder uriBuilder = new URIBuilder(new URI(url));
        nextPageParams.entrySet().forEach(entry -> uriBuilder.addParameter(entry.getKey(), entry.getValue()));

        final HttpGet httpGet = new HttpGet(uriBuilder.build());

        Optional.ofNullable(authToken).ifPresent(t -> httpGet.addHeader(HttpHeaders.AUTHORIZATION, t));

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final Result result = httpclient.execute(
                httpGet, response -> {
                    if (response.getCode() == HttpStatus.SC_OK) {
                        final HttpEntity entity = response.getEntity();
                        final String resultContent = EntityUtils.toString(entity);
                        final JsonParser jsonParser = new JsonFactory().createParser(resultContent);
                        allTags.addAll(MAPPER.readValue(jsonParser, TagsResponse.class).getTags());
                        final String link = response.getHeader("link") == null
                        ? null
                        : response.getHeader("link").getValue();
                        return new Result(response.getCode(), link);
                    }
                    return new Result(response.getCode(), null);
                }
            );

            if (result.status == HttpStatus.SC_OK) {
                return extractNextPageParams(result.data);
            }

            throw new DockerRegistryException("Error getting tags: " + result.status);
        } catch (final IOException ex) {
            throw new DockerRegistryException("Error creating http client for getting tags", ex);
        }
    }

    private static Map<String, String> extractNextPageParams(final String linkHeaderValue) throws URISyntaxException
    {
        // Parse the linkHeader to extract the next page params
        // Link header format: <<url>?n=<last n value>&last=<last entry from response>>; rel="next"

        if (linkHeaderValue == null) {
            return null;
        }

        // Extract the URL from the link header
        final Matcher m = LINK_HEADER_PATTERN.matcher(linkHeaderValue);
        if (!m.matches()) {
            return null;
        }

        final URI uri = new URI(m.group(1));
        final String query = uri.getQuery();

        final Map<String, String> nextPageParams = new HashMap<>();

        // Extract the 'n' and 'last' parameters
        final String[] params = query.split("&");
        for (final String param : params) {
            final String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                nextPageParams.put(keyValue[0], keyValue[1]);
            }
        }

        return nextPageParams;
    }

    public static DockerRegistrySchema getSchema(final String registry)
    {
        String host = registry;
        if (isDockerHub(registry)) {
            host = "registry-1.docker.io";
        }
        try {
            final RegistryBaseResult result = getBase(SCHEMA_HTTPS + "://" + host);
            final DockerRegistryAuthUrl authUrl = result.authUrl;
            return new DockerRegistrySchema(SCHEMA_HTTPS, authUrl);
        } catch (final SSLException e) {
            // Try "http"
        } catch (final IOException e) {
            throw new RuntimeException("No response from the registry server.", e);
        }

        try {
            // Try "http"
            final int code = getBase(SCHEMA_HTTP + "://" + host).status;
            if (code == HttpStatus.SC_OK) {
                return new DockerRegistrySchema(SCHEMA_HTTP, null);
            }
        } catch (final IOException e) {
            LOGGER.debug("Error fnding schema for host {}", host, e);
        }
        throw new RuntimeException("No response from the registry Server.");
    }

    private static RegistryBaseResult getBase(final String endpoint)
        throws IOException
    {
        // lightweight version checks and to validate registry authentication
        final HttpGet httpGet = new HttpGet(String.format(BASE, endpoint) + "/");
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            return httpclient.execute(
                httpGet, response -> {
                    final int code = response.getCode();
                    if (code == HttpStatus.SC_UNAUTHORIZED) {
                        final String authMethods = response.getHeader("Www-Authenticate") == null ? null
                            : response.getHeader("Www-Authenticate").getValue();
                        // https://distribution.github.io/distribution/spec/api/
                        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate
                        LOGGER.debug("Registry base url: {}, authentication methods: {}", endpoint, authMethods);
                        if (authMethods == null) {
                            return new RegistryBaseResult(code, null);
                        }
                        final Matcher matcher = AUTH_URL_PATTERN.matcher(authMethods);
                        if (!matcher.find()) {
                            return new RegistryBaseResult(code, null);
                        }
                        return new RegistryBaseResult(code, new DockerRegistryAuthUrl(matcher.group(1), matcher.group(2)));
                    }
                    return new RegistryBaseResult(code, null);
                }
            );
        }
    }

    private static String getBasicRegistryAuth(final DockerRegistryAuthConfig authConfig)
    {
        if (authConfig == null) {
            return null;
        }

        if (authConfig.getIdentityToken() != null) {
            return "Bearer " + authConfig.getIdentityToken();
        }
        final String authString;
        if (authConfig.getAuth() != null) {
            authString = authConfig.getAuth();
        } else {
            final String username = authConfig.getUsername() != null
                ? authConfig.getUsername()
                : "";

            final String password = authConfig.getPassword() != null
                ? authConfig.getPassword()
                : "";

            final String usernameAndPwd = username + ":" + password;

            authString = BaseEncoding.base64Url().encode(usernameAndPwd.getBytes(StandardCharsets.UTF_8));
        }

        return "Basic " + authString;
    }

    private static URI getAuthUrl(final String url, final String service, final String repository)
        throws DockerRegistryException
    {
        try {
            final URIBuilder uriBuilder = new URIBuilder(new URI(url));
            uriBuilder.addParameter("service", service);
            uriBuilder.addParameter("scope", "repository:" + repository + ":pull");
            return uriBuilder.build();
        } catch (final URISyntaxException e) {
            throw new DockerRegistryException(e);
        }
    }

    private static String getRegistryName(final String registry)
    {
        if (isDockerHub(registry)) {
            return "registry-1.docker.io";
        }
        return registry.endsWith("/") ? registry.substring(0, registry.length() - 1) : registry;
    }

    public static String getAuthToken(
        final DockerRegistryAuthUrl authUrl,
        final String registry,
        final String repository,
        final DockerRegistryAuthConfig registryAuth)
        throws DockerRegistryException
    {
        LOGGER.debug("Get AuthToken for registry: {}...", registry);

        if(authUrl != null) {
            return "Bearer " + getAuthToken(authUrl.getUrl(), authUrl.getService(), repository, registryAuth);
        }
        return getBasicRegistryAuth(registryAuth);
    }

    private static boolean isDockerHub(final String registry)
    {
        return registry.equals(Constants.DEFAULT_REGISTRY);
    }

    private static String getAuthToken(
        final String url,
        final String service,
        final String repository,
        final DockerRegistryAuthConfig authConfig)
        throws DockerRegistryException
    {
        // https://distribution.github.io/distribution/spec/auth/token/
        final URI authUrl = getAuthUrl(url, service, repository);
        final HttpGet httpGet = new HttpGet(authUrl);

        if (authConfig != null) {
            httpGet.addHeader(HttpHeaders.AUTHORIZATION, getBasicRegistryAuth(authConfig));
        }

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final Result result = httpclient.execute(
                httpGet, response -> {
                    if (response.getCode() == HttpStatus.SC_OK) {
                        final HttpEntity entity = response.getEntity();
                        final String resultContent = EntityUtils.toString(entity);
                        final DockerAuthResponse resp = MAPPER.readValue(resultContent, DockerAuthResponse.class);
                        return new Result(response.getCode(), resp.getToken());
                    }
                    return new Result(response.getCode(), null);
                }
            );

            if (result.status == HttpStatus.SC_OK) {
                return result.data;
            } else if (result.status == HttpStatus.SC_UNAUTHORIZED) {
                throw new DockerRegistryException("Unauthorized access : " + authUrl);
            } else {
                throw new DockerRegistryException("Error making getToken request, status:" + result.status);
            }
        } catch (final IOException ex) {
            throw new DockerRegistryException("Error creating http client for getting auth token", ex);
        }
    }

    static class Result
    {
        final int status;
        final String data;

        Result(final int status, final String data)
        {
            this.status = status;
            this.data = data;
        }
    }

    static class RegistryBaseResult
    {
        final int status;
        final DockerRegistryAuthUrl authUrl;

        RegistryBaseResult(final int status, final DockerRegistryAuthUrl authUrl)
        {
            this.status = status;
            this.authUrl = authUrl;
        }
    }
}
