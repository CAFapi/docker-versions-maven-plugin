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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cafapi.docker_versions.plugins.DockerVersionsHelper;

/**
 * This maven build extension adds the populate-project-registry and depopulate-project-registry goals to the maven session.
 */
@Named("docker-versions-maven-extension")
@Singleton
public final class DockerVersionsLifecycleParticipant extends AbstractMavenLifecycleParticipant
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerVersionsLifecycleParticipant.class);

    private static final List<String> AVOID_AUTO_POPULATE_PHASES = Arrays.asList(new String[]{"clean", "validate", "site"});
    private static final List<String> AVOID_AUTO_DEPOPULATE_PHASES = Arrays.asList(new String[]{"validate", "site"});

    @Override
    public void afterProjectsRead(final MavenSession session) throws MavenExecutionException
    {
        if (Boolean.parseBoolean(session.getUserProperties().getProperty("skipAutoPopulateRegistry"))) {
            return;
        }

        final List<String> sessionTasks = session.getRequest().getGoals();

        if (sessionTasks == null || sessionTasks.size() == 0) {
            LOGGER.debug("DockerVersionsLifecycleParticipant no tasks in session.");
            return;
        }

        final List<String> phasesInSession = getPhases(sessionTasks);
        if (phasesInSession.size() == 0) {
            // Maven invoked with only goals, no lifecycle phases
            LOGGER.debug("DockerVersionsLifecycleParticipant no phases in session.");
            return;
        }

        final List<String> updatedSessionTasks = new ArrayList<>(sessionTasks);

        // Update the maven tasks to include the docker-versions goals at the start and end of execution
        if (shouldAddPopulateGoal(sessionTasks, phasesInSession)) {
            updatedSessionTasks.add(0, "docker-versions:populate-project-registry");
        }

        if (shouldAddDepopulateGoal(sessionTasks, phasesInSession)) {
            updatedSessionTasks.add("docker-versions:depopulate-project-registry");
        }

        if (updatedSessionTasks.size() == sessionTasks.size()) {
            // No need to run the docker-version goals
            return;
        }

        session.getRequest().setGoals(updatedSessionTasks);

        LOGGER.info("Adding docker version management goals... {}", updatedSessionTasks);

        final List<MavenProject> projects = session.getProjects();
        printBuildOrder(projects);

        final int projectsCount = projects.size();

        // Skip the docker-versions goals for all projects other than the first and last project to be built
        if (projectsCount == 1) {
            // Single project, so execute both populate and de-populate project registry goals
            return;
        }

        final List<Entry<Plugin, Xpp3Dom>> pluginConfigsToUpdate = new ArrayList<>();

        for (int i = 0; i < projectsCount; i++) {
            final MavenProject project = projects.get(i);

            final Plugin plugin = DockerVersionsHelper.getPlugin(project);
            if (plugin == null) {
                continue;
            }

            final Xpp3Dom pluginConfig = DockerVersionsHelper.getPluginConfig(plugin);
            if (pluginConfig == null) {
                continue;
            }

            final List<Xpp3Dom> imagesConfig = DockerVersionsHelper.getImagesConfig(pluginConfig);
            if (imagesConfig.isEmpty()) {
                continue;
            }

            pluginConfigsToUpdate.add(new AbstractMap.SimpleEntry<Plugin, Xpp3Dom>(plugin, pluginConfig));
        }

        if (pluginConfigsToUpdate.isEmpty() || pluginConfigsToUpdate.size() == 1) {
            // Plugin is not configured in any project
            // or just one project has the plugin configured, so run both goals
            return;
        }

        // For the first project, run populate goal, skip depopulate goal
        final Entry<Plugin, Xpp3Dom> first = pluginConfigsToUpdate.remove(0);
        setSkipMojoConfig(first.getKey(), first.getValue(), "skipDepopulateProjectRegistry");

        // For the last project, skip populate goal, run depopulate goal
        final Entry<Plugin, Xpp3Dom> last = pluginConfigsToUpdate.remove(pluginConfigsToUpdate.size() - 1);
        setSkipMojoConfig(last.getKey(), last.getValue(), "skipPopulateProjectRegistry");

        // For all other projects skip plugin execution entirely
        pluginConfigsToUpdate.forEach(entry -> setSkipMojoConfig(entry.getKey(), entry.getValue(), "skip"));
    }

    public static boolean shouldAddPopulateGoal(final List<String> tasks, final List<String> phases)
    {
        return !tasks.contains("docker-versions:populate-project-registry")
            && phases.stream().anyMatch(p -> !AVOID_AUTO_POPULATE_PHASES.contains(p));
    }

    public static boolean shouldAddDepopulateGoal(final List<String> tasks, final List<String> phases)
    {
        return !tasks.contains("docker-versions:depopulate-project-registry")
            && phases.stream().anyMatch(p -> !AVOID_AUTO_DEPOPULATE_PHASES.contains(p));
    }

    public static List<String> getPhases(final List<String> tasks)
    {
        return tasks.stream().filter(t -> !t.contains(":")).collect(Collectors.toList());
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

    private static void printBuildOrder(final List<MavenProject> projects)
    {
        final int totalModules = projects.size();
        LOGGER.debug("--- Build order of {} projects --- ", totalModules);
        projects.stream().forEach(p -> LOGGER.debug("{}", p.getName()));
    }
}
