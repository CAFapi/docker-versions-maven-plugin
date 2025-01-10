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
package com.github.cafapi.docker_versions.plugins.extension.test;

import java.util.Arrays;
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
    public void testDisableAutoPopulate() throws Exception
    {
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"validate"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:populate-project-registry"}));

        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "validate"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:populate-project-registry", "validate"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "docker-versions:populate-project-registry"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:populate-project-registry", "verify"}));

        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "validate", "docker-versions:populate-project-registry"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "validate", "docker-versions:depopulate-project-registry"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "docker-versions:depopulate-project-registry"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:depopulate-project-registry"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"validate", "docker-versions:depopulate-project-registry"}));

        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "validate", "site"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "validate", "docker-versions:populate-project-registry", "install"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "docker-versions:populate-project-registry", "verify"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"validate", "docker-versions:populate-project-registry", "site"}));

        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:populate-project-registry", "install", "deploy"}));

        verifyDisableAutoPopulate(Arrays.asList(new String[]{"clean", "compiler:compile"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"compiler:help"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"site"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker:build", "docker:start", "docker:stop"}));
        verifyDisableAutoPopulate(Arrays.asList(new String[]{"docker-versions:populate-project-registry", "docker:build", "verify"}));
    }

    @Test
    public void testEnableAutoPopulate() throws Exception
    {
        verifyEnableAutoPopulate(Arrays.asList(new String[]{"verify"}));

        verifyEnableAutoPopulate(Arrays.asList(new String[]{"install", "deploy"}));
        verifyEnableAutoPopulate(Arrays.asList(new String[]{"clean", "deploy"}));
        verifyEnableAutoPopulate(Arrays.asList(new String[]{"validate", "install"}));

        verifyEnableAutoPopulate(Arrays.asList(new String[]{"clean", "install", "docker-versions:depopulate-project-registry"}));

        verifyEnableAutoPopulate(Arrays.asList(new String[]{"compile", "install", "deploy"}));

        verifyEnableAutoPopulate(Arrays.asList(new String[]{"clean", "install", "deploy"}));
        verifyEnableAutoPopulate(Arrays.asList(new String[]{"validate", "install", "deploy"}));
    }

    private static void verifyDisableAutoPopulate(final List<String> tasks)
    {
        LOGGER.info("Ignore populate goal for tasks in session: {}...", tasks);
        final List<String> phasesInSession = DockerVersionsLifecycleParticipant.getPhases(tasks);
        Assertions.assertFalse(DockerVersionsLifecycleParticipant.shouldAddPopulateGoal(tasks, phasesInSession));
    }

    private static void verifyEnableAutoPopulate(final List<String> tasks)
    {
        LOGGER.info("Include populate goal for tasks in session: {}...", tasks);
        final List<String> phasesInSession = DockerVersionsLifecycleParticipant.getPhases(tasks);
        Assertions.assertTrue(DockerVersionsLifecycleParticipant.shouldAddPopulateGoal(tasks, phasesInSession));
    }
}
