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
package com.github.cafapi.docker_versions.plugins.test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;

import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.plugins.DockerVersionsHelper;

final class DockerVersionsHelperTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsHelperTest.class);

    @BeforeEach
    void init(final TestInfo testInfo) throws IOException
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());
    }

    @Test
    public void testSetImageVersion() throws XMLStreamException, URISyntaxException, IOException
    {
        final URL pomUrl = DockerVersionsHelperTest.class.getResource("testPluginPom.xml");
        final File pomFile = new File(pomUrl.toURI());
        final StringBuilder input = DockerVersionsHelper.readFile(pomFile);
        final ModifiedPomXMLEventReader pomToUpdate = DockerVersionsHelper.createPomXmlEventReader(input, pomFile.getAbsolutePath());

        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));

        final Properties properties = new Properties();
        properties.load(DockerVersionsHelperTest.class.getResourceAsStream("test.properties"));

        final boolean madeReplacement = DockerVersionsHelper.setImageVersion(pomToUpdate, imagesConfig, properties);

        LOGGER.info("Updated pom : {}", pomToUpdate.asStringBuilder());

        Assertions.assertTrue(madeReplacement, "Pom file was updated");
    }

    @Test
    public void testSetImageVersionSameRepo() throws XMLStreamException, URISyntaxException, IOException
    {
        final URL pomUrl = DockerVersionsHelperTest.class.getResource("testSameRepoPluginPom.xml");
        final File pomFile = new File(pomUrl.toURI());
        final StringBuilder input = DockerVersionsHelper.readFile(pomFile);
        final ModifiedPomXMLEventReader pomToUpdate = DockerVersionsHelper.createPomXmlEventReader(input, pomFile.getAbsolutePath());

        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-liquibase",
            "3.5",
            "3.5.0",
            "sha256:676c166c8749f81c07d0e24ec6528fd8b5f3950514d9f29e6514cad64b1c0dc3"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-flyway",
            "7.0",
            "7.0.2",
            "sha256:676c166c8749f81c07d0e24ec6528fd8b5f3950514d9f29e6514cad64b1c0dc3"));

        final Properties properties = new Properties();
        properties.load(DockerVersionsHelperTest.class.getResourceAsStream("test.properties"));

        final boolean madeReplacement = DockerVersionsHelper.setImageVersion(pomToUpdate, imagesConfig, properties);

        LOGGER.info("Updated pom : {}", pomToUpdate.asStringBuilder());

        Assertions.assertTrue(madeReplacement, "Pom file was updated");
    }

    @Test
    public void testSetImageVersionMixedRepos() throws XMLStreamException, URISyntaxException, IOException
    {
        final URL pomUrl = DockerVersionsHelperTest.class.getResource("testSameRepoPluginPom.xml");
        final File pomFile = new File(pomUrl.toURI());
        final StringBuilder input = DockerVersionsHelper.readFile(pomFile);
        final ModifiedPomXMLEventReader pomToUpdate = DockerVersionsHelper.createPomXmlEventReader(input, pomFile.getAbsolutePath());

        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-liquibase",
            "3.5",
            "3.5.0",
            "sha256:676c166c8749f81c07d0e24ec6528fd8b5f3950514d9f29e6514cad64b1c0dc3"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-flyway",
            "7.0",
            "7.0.2",
            "sha256:676c166c8749f81c07d0e24ec6528fd8b5f3950514d9f29e6514cad64b1c0dc3"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));

        final Properties properties = new Properties();
        properties.load(DockerVersionsHelperTest.class.getResourceAsStream("test.properties"));

        final boolean madeReplacement = DockerVersionsHelper.setImageVersion(pomToUpdate, imagesConfig, properties);

        LOGGER.info("Updated pom : {}", pomToUpdate.asStringBuilder());

        Assertions.assertTrue(madeReplacement, "Pom file was updated");
    }

    private static Xpp3Dom createImage(final String repository, final String tag, final String digest)
    {
        final Xpp3Dom image = new Xpp3Dom("image");

        final Xpp3Dom repoElm = new Xpp3Dom("repository");
        repoElm.setValue(repository);

        final Xpp3Dom tagElm = new Xpp3Dom("tag");
        tagElm.setValue(tag);

        final Xpp3Dom digestElm = new Xpp3Dom("digest");
        digestElm.setValue(digest);

        image.addChild(repoElm);
        image.addChild(tagElm);
        image.addChild(digestElm);

        return image;
    }

    private static Xpp3Dom createImage(
        final String repository,
        final String targetRepository,
        final String tag,
        final String latestTag,
        final String digest)
    {
        final Xpp3Dom image = new Xpp3Dom("image");

        final Xpp3Dom repoElm = new Xpp3Dom("repository");
        repoElm.setValue(repository);

        final Xpp3Dom targetRepoElm = new Xpp3Dom("targetRepository");
        targetRepoElm.setValue(targetRepository);

        final Xpp3Dom tagElm = new Xpp3Dom("tag");
        tagElm.setValue(tag);

        final Xpp3Dom latestTagElm = new Xpp3Dom("latestTag");
        latestTagElm.setValue(latestTag);

        final Xpp3Dom digestElm = new Xpp3Dom("digest");
        digestElm.setValue(digest);

        image.addChild(repoElm);
        image.addChild(targetRepoElm);
        image.addChild(tagElm);
        image.addChild(latestTagElm);
        image.addChild(digestElm);

        return image;
    }
}
