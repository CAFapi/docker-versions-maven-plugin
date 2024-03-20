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

import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.utils.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.AuthConfig;

final class AuthHelper
{
    private static final String[] DEFAULT_REGISTRIES = new String[]{
        "docker.io", "index.docker.io", "registry.hub.docker.com", AuthConfig.DEFAULT_SERVER_ADDRESS};

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHelper.class);

    public static AuthConfig createAuthConfigFromMavenSettings(final Settings settings, final String user, final String registry)
    {
        LOGGER.trace("Lookup the registry & user from ~/.m2/setting.xml...");
        Server defaultServer = null;
        Server found;
        for (final Server server : settings.getServers()) {
            final String id = server.getId();

            // Remember a default server without user as fallback for later
            if (defaultServer == null) {
                defaultServer = checkForServer(server, id, registry, null);
            }
            // Check for specific server with user part
            found = checkForServer(server, id, registry, user);
            if (found != null) {
                return createAuthConfigFromServer(found);
            }
        }
        return defaultServer != null ? createAuthConfigFromServer(defaultServer) : null;
    }

    private static Server checkForServer(final Server server, final String id, final String registry, final String user)
    {
        final String[] registries = registry != null ? new String[] { registry } : DEFAULT_REGISTRIES;
        for (final String reg : registries) {
            if (id.equals(user == null ? reg : reg + "/" + user)) {
                return server;
            }
        }
        return null;
    }

    private static AuthConfig createAuthConfigFromServer(final Server server)
    {
        final AuthConfig authConfig =  new AuthConfig();
        authConfig.withUsername(server.getUsername())
            .withPassword(server.getPassword())
            .withEmail(extractFromServerConfiguration(server.getConfiguration(), "email"))
            .withAuth(extractFromServerConfiguration(server.getConfiguration(), "auth"));
        return authConfig;
    }

    private static String extractFromServerConfiguration(final Object configuration, final String prop)
    {
        if (configuration != null) {
            final Xpp3Dom dom = (Xpp3Dom) configuration;
            final Xpp3Dom element = dom.getChild(prop);
            if (element != null) {
                return element.getValue();
            }
        }
        return null;
    }

}
