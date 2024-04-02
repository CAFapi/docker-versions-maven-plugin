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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;

final class DockerAuthUtil
{
    private DockerAuthUtil()
    {
    }

    public static String convertToHostname(final String registry)
    {
        String stripped = registry;
        if (registry.startsWith("http://")) {
            stripped = registry.substring(7);
        } else if (registry.startsWith("https://")) {
            stripped = registry.substring(8);
        }
        final String[] numParts = stripped.split("/", 2);
        return numParts[0];
    }

    public static String ensureRegistryHttpUrl(final String registry)
    {
        if (registry.toLowerCase().startsWith("http")) {
            return registry;
        }
        // Default to https:// schema
        return "https://" + registry;
    }

    public static Reader getFileReaderFromDir(final File file)
    {
        if (file.exists() && file.length() != 0) {
            try {
                return new FileReader(file);
            } catch (final FileNotFoundException e) {
                throw new IllegalStateException("Cannot find " + file, e);
            }
        }
        return null;
    }

    public static File getHomeDir()
    {
        return new File(getUserHome());
    }

    private static String getUserHome()
    {
        String homeDir = System.getenv("HOME");
        if (homeDir == null) {
            homeDir = System.getProperty("user.home");
        }
        return homeDir;
    }
}
