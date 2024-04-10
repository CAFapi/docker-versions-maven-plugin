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
package com.github.cafapi.docker_versions.docker.auth;

import org.apache.maven.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.AuthConfig;

public final class AuthConfigHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthConfigHelper.class);

    private AuthConfigHelper()
    {
    }

    public static AuthConfig getAuthConfig(
        final Settings settings,
        final String registry)
        throws DockerRegistryAuthException
    {
        final DockerRegistryAuthConfig authConfig = getRegistryAuthConfig(settings, registry);

        if (authConfig == null) {
            return null;
        }

        final AuthConfig dockerJavaClientAuthConfig = new AuthConfig();
        dockerJavaClientAuthConfig
            .withUsername(authConfig.getUsername())
            .withPassword(authConfig.getPassword())
            .withEmail(authConfig.getEmail())
            .withAuth(authConfig.getAuth())
            .withIdentityToken(authConfig.getIdentityToken());
        return dockerJavaClientAuthConfig;
    }

    public static DockerRegistryAuthConfig getRegistryAuthConfig(final Settings settings, final String registry)
    {
        // Check maven settings stored typically in ~/.m2/settings.xml
        DockerRegistryAuthConfig ret = MavenSettingsAuthConfig.getRegistryAuthConfig(settings, registry);
        if (ret != null) {
            LOGGER.debug("Found credentials in ~/.m2/settings.xml");
            return ret;
        }

        // Check ~/.docker/config.json
        ret = DockerAuthConfig.getRegistryAuthConfig(registry);
        if (ret != null) {
            LOGGER.debug("Found credentials in ~/.docker/config.json");
            return ret;
        }

        LOGGER.info("Credentials not found for {}", registry);
        return null;
    }
}
