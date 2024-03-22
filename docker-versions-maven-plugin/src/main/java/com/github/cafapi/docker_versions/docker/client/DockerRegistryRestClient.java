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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;

import com.google.common.io.BaseEncoding;

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

    private final ObjectMapper mapper;

    private final Map<String, AuthConfig> authConfis;

    public DockerRegistryRestClient()
    {
        final DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        authConfis = config.getAuthConfigurations().getConfigs();
        mapper = config.getObjectMapper();
    }

    public String getDigest(
        final AuthConfig authConfiguration,
        final String registry,
        final String repository,
        final String tag)
        throws DockerRegistryException, ImageNotFoundException
    {
        final String registryWithoutTrailingSlash = getRegistryName(registry);

        final String imageNameWithTag = registryWithoutTrailingSlash + "/" + repository + ":" + tag;

        LOGGER.info("Getting digest for image '{}'", imageNameWithTag);

        final AuthConfig registryAuth =
            authConfiguration == null
            ? authConfis.get(registry)
            : authConfiguration;
        final String basictoken = getBasicRegistryAuth(registryAuth);

        final String url = String.format("%s://%s/%s",
                                            getSchema(registryWithoutTrailingSlash),
                                            String.format(BASE, registryWithoutTrailingSlash),
                                            String.format(MANIFEST, repository, tag));

        final HttpHead httpHead = new HttpHead(url);

        Optional.ofNullable(basictoken).ifPresent(t -> httpHead.addHeader(HttpHeaders.AUTHORIZATION, t));

        httpHead.addHeader(HttpHeaders.ACCEPT, "application/vnd.docker.distribution.manifest.v2+json");

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (final CloseableHttpResponse response = httpclient.execute(httpHead)) {
                if (response.getCode() == HttpStatus.SC_OK) {
                    if (response.getHeader("Docker-Content-Digest") == null ) {
                        throw new DockerRegistryException("Docker-Content-Digest header was not set in the response");
                    }
                    return response.getHeader("Docker-Content-Digest").getValue();
                }
                if (response.getCode() == HttpStatus.SC_NOT_FOUND) {
                    throw new ImageNotFoundException("Image not found in registry " + imageNameWithTag);
                }
            } catch (final IOException | ProtocolException e) {
                new DockerRegistryException("Error getting digest", e);
            }
        } catch (final IOException ex) {
            throw new DockerRegistryException("Error creating http client for getting digest", ex);
        }
        throw new DockerRegistryException("Digest not found for " + imageNameWithTag);
    }

    public List<String> getTags(
        final AuthConfig authConfiguration,
        final String registry,
        final String repository)
        throws DockerRegistryException
    {
        final List<String> allTags = new ArrayList<>();
        final String registryWithoutTrailingSlash = getRegistryName(registry);
        LOGGER.info("Finding image tags '{}/{}'", registryWithoutTrailingSlash, repository);

        final AuthConfig registryAuth =
            authConfiguration == null
            ? authConfis.get(registry)
            : authConfiguration;

        final String token = getBasicRegistryAuth(registryAuth);

        final String url = String.format("%s://%s/%s",
            getSchema(registryWithoutTrailingSlash),
            String.format(BASE, registryWithoutTrailingSlash),
            String.format(TAGS, repository));

        try {
            // Make the initial request to get the first page of 100 tags
            Map<String, String> nextPageParams = getPageOfTags(url, Map.of("n", "100"), token, allTags);

            // Fetch subsequent pages until there are no more tags
            while (nextPageParams != null) {
                nextPageParams = getPageOfTags(url, nextPageParams, token, allTags);
            }

            return allTags;
        } catch (final URISyntaxException e) {
            throw new DockerRegistryException("Error creating http client for getting tags", e);
        }
    }

    private Map<String, String> getPageOfTags(
        final String url,
        final Map<String, String> nextPageParams,
        final String authHeaderValue,
        final List<String> allTags)
        throws DockerRegistryException, URISyntaxException
    {
        LOGGER.info("Getting page of tags: {}", nextPageParams);
        final URIBuilder uriBuilder = new URIBuilder(new URI(url));
        nextPageParams.entrySet().forEach( entry -> uriBuilder.addParameter(entry.getKey(), entry.getValue()));

        final HttpGet httpGet = new HttpGet(uriBuilder.build());

        Optional.ofNullable(authHeaderValue).ifPresent(t -> httpGet.addHeader(HttpHeaders.AUTHORIZATION, t));

        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (final CloseableHttpResponse response = httpclient.execute(httpGet)) {
                if (response.getCode() == HttpStatus.SC_OK) {
                    final HttpEntity entity = response.getEntity();
                    final String resultContent = EntityUtils.toString(entity);
                    allTags.addAll(mapper.readValue(resultContent, TagsResponse.class).getTags());
                    final String nextPageLink = response.getHeader("link") == null ? null : response.getHeader("link").getValue();
                    return extractNextPageParams(nextPageLink);
                }
                throw new DockerRegistryException("Error getting tags: " + response.getCode());
            } catch (final ParseException e) {
                throw new DockerRegistryException("Error parsing get tags response", e);
            } catch (final IOException | ProtocolException e) {
                throw new DockerRegistryException("Error getting tags", e);
            }
        } catch (final IOException ex) {
            throw new DockerRegistryException("Error creating http client for getting tags", ex);
        }
    }

    private static Map<String, String> extractNextPageParams(final String linkHeaderValue) throws URISyntaxException
    {
        // Parse the linkHeader to extract the next page params
        // link header format: <my-registry.io/v2/_catalog?n=1&last=centos>; rel="next"

        if (linkHeaderValue == null) {
            return null;
        }

        final Map<String, String> nextPageParams = new HashMap<>();

        // Extract the URL from the link header
        final Matcher m = LINK_HEADER_PATTERN.matcher(linkHeaderValue);
        if (!m.matches()) {
            return null;
        }

        final String url = m.group(1);
        LOGGER.info("Next page of tags: {}", url);
        final URI uri = new URI(url);
        final String query = uri.getQuery();

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

    private static int getBase(final String endpoint)
        throws IOException, ProtocolException
    {
        final HttpHead httpHead = new HttpHead(String.format(BASE, endpoint));
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (final CloseableHttpResponse response = httpclient.execute(httpHead)) {
                final int code = response.getCode();
                if (code != HttpStatus.SC_UNAUTHORIZED) {
                    return code;
                }
                final String auth = response.getHeader("Www-Authenticate") == null
                    ? null
                    : response.getHeader("Www-Authenticate").getValue();
                if (auth == null) {
                    return code;
                }
                final Matcher matcher = AUTH_URL_PATTERN.matcher(auth);
                if (!matcher.find()) {
                    return code;
                }
                final String url = matcher.group(1);
                final String service = matcher.group(2);
                LOGGER.info("Registry base url: {} for service: {}", url, service);
                // Authenticator.instance().setAuthUrl(new Authenticator.AuthUrl(url, service));
                return code;
            }
        }
    }

    private static String getSchema(final String endpoint)
    {
        try {
            // Try "https"
            getBase(String.format("%s://%s", SCHEMA_HTTPS, endpoint));
            return SCHEMA_HTTPS;
        } catch (final SSLException | ProtocolException e) {
            // Try "http"
        } catch (final IOException e) {
            throw new RuntimeException("No response from the registry server.");
        }

        try {
            getBase(String.format("%s://%s", SCHEMA_HTTP, endpoint));
            return SCHEMA_HTTP;
        } catch (final IOException | ProtocolException e) {
            throw new RuntimeException("No response from the registry server.");
        }
    }

    private static String getBasicRegistryAuth(final AuthConfig authConfig) {
        final String plainAuthString = String.format(
            "%s:%s",
            authConfig.getUsername() == null ? "" : authConfig.getUsername(),
            authConfig.getPassword() == null
                ? authConfig.getAuth() == null
                    ? "" 
                    : authConfig.getAuth()
                : authConfig.getPassword());

        return "Basic " + BaseEncoding.base64Url().encode(plainAuthString.getBytes());
    }

    private static String getRegistryName(final String registry)
    {
       return  registry.endsWith("/") ? registry.substring(0, registry.length() - 1) : registry;
    }

}
