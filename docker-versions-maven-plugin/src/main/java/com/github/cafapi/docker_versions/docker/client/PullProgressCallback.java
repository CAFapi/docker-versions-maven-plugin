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
 * Logs docker image pull progress in a manner similar to the {@code docker pull} CLI command: layers that are
 * already present locally, per-layer download/extraction progress, per-layer completion, and a blended overall
 * percentage across all layers currently known to be part of the pull.
 */
public final class PullProgressCallback extends PullImageResultCallback
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PullProgressCallback.class);

    private static final double ONE_GB = 1_000_000_000.0;
    private static final double ONE_MB = 1_000_000.0;

    private final String repository;
    private final String tag;

    private final Map<String, Long> itemBytes = new ConcurrentHashMap<>();
    private final Map<String, Long> totalBytes = new ConcurrentHashMap<>();
    private final Set<String> countedItems = ConcurrentHashMap.newKeySet();
    private final Set<String> loggedItems = ConcurrentHashMap.newKeySet();
    private final Set<String> completedItems = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastStatusByItem = new ConcurrentHashMap<>();
    private final AtomicLong knownTotalBytesExpected = new AtomicLong(0L);
    private final AtomicInteger pullPercentage = new AtomicInteger(0);

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
        final String itemId = item.getId();

        if (itemId == null) {
            return;
        }

        if ("Already exists".equalsIgnoreCase(status)) {
            if (loggedItems.add(itemId)) {
                LOGGER.info("Already exists: {}", itemId);
            }
            return;
        }

        if ("Pull complete".equalsIgnoreCase(status)) {
            if (completedItems.add(itemId)) {
                LOGGER.info("Pull complete: {} ({})", itemId, stringifyBytes(totalBytes.getOrDefault(itemId, 0L)));
            }
            return;
        }

        if (item.getProgressDetail() == null) {
            return;
        }

        final ResponseItem.ProgressDetail progressDetail = item.getProgressDetail();
        final String previous = lastStatusByItem.get(itemId);
        lastStatusByItem.put(itemId, status);
        final long itemCurrent = Optional.ofNullable(progressDetail.getCurrent()).orElse(0L);
        final long itemTotal = Optional.ofNullable(progressDetail.getTotal()).orElse(0L);

        if ("Extracting".equalsIgnoreCase(status)) {
            final long pulledBytes = totalBytes.getOrDefault(itemId, 0L);
            if (!"Extracting".equalsIgnoreCase(previous)) {
                LOGGER.info("Extracting {} ({})", itemId, stringifyBytes(pulledBytes));
            }
        } else if ("Downloading".equalsIgnoreCase(status)) {
            if (itemCurrent > 0) {
                itemBytes.put(itemId, itemCurrent);
            }

            // Add to aggregate total only on first progress for this item
            if (itemTotal > 0 && countedItems.add(itemId)) {
                totalBytes.put(itemId, itemTotal);
                knownTotalBytesExpected.addAndGet(itemTotal);
            }

            final long bytesDownloaded = itemBytes.values().stream().mapToLong(Long::longValue).sum();
            if (knownTotalBytesExpected.get() > 0) {
                final int percent = (int) Math.round(bytesDownloaded * 100.0 / knownTotalBytesExpected.get());
                final int progress = (percent / 10) * 10;
                if (LOGGER.isInfoEnabled() && progress > pullPercentage.get()) {
                    pullPercentage.set(progress);
                    // Fall back to this item's own (possibly zero) total when it hasn't been recorded yet, e.g.
                    // when a registry omits the size on a layer's very first progress event.
                    final long itemTotalBytes = totalBytes.getOrDefault(itemId, itemTotal);
                    LOGGER.info("Pulling {} ({}) {}% of {} item{} completed, {} of {}",
                        itemId,
                        stringifyBytes(itemTotalBytes),
                        progress,
                        countedItems.size(),
                        (countedItems.size() > 1) ? "s" : "",
                        stringifyBytes(bytesDownloaded),
                        stringifyBytes(knownTotalBytesExpected.get()));
                }
            }
            if (loggedItems.add(itemId)) {
                // Ensures each item is logged at least once with its size
                LOGGER.info("Pulling {} ({})", itemId, stringifyBytes(itemTotal));
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
