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
