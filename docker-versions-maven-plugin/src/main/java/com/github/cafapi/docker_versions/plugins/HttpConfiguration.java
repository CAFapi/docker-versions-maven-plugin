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

import org.apache.maven.plugins.annotations.Parameter;

public final class HttpConfiguration
{
    private static final int CONNECTION_TIMEOUT_SECONDS = getIntPropertyOrEnvVar("CONNECTION_TIMEOUT_SECONDS", "30");
    private static final int RESPONSE_TIMEOUT_SECONDS = getIntPropertyOrEnvVar("RESPONSE_TIMEOUT_SECONDS", "45");
    private static final long DOWNLOAD_IMAGE_TIMEOUT_SECONDS = getLongPropertyOrEnvVar("DOWNLOAD_IMAGE_TIMEOUT_SECONDS", "300");

    @Parameter
    private int connectionTimout = CONNECTION_TIMEOUT_SECONDS;

    @Parameter()
    private int responseTimout = RESPONSE_TIMEOUT_SECONDS;

    @Parameter()
    private long downloadImageTimout = DOWNLOAD_IMAGE_TIMEOUT_SECONDS;

    public int getConnectionTimout() {
        return connectionTimout;
    }

    public int getResponseTimout() {
        return responseTimout;
    }

    public long getDownloadImageTimout() {
        return downloadImageTimout;
    }

    public HttpConfiguration()
    {
        System.out.println("Default ctor..");
    }

    @Override
    public String toString() {
        return "HttpConfiguration [ connectionTimout="
            + connectionTimout
            + "s, responseTimout="
            + responseTimout
            + "s, downloadImageTimout="
            + downloadImageTimout
            + "s ]";
    }

    private static int getIntPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = getPropertyOrEnvVar(key, defaultValue);
        return Integer.parseInt(propertyValue);
    }

    private static long getLongPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = getPropertyOrEnvVar(key, defaultValue);
        return Long.parseLong(propertyValue);
    }

    private static String getPropertyOrEnvVar(final String key, final String defaultValue)
    {
        final String propertyValue = System.getProperty(key);
        return (propertyValue != null)
            ? propertyValue
            : (System.getenv(key) != null) ? System.getenv(key) : defaultValue;
    }
}
