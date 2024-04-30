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
    public void testDisableAutoPopulate() throws Exception
    {
        verifyDisableAutoPopulate(List.of("clean"));
        verifyDisableAutoPopulate(List.of("validate"));
        verifyDisableAutoPopulate(List.of("docker-versions:populate-project-registry"));

        verifyDisableAutoPopulate(List.of("clean", "validate"));
        verifyDisableAutoPopulate(List.of("docker-versions:populate-project-registry", "validate"));
        verifyDisableAutoPopulate(List.of("clean", "docker-versions:populate-project-registry"));
        verifyDisableAutoPopulate(List.of("docker-versions:populate-project-registry", "verify"));

        verifyDisableAutoPopulate(List.of("clean", "validate", "docker-versions:populate-project-registry"));
        verifyDisableAutoPopulate(List.of("clean", "validate", "docker-versions:depopulate-project-registry"));
        verifyDisableAutoPopulate(List.of("clean", "docker-versions:depopulate-project-registry"));
        verifyDisableAutoPopulate(List.of("docker-versions:depopulate-project-registry"));
        verifyDisableAutoPopulate(List.of("validate", "docker-versions:depopulate-project-registry"));

        verifyDisableAutoPopulate(List.of("clean", "validate", "site"));
        verifyDisableAutoPopulate(List.of("clean", "validate", "docker-versions:populate-project-registry", "install"));
        verifyDisableAutoPopulate(List.of("clean", "docker-versions:populate-project-registry", "verify"));
        verifyDisableAutoPopulate(List.of("validate", "docker-versions:populate-project-registry", "site"));

        verifyDisableAutoPopulate(List.of("docker-versions:populate-project-registry", "install", "deploy"));

        verifyDisableAutoPopulate(List.of("clean", "compiler:compile"));
        verifyDisableAutoPopulate(List.of("compiler:help"));
        verifyDisableAutoPopulate(List.of("site"));
        verifyDisableAutoPopulate(List.of("docker:build", "docker:start", "docker:stop"));
        verifyDisableAutoPopulate(List.of("docker-versions:populate-project-registry", "docker:build", "verify"));
    }

    @Test
    public void testEnableAutoPopulate() throws Exception
    {
        verifyEnableAutoPopulate(List.of("verify"));

        verifyEnableAutoPopulate(List.of("install", "deploy"));
        verifyEnableAutoPopulate(List.of("clean", "deploy"));
        verifyEnableAutoPopulate(List.of("validate", "install"));

        verifyEnableAutoPopulate(List.of("clean", "install", "docker-versions:depopulate-project-registry"));

        verifyEnableAutoPopulate(List.of("compile", "install", "deploy"));

        verifyEnableAutoPopulate(List.of("clean", "install", "deploy"));
        verifyEnableAutoPopulate(List.of("validate", "install", "deploy"));
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
