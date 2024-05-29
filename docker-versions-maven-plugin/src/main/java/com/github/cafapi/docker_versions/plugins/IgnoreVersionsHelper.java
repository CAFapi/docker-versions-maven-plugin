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
package com.github.cafapi.docker_versions.plugins;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for managing ignore versions.
 */
public final class IgnoreVersionsHelper
{
    private static final List<String> DEFAULT_IGNORE_VERSIONS = Arrays.asList(new String[]{"latest", "stable"});

    private static final Logger LOGGER = LoggerFactory.getLogger(IgnoreVersionsHelper.class);

    public static List<String> getRelevantTags(
        final Set<IgnoreVersion> configuredIgnoreVersions,
        final List<String> tags,
        final String repository)
    {
        LOGGER.debug("Configured ignore versions: {}", configuredIgnoreVersions);
        final Set<IgnoreVersion> imageIgnoreVersions = getImageIgnoreVersions(configuredIgnoreVersions, repository);
        return tags.stream()
            .filter(t -> !isIgnoredVersion(configuredIgnoreVersions, imageIgnoreVersions, t))
            .collect(Collectors.toList());
    }

    private static boolean isIgnoredVersion(
        final Set<IgnoreVersion> configuredIgnoreVersions,
        final Set<IgnoreVersion> imageIgnoreVersions,
        final String tag)
    {
        if (DEFAULT_IGNORE_VERSIONS.contains(tag.toLowerCase(Locale.ENGLISH))) {
            return true;
        }

        if (configuredIgnoreVersions.isEmpty()) {
            return false;
        }

        boolean isMatch;
        for (final IgnoreVersion iVersion : imageIgnoreVersions) {
            isMatch = "regex".equals(iVersion.getType())
                ? Pattern.matches(iVersion.getVersion(), tag)
                : iVersion.getVersion().equals(tag);
            LOGGER.trace("Check if tag {} matches version pattern {} : {}", tag, iVersion.getVersion(), isMatch);
            if (isMatch) {
                return true;
            }
        }
        return false;
    }

    private static Set<IgnoreVersion> getImageIgnoreVersions(
        final Set<IgnoreVersion> configuredIgnoreVersions,
        final String imageName)
    {
        final Set<IgnoreVersion> imageIgnoreVersions = new HashSet<>();

        for (final IgnoreVersion iVersion : configuredIgnoreVersions) {
            final Set<String> images = iVersion.getImages();
            if (images == null || images.isEmpty()) {
                imageIgnoreVersions.add(iVersion);
                continue;
            }
            for (final String img : images) {
                LOGGER.trace("Check if image {} matches pattern {}", imageName, img);
                if (Pattern.matches(img, imageName)) {
                    imageIgnoreVersions.add(iVersion);
                }
            }
        }

        LOGGER.debug("Ignore versions for image {} : {}", imageName, imageIgnoreVersions);
        return imageIgnoreVersions;
    }
}
