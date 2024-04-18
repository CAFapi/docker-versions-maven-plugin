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
package com.github.cafapi.docker_versions.plugins.extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("docker-versions-maven-extension")
@Singleton
/**
 * This maven build extension adds executions to the docker-versions plugin.
 * This is a build extension which is loaded late.
 * Dynamic phases: https://cwiki.apache.org/confluence/display/MAVEN/Dynamic+phases
 * Refer: https://maven.apache.org/guides/mini/guide-using-extensions.html#build-extension
 * https://maven.apache.org/ref/4.0.0-alpha-13/xref/org/apache/maven/ReactorReader.html
 * https://github.com/sandipchitale/m2e-phasesandgoals/blob/master/org.eclipse.m2e.core.ui.phasesandgoals/src/org/eclipse/m2e/core/ui/internal/handlers/ShowPhasesAndGoalsHandler.java#L73
 */
public final class DockerVersionsLifecycleParticipant extends AbstractMavenLifecycleParticipant
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsLifecycleParticipant.class);

    private static final String DOCKER_VERSION_PLUGIN_GROUP_ID = "com.github.cafapi.plugins.docker.versions";
    private static final String DOCKER_VERSION_PLUGIN_ARTIFACT_ID = "docker-versions-maven-plugin";
    private static final String DOCKER_VERSION_PLUGIN_NAME = DOCKER_VERSION_PLUGIN_GROUP_ID + ":" + DOCKER_VERSION_PLUGIN_ARTIFACT_ID;

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException
    {
        final List<String> goalsForSession = session.getGoals();

        if (goalsForSession == null || goalsForSession.isEmpty()) {
            return;
        }

        // Update the maven tasks to include the docker-versions goals at the beginning
        // and end of execution
        goalsForSession.add(0, "docker-versions:populate-project-registry");
        goalsForSession.add("docker-versions:depopulate-project-registry");
        LOGGER.info("Updated goals: {}", goalsForSession);
        session.getRequest().setGoals(goalsForSession);

        final List<MavenProject> projects = session.getProjects();
        getBuildOrder(projects);

        // Skip the docker-versions goals for all projects other than the first and last
        if (projects.size() == 1) {
            // Single project, so execute both populate and de-populate project registry goals
            return;
        }

        final int projectsCount = projects.size();

        final List<Entry<Plugin, Xpp3Dom>> pluginConfigs = new ArrayList<>();

        for (int i = 0; i < projectsCount; i++) {
            final MavenProject project = projects.get(i);

            final Plugin plugin = getPlugin(project);

            if (plugin == null) {
                continue;
            }

            final Xpp3Dom pluginConfig = getPluginConfig(plugin);
            if (pluginConfig == null) {
                continue;
            }

            final List<Xpp3Dom> imagesConfig = getImagesConfig(pluginConfig);
            if (imagesConfig.isEmpty()) {
                continue;
            }

            pluginConfigs.add(Map.entry(plugin, pluginConfig));
        }

        if (pluginConfigs.isEmpty() || pluginConfigs.size() == 1) {
            // No projects have plugin configured
            // or just one has the plugin configured, so run both goals
            return;
        }

        final Entry<Plugin, Xpp3Dom> first = pluginConfigs.remove(0);
        // First project, run populate goal, skip dePopulate goal
        setSkipMojoConfig(first.getKey(), first.getValue(), "skipDepopulateProjectRegistry");

        // Last project, skip populate goal, run dePopulate goal
        final Entry<Plugin, Xpp3Dom> last = pluginConfigs.remove(pluginConfigs.size() - 1);
        setSkipMojoConfig(last.getKey(), last.getValue(), "skipPopulateProjectRegistry");

        // Skip plugin execution entirely for all other projects
        pluginConfigs.forEach(entry -> setSkipMojoConfig(entry.getKey(), entry.getValue(), "skip"));
    }

    private static void setSkipMojoConfig(final Plugin plugin, final Xpp3Dom config, final String name)
    {
        final Xpp3Dom skipPluginConfigParam = config.getChild("skip");
        if (skipPluginConfigParam != null) {
            config.removeChild(skipPluginConfigParam);
        }

        final Xpp3Dom configParam = new Xpp3Dom(name);
        configParam.setValue("true");
        config.addChild(configParam);
        plugin.setConfiguration(config);
    }

    private static Plugin getPlugin(final MavenProject project)
    {
        final Plugin plugin = getPlugin(project.getPluginManagement());
        if (plugin != null) {
            return plugin;
        }

        // Look in build/plugins
        return getPluginWithImageConfig(project.getPlugin(DOCKER_VERSION_PLUGIN_NAME));
    }

    private static Plugin getPlugin(final PluginManagement pluginManagement)
    {
        if (pluginManagement == null) {
            return null;
        }

        final Plugin plugin = pluginManagement.getPluginsAsMap().get(DOCKER_VERSION_PLUGIN_NAME);

        return getPluginWithImageConfig(plugin);
    }

    private static Plugin getPluginWithImageConfig(final Plugin plugin)
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

    private static Xpp3Dom getPluginConfig(final Plugin plugin)
    {
        final Object configuration = plugin.getConfiguration();

        if (configuration == null) {
            return null;
        }
        return new Xpp3Dom((Xpp3Dom) configuration);
    }

    private static List<Xpp3Dom> getImagesConfig(final Xpp3Dom config)
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

    private static void getBuildOrder(final List<MavenProject> projects)
    {
        final int totalModules = projects.size();
        LOGGER.debug("--- {} projects build order --- ", totalModules);
        projects.stream().forEach(p -> LOGGER.debug("{}", p.getBuild().getFinalName()));
    }
}
