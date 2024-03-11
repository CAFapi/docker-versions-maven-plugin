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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

public final class DockerRestClient
{
    // TODO: Update to allow configuration in the Maven-standard way - i.e. from the pom, but still fallback to these
    private static final int CONNECTION_TIMEOUT_SECONDS = getIntPropertyOrEnvVar("CONNECTION_TIMEOUT_SECONDS", "30");
    private static final int RESPONSE_TIMEOUT_SECONDS = getIntPropertyOrEnvVar("RESPONSE_TIMEOUT_SECONDS", "45");
    private static final long DOWNLOAD_IMAGE_TIMEOUT_SECONDS = getLongPropertyOrEnvVar("DOWNLOAD_IMAGE_TIMEOUT_SECONDS", "300");

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRestClient.class);

    final DockerClient dockerClient;

    public DockerRestClient()
    {
        final DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();

        final DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .connectionTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
            .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
            .build();

        dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public InspectImageResponse findImage(
        final String imageName)
        throws ImageNotFoundException
    {
        LOGGER.debug("Checking if image '{}' is present...", imageName);
        try {
            return dockerClient.inspectImageCmd(imageName)
            .exec();
        }
        catch(final NotFoundException e) {
            throw new ImageNotFoundException(e);
        }
    }

    public boolean pullImage(
        final String repository,
        final String tag)
        throws InterruptedException
    {
        LOGGER.info("Pulling {}:{}...", repository, tag);

        return dockerClient.pullImageCmd(repository)
            .withTag(tag)
            .exec(new PullImageResultCallback())
            .awaitCompletion(DOWNLOAD_IMAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void tagImage(
        final InspectImageResponse image,
        final String imageNameWithRepository,
        final String tag
    ) throws ImageTaggingException
    {
        LOGGER.info("Tagging {}:{}... current tags: {}", imageNameWithRepository, tag, image.getRepoTags());

        final String imageId = image.getId();

        dockerClient.tagImageCmd(imageId, imageNameWithRepository, tag)
            .exec();

        // Verify image was tagged
        try {
            final InspectImageResponse newImage = findImage(imageNameWithRepository + ":" + tag);
            LOGGER.debug("Image '{}' as '{}:{}'...", newImage.getId(), newImage.getRepoTags());
        } catch (final ImageNotFoundException e) {
            throw new ImageTaggingException("Image '" + imageId + "' was not tagged as " + imageNameWithRepository + ":" + tag);
        }
    }

    public void untagImage(final String image) throws ImageTaggingException
    {
        LOGGER.info("Untagging {}...", image);

        dockerClient.removeImageCmd(image)
            .exec();

        // Verify image was untagged
        try {
            final InspectImageResponse taggedImage = findImage(image);
            LOGGER.error("Image with id '{}' still tagged '{}:{}'...", taggedImage.getId(), taggedImage.getRepoTags());
            throw new ImageTaggingException("Image '" + image + "' was not un-tagged");
        } catch (final ImageNotFoundException e) {
            // Image has been untagged
        }
    }

    private static int getIntPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = getPropertyOrEnvVar(key, defaultValue);
        return Integer.parseInt(propertyValue);
    }

    private static long getLongPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = getPropertyOrEnvVar(key, defaultValue);
        return Long.parseLong(propertyValue);
    }

    private static String getPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = System.getProperty(key);
        return (propertyValue != null)
            ? propertyValue
            : (System.getenv(key) != null) ? System.getenv(key) : defaultValue;
    }
}
