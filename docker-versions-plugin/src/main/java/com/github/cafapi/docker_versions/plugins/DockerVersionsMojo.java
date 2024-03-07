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
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class DockerVersionsMojo extends AbstractMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsMojo.class);

    protected static final String PROJECT_DOCKER_REGISTRY = "projectDockerRegistry";
    protected static final String LATEST_TAG = "latest";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter
    protected List<ImageConfiguration> imageManagement;

    private Properties properties;

    protected String getProjectDockerRegister()
    {
        return getProperties().getProperty(
                PROJECT_DOCKER_REGISTRY,
                project.getName() + "." + project.getVersion() + ".project-registries.local");
    }

    protected String getPropertyOrValue(final String data)
    {
        LOGGER.debug("Finding getPropertyOrValue: {}", data);

        LOGGER.debug("Project properties: {}", getProperties().entrySet());

        if (data != null && data.startsWith("${")) {
            final String propName = data.substring(2, data.length() - 1);
            LOGGER.debug("Getting property: {}", propName);
            return getProperties().getProperty(propName);
        }

        return data;

    }

    private Properties getProperties()
    {
        if (properties == null) {
            properties = project.getProperties();
        }
        return properties;
    }
}
