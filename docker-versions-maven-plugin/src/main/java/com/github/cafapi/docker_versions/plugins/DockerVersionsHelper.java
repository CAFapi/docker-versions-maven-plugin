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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.input.XmlStreamReader;
import org.apache.commons.io.output.XmlStreamWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.stax2.XMLInputFactory2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for reading and updating pom files.
 */
public final class DockerVersionsHelper
{
    protected static final String DOCKER_VERSION_PLUGIN_NAME = "com.github.cafapi.plugins.docker.versions:docker-versions-maven-plugin";

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsHelper.class);

    private static final Pattern IMAGE_MATCH_PATTERN = Pattern.compile(
        "/project"
        + "(/profiles/profile)?"
        + "((/build(/pluginManagement)?/plugins/plugin))?"
        + "(/configuration/imageManagement/image)");

    private static final Pattern IMAGE_CONFIG_MATCH_PATTERN = Pattern.compile(
        "/project"
        + "(/profiles/profile)?"
        + "((/build(/pluginManagement)?/plugins/plugin))?"
        + "(/configuration/imageManagement/image)((/repository)|(/targetRepository)|(/tag)|(/digest))");

    private DockerVersionsHelper()
    {
    }

    public static boolean setImageVersion(
        final ModifiedPomXMLEventReader pom,
        final List<Xpp3Dom> imagesConfig,
        final Properties properties)
        throws XMLStreamException
    {
        LOGGER.debug("Set image version in plugin configuration...");
        final Map<String, String> implicitProperties = new HashMap<>();

        properties.entrySet().forEach(entry -> {
            implicitProperties.put((String) entry.getKey(), (String) entry.getValue());
        });

        // Update tags
        final boolean updatedTags = setElement(pom, imagesConfig, implicitProperties, "tag");

        // Update digests
        pom.rewind();

        final boolean updatedDigests = setElement(pom, imagesConfig, implicitProperties, "digest");

        final boolean madeReplacement = updatedTags || updatedDigests;
        LOGGER.debug("Completed image version updates in plugin configuration. Made {}replacements.", madeReplacement ? "" : "no ");
        return madeReplacement;
    }

    private static boolean setElement(
        final ModifiedPomXMLEventReader pom,
        final List<Xpp3Dom> imagesConfig,
        final Map<String, String> properties,
        final String element)
        throws XMLStreamException
    {
        final Stack<String> stack = new Stack<>();
        String path = "";

        String repository = null;
        String targetRepository = null;

        boolean madeReplacement = false;

        while (pom.hasNext()) {
            final XMLEvent event = pom.nextEvent();

            if (event.isStartElement()) {
                stack.push(path);
                final String elementName = event.asStartElement().getName().getLocalPart();
                path = path + "/" + elementName;

                if (IMAGE_CONFIG_MATCH_PATTERN.matcher(path).matches()) {
                    if ("repository".equals(elementName)) {
                        repository = PomHelper.evaluate(pom.getElementText().trim(), properties);
                        path = stack.pop();
                    } else if ("targetRepository".equals(elementName)) {
                        targetRepository = pom.getElementText().trim();
                        path = stack.pop();
                    } else if (element.equals(elementName)) {
                        pom.mark(0);
                    }
                }
            }
            if (event.isEndElement()) {
                if (IMAGE_CONFIG_MATCH_PATTERN.matcher(path).matches()) {
                    if (element.equals(event.asEndElement().getName().getLocalPart())) {
                        pom.mark(1);
                        targetRepository = null;
                    }
                }
                if (IMAGE_MATCH_PATTERN.matcher(path).matches()) {
                    // Update the digest if necessary
                    final Optional<Xpp3Dom> repo = findRepository(repository, targetRepository, imagesConfig);
                    if (repo.isPresent()) {
                        final Xpp3Dom repoToUpdate = repo.get();

                        final String newValue = repoToUpdate.getChild(element).getValue();

                        if (pom.hasMark(0) && pom.hasMark(1)) {
                            LOGGER.debug("Updating {} for repo : {}", element, repository);
                            pom.replaceBetween(0, 1, newValue);
                            pom.clearMark(0);
                            pom.clearMark(1);
                        } else if ("digest".equals(element)) {
                            LOGGER.debug("Setting digest for repo : {}", repository);
                            final StringBuffer builder = new StringBuffer("    <digest>");
                            builder.append(newValue).append("</digest>").append('\n');
                            final int endTagLocation = event.asEndElement().getLocation().getColumnNumber();
                            final String endTag = "</image>";
                            final String endImageTag = endTagLocation <= 0
                                ? endTag
                                : StringUtils.leftPad(endTag, endTagLocation + endTag.length());

                            builder.append(endImageTag);
                            pom.replace(builder.toString());
                        }
                        madeReplacement = true;
                    }
                }
                path = stack.pop();
            }
        }
        return madeReplacement;
    }

    public static Optional<Xpp3Dom> findRepository(
        final String repository,
        final String targetRepository,
        final List<Xpp3Dom> imagesConfig)
    {
        LOGGER.debug("Finding config with repository {} and targetRepository {}...", repository, targetRepository);
        if (targetRepository == null) {
            return imagesConfig.stream()
                .filter(img -> img.getChild("repository").getValue().endsWith(repository))
                .findFirst();
        }
        return imagesConfig.stream()
            .filter(img -> img.getChild("targetRepository") != null
                && img.getChild("targetRepository").getValue().equals(targetRepository)
                && img.getChild("repository").getValue().endsWith(repository))
            .findFirst();
    }

    public static ModifiedPomXMLEventReader createPomXmlEventReader(final StringBuilder input, final String path)
        throws XMLStreamException
    {
        try {
            final XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
            inputFactory.setProperty(XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE);
            return new ModifiedPomXMLEventReader(input, inputFactory, path);
        } catch (final XMLStreamException e) {
            LOGGER.error("Error parsing pom file", e);
            throw e;
        }
    }

    public static StringBuilder readFile(final File inFile) throws IOException
    {
        LOGGER.debug("Reading pom : {}", inFile);
        try (final Reader reader = XmlStreamReader.builder().setFile(inFile).setCharset(StandardCharsets.UTF_8).get()) {
            return new StringBuilder(IOUtil.toString(reader));
        }
    }

    public static void writeFile(final File outFile, final StringBuilder input) throws IOException
    {
        LOGGER.debug("Writing updated pom to: {}", outFile);
        try (final Writer writer = XmlStreamWriter.builder().setFile(outFile).setCharset(StandardCharsets.UTF_8).get()) {
            IOUtil.copy(input.toString(), writer);
        }
    }

    public static Plugin getPlugin(final MavenProject project)
    {
        // Look in build/plugins
        return project.getPlugin(DOCKER_VERSION_PLUGIN_NAME);
    }

    public static Plugin getPluginWithImageConfig(final MavenProject project)
    {
        final Plugin plugin = getPluginWithImageConfig(project.getPluginManagement());
        if (plugin != null) {
            return plugin;
        }

        // Look in build/plugins
        return getPluginWithImageConfig(project.getPlugin(DOCKER_VERSION_PLUGIN_NAME));
    }

    public static Plugin getPluginWithImageConfig(final PluginManagement pluginManagement)
    {
        if (pluginManagement == null) {
            return null;
        }

        final Plugin plugin = pluginManagement.getPluginsAsMap().get(DOCKER_VERSION_PLUGIN_NAME);

        return getPluginWithImageConfig(plugin);
    }

    public static Plugin getPluginWithImageConfig(final Plugin plugin)
    {
        if (plugin == null) {
            return null;
        }
        final Xpp3Dom pluginConfig = getPluginConfig(plugin);
        if (pluginConfig == null) {
            return null;
        }
        final List<Xpp3Dom> imagesConfig = getImagesConfig(pluginConfig);

        if (imagesConfig.isEmpty()) {
            return null;
        }
        return plugin;
    }

    public static Xpp3Dom getPluginConfig(final Plugin plugin)
    {
        final Object configuration = plugin.getConfiguration();
        if (configuration == null) {
            return null;
        }

        return new Xpp3Dom((Xpp3Dom) configuration);
    }

    public static List<Xpp3Dom> getImagesConfig(final Xpp3Dom config)
    {
        if (config == null) {
            return Collections.emptyList();
        }

        final Xpp3Dom configImageManagement = config.getChild("imageManagement");
        if (configImageManagement == null) {
            throw new IllegalArgumentException("'imageManagement' is not set in plugin configuration");
        }

        final Xpp3Dom[] images = configImageManagement.getChildren("image");
        return Arrays.asList(images);
    }
}
