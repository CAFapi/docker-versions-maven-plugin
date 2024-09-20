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
        // No images with "targetRepository"
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testPluginPom.xml");

        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));

        verifyPomUpdate(pomToUpdate, imagesConfig);
    }

    @Test
    public void testSetImageVersionSameRepo() throws XMLStreamException, URISyntaxException, IOException
    {
        // Two images with same repo and different "targetRepository"
        // both images are at the end of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSameRepoPluginPom.xml");

        // Updating only images with "targetRepository"
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

        verifyPomUpdate(pomToUpdate, imagesConfig);
    }

    @Test
    public void testSetImageVersionMixedRepos() throws XMLStreamException, URISyntaxException, IOException
    {
        // Two images with same repo and different "targetRepository",
        // both images are at the end of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSameRepoPluginPom.xml");
        // Updating images with and without "targetRepository"
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionCheckOrder() throws XMLStreamException, URISyntaxException, IOException
    {
        // Two images with same repo and different "targetRepository",
        // both images are at the end of the list
        // Order of the child elements of <image> is different in the 2 images with "targetRepository"
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testCheckOrderPluginPom.xml");
        // Updating images with and without "targetRepository"
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionNonTargetReop() throws XMLStreamException, URISyntaxException, IOException
    {
        // Two images with same repo and different "targetRepository"
        // both images are at the end of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSameRepoPluginPom.xml");

        // Updating only images without "targetRepository"
        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));

        verifyPomUpdate(pomToUpdate, imagesConfig);
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoFirst() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" first in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoFirst.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateOneWithTargetRepo());
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoMiddle() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" not first or last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateOneWithTargetRepo());
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoLast() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateOneWithTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirst() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" all at the beginning of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirst.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoMiddle() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" all not first or last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstAndLast() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstAndMiddle() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first and middle of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstAndMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoMiddleAndLast() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" middle and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoMiddleAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstMiddleAndLast() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first, middle and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstMiddleAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoFirstNoTargetRepoUpdate() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" first in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoFirst.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoMiddleNoTargetRepoUpdate() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" not first or last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateOneWithTargetRepo());
    }

    @Test
    public void testSetImageVersionWithOneTargetRepoLastNoTargetRepoUpdate() throws XMLStreamException, URISyntaxException, IOException
    {
        // One image with "targetRepository" last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithOneTargetRepoLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstNoTargetRepoUpdate() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" all at the beginning of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirst.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdate());
    }

    @Test
    public void testSetImageVersionWithTargetRepoMiddleNoTargetRepoUpdate() throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" all not first or last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstAndLastNoTargetRepoUpdate()
        throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstAndMiddleNoTargetRepoUpdate()
        throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first and middle of the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstAndMiddle.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoMiddleAndLastNoTargetRepoUpdate()
        throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" middle and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoMiddleAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    @Test
    public void testSetImageVersionWithTargetRepoFirstMiddleAndLastNoTargetRepoUpdate()
        throws XMLStreamException, URISyntaxException, IOException
    {
        // Multiple images with "targetRepository" first, middle and last in the list
        final ModifiedPomXMLEventReader pomToUpdate = getPomToUpdate("testSetImageVersionWithTargetRepoFirstMiddleAndLast.xml");
        verifyPomUpdate(pomToUpdate, getTestImagesToUpdateNoTargetRepo());
    }

    private static ModifiedPomXMLEventReader getPomToUpdate(final String fileName)
        throws URISyntaxException, IOException, XMLStreamException
    {
        final URL pomUrl = DockerVersionsHelperTest.class.getResource(fileName);
        final File pomFile = new File(pomUrl.toURI());
        final StringBuilder input = DockerVersionsHelper.readFile(pomFile);
        return DockerVersionsHelper.createPomXmlEventReader(input, pomFile.getAbsolutePath());
    }

    private static void verifyPomUpdate(final ModifiedPomXMLEventReader pomToUpdate, final List<Xpp3Dom> imagesConfig)
        throws IOException, XMLStreamException
    {
        final Properties properties = new Properties();
        properties.load(DockerVersionsHelperTest.class.getResourceAsStream("test.properties"));

        final boolean madeReplacement = DockerVersionsHelper.setImageVersion(pomToUpdate, imagesConfig, properties);

        LOGGER.info("Updated pom : {}", pomToUpdate.asStringBuilder());

        Assertions.assertTrue(madeReplacement, "Pom file was updated");
    }

    private static List<Xpp3Dom> getTestImagesToUpdate()
    {
        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-liquibase",
            "3.5",
            "3.5.0",
            "sha256:5323cd5945f90795e6448480b8cc622a9472b76f93c0eb97510ca15058e7b337"));
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
        return imagesConfig;
    }

    private static List<Xpp3Dom> getTestImagesToUpdateOneWithTargetRepo()
    {
        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/jobservice/job-service-postgres",
            "jobservice/job-service-postgres-liquibase",
            "3.5",
            "3.5.0",
            "sha256:5323cd5945f90795e6448480b8cc622a9472b76f93c0eb97510ca15058e7b337"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));
        return imagesConfig;
    }

    private static List<Xpp3Dom> getTestImagesToUpdateNoTargetRepo()
    {
        final List<Xpp3Dom> imagesConfig = new ArrayList<>();
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre17",
            "1.5.0",
            "sha256:6308e00f71d9c3fe6b5181aafe08abe72824301fd917887b8b644436f0de9740"));
        imagesConfig.add(createImage(
            "dockerhub-public.artifactory.acme.net/cafapi/opensuse-jre8",
            "3.10.0",
            "sha256:7fb9a3aecb3e8112e61569f5510a602b9a18a5712c5e90497f77feaedec2c66c"));
        return imagesConfig;
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
