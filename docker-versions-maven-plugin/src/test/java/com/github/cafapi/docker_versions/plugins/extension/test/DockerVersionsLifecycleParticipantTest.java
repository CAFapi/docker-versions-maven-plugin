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
package com.github.cafapi.docker_versions.plugins.extension.test;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.plugins.extension.DockerVersionsLifecycleParticipant;

final class DockerVersionsLifecycleParticipantTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsLifecycleParticipantTest.class);

    @BeforeEach
    void init(final TestInfo testInfo)
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());
    }

    @Test
    public void testIgnoreTagging() throws Exception
    {
        verifyIgnoreTagging(List.of("clean"));
        verifyIgnoreTagging(List.of("validate"));
        verifyIgnoreTagging(List.of("docker-versions:populate-project-registry"));

        verifyIgnoreTagging(List.of("clean", "validate"));
        verifyIgnoreTagging(List.of("docker-versions:populate-project-registry", "validate"));
        verifyIgnoreTagging(List.of("clean", "docker-versions:populate-project-registry"));
        verifyIgnoreTagging(List.of("docker-versions:populate-project-registry", "verify"));

        verifyIgnoreTagging(List.of("clean", "validate", "docker-versions:populate-project-registry"));
        verifyIgnoreTagging(List.of("clean", "validate", "docker-versions:depopulate-project-registry"));
        verifyIgnoreTagging(List.of("clean", "docker-versions:depopulate-project-registry"));
        verifyIgnoreTagging(List.of("docker-versions:depopulate-project-registry"));
        verifyIgnoreTagging(List.of("validate", "docker-versions:depopulate-project-registry"));

        verifyIgnoreTagging(List.of("clean", "validate", "docker-versions:populate-project-registry", "install"));
        verifyIgnoreTagging(List.of("clean", "docker-versions:populate-project-registry", "verify"));
        verifyIgnoreTagging(List.of("validate", "docker-versions:populate-project-registry", "site"));

        verifyIgnoreTagging(List.of("docker-versions:populate-project-registry", "install", "deploy"));
    }

    @Test
    public void testIncludeTagging() throws Exception
    {
        verifyIncludeTagging(List.of("verify"));

        verifyIncludeTagging(List.of("install", "deploy"));
        verifyIncludeTagging(List.of("clean", "deploy"));
        verifyIncludeTagging(List.of("validate", "install"));

        verifyIncludeTagging(List.of("clean", "install", "docker-versions:depopulate-project-registry"));

        verifyIncludeTagging(List.of("compile", "install", "deploy"));
        verifyIncludeTagging(List.of("clean", "validate", "site"));
        verifyIncludeTagging(List.of("clean", "install", "deploy"));
        verifyIncludeTagging(List.of("validate", "install", "deploy"));
    }

    private static void verifyIgnoreTagging(final List<String> tasks)
    {
        LOGGER.info("Ignore tagging for tasks in session: {}...", tasks);
        Assertions.assertTrue( DockerVersionsLifecycleParticipant.ignoreTagging(tasks) );
    }

    private static void verifyIncludeTagging(final List<String> tasks)
    {
        LOGGER.info("Include tagging for tasks in session: {}...", tasks);
        Assertions.assertFalse( DockerVersionsLifecycleParticipant.ignoreTagging(tasks) );
    }
}
