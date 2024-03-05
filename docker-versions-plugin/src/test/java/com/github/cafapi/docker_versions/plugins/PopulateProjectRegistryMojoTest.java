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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public final class PopulateProjectRegistryMojoTest
{
    @Test
    public void testGetInterpolatedValue()
    {
        final PopulateProjectRegistryMojo mojo = new PopulateProjectRegistryMojo();
        final  Map<String, String> props =  new HashMap<>() {{
            put("maven.compiler.release", "17");
            put("dockerOrgSeperator", "/");
            put("docker.host.address", "localhost");
            put("gmavenplusPluginGroovyVersion", "3.0.19");
            put("homeDockerPrereleaseRegistry", "saas-docker-prerelease.svsartifactory.swinfra.net");
            put("dockerVersionSeperator", ":");
            put("git.branch", "UNKNOWN_BRANCH");
            put("dockerImagePrefix", "dev/");
            put("buildDate", "UNKNOWN_BUILD_DATE");
            put("env.CAF_STORAGE_AWS_S3_ACCESSKEY", "AKIA3JX3L7JYXTKYCK7A");
            put("env.CAF_STORAGE_AWS_S3_REGION", "us-east-1");
            put("copyrightYear", "2024");
            put("dockerHubPublic", "dockerhub-public.svsartifactory.swinfra.net");
            put("corporateRepositoryManager", "svsartifactory.swinfra.net");
            put("enforceCorrectDependencies", "true");
            put("homeDockerReleaseRegistry", "saas-docker-release.svsartifactory.swinfra.net");
            put("project.build.sourceEncoding", "UTF-8");
            put("git.commitDate", "UNKNOWN_COMMIT_DATE");
            put("netbeans.hint.jdkPlatform", "JDK_17");
            put("dockerHubPrivate", "dockerhub-private.svsartifactory.swinfra.net");
            put("git.commitsCount", "-1");
            put("git.buildnumber", "UNKNOWN_BUILDNUMBER");
            put("licenseHeaderText", "Copyright 2024 Open Text.");
            put("enforceCompatibleDependencies", "true");
            put("project.reporting.outputEncoding", "UTF-8");
            put("docker.host", "tcp:localhost:2375");
            put("skipLicenseHeadersCheck", "false");
            put("git.shortRevision", "UNKNOWN_REVISION");
            put("git.parent", "UNKNOWN_PARENT");
            put("copyrightNotice", "Copyright 2024 Open Text.");
            put("git.revision", "UNKNOWN_REVISION");
            put("git.tag", "UNKNOWN_TAG");
            put("enforceBannedDependencies", "false");
            put("projectDockerRegistry", "${project.name}-1.0.0-SNAPSHOT.project-registries.local");
        }};

        final Properties testProperties = new Properties();
        testProperties.putAll(props);
        //mojo.project.getModel().setProperties(testProperties);
        //mojo.getInterpolatedValue("");

    }
}
