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

import java.util.Hashtable;
import java.util.Map;

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.utils.xml.Xpp3Dom;

import com.github.dockerjava.api.model.AuthConfig;

final class MavenSettingsAuthConfig
{
    private final Map<String, AuthConfig> authConfigs = new Hashtable<>();

    MavenSettingsAuthConfig(final Settings settings)
    {
        loadConfig(settings);
    }

    public AuthConfig getAuthConfig(final String registry)
    {
        return authConfigs.get(registry);
    }

    private void loadConfig(final Settings settings)
    {
        settings.getServers().forEach(server -> authConfigs.put(server.getId(), createAuthConfigFromServer(server)));
    }

    private static AuthConfig createAuthConfigFromServer(final Server server)
    {
        final Object serverConfig = server.getConfiguration();
        final AuthConfig authConfig =  new AuthConfig();
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
