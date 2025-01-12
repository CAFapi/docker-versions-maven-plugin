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

import com.github.cafapi.docker_versions.docker.auth.AuthConfigHelper;
import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthException;
import com.github.cafapi.docker_versions.docker.client.DockerRestClient;
import com.github.cafapi.docker_versions.docker.client.ImageNotFoundException;
import com.github.cafapi.docker_versions.docker.client.ImageTaggingException;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.AuthConfig;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a maven plugin that retags the Docker images that are used by a project, to a project specific name. The project specific name
 * can then be used in place of the actual Docker image name.
 */
@Mojo(name = "populate-project-registry")
public final class PopulateProjectRegistryMojo extends DockerVersionsMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PopulateProjectRegistryMojo.class);

    @Parameter(property = "skipPopulateProjectRegistry", defaultValue = "false")
    private boolean skipPopulateProjectRegistry;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if (skip || skipPopulateProjectRegistry) {
            LOGGER.info("Skipping populate project registry.");
            return;
        }

        try {
            new ExecutionImpl().executeImpl();
        } catch (final DockerRegistryAuthException ex) {
            throw new MojoExecutionException("Unable to find auth configuration", ex);
        } catch (final ImageNotFoundException ex) {
            throw new MojoExecutionException("Unable to retag image", ex);
        } catch (final ImagePullException ex) {
            throw new MojoExecutionException("Unable to pull and retag image", ex);
        } catch (final ImageTaggingException ex) {
            throw new MojoExecutionException("Unable to retag image", ex);
        } catch (final IncorrectDigestException ex) {
            throw new MojoExecutionException("Digest of downloaded image does not match specified digest", ex);
        } catch (final InterruptedException ex) {
            LOGGER.warn("Plugin interrupted", ex);
            Thread.currentThread().interrupt();
        }
    }

    private final class ExecutionImpl
    {
        final DockerRestClient dockerClient;

        public ExecutionImpl()
        {
            dockerClient = new DockerRestClient(httpConfiguration, dockerHost);
        }

        public void executeImpl()
            throws DockerRegistryAuthException,
                   ProjectRegistryNotSetException,
                   ImageNotFoundException,
                   ImagePullException,
                   ImageTaggingException,
                   IncorrectDigestException,
                   InterruptedException
        {
            LOGGER.debug("PopulateProjectRegistryMojo with this configuration {}", imageManagement);

            for (final ImageConfiguration imageConfig : imageManagement) {
                final ImageMoniker imageMoniker = new ImageMoniker(
                    imageConfig.getRepository(),
                    imageConfig.getTag(),
                    imageConfig.getDigest());

                // Skip pull if explicitly configured
                // Always pull image if digest is not specified
                // Avoid pull if image already exists and its digest matches specified digest, else pull image again
                final InspectImageResponse image = getImageToTag(imageMoniker, imageConfig.isSkipPull());

                final String targetRepository = StringUtils.isNotBlank(imageConfig.getTargetRepository())
                    ? imageConfig.getTargetRepository()
                    : imageMoniker.getRepositoryWithoutRegistry();

                final String projectDockerRegistryImageName = getProjectDockerRegistry() + "/" + targetRepository;

                dockerClient.tagImage(image, projectDockerRegistryImageName, LATEST_TAG);
            }
        }

        private InspectImageResponse getImageToTag(final ImageMoniker imageMoniker, final boolean skipPull)
            throws DockerRegistryAuthException,
                   ImageNotFoundException,
                   ImagePullException,
                   IncorrectDigestException,
                   InterruptedException
        {
            final String imageName = imageMoniker.getFullImageNameWithTag();
            if (skipPull) {
                LOGGER.debug("Image pull is skipped...check if image '{}' is already present...", imageName);
                final Optional<InspectImageResponse> existingImage = dockerClient.findImage(imageName);
                if (existingImage.isPresent()) {
                    return existingImage.get();
                }
                throw new ImageNotFoundException("Image pull is skipped but image is not found: " + imageName);
            }

            if (!imageMoniker.hasDigest()) {
                LOGGER.debug("Digest not specified for image '{}', pull it...", imageMoniker.getFullImageNameWithTag());
                return pullImage(imageMoniker);
            }

            LOGGER.debug("Check if image '{}' is already present...", imageName);
            final Optional<InspectImageResponse> existingImage = dockerClient.findImage(imageName);

            if (existingImage.isPresent()) {
                final InspectImageResponse image = existingImage.get();
                // Digest and image are both present, check if the digests match
                final String digest = imageMoniker.getDigest();
                if (doesDigestMatchImage(image, digest)) {
                    LOGGER.debug("Digest of existing image '{}-{}' matches {}.", image.getId(), image.getRepoDigests(), digest);
                    return image;
                }
            }

            // Image is not present or digest of existing image does not match the specified digest, so pull it again
            return pullImage(imageMoniker);
        }

        private InspectImageResponse pullImage(final ImageMoniker imageMoniker)
            throws DockerRegistryAuthException, ImagePullException, IncorrectDigestException, InterruptedException
        {
            final AuthConfig authConfig = AuthConfigHelper.getAuthConfig(settings, imageMoniker.getRegistry());

            final boolean imagePullCompleted = dockerClient.pullImage(
                imageMoniker.getFullImageNameWithoutTag(),
                imageMoniker.getTag(),
                authConfig);

            final String imageName = imageMoniker.getFullImageNameWithTag();

            if (!imagePullCompleted) {
                throw new ImagePullException("Image was not pulled: " + imageName);
            }

            LOGGER.debug("Pulled image '{}', verify that it is now present...", imageName);
            final Optional<InspectImageResponse> image = dockerClient.findImage(imageName);
            if (!image.isPresent()) {
                throw new ImagePullException("Image not found after pulling it: " + imageName);
            }

            final InspectImageResponse pulledImage = image.get();

            // Check if the digest of the image that was pulled matches the specified digest
            final String digest = imageMoniker.getDigest();
            if (StringUtils.isNotBlank(digest) && !doesDigestMatchImage(pulledImage, digest)) {
                throw new IncorrectDigestException(
                    "Digest of the pulled image '" + imageName + "' does not match specified digest '" + digest + "'");
            }
            return pulledImage;
        }

        private boolean doesDigestMatchImage(
            final InspectImageResponse image,
            final String digest)
        {
            final List<String> repoDigests = image.getRepoDigests();
            LOGGER.debug("Verifying digest '{}' for image '{}-{}'...", digest, image.getId(), repoDigests);
            return repoDigests != null
                && repoDigests.stream().anyMatch(di -> di.endsWith("@" + digest));
        }
    }
}
