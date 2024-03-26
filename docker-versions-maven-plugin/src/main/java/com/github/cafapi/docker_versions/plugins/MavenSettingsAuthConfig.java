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

import com.github.dockerjava.api.model.AuthConfig;
import java.util.Optional;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.utils.xml.Xpp3Dom;

final class MavenSettingsAuthConfig
{
    private MavenSettingsAuthConfig()
    {
    }

    public static AuthConfig getAuthConfig(final Settings settings, final String registry)
    {
        return Optional.ofNullable(settings.getServer(registry))
            .map(MavenSettingsAuthConfig::createAuthConfigFromServer)
            .orElse(null);
    }

    private static AuthConfig createAuthConfigFromServer(final Server server)
    {
        final Object serverConfig = server.getConfiguration();
        final AuthConfig authConfig = new AuthConfig();
        authConfig.withUsername(server.getUsername())
            .withPassword(server.getPassword())
            .withEmail(getProperty(serverConfig, "email"))
            .withAuth(getProperty(serverConfig, "auth"));
        return authConfig;
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
