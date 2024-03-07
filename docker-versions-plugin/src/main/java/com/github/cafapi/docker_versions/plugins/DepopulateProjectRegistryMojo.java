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

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.docker.client.DockerRestClient;
import com.github.cafapi.docker_versions.docker.client.ImageTaggingException;
import com.github.dockerjava.api.model.Image;

/**
* This is a maven plugin that untags the Docker images that were tagged with a project specific name.
*/
@Mojo(name = "depopulate-project-registry")
public final class DepopulateProjectRegistryMojo extends DockerVersionsMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DepopulateProjectRegistryMojo.class);

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try {
            new ExecutionImpl().executeImpl();
        } catch (final ImageTaggingException ex) {
            throw new MojoExecutionException("Unable to untag image", ex);
        }
    }

    private final class ExecutionImpl
    {
        final DockerRestClient dockerClient;

        public ExecutionImpl()
        {
            dockerClient = new DockerRestClient();
        }

        public void executeImpl() throws ImageTaggingException
        {
            LOGGER.info("DepopulateProjectRegistry with this configuration {}", imageManagement);

            for (final ImageConfiguration imageConfig : imageManagement) {
                final String repository = getPropertyOrValue(imageConfig.getRepository());

                if (StringUtils.isBlank(repository)) {
                    throw new IllegalArgumentException("Repository not specified for image " + repository);
                }

                final String[] repositoryInfo = repository.split("/", 2);
                if (repositoryInfo.length != 2) {
                    throw new IllegalArgumentException("Unable to get registry information for " + repository);
                }

                final String name = getPropertyOrValue(repositoryInfo[1]);
                final String targetRepository = getPropertyOrValue(imageConfig.getTargetRepository());

                final String projectDockerRegistryImageName
                    = getProjectDockerRegister()
                    + "/"
                    + (StringUtils.isNotBlank(targetRepository)
                            ? targetRepository
                            : name);

                final String imageName = projectDockerRegistryImageName + ":" + LATEST_TAG;

                LOGGER.debug("Check if image '{}' is present...", imageName);
                final Optional<Image> taggedImage = dockerClient.findImage(imageName);
                if (taggedImage.isPresent()) {
                    dockerClient.untagImage(imageName);
                }
                else {
                    LOGGER.warn("Image '{}' was not found", imageName);
                }
            }
        }
    }
}
