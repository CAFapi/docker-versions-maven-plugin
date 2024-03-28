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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsParseException;
import org.apache.maven.settings.io.SettingsReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.cafapi.docker_versions.docker.auth.DockerRegistryAuthConfig;
import com.github.cafapi.docker_versions.docker.auth.AuthHelper;
import com.github.cafapi.docker_versions.docker.client.DockerRegistryException;

final class AuthHelperTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHelperTest.class);

    @BeforeEach
    void init(final TestInfo testInfo)
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());
    }

    @Test
    public void testConfigPresentInBothLocations() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "saas-docker-release.svsartifactory.swinfra.net");
        Assertions.assertNotNull(authConfig, "Got authConfig PresentInBothLocations");
        Assertions.assertTrue(authConfig.getUsername().equals("vgkusuma@microfocus.com"), "Got username from auth config PresentInBothLocations");
        LOGGER.info("Got auth config PresentInBothLocations: {} ", authConfig);
    }

    @Test
    public void testConfigOnlyInMavenSettings() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "dockerhub-private.svsartifactory.swinfra.net");
        Assertions.assertNotNull(authConfig, "Got authConfig OnlyInMavenSettings");
        Assertions.assertTrue(authConfig.getUsername().equals("vgkusuma@microfocus.com"), "Got username from auth config OnlyInMavenSettings");
        LOGGER.info("Got auth config OnlyInMavenSettings: {} ", authConfig);
    }

    @Test
    public void testConfigOnlyInDockerConfig() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "dockerhub-public.svsartifactory.swinfra.net");
        Assertions.assertNotNull(authConfig, "Got authConfig");
        Assertions.assertTrue(authConfig.getUsername().equals("vgkusuma@microfocus.com"), "Got username from auth config OnlyInDockerConfig");
        LOGGER.info("Got auth config OnlyInDockerConfig: {} ", authConfig);
    }

    @Test
    public void testConfigForDockerHub() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "docker.io");
        Assertions.assertNotNull(authConfig, "Got authConfig");
        Assertions.assertTrue(authConfig.getUsername().equals("ghoshdas"), "Got username from auth config");
        LOGGER.info("Got auth config: {} ", authConfig);
    }

    //@Test
    public void testConfigFromCredHelpers() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "saas-docker-release.svsartifactory.swinfra.net");
        Assertions.assertNotNull(authConfig, "Got authConfig FromCredHelpers");
        Assertions.assertTrue(authConfig.getUsername().equals("vgkusuma@microfocus.com"), "Got username from auth config FromCredHelpers");
        LOGGER.info("Got auth config FromCredHelpers: {} ", authConfig);
    }

    //@Test
    public void testConfigFromCredStore() throws JsonParseException, JsonMappingException, DockerRegistryException, IOException
    {
        final Settings testSettings = getTestSettings();
        final DockerRegistryAuthConfig authConfig = AuthHelper.getRegistryAuthConfig(testSettings, "saas-docker-release.svsartifactory.swinfra.net");
        Assertions.assertNotNull(authConfig, "Got authConfig FromCredStore");
        Assertions.assertTrue(authConfig.getUsername().equals("vgkusuma@microfocus.com"), "Got username from auth config FromCredStore");
        LOGGER.info("Got auth config FromCredStore: {} ", authConfig);
    }

    private static Settings getTestSettings()
    {
        final SettingsReader settingsReader = new DefaultSettingsReader();
        Settings settings;

        try {
            Map<String, ?> options = Collections.singletonMap(SettingsReader.IS_STRICT, Boolean.TRUE);
            final InputStream settingsSource = AuthHelperTest.class.getResourceAsStream("/testSettings.xml");
            try {
                settings = settingsReader.read(settingsSource, options);
            } catch (SettingsParseException e) {
                options = Collections.singletonMap(SettingsReader.IS_STRICT, Boolean.FALSE);

                settings = settingsReader.read(settingsSource, options);

                LOGGER.warn("{} {} {} ", e.getMessage(), e.getLineNumber(), e.getColumnNumber(), e);
            }
        } catch (final SettingsParseException e) {
            LOGGER.error(
                    "Non-parseable settings : {} {} {}", e.getMessage(),
                    e.getLineNumber(),
                    e.getColumnNumber(),
                    e);
            return new Settings();
        } catch (final IOException e) {
            LOGGER.error(
                    "Non-readable settings : {} {} {}", e.getMessage(),
                    -1,
                    -1,
                    e);
            return new Settings();
        }
        return settings;
    }
}
