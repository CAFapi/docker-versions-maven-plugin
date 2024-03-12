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

import com.github.cafapi.docker_versions.plugins.HttpConfiguration;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRestClient.class);

    private final long downloadImageTimeout;
    private final DockerClient dockerClient;

    public DockerRestClient(final HttpConfiguration httpConfiguration)
    {
        HttpConfiguration httpConfig = httpConfiguration;
        if (httpConfig == null) {
            httpConfig = new HttpConfiguration();
        }
        LOGGER.info("HttpConfig: {}", httpConfig);
        downloadImageTimeout = httpConfig.getDownloadImageTimout();
        final DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();

        final DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .connectionTimeout(Duration.ofSeconds(httpConfig.getConnectionTimout()))
            .responseTimeout(Duration.ofSeconds(httpConfig.getResponseTimout()))
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

}
