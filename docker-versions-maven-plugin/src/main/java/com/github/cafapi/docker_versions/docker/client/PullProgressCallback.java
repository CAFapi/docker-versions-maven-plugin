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

import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.ResponseItem;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs docker image pull progress in a manner similar to the {@code docker pull} CLI command: image-level status
 * lines, layers that are already present locally, per-layer download/extraction progress, per-layer completion,
 * and a blended overall percentage across all layers currently known to be part of the pull.
 */
public final class PullProgressCallback extends PullImageResultCallback
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PullProgressCallback.class);

    private static final double ONE_GB = 1_000_000_000.0;
    private static final double ONE_MB = 1_000_000.0;

    private final String repository;
    private final String tag;

    private final Map<String, Long> downloadedPerLayer = new ConcurrentHashMap<>();
    private final Map<String, Long> layerSizes = new ConcurrentHashMap<>();
    private final Set<String> registeredLayers = ConcurrentHashMap.newKeySet();
    private final Set<String> announcedLayers = ConcurrentHashMap.newKeySet();
    private final Set<String> downloadCompleteLayers = ConcurrentHashMap.newKeySet();
    private final Set<String> pullCompleteLayers = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastStatusPerLayer = new ConcurrentHashMap<>();
    private final AtomicLong aggregateExpectedBytes = new AtomicLong(0L);
    private final AtomicInteger lastLoggedMilestone = new AtomicInteger(0);

    public PullProgressCallback(final String repository, final String tag)
    {
        this.repository = repository;
        this.tag = tag;
    }

    @Override
    public void onError(final Throwable throwable)
    {
        LOGGER.error("Error pulling image {}:{} ", repository, tag, throwable);
        super.onError(throwable);
    }

    @Override
    public void onComplete()
    {
        super.onComplete();
        LOGGER.info("Successfully pulled {}:{}", repository, tag);
    }

    /**
     * This method logs pull progress when images are pulled from docker.
     * @param item the progress item reported by the docker daemon
     */
    @Override
    public void onNext(final PullResponseItem item)
    {
        super.onNext(item);

        final String status = item.getStatus();
        final String layerId = item.getId();

        // Image-level messages (e.g. "Status: Downloaded newer image...", "Digest: sha256:...") carry no layer id
        if (layerId == null) {
            if (status != null) {
                LOGGER.info("{}", status);
            }
            return;
        }

        if ("Already exists".equalsIgnoreCase(status)) {
            if (announcedLayers.add(layerId)) {
                LOGGER.info("Already exists: {}", layerId);
            }
            return;
        }

        if ("Download complete".equalsIgnoreCase(status)) {
            if (downloadCompleteLayers.add(layerId)) {
                LOGGER.info("Download complete: {} ({})", layerId, stringifyBytes(layerSizes.getOrDefault(layerId, 0L)));
            }
            return;
        }

        if ("Pull complete".equalsIgnoreCase(status)) {
            if (pullCompleteLayers.add(layerId)) {
                LOGGER.info("Pull complete: {} ({})", layerId, stringifyBytes(layerSizes.getOrDefault(layerId, 0L)));
            }
            return;
        }

        if (item.getProgressDetail() == null) {
            return;
        }

        final ResponseItem.ProgressDetail progressDetail = item.getProgressDetail();
        final String previousStatus = lastStatusPerLayer.get(layerId);
        lastStatusPerLayer.put(layerId, status);
        final long layerDownloaded = Optional.ofNullable(progressDetail.getCurrent()).orElse(0L);
        final long layerSize = Optional.ofNullable(progressDetail.getTotal()).orElse(0L);

        if ("Extracting".equalsIgnoreCase(status)) {
            if (!"Extracting".equalsIgnoreCase(previousStatus)) {
                LOGGER.info("Extracting {} ({})", layerId, stringifyBytes(layerSizes.getOrDefault(layerId, 0L)));
            }
        } else if ("Downloading".equalsIgnoreCase(status)) {
            if (layerDownloaded > 0) {
                downloadedPerLayer.put(layerId, layerDownloaded);
            }

            // Add to aggregate total only on first progress for this layer
            if (layerSize > 0 && registeredLayers.add(layerId)) {
                layerSizes.put(layerId, layerSize);
                aggregateExpectedBytes.addAndGet(layerSize);
            }

            final long totalDownloaded = downloadedPerLayer.values().stream().mapToLong(Long::longValue).sum();
            if (aggregateExpectedBytes.get() > 0) {
                final int percent = (int) Math.round(totalDownloaded * 100.0 / aggregateExpectedBytes.get());
                final int milestone = (percent / 10) * 10;
                if (LOGGER.isInfoEnabled() && milestone > lastLoggedMilestone.get()) {
                    lastLoggedMilestone.set(milestone);
                    // Fall back to this layer's own (possibly zero) size when it hasn't been recorded yet, e.g.
                    // when a registry omits the size on a layer's very first progress event.
                    final long knownLayerSize = layerSizes.getOrDefault(layerId, layerSize);
                    LOGGER.info("Pulling {} ({}) {}% of {} layer{} completed, {} of {}",
                        layerId,
                        stringifyBytes(knownLayerSize),
                        milestone,
                        registeredLayers.size(),
                        (registeredLayers.size() > 1) ? "s" : "",
                        stringifyBytes(totalDownloaded),
                        stringifyBytes(aggregateExpectedBytes.get()));
                }
            }
            if (announcedLayers.add(layerId)) {
                // Ensures each layer is logged at least once with its size
                LOGGER.info("Pulling {} ({})", layerId, stringifyBytes(layerSize));
            }
        }
    }

    private static String stringifyBytes(final long bytes)
    {
        if (bytes >= ONE_GB) {
            return String.format("%.2fGB", (bytes / ONE_GB));
        } else if (bytes >= ONE_MB) {
            return String.format("%.2fMB", (bytes / ONE_MB));
        }
        return bytes + "bytes";
    }
}
