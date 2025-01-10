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

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

abstract class DockerVersionsMojo extends AbstractMojo
{
    protected static final String PROJECT_DOCKER_REGISTRY = "projectDockerRegistry";
    protected static final String LATEST_TAG = "latest";

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    // This parameter is used only when the plugin extension is enabled
    // and is defined here to prevent plugin warnings about unrecognized parameter
    @Parameter
    protected String projectDockerRegistry;

    @Parameter(required = true)
    protected List<ImageConfiguration> imageManagement;

    @Parameter
    protected HttpConfiguration httpConfiguration;

    @Parameter(property = "docker.versions.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "docker.host")
    protected String dockerHost;

    protected String getProjectDockerRegistry() throws ProjectRegistryNotSetException
    {
        final String projectDockerRegistryUserProperty = session.getUserProperties().getProperty(PROJECT_DOCKER_REGISTRY);
        final String projectDockerRegistryValue = projectDockerRegistryUserProperty == null
            ? project.getProperties().getProperty(PROJECT_DOCKER_REGISTRY)
            : projectDockerRegistryUserProperty;
        if (projectDockerRegistryValue == null) {
            throw new ProjectRegistryNotSetException();
        }

        return projectDockerRegistryValue;
    }
}
