/*
 * Copyright 2024-2025 Open Text.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RegistryNameHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryNameHelper.class);

    private RegistryNameHelper()
    {
    }

    public static String sanitizeRegistryName(final String registryName)
    {
        // Valid characters are case insensitive alphabets (a-z) (A-Z), digits (0-9), minus sign (-), and period (.)
        // replace all other chars with 2 hyphens
        if (!registryName.matches("[a-zA-Z0-9-.]+")) {
            final String sanitizedRegistryName = registryName.replaceAll("[^a-zA-Z0-9-.]+", "--");
            LOGGER.warn("Invalid project docker registry name: {}, sanitized to: {}", registryName, sanitizedRegistryName);
            return sanitizedRegistryName;
        }
        return registryName;
    }
}
