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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.api.command.PullImageResultCallback;

public final class DockerRestClient
{
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

    public Optional<Image> findImage(
        final String imageName)
    {
        LOGGER.info("Checking if image '{}' is present", imageName);
        final List<Image> images = dockerClient.listImagesCmd()
                                               .exec();
        images.forEach(i -> LOGGER.debug("Found image - ID: {}\n Tags: {}\n Labels: {}\n Digests: {}",
                             i.getId(), i.getRepoTags(), i.getLabels(), i.getRepoDigests())
                      );

        return images.stream()
                     .filter(i -> i.getRepoTags() != null && Arrays.asList(i.getRepoTags()).contains(imageName))
                     .findFirst();
    }

    public boolean pullImage(
        final String repository,
        final String tag)
        throws InterruptedException
    {
        LOGGER.info("Pulling image '{}:{}'...", repository, tag);

        return dockerClient.pullImageCmd(repository)
                           .withTag(tag)
                           .exec(new PullImageResultCallback())
                           .awaitCompletion(DOWNLOAD_IMAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public void tagImage(
        final String imageId,
        final String imageNameWithRepository,
        final String tag)
        throws ImageTaggingException
    {
        LOGGER.info("Tagging image with id '{}' as '{}:{}'...", imageId, imageNameWithRepository, tag);

        dockerClient.tagImageCmd(imageId, imageNameWithRepository, tag)
                    .exec();

        // Verify image was tagged
        final Optional<Image> taggedImage = findImage(imageNameWithRepository + ":" + tag);
        if (taggedImage.isEmpty()) {
            throw new ImageTaggingException("Image '" + imageId + "' was not tagged as " + imageNameWithRepository + ":" + tag);
        }
        final Image image = taggedImage.get();
        LOGGER.info("Image tagged with id '{}' as '{}:{}'...", image.getId(), image.getRepoTags());
    }

    public void untagImage(
        final String image)
        throws ImageTaggingException
    {
        LOGGER.info("Untagging image {}...", image);

        dockerClient.removeImageCmd(image)
                    .exec();

        // Verify image was untagged
        final Optional<Image> taggedImage = findImage(image);
        if (taggedImage.isPresent()) {
            final Image unTaggedImage = taggedImage.get();
            LOGGER.error("Image with id '{}' still tagged '{}:{}'...", unTaggedImage.getId(), unTaggedImage.getRepoTags());
            throw new ImageTaggingException("Image '" + image + "' was not un-tagged");
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
