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

import java.util.Objects;

import org.apache.maven.plugins.annotations.Parameter;

public final class IgnoreVersion
{
    /**
     * A version number or a regular expression for version numbers that should be ignored.
     */
    @Parameter(required = true)
    private String version;

    /**
     * The type of ignore mechanism to use. Allowed values are 'exact' and 'regex'.
     */
    @Parameter(required = true, defaultValue = "exact")
    private String type;

    /**
     * Get the type of ignore mechanism to use. Allowed values are 'exact' and 'regex'.
     *
     * @return String
     */
    public String getType()
    {
        return this.type;
    }

    /**
     * Get a version number or a regular expression for version numbers that should be ignored.
     *
     * @return String
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * Set the type of ignore mechanism to use. Allowed values are 'exact' and 'regex'.
     *
     * @param type a type object.
     */
    public void setType(final String type)
    {
        this.type = type;
    }

    /**
     * Set a version number or a regular expression for version numbers that should be ignored.
     *
     * @param version a version object.
     */
    public void setVersion(final String version)
    {
        this.version = version;
    }

    @Override
    public String toString()
    {
        return "IgnoreVersion [version=" + version + ", type=" + type + "]";
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, version);
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof IgnoreVersion)) {
            return false;
        }
        final IgnoreVersion other = (IgnoreVersion) obj;
        return Objects.equals(type, other.type) && Objects.equals(version, other.version);
    }
}
