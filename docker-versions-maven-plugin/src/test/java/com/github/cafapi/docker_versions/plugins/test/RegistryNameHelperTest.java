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
package com.github.cafapi.docker_versions.plugins.test;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.plugins.RegistryNameHelper;

final class RegistryNameHelperTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryNameHelperTest.class);

    @BeforeEach
    void init(final TestInfo testInfo)
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());
    }

    @Test
    public void testValidRegistryNames()
    {
        final List<String> registryNames = Arrays.asList(new String[]{
            "acme", "acme-0.0.1-SNAPSHOT.project-registries.local",
            "opensuse-opensearch2-image-2.1.0-os-cve-test-SNAPSHOT.project-registries.local", "localHost",
            "LOCALDOMAIN", "docker.io", "index.docker.io", "127.0.0.1", "192.168.0.1",
            "thisisthesongthatneverendsitgoesonandonandonthisisthesongthatnev",
            "-12345",
            ".acme"});
        for (final String registryName : registryNames) {
            final String sanitizedRegistryName = RegistryNameHelper.sanitizeRegistryName(registryName);
            Assertions.assertTrue(registryName.equals(sanitizedRegistryName), registryName + " - valid registry name");
        }
    }

    @Test
    public void testInvalidRegistryNames()
    {
        final List<String> registryNames = Arrays.asList(new String[]{
            "acme_0.0.1-SNAPSHOT.project-registries.local",
            "opensuse-opensearch2-image-2.1.0-os_cve_test-SNAPSHOT.project-registries.local",
            "https://github.com/docker/docker",
            "[fc00::1]",
            "[fe80::1%eth0]",
            "acme com"});
        for (final String registryName : registryNames) {
            final String sanitizedRegistryName = RegistryNameHelper.sanitizeRegistryName(registryName);
            Assertions.assertFalse(registryName.equals(sanitizedRegistryName) && sanitizedRegistryName.contains("--"),
                                   registryName + " - invalid registry is sanitized");
        }
    }
}
