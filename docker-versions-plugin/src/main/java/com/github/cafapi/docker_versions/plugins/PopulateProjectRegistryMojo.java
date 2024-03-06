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

import java.util.Arrays;
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
* This is a maven plugin that retags the Docker images that are used by a project, to a project specific name.
* The project specific name can then be used in place of the actual Docker image name.
*/
@Mojo(name = "populate-project-registry")
public final class PopulateProjectRegistryMojo extends DockerVersionsMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PopulateProjectRegistryMojo.class);

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try {
            new ExecutionImpl().executeImpl();
        } catch (final ImagePullException ex) {
            throw new MojoExecutionException("Unable to pull and retag image", ex);
        } catch (final ImageTaggingException ex) {
            throw new MojoExecutionException("Unable to retag image", ex);
        } catch (final IncorrectDigestException ex) {
            throw new MojoExecutionException("Digest of downloaded image does not match specified digest", ex);
        }
    }

    private final class ExecutionImpl
    {
        final DockerRestClient dockerClient;

        public ExecutionImpl()
        {
            dockerClient = new DockerRestClient();
        }

        public void executeImpl() throws ImagePullException, ImageTaggingException, IncorrectDigestException
        {
            LOGGER.info("PopulateProjectRegistryMojo with this configuration {}", imageManagement);

            // Pull the images if necessary, check if image is already on docker host?
            for (final ImageConfiguration imageConfig : imageManagement) {
                final String repository = imageConfig.getRepository(); // TODO: What if this is a ${}
                final String[] repositoryInfo = repository.split("/", 2);
                if (repositoryInfo.length != 2) {
                    throw new IllegalArgumentException("Unable to get registry information for " + repository);
                }

                //final String registry = getInterpolatedValue(repositoryInfo[0]);
                final String registry = getPropertyOrValue(repositoryInfo[0]);
                final String name = getPropertyOrValue(repositoryInfo[1]);
                final String digest = getPropertyOrValue(imageConfig.getDigest());
                final String tag = getPropertyOrValue(imageConfig.getTag()); // throw error

                if(StringUtils.isBlank(tag)) {
                    throw new IllegalArgumentException("Tag not specified for image " + repository);
                }

                final String imageName = registry + "/" + name + ":" + tag;
                // Always pull if digest is not specified
                // Avoid pull if image already exists and its digest matches specified digest, else pull image again

                LOGGER.info("Check if image '{}' is already there...", imageName);
                final Optional<Image> image = dockerClient.findImage(imageName);

                if (StringUtils.isBlank(digest) || image.isEmpty()) {
                    LOGGER.info("Image '{}' not found, pull it...", imageName);
                    pullImage(registry, name, tag, digest);
                }
                else {
                    // Digest and image are both present, check if the digests match
                    if (!verifyDigest(image.get(), digest)) {
                        // Digest of existing image does not match the specified digest, so pull it again
                        pullImage(registry, name, tag, digest);
                    }
                }

                final String projectDockerRegistryImageName
                    = getProjectDockerRegister()
                    + "/"
                    + (StringUtils.isNotBlank(imageConfig.getTargetRepository())
                            ? imageConfig.getTargetRepository()
                            : name);

                final String imageId = image.get().getId();

                LOGGER.info("Tagging image {} to '{}'", imageId, projectDockerRegistryImageName);

                dockerClient.tagImage(imageId, projectDockerRegistryImageName, "latest");
            }
        }

        private void pullImage(
            final String registry,
            final String name,
            final String tag,
            final String digest)
            throws ImagePullException, IncorrectDigestException
        {
            try {
                final boolean imagePullCompleted = dockerClient.pullImage(registry + "/" + name, tag);
                if (imagePullCompleted) {
                    LOGGER.info("Check if image '{}/{}:{}' that was just pulled is present...",  registry, name, tag);
                    final Optional<Image> image = dockerClient.findImage(registry + "/" + name + ":" + tag);
                    if (image.isEmpty()) {
                        throw new ImagePullException("Image not found after pulling it " + registry + "/" + name + ":" + tag);
                    }
                    // Check the digest of the image that was pulled
                    if (StringUtils.isNotBlank(digest)) {
                        if (!verifyDigest(image.get(), digest)) {
                            throw new IncorrectDigestException(
                                    "Digest of the pulled image does not match specified digest '" + digest + "'");
                        }
                    }
                }
                throw new ImagePullException("Image was not pulled: " + registry + "/" + name + ":" + tag);
            } catch (final InterruptedException e) {
                throw new ImagePullException("Image pull was interrupted " + registry + "/" + name + ":" + tag, e);
            }
        }

        private boolean verifyDigest(
            final Image image,
            final String digest)
        {
            final String[] existingImageDigests = image.getRepoDigests();
            return existingImageDigests != null && Arrays.asList(existingImageDigests).contains(digest);
        }

    }

}
