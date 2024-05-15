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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.error.YAMLException;

import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthConfig;
import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.github.cafapi.docker_versions.docker.auth.AuthConfigHelper;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryRestClient;
import com.github.cafapi.docker_versions.docker.client.ImageNotFoundException;

/**
 * This is a maven plugin that replaces any Docker image release versions with the latest static version of the image in the plugin
 * configuration. This goal will not replace versions of images which use SNAPSHOT versions.
 */
@Mojo(name = "use-latest-releases", defaultPhase = LifecyclePhase.NONE)
public final class UseLatestReleasesMojo extends DockerVersionsUpdaterMojo
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UseLatestReleasesMojo.class);

    @Parameter(property = "skipUseLatestReleases", defaultValue = "false")
    private boolean skipUseLatestReleases;

    private Set<IgnoreVersion> effectiveIgnoreVersions;

    @Override
    protected boolean shouldSkip()
    {
        return skip || skipUseLatestReleases;
    }

    @Override
    protected void update(final ModifiedPomXMLEventReader pom)
        throws DockerRegistryAuthException,
               DockerRegistryException,
               ImageNotFoundException,
               IncorrectDigestException,
               XMLStreamException
    {
        LOGGER.debug("UseLatestReleasesMojo with this configuration {}", pluginConfig);
        effectiveIgnoreVersions = getIgnoreVersions();
        final List<Xpp3Dom> imagesToUpdate = new ArrayList<>();

        for (final ImageConfiguration imageConfig : imageManagement) {
            final ImageMoniker imageMoniker = new ImageMoniker(
                imageConfig.getRepository(),
                imageConfig.getTag(),
                imageConfig.getDigest());

            // Ignore intentionally dynamic versions
            final String tag = imageMoniker.getTag();
            if (tag.equals(LATEST_TAG) || tag.endsWith(SNAPSHOT_SUFFIX)) {
                continue;
            }

            final String latestTag = imageConfig.getLatestTag() != null
                ? imageConfig.getLatestTag()
                : LATEST_TAG;

            final String latestImageName = imageMoniker.getFullImageNameWithoutTag() + ":" + latestTag;

            final DockerRegistryAuthConfig authConfig = AuthConfigHelper.getRegistryAuthConfig(settings, imageMoniker.getRegistry());
            final String registrySchema = DockerRegistryRestClient.getSchema(imageMoniker.getRegistry());
            final String authToken = DockerRegistryRestClient.getAuthToken(
                imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), authConfig);

            final String latestDigest = DockerRegistryRestClient.getDigest(
                authToken, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), latestTag);

            LOGGER.debug("Got digest for {} -- {}", latestImageName, latestDigest);

            // Lookup the latest 'static' tag of the repository
            // The longest value would be the 'static' tag
            final String staticTag = getLatestStaticTag(authToken, registrySchema, imageMoniker, latestTag, latestDigest);

            final Xpp3Dom imageToUpdate
                = DockerVersionsHelper.findRepository(imageMoniker.getRepositoryFromConfigSansRegistry(), imagesConfig)
                    .orElseThrow(()
                        -> new IllegalArgumentException("Image configuration not found '" + imageMoniker.getFullImageNameWithoutTag()));

            if (!tag.equals(staticTag)) {
                // the latest image is different from the one that is currently configured
                // update the plugin configuration to reference this new image

                updateTagAndDigest(imageMoniker, imageToUpdate, staticTag, latestDigest);
                imagesToUpdate.add(imageToUpdate);
            } else {
                LOGGER.info("Plugin already references the latest image: {}:{}", imageMoniker.getFullImageNameWithoutTag(), tag);

                final String imageNameWithStaticTag = imageMoniker.getFullImageNameWithoutTag() + ":" + staticTag;

                final String staticDigest = DockerRegistryRestClient.getDigest(
                    authToken, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), staticTag);
                LOGGER.debug("Got digest for {} -- {}", imageNameWithStaticTag, staticDigest);

                if (!staticDigest.equals(latestDigest)) {
                    throw new IncorrectDigestException("Static image digest does not match latest image digest");
                }

                final String digest = imageMoniker.getDigest();

                // Check if the specified digest matches digest of latest image
                if (StringUtils.isBlank(digest) || !digest.equals(staticDigest)) {
                    // Add or update the digest
                    upsertDigest(imageMoniker, imageToUpdate, latestDigest);
                    imagesToUpdate.add(imageToUpdate);
                } else {
                    // Image config does not need any updates
                    LOGGER.debug("Image config updates not required: {}", imageMoniker.getFullImageNameWithTag());
                }
            }
        }

        if (!imagesToUpdate.isEmpty()) {
            LOGGER.debug("Images needing configuration updates : {}", imagesToUpdate);
            DockerVersionsHelper.setImageVersion(pom, imagesToUpdate, project.getModel().getProperties());
        }
    }

    private String getLatestStaticTag(
        final String authToken,
        final String registrySchema,
        final ImageMoniker imageMoniker,
        final String latestTag,
        final String digestOfLatestVersion
    ) throws DockerRegistryException
    {
        LOGGER.info("Getting latest static tag for {}...", imageMoniker.getFullImageNameWithTag());
        final List<String> tags = DockerRegistryRestClient.getTags(
            authToken, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry());

        LOGGER.debug("Tags for latest image: {}-{}", imageMoniker.getFullImageNameWithTag(), tags);

        if (tags.isEmpty()) {
            return imageMoniker.getTag();
        }

        // Filter out tags that are to be ignored
        final List<String> relevantTags = tags.stream().filter(t -> !isIgnoredVersion(t)).collect(Collectors.toList());
        LOGGER.debug("Relevant tags for latest image: {}-{}", imageMoniker.getFullImageNameWithTag(), relevantTags);

        // For the rest of the tags, fetch the digest from the manifest and compare to digest of latest version
        final List<String> tagsOfLatestVersion = getLatestVersionTagsOrderedByLength(
            authToken, registrySchema, imageMoniker, relevantTags, digestOfLatestVersion);
        LOGGER.debug("tagsOfLatestVersion {}", tagsOfLatestVersion);

        final int numberOfLatestTags = tagsOfLatestVersion.size();
        if (numberOfLatestTags == 0) {
            return imageMoniker.getTag();
        }

        // Find the longest tag the list of latest version tags
        if (numberOfLatestTags == 1) {
            return tagsOfLatestVersion.get(0);
        }

        String longestLatestStaticTag = tagsOfLatestVersion.get(numberOfLatestTags - 1);
        if (latestTag.equalsIgnoreCase(longestLatestStaticTag)) {
            longestLatestStaticTag = tagsOfLatestVersion.get(numberOfLatestTags - 2);
        }
        LOGGER.debug("Static tag for latest image: {} : {}", imageMoniker.getFullImageNameWithTag(), longestLatestStaticTag);
        return longestLatestStaticTag;
    }

    private static List<String> getLatestVersionTagsOrderedByLength(
        final String authToken,
        final String registrySchema,
        final ImageMoniker imageMoniker,
        final List<String> tags,
        final String digestOfLatestVersion
    ) throws DockerRegistryException
    {
        final List<String> latestVersionTags = new ArrayList<>();
        final int tagsCount = tags.size();
        int i = 0;
        for (final String tag : tags) {
            try {
                final String tagDigest = DockerRegistryRestClient.getDigest(
                    authToken, registrySchema, imageMoniker.getRegistry(), imageMoniker.getRepositoryWithoutRegistry(), tag);
                LOGGER.debug("Match digest of tag {} : latest, {} : {}", tag, tagDigest, digestOfLatestVersion);
                // Find all the ones that match the digest of the image with 'latest' tag
                if (tagDigest.equals(digestOfLatestVersion)) {
                    latestVersionTags.add(tag);
                }
                i++;
                if (i % 100 == 0 || i == tagsCount) {
                    LOGGER.info("Processed {} of {} tags", i, tagsCount);
                }
            } catch (final ImageNotFoundException e) {
                LOGGER.debug("Cannot find image digest for {}:{}", imageMoniker.getRepositoryWithoutRegistry(), tag, e);
            }
        }
        latestVersionTags.sort(Comparator.comparingInt(String::length));
        return latestVersionTags;
    }

    private boolean isIgnoredVersion(final String tag)
    {
        if (effectiveIgnoreVersions.isEmpty()) {
            return false;
        }

        boolean isMatch;
        for (final IgnoreVersion iVersion : effectiveIgnoreVersions) {
            isMatch = "regex".equals(iVersion.getType())
                ? Pattern.matches(iVersion.getVersion(), tag)
                : iVersion.getVersion().equals(tag);
            if (isMatch) {
                return true;
            }
        }
        return false;
    }

    private Set<IgnoreVersion> getIgnoreVersions()
    {
        final Set<IgnoreVersion> ignoreImageVersions
            = ignoreVersions == null
                ? new HashSet<>()
                : new HashSet<>(ignoreVersions);
        if (ignoreVersionsConfigPath != null) {
            try (final FileInputStream ignoreVersionsConfig = new FileInputStream(ignoreVersionsConfigPath)) {
                 final YAMLMapper yamlMapper = new YAMLMapper();
                 final Set<IgnoreVersion> ignoreVersionsFromConfigFile
                     = yamlMapper.readValue(ignoreVersionsConfig, new TypeReference<Set<IgnoreVersion>>() {});
                 ignoreImageVersions.addAll(ignoreVersionsFromConfigFile);
            } catch (final FileNotFoundException e) {
                throw new IllegalArgumentException("Ignore versions config file not found", e);
            } catch (final IOException e) {
                throw new IllegalArgumentException("Error reading ignore versions config file", e);
            } catch (final YAMLException e) {
                throw new IllegalArgumentException("Error parsing ignore versions config file", e);
            }
        }
        return ignoreImageVersions;
    }

    private static void updateTagAndDigest(
        final ImageMoniker imageMoniker,
        final Xpp3Dom imageToUpdate,
        final String latestTag,
        final String latestDigest)
    {
        updateTag(imageMoniker, imageToUpdate, latestTag);
        upsertDigest(imageMoniker, imageToUpdate, latestDigest);
    }

    private static void updateTag(final ImageMoniker imageMoniker, final Xpp3Dom imageToUpdate, final String latestTag)
    {
        LOGGER.info("Updating {} from version {} to {}",
                    imageMoniker.getFullImageNameWithoutTag(), imageToUpdate.getChild("tag").getValue(), latestTag);

        imageToUpdate.getChild("tag").setValue(latestTag);
    }

    private static void upsertDigest(final ImageMoniker imageMoniker, final Xpp3Dom imageToUpdate, final String latestDigest)
    {
        if (imageToUpdate.getChild("digest") == null) {
            // Add digest
            LOGGER.info("Setting digest for {} to {}", imageMoniker.getFullImageNameWithoutTag(), latestDigest);

            final Xpp3Dom digestParam = new Xpp3Dom("digest");
            digestParam.setValue(latestDigest);
            imageToUpdate.addChild(digestParam);
        } else {
            // Update digest
            LOGGER.info("Updating digest of {} to {}", imageMoniker.getFullImageNameWithoutTag(), latestDigest);
            imageToUpdate.getChild("digest").setValue(latestDigest);
        }
    }
}
