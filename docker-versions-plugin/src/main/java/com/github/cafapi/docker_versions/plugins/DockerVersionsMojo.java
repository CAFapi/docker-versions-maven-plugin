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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DockerVersionsMojo extends AbstractMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsMojo.class);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.+?)\\}");
    protected static final String PROJECT_DOCKER_REGISTRY = "projectDockerRegistry";

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
        LOGGER.info("Finding getPropertyOrValue: {}", data);

        LOGGER.debug("Project properties: {}", getProperties().entrySet());

        if (data != null && data.startsWith("${")) {
            final String propName = data.substring(2, data.length() - 1);
            LOGGER.info("Getting property: {}", propName);
            return getProperties().getProperty(propName);
        }

        return data;

    }

    public String getInterpolatedValue(final String value) {
        final String strippedValue
                = value.startsWith("${")
                ? value.substring(2, value.length() - 1)
                : value;
        String valueString = getProperties().getProperty(strippedValue);
        while (valueString.contains("${")) {
            final Matcher matcher = PROPERTY_PATTERN.matcher(valueString);
            if (matcher.find()) {
                final String key = matcher.group(1);
                final String nestedValue = getProperties().getProperty(key);
                final String nestedPlaceholder = "\\$\\{" + key + "\\}";
                valueString = valueString.replaceAll(nestedPlaceholder, nestedValue);
                if(nestedValue.contains("S{")) {
                    getInterpolatedValue(valueString);
                }
            }
        }
        return valueString;
    }

    protected Properties getProperties()
    {
        if (properties == null) {
            properties = project.getProperties();
        }
        return properties;
    }
}
