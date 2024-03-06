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

            for (final ImageConfiguration imageConfig : imageManagement) {
                final String repository = getPropertyOrValue(imageConfig.getRepository());

                if (StringUtils.isBlank(repository)) {
                    throw new IllegalArgumentException("Repository not specified for image " + repository);
                }

                final String[] repositoryInfo = repository.split("/", 2);
                if (repositoryInfo.length != 2) {
                    throw new IllegalArgumentException("Unable to get registry information for " + repository);
                }

                final String registry = getPropertyOrValue(repositoryInfo[0]);
                final String name = getPropertyOrValue(repositoryInfo[1]);
                final String digest = getPropertyOrValue(imageConfig.getDigest());
                final String tag = getPropertyOrValue(imageConfig.getTag());

                if (StringUtils.isBlank(tag)) {
                    throw new IllegalArgumentException("Tag not specified for image " + repository);
                }

                final String imageName = registry + "/" + name + ":" + tag;

                // Always pull image if digest is not specified
                // Avoid pull if image already exists and its digest matches specified digest, else pull image again
                final Image image = getImageToTag(imageName, registry, name, tag, digest);

                final String projectDockerRegistryImageName
                    = getProjectDockerRegister()
                    + "/"
                    + (StringUtils.isNotBlank(imageConfig.getTargetRepository())
                            ? imageConfig.getTargetRepository()
                            : name);

                final String imageId = image.getId();

                LOGGER.info("Tagging image {} to '{}'", imageId, projectDockerRegistryImageName);

                dockerClient.tagImage(imageId, projectDockerRegistryImageName, PROJECT_DOCKER_REGISTRY_TAG);
            }
        }

        private Image getImageToTag(
            final String imageName,
            final String registry,
            final String name,
            final String tag,
            final String digest) throws ImagePullException, IncorrectDigestException
        {
            if (StringUtils.isBlank(digest)) {
                LOGGER.info("Digest not specified for image '{}', pull it...", imageName);
                return pullImage(imageName, registry, name, tag, digest);
            }

            LOGGER.debug("Check if image '{}' is already there...", imageName);
            final Optional<Image> existingImage = dockerClient.findImage(imageName);

            if (existingImage.isPresent()) {
                final Image image = existingImage.get();
                // Digest and image are both present, check if the digests match
                if (verifyDigest(image, digest)) {
                    LOGGER.info("Digest of existing image '{}-{}' matches {}.", image.getId(), image.getRepoDigests(), digest);
                    return image;
                }
            }

            // Image is not present or digest of existing image does not match the specified digest, so pull it again
            return pullImage(imageName, registry, name, tag, digest);
        }

        private Image pullImage(
            final String imageName,
            final String registry,
            final String name,
            final String tag,
            final String digest)
            throws ImagePullException, IncorrectDigestException
        {
            try {
                final boolean imagePullCompleted = dockerClient.pullImage(registry + "/" + name, tag);
                if (imagePullCompleted) {
                    LOGGER.info("Pulled image '{}', verify that it is present...", imageName);
                    final Optional<Image> image = dockerClient.findImage(imageName);
                    if (image.isEmpty()) {
                        throw new ImagePullException("Image not found after pulling it " + imageName);
                    }

                    final Image pulledImage = image.get();
                    // Check if the digest of the image that was pulled matches the specified digest
                    if (StringUtils.isNotBlank(digest)) {
                        if (!verifyDigest(pulledImage, digest)) {
                            throw new IncorrectDigestException(
                                "Digest of the pulled image '" + imageName + "' does not match specified digest '" + digest + "'");
                        }
                        return pulledImage;
                    }
                    return pulledImage;
                }
                throw new ImagePullException("Image was not pulled: " + imageName);
            } catch (final InterruptedException e) {
                throw new ImagePullException("Image pull was interrupted " + imageName, e);
            }
        }

        private boolean verifyDigest(
            final Image image,
            final String digest)
        {
            LOGGER.debug("Verifying digest '{}' for image '{}-{}'...", digest, image.getId(), image.getRepoDigests());
            return image.getRepoDigests() == null || Arrays.asList(image.getRepoDigests()).stream().anyMatch(di -> di.endsWith(digest));
        }
    }
}
