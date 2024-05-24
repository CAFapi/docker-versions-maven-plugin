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

import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class DockerVersionsMojo extends AbstractMojo
{
    protected static final String PROJECT_DOCKER_REGISTRY = "projectDockerRegistry";
    protected static final String LATEST_TAG = "latest";

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsMojo.class);

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    @Parameter(required = true)
    protected List<ImageConfiguration> imageManagement;

    @Parameter
    protected HttpConfiguration httpConfiguration;

    @Parameter(property = "docker.versions.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "docker.host")
    protected String dockerHost;

    protected String getProjectDockerRegister()
    {
        final String projectDockerReg = project.getProperties().getProperty(
            PROJECT_DOCKER_REGISTRY,
            project.getArtifactId() + "-" + project.getVersion() + ".project-registries.local");

        final String sanitizedProjectDockerReg = sanitize(projectDockerReg);

        project.getProperties().put(PROJECT_DOCKER_REGISTRY, sanitizedProjectDockerReg);

        return sanitizedProjectDockerReg;
    }

    private static String sanitize(final String projectDockerReg)
    {
        // Valid characters are case insensitive alphabets (a-z) (A-Z), digits (0-9), minus sign (-), and period (.)
        // replace all other chars with 2 hyphens
        if (!projectDockerReg.matches("[a-zA-Z0-9-.]+")) {
            final String sanitizedProjectDockerReg = projectDockerReg.replaceAll("[^a-zA-Z0-9-.]+", "--");
            LOGGER.warn("Invalid project docker registry name: {}, using sanitized name instead: {}",
                projectDockerReg, sanitizedProjectDockerReg);
            return sanitizedProjectDockerReg;
        }
        return projectDockerReg;
    }
}
