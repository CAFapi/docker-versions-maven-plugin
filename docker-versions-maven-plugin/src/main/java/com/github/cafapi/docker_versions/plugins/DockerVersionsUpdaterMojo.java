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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthException;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;
import com.github.cafapi.docker_versions.docker.client.ImageNotFoundException;

abstract class DockerVersionsUpdaterMojo extends DockerVersionsMojo
{
    protected static final String DOCKER_VERSION_PLUGIN_NAME = "com.github.cafapi.plugins.docker.versions:docker-versions-maven-plugin";
    protected static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsUpdaterMojo.class);

    @Component
    protected ProjectBuilder projectBuilder;

    @Parameter
    protected Set<IgnoreVersion> ignoreVersions;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    protected Plugin dockerVersionsPlugin;
    protected Xpp3Dom pluginConfig;
    protected List<Xpp3Dom> imagesConfig;
    protected MavenProject projectToUpdate;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            dockerVersionsPlugin = getPlugin();
            pluginConfig = getPluginConfig(dockerVersionsPlugin);
            imagesConfig = getImagesConfig(pluginConfig);

            final File outFile = projectToUpdate.getFile();

            process(outFile);
        } catch (final DockerRegistryAuthException
            | DockerRegistryException
            | ImageNotFoundException
            | IncorrectDigestException
            | IOException
            | XMLStreamException e) {
            throw new MojoExecutionException("Error updating image versions", e);
        }
    }

    protected void process(final File outFile)
        throws DockerRegistryAuthException,
            DockerRegistryException,
            ImageNotFoundException,
            IncorrectDigestException,
            IOException,
            XMLStreamException
    {
        final StringBuilder input = DockerVersionsHelper.readFile(outFile);
        final ModifiedPomXMLEventReader pomToUpdate = DockerVersionsHelper.createPomXmlEventReader(input, outFile.getAbsolutePath());

        update(pomToUpdate);

        if (pomToUpdate.isModified()) {
            DockerVersionsHelper.writeFile(outFile, input);
            LOGGER.info("Pom has been updated.");
        }
        else {
            LOGGER.info("Pom is unmodified.");
        }
    }

    protected abstract void update(final ModifiedPomXMLEventReader pom)
        throws DockerRegistryAuthException,
            DockerRegistryException,
            ImageNotFoundException,
            IncorrectDigestException,
            XMLStreamException;

    private Plugin getPlugin()
    {
        // Look for imageConfiguration in aggregator project's pluginManagement
        final MavenProject rootProject = PomHelper.getLocalRoot(projectBuilder, session, getLog());

        projectToUpdate = rootProject;
        Plugin plugin = getPlugin(rootProject);

        if (plugin != null) {
            LOGGER.debug("Found plugin in aggregator project");
            return plugin;
        }

        LOGGER.debug("Plugin not found in aggregator project, look in the project");

        projectToUpdate = project;
        plugin = getPlugin(project);

        if (plugin != null) {
            LOGGER.debug("Found plugin in project {}", project.getArtifactId());
            return plugin;
        }

        // If this mojo is being executed, the plugin must be defined in the project
        throw new RuntimeException("Plugin is not found in " + project.getArtifactId());
    }

    private static Plugin getPlugin(final MavenProject pluginProject)
    {
        final PluginManagement pluginManagement = pluginProject.getPluginManagement();

        Plugin plugin = null;

        if (pluginManagement != null) {
            plugin = lookupPlugin(pluginManagement);
            LOGGER.debug("{} does not have plugin management ", pluginProject.getArtifactId());
        }

        if (plugin == null) {
            // Look for imageConfiguration in build/plugins/plugin
            return pluginProject.getPlugin(DOCKER_VERSION_PLUGIN_NAME);
        }

        return plugin;
    }

    private static Plugin lookupPlugin(final PluginManagement pluginManagement)
    {
        final Map<String, Plugin> pluginMap = pluginManagement.getPluginsAsMap();
        return pluginMap.get(DOCKER_VERSION_PLUGIN_NAME);
    }

    private static Xpp3Dom getPluginConfig(final Plugin plugin)
    {
        final Object configuration = plugin.getConfiguration();

        if (configuration == null) {
            throw new IllegalArgumentException("'configuration' is not set in plugin");
        }
        return new Xpp3Dom((Xpp3Dom)configuration);
    }

    private static List<Xpp3Dom> getImagesConfig(final Xpp3Dom config)
    {
        final Xpp3Dom configImageManagement = config.getChild("imageManagement");
        if (configImageManagement == null){
            throw new IllegalArgumentException("'imageManagement' is not set in plugin configuration");
        }

        final Xpp3Dom[] images = configImageManagement.getChildren("image");
        return Arrays.asList(images);
    }
}
