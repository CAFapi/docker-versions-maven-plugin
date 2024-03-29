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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthConfig;
import com.github.cafapi.docker_versions.docker.auth.AuthConfigHelper;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryRestClient;
import com.github.cafapi.docker_versions.docker.client.ImageNotFoundException;

/**
* This is a maven plugin that replaces any Docker image release versions with the latest static version of the image in the
* plugin configuration.
* This goal will not replace versions of images which use SNAPSHOT versions.
*/
@Mojo(name = "use-latest-releases", defaultPhase = LifecyclePhase.NONE)
public final class UseLatestReleasesMojo extends DockerVersionsUpdaterMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UseLatestReleasesMojo.class);

    @Override
    protected void update(final ModifiedPomXMLEventReader pom)
        throws DockerRegistryException,
            ImageNotFoundException,
            IncorrectDigestException,
            XMLStreamException
    {
        LOGGER.info("UseLatestReleasesMojo with this configuration {}", pluginConfig);
        final List<Xpp3Dom> imagesToUpdate = new ArrayList<>();

        for (final ImageConfiguration imageConfig : imageManagement) {
            final ImageMoniker imageMoniker = new ImageMoniker(
                imageConfig.getRepository(),
                imageConfig.getTag(),
                imageConfig.getDigest());

            if(imageMoniker.getTag().endsWith(SNAPSHOT_SUFFIX)) {
                // Ignore SNAPSHOT versions
                continue;
            }

            final String latestTag = imageConfig.getLatestTag() != null
                ? imageConfig.getLatestTag()
                : LATEST_TAG;

            final String latestImageName = imageMoniker.getFullImageNameWithoutTag() + ":" + latestTag;

            final DockerRegistryAuthConfig authConfig = AuthConfigHelper.getRegistryAuthConfig(settings, imageMoniker.getRegistry());
            final String registrySchema = DockerRegistryRestClient.getSchema(imageMoniker.getRegistry());

            final String latestDigest = DockerRegistryRestClient.getDigest(
                authConfig, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), latestTag);

            final String tag = imageMoniker.getTag();

            LOGGER.info("Got digest for {} -- {}" , latestImageName, latestDigest);

            // Lookup the latest 'static' tag of the repository
            // The longest value would be the 'static' tag
            final String staticTag = getLatestStaticTag(authConfig, registrySchema, imageMoniker, latestTag, latestDigest);

            final Xpp3Dom imageToUpdate = DockerVersionsHelper.findRepository(imageMoniker.getRepositoryWithoutRegistry(), imagesConfig)
                .orElseThrow(() ->
                    new IllegalArgumentException("Image configuration not found '" + imageMoniker.getFullImageNameWithoutTag()));

            if (!tag.equals(staticTag)) {
                // the latest image is different from the one that is currently configured
                // update the plugin configuration to reference this new image

                updateTagAndDigest(imageMoniker, imageToUpdate, staticTag, latestDigest);
                imagesToUpdate.add(imageToUpdate);
            }
            else {
                LOGGER.info("Plugin already references the latest image: {}:{}", imageMoniker.getFullImageNameWithoutTag(), tag);

                final String imageNameWithStaticTag = imageMoniker.getFullImageNameWithoutTag() + ":" + staticTag;

                final String staticDigest = DockerRegistryRestClient.getDigest(
                    authConfig, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), staticTag);
                LOGGER.info("Got digest for {} -- {}" , imageNameWithStaticTag, staticDigest);

                if (!staticDigest.equals(latestDigest)) {
                    throw new IncorrectDigestException("Static image digest does not match latest image digest");
                }

                final String digest = imageMoniker.getDigest();

                // Check if the specified digest matches digest of latest image
                if (StringUtils.isBlank(digest) || !digest.equals(staticDigest)) {
                    // Add or update the digest
                    upsertDigest(imageMoniker, imageToUpdate, latestDigest);
                    imagesToUpdate.add(imageToUpdate);
                }
                else {
                    // Image config does not need any updates
                    LOGGER.info("Image config updates not required: {}", imageMoniker.getFullImageNameWithTag());
                }
            }
        }

        if (!imagesToUpdate.isEmpty()) {
            LOGGER.info("Images needing configuration updates : {}", imagesToUpdate);
            DockerVersionsHelper.setImageVersion(pom, imagesToUpdate, project.getModel().getProperties());
        }
    }

    private String getLatestStaticTag(
        final DockerRegistryAuthConfig authConfig,
        final String registrySchema,
        final ImageMoniker imageMoniker,
        final String latestTag,
        final String digestOfLatestVersion)
        throws DockerRegistryException
    {
        final List<String> tags = DockerRegistryRestClient.getTags(
            authConfig, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry());

        LOGGER.info("Tags for latest image: {}-{}", imageMoniker.getFullImageNameWithTag(), tags);

        if (tags.isEmpty()) {
            return imageMoniker.getTag();
        }

        // Filter out tags that are to be ignored
        final List<String> relevantTags = tags.stream().filter(t -> !isIgnoredVersion(t)).collect(Collectors.toList());
        LOGGER.info("Relevant tags for latest image: {}-{}", imageMoniker.getFullImageNameWithTag(), relevantTags);

        // For the rest of the tags, fetch the digest from the manifest and compare to digest of latest version
        final List<String> tagsOfLatestVersion = getLatestVersionTags(
            authConfig, registrySchema, imageMoniker, relevantTags, digestOfLatestVersion);
        LOGGER.info("tagsOfLatestVersion {}", tagsOfLatestVersion);

        // Find the longest tag the list of latest version tags
        if (tagsOfLatestVersion.size() == 1) {
            return tagsOfLatestVersion.get(0);
        }

        String latestStaticTag = tagsOfLatestVersion.get(tagsOfLatestVersion.size() - 1);
        if (latestTag.equalsIgnoreCase(latestStaticTag)) {
            try {
                latestStaticTag = tagsOfLatestVersion.get(tagsOfLatestVersion.size() - 2);
            } catch (final IndexOutOfBoundsException e) {
                latestStaticTag = imageMoniker.getTag();
            }
        }
        LOGGER.info("Static tag for latest image: {} : {}", imageMoniker.getFullImageNameWithTag(), latestStaticTag);
        return latestStaticTag;
    }

    private static List<String> getLatestVersionTags(
        final DockerRegistryAuthConfig authConfig,
        final String registrySchema,
        final ImageMoniker imageMoniker,
        final List<String> relevantTags,
        final String digestOfLatestVersion)
        throws DockerRegistryException
    {
        final List<String> latestVersionTags = new ArrayList<>();
        for (final String rTag : relevantTags) {
            try {
                final String tagDigest = DockerRegistryRestClient.getDigest(
                    authConfig, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), rTag);
                LOGGER.info("Match digest of tag {} : latest, {} : {}", rTag, tagDigest, digestOfLatestVersion);
                // Find all the ones that match the digest of the image with 'latest' tag
                if (tagDigest.equals(digestOfLatestVersion)) {
                    latestVersionTags.add(rTag);
                }
            } catch(final ImageNotFoundException e) {
                LOGGER.trace("Cannot find image digest for {}:{}", imageMoniker.getRepositoryWithoutRegistry(), rTag, e);
            }
        }
        latestVersionTags.sort(Comparator.comparingInt(String::length));
        return latestVersionTags;
    }

    private boolean isIgnoredVersion(final String tag)
    {
        boolean isMatch;
        for (final IgnoreVersion iVersion : ignoreVersions) {
            isMatch = "regex".equals(iVersion.getType())
                ? Pattern.matches(iVersion.getVersion(), tag)
                : iVersion.getVersion().equals(tag);
            if (isMatch) {
                return true;
            }
        }
        return false;
    }

    private static void updateTagAndDigest(
        final ImageMoniker imageMoniker,
        final Xpp3Dom imageToUpdate,
        final String latestTag,
        final String latestDigest)
    {
        LOGGER.info("Updating image: {} from version {} to {}",
            imageMoniker.getFullImageNameWithoutTag(), imageMoniker.getTag(), latestTag);

        updateTag(imageMoniker, imageToUpdate, latestTag);
        upsertDigest(imageMoniker, imageToUpdate, latestDigest);
    }

    private static void updateTag(final ImageMoniker imageMoniker, final Xpp3Dom imageToUpdate, final String latestTag)
    {
        LOGGER.info("Updating tag for {} from {} to {}",
            imageMoniker.getFullImageNameWithoutTag(), imageToUpdate.getChild("tag").getValue(), latestTag);

        imageToUpdate.getChild("tag").setValue(latestTag);
    }

    private static void upsertDigest(final ImageMoniker imageMoniker, final Xpp3Dom imageToUpdate, final String latestDigest)
    {
        if (imageToUpdate.getChild("digest") == null) {
            // Add digest
            LOGGER.info("Adding digest for {}", imageMoniker.getFullImageNameWithoutTag());

            final Xpp3Dom digestParam = new Xpp3Dom("digest");
            digestParam.setValue(latestDigest);
            imageToUpdate.addChild(digestParam);
        }
        else {
            // Update digest
            imageToUpdate.getChild("digest").setValue(latestDigest);
        }
    }
}
