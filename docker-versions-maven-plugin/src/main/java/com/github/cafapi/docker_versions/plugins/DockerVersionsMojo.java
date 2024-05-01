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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import com.github.cafapi.docker_versions.docker.client.DockerRestClient;

abstract class DockerVersionsMojo extends AbstractMojo
{
    protected static final String PROJECT_DOCKER_REGISTRY = "projectDockerRegistry";
    protected static final String LATEST_TAG = "latest";

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${settings}", readonly = true)
    protected Settings settings;

    @Parameter(required = true)
    protected List<ImageConfiguration> imageManagement;

    @Parameter
    protected HttpConfiguration httpConfiguration;

    @Parameter(property = "docker.versions.skip", defaultValue = "false")
    protected boolean skip;

    protected final DockerRestClient dockerClient;

    protected DockerVersionsMojo()
    {
        dockerClient = new DockerRestClient(httpConfiguration);
    }

    protected String getProjectDockerRegister()
    {
        return project.getProperties().getProperty(
            PROJECT_DOCKER_REGISTRY,
            project.getArtifactId() + "-" + project.getVersion() + ".project-registries.local");
    }

    protected int getPopulateRegistryCount() throws IOException
    {
        return Files.lines(getCountFilePath())
            .map(Integer::parseInt)
            .findFirst()
            .orElseThrow();
    }

    protected void incrementPopulateRegistryCount() throws IOException
    {
        if (Files.exists(getCountFilePath())) {
            final int count = getPopulateRegistryCount();
            writePopulateRegistryCount(count + 1);
        } else {
            writePopulateRegistryCount(1);
        }
    }

    protected void writePopulateRegistryCount(final int count) throws IOException
    {
        Files.write(getCountFilePath(), ("" + count).getBytes(StandardCharsets.UTF_8));
    }

    protected void removePopulateGoalCounter() throws IOException
    {
        Files.deleteIfExists(getCountFilePath());
        // TODO: remove the .m2\repository\.cache\docker-version-plugin folder if it has no more files?
    }

    private String getCacheId()
    {
        final String dockerHostId = dockerClient.getDockerSystemId();
        return dockerHostId + "-" + getProjectDockerRegister();
    }

    private File getCacheDirectory() throws IOException
    {
        // Create a folder for the plugin to create a file to store counter_per_docker_host
        // .m2\repository\.cache\docker-version-plugin\<docker_host_id>-<projectRegistrydir>
        final File cacheFolder = new File(
            session.getRepositorySession().getLocalRepository().getBasedir(),
            ".cache/docker-versions-plugin");
        if (!Files.exists(Paths.get(cacheFolder.getAbsolutePath()))) {
            Files.createDirectory(Paths.get(cacheFolder.getAbsolutePath()));
        }
        return cacheFolder;
    }

    private Path getCountFilePath() throws IOException
    {
        return Paths.get(getCacheDirectory().getAbsolutePath(), getCacheId());
    }
}
