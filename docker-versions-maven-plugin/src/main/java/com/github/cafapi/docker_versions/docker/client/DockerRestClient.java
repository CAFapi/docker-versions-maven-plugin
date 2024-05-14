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

import com.github.cafapi.docker_versions.plugins.HttpConfiguration;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerRestClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRestClient.class);

    private final long downloadImageTimeout;
    private final DockerClient dockerClient;

    public DockerRestClient(final HttpConfiguration httpConfiguration, final String dockerHost)
    {
        final HttpConfiguration httpConfig = (httpConfiguration == null)
            ? new HttpConfiguration()
            : httpConfiguration;
        LOGGER.debug("HttpConfig: {}", httpConfig);

        final DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        if (!configBuilder.isDockerHostSetExplicitly() && dockerHost != null) {
            configBuilder.withDockerHost(dockerHost);
        }

        final DockerClientConfig config = configBuilder.build();
        final DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .connectionTimeout(Duration.ofSeconds(httpConfig.getConnectionTimout()))
            .responseTimeout(Duration.ofSeconds(httpConfig.getResponseTimout()))
            .build();

        this.downloadImageTimeout = httpConfig.getDownloadImageTimout();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public Optional<InspectImageResponse> findImage(final String imageName)
    {
        LOGGER.debug("Checking if image '{}' is present...", imageName);
        try {
            final InspectImageResponse image = dockerClient.inspectImageCmd(imageName)
                .exec();

            return Optional.of(image);
        } catch (final NotFoundException e) {
            return Optional.empty();
        }
    }

    public boolean pullImage(
        final String repository,
        final String tag,
        final AuthConfig authConfig
    ) throws InterruptedException
    {
        LOGGER.info("Pulling {}:{}...", repository, tag);
        final PullImageCmd pullCommand = dockerClient.pullImageCmd(repository);

        if (authConfig != null) {
            pullCommand.withAuthConfig(authConfig);
        }

        return pullCommand
            .withTag(tag)
            .exec(new PullImageResultCallback())
            .awaitCompletion(downloadImageTimeout, TimeUnit.SECONDS);
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
        final InspectImageResponse newImage = findImage(imageNameWithRepository + ":" + tag).orElseThrow(
            () -> new ImageTaggingException("Image '" + imageId + "' was not tagged as " + imageNameWithRepository + ":" + tag));
        LOGGER.debug("Image '{}' as '{}:{}'...", newImage.getId(), newImage.getRepoTags());
    }

    public void untagImage(final String image) throws ImageTaggingException
    {
        LOGGER.info("Untagging {}...", image);

        dockerClient.removeImageCmd(image)
            .exec();

        // Verify image was untagged
        final Optional<InspectImageResponse> taggedImage = findImage(image);
        if (taggedImage.isPresent()) {
            final InspectImageResponse unTaggedImage = taggedImage.get();
            LOGGER.error("Image with id '{}' still tagged '{}:{}'...", unTaggedImage.getId(), unTaggedImage.getRepoTags());
            throw new ImageTaggingException("Image '" + image + "' was not un-tagged");
        }
    }
}
