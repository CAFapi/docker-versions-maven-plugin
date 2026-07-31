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
package com.github.cafapi.docker_versions.docker.client;

import com.github.cafapi.docker_versions.plugins.HttpConfiguration;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.ResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerRestClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerRestClient.class);

    private final DockerClient dockerClient;

    public DockerRestClient(final HttpConfiguration httpConfiguration, final String dockerHost)
    {
        final HttpConfiguration httpConfig = (httpConfiguration == null)
            ? new HttpConfiguration()
            : httpConfiguration;
        LOGGER.debug("HttpConfig: {}", httpConfig);

        final DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        if (dockerHost != null) {
            configBuilder.withDockerHost(dockerHost);
        }

        final String dockerConfig = getDockerConfig();
        configBuilder.withDockerConfig(dockerConfig);

        final DockerClientConfig config = configBuilder.build();
        final DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .connectionTimeout(Duration.ofSeconds(httpConfig.getConnectionTimout()))
            .responseTimeout(Duration.ofSeconds(httpConfig.getResponseTimout()))
            .build();

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

    public void pullImage(
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

        final PullImageResultCallback callback = new PullImageResultCallback() {

            private static final double ONE_GB = 1_000_000_000.0;
            private static final double ONE_MB = 1_000_000.0;
            private final Map<String, Long> layerBytes = new ConcurrentHashMap<>();
            private final Map<String, Long> totalBytes = new ConcurrentHashMap<>();
            private final Set<String> countedLayers = ConcurrentHashMap.newKeySet();
            private final Set<String> loggedLayers = ConcurrentHashMap.newKeySet();
            private final Map<String, String> lastStatusByLayer = new ConcurrentHashMap<>();
            private final AtomicLong knownTotalBytesExpected = new AtomicLong(0L);
            private final AtomicInteger pullPercentage = new AtomicInteger(0);

            @Override
            public void onError(final Throwable throwable)
            {
                LOGGER.error("Error pulling image {}:{} ", repository, tag, throwable);
                super.onError(throwable);
            }

            /**
             * This method logs pull progress when images are pulled from docker.
             * @param item
             */
            @Override
            public void onNext(final PullResponseItem item) {
                super.onNext(item);

                final String status = item.getStatus();

                if (item.getId() != null && item.getProgressDetail() != null) {
                    final String itemId = item.getId();
                    final ResponseItem.ProgressDetail progressDetail = item.getProgressDetail();
                    final String previous = lastStatusByLayer.get(itemId);
                    lastStatusByLayer.put(itemId, status);
                    final Long layerCurrent = Optional.ofNullable(progressDetail.getCurrent()).orElse(0L);
                    final Long layerTotal = Optional.ofNullable(progressDetail.getTotal()).orElse(0L);
                    if ("Extracting".equalsIgnoreCase(status)) {
                        final long pulledBytes = totalBytes.getOrDefault(itemId, 0L);
                        if (!"Extracting".equalsIgnoreCase(previous)) {
                            LOGGER.info("Extracting {} ({})", itemId, stringifyBytes(pulledBytes));
                        }
                        return;
                    } else if ("Downloading".equalsIgnoreCase(status)) {
                        if (layerCurrent > 0) {
                            layerBytes.put(itemId, layerCurrent);
                        }

                        // Add to aggregate total only on first progress for this item
                        if (layerTotal > 0 && countedLayers.add(itemId)) {
                            totalBytes.put(itemId, layerTotal);
                            knownTotalBytesExpected.addAndGet(layerTotal);
                            // If a new item is received pull percentage is now invalid.
                            pullPercentage.set(0);
                        }

                        final long bytesDownloaded = layerBytes.values().stream().mapToLong(Long::longValue).sum();
                        if (knownTotalBytesExpected.get() > 0) {
                            final int percent = (int) Math.round(bytesDownloaded * 100.0 / knownTotalBytesExpected.get());
                            final int progress = (percent / 10) * 10;
                            if (LOGGER.isInfoEnabled() && progress > pullPercentage.get()) {
                                pullPercentage.set(progress);
                                LOGGER.info("Pulling {} ({}) {}% of {} item{} completed, {} of {}",
                                    itemId,
                                    stringifyBytes(totalBytes.get(itemId)),
                                    progress,
                                    countedLayers.size(),
                                    (countedLayers.size() > 1)?"s":"",
                                    stringifyBytes(bytesDownloaded),
                                    stringifyBytes(knownTotalBytesExpected.get()));
                                loggedLayers.add(itemId);
                            }
                        }
                        if (loggedLayers.add(itemId)) {
                            // Ensures each item is logged at least once with its size
                            LOGGER.info("Pulling {} ({})", itemId, stringifyBytes(layerTotal));
                        }
                    }
                }
            }

            private String stringifyBytes(final long bytes)
            {
                if (bytes >= ONE_GB) {
                    return String.format("%.2fGB",  (bytes / ONE_GB));
                } else if (bytes >= ONE_MB) {
                    return String.format("%.2fMB",  (bytes / ONE_MB));
                }
                return bytes + "bytes";
            }
        };

        pullCommand
            .withTag(tag)
            .exec(callback)
            .awaitCompletion();
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

    private static String getDockerConfig()
    {
        final String dockerConfigEnv = System.getenv(DefaultDockerClientConfig.DOCKER_CONFIG);
        LOGGER.debug("DOCKER_CONFIG environment variable set: {}", dockerConfigEnv);

        final String dockerConfigDir = dockerConfigEnv == null
            ? SystemUtils.USER_HOME + "/.docker"
            : dockerConfigEnv;

        LOGGER.debug("Looking for docker config in: {}", dockerConfigDir);
        final File dockerCfgFile = new File(dockerConfigDir, "config.json");

        if (!dockerCfgFile.exists() || !dockerCfgFile.isFile()) {
            LOGGER.debug("{} does not exist or is not a file", dockerCfgFile);
            return null;
        }

        try {
            if (dockerCfgFile.length() == 0 || FileUtils.readFileToString(dockerCfgFile, StandardCharsets.UTF_8).trim().isEmpty()) {
                LOGGER.debug("{} is empty.", dockerCfgFile);
                return null;
            }
        } catch (final IOException e) {
            throw new IllegalArgumentException("Error reading default docker config", e);
        }
        LOGGER.debug("Use dockerConfigDir : {}", dockerConfigDir);
        return dockerConfigDir;
    }
}
