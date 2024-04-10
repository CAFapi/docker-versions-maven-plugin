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
        + "(/configuration/imageManagement/image)((/repository)|(/tag)|(/digest))");

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

        final Stack<String> stack = new Stack<>();
        String path = "";

        boolean needsUpdate = false;
        boolean madeReplacement = false;
        boolean hasDigest = false;

        String repository;
        String newVersion = null;
        String newDigest = null;

        while (pom.hasNext()) {
            final XMLEvent event = pom.nextEvent();

            if (event.isStartElement()) {
                stack.push(path);
                final String elementName = event.asStartElement().getName().getLocalPart();
                path = path + "/" + elementName;

                if (IMAGE_CONFIG_MATCH_PATTERN.matcher(path).matches()) {
                    if ("repository".equals(elementName)) {
                        repository = PomHelper.evaluate(pom.getElementText().trim(), implicitProperties);

                        final Optional<Xpp3Dom> repo = findRepository(repository, imagesConfig);
                        if (repo.isEmpty()) {
                            needsUpdate = false;
                        } else {
                            LOGGER.debug("Updating repo : {}", repository);
                            needsUpdate = true;
                            final Xpp3Dom repoToUpdate = repo.get();

                            newVersion = repoToUpdate.getChild("tag").getValue();
                            newDigest = repoToUpdate.getChild("digest").getValue();
                            hasDigest = false;
                        }

                        path = stack.pop();
                    } else if (needsUpdate && "tag".equals(elementName)) {
                        pom.mark(0);
                    } else if (needsUpdate && "digest".equals(elementName)) {
                        hasDigest = true;
                        pom.mark(0);
                    }
                }
            }
            if (event.isEndElement()) {
                if (needsUpdate && IMAGE_CONFIG_MATCH_PATTERN.matcher(path).matches()) {
                    if ("tag".equals(event.asEndElement().getName().getLocalPart())) {
                        pom.mark(1);
                        if (pom.hasMark(0) && pom.hasMark(1)) {
                            pom.replaceBetween(0, 1, newVersion);
                            pom.clearMark(0);
                            pom.clearMark(1);
                            madeReplacement = true;
                        }
                    } else if ("digest".equals(event.asEndElement().getName().getLocalPart())) {
                        pom.mark(1);
                        if (pom.hasMark(0) && pom.hasMark(1)) {
                            pom.replaceBetween(0, 1, newDigest);
                            pom.clearMark(0);
                            pom.clearMark(1);
                            madeReplacement = true;
                        }
                    }
                }
                if (IMAGE_MATCH_PATTERN.matcher(path).matches()) {
                    if (needsUpdate && !hasDigest) {
                        final StringBuffer builder = new StringBuffer("    <digest>");
                        builder.append(newDigest).append("</digest>").append('\n');
                        final int endTagLocation = event.asEndElement().getLocation().getColumnNumber();
                        final String endTag = "</image>";
                        final String endImageTag = endTagLocation <= 0
                            ? endTag
                            : StringUtils.leftPad(endTag, endTagLocation + endTag.length());

                        builder.append(endImageTag);
                        pom.replace(builder.toString());
                        madeReplacement = true;
                    }
                    needsUpdate = false;
                }
                path = stack.pop();
            }
        }
        LOGGER.debug("Completed image version updates in plugin configuration. Made {}replacements.", madeReplacement ? "" : "no ");
        return madeReplacement;
    }

    public static Optional<Xpp3Dom> findRepository(final String repository, final List<Xpp3Dom> imagesConfig)
    {
        return imagesConfig.stream()
            .filter(img -> img.getChild("repository").getValue().endsWith(repository))
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
}
