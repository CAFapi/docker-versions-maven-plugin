/*
 * Copyright 2024-2026 Open Text.
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

import com.github.dockerjava.api.model.AuthConfig;
import java.util.Optional;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.xml.Xpp3Dom;

final class MavenSettingsAuthConfig
{
    private MavenSettingsAuthConfig()
    {
    }

    public static AuthConfig getAuthConfig(final Settings settings, final String registry)
    {
        return Optional.ofNullable(getRegistryAuthConfig(settings, registry))
            .map(regAuthConfig -> createAuthConfig(regAuthConfig))
            .orElse(null);
    }

    public static DockerRegistryAuthConfig getRegistryAuthConfig(final Settings settings, final String registry)
    {
        return Optional.ofNullable(settings.getServer(registry))
            .map(MavenSettingsAuthConfig::createRegistryAuthConfigFromServer)
            .orElse(null);
    }

    private static AuthConfig createAuthConfig(final DockerRegistryAuthConfig regAuthConfig)
    {
        final AuthConfig authConfig = new AuthConfig();
        authConfig.withUsername(regAuthConfig.getUsername())
            .withPassword(regAuthConfig.getPassword())
            .withEmail(regAuthConfig.getEmail())
            .withAuth(regAuthConfig.getAuth());
        return authConfig;
    }

    private static DockerRegistryAuthConfig createRegistryAuthConfigFromServer(final Server server)
    {
        final Object serverConfig = server.getConfiguration();
        return new DockerRegistryAuthConfig(
            server.getUsername(),
            server.getPassword(),
            getProperty(serverConfig, Constants.AUTH_EMAIL),
            getProperty(serverConfig, Constants.AUTH_AUTH));
    }

    private static String getProperty(final Object configuration, final String property)
    {
        if (configuration != null) {
            final Xpp3Dom dom = (Xpp3Dom) configuration;
            final Xpp3Dom element = dom.getChild(property);
            if (element != null) {
                return element.getValue();
            }
        }
        return null;
    }
}
