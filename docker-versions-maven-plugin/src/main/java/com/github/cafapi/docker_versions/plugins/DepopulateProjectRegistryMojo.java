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

import com.github.cafapi.docker_versions.docker.client.DockerRestClient;
import com.github.cafapi.docker_versions.docker.client.ImageTaggingException;
import com.github.dockerjava.api.command.InspectImageResponse;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            dockerClient = new DockerRestClient(httpConfiguration);
        }

        public void executeImpl() throws ImageTaggingException
        {
            LOGGER.debug("DepopulateProjectRegistry with this configuration {}", imageManagement);

            for (final ImageConfiguration imageConfig : imageManagement) {
                final ImageMoniker imageMoniker = new ImageMoniker(
                    imageConfig.getRepository(),
                    imageConfig.getTag(),
                    imageConfig.getDigest());

                final String targetRepository = StringUtils.isNotBlank(imageConfig.getTargetRepository())
                    ? imageConfig.getTargetRepository()
                    : imageMoniker.getRepositoryWithoutRegistry();

                final String projectDockerRegistryImageName = getProjectDockerRegister() + "/" + targetRepository;

                final String imageName = projectDockerRegistryImageName + ":" + LATEST_TAG;

                LOGGER.debug("Check if image '{}' is present...", imageName);
                final Optional<InspectImageResponse> taggedImage = dockerClient.findImage(imageName);
                if (taggedImage.isPresent()) {
                    dockerClient.untagImage(imageName);
                } else {
                    LOGGER.info("Untagging {}... unnecessary as image not found", imageName);
                }
            }
        }
    }
}
