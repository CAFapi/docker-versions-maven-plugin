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
import java.util.Set;

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
     * The set of regular expressions representing images the ignore pattern applies to.
     */
    @Parameter
    private Set<String> images;

    public IgnoreVersion()
    {
    }

    public IgnoreVersion(final String type, final String version, final Set<String> images)
    {
        this.type = type;
        this.version = version;
        this.images = images;
    }

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
     * Get the list of images this ignore rule applies to.
     *
     * @return Set of regular expressions representing images.
     */
    public Set<String> getImages()
    {
        return this.images;
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

    /**
     * Set the list of images this ignore rule applies to.
     *
     * @param images a set of images.
     */
    public void setImages(final Set<String> images)
    {
        this.images = images;
    }

    @Override
    public String toString()
    {
        return "IgnoreVersion [version=" + version + ", type=" + type + ", images=" + images + "]";
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, version, images);
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IgnoreVersion)) {
            return false;
        }
        final IgnoreVersion other = (IgnoreVersion) obj;
        return Objects.equals(type, other.type)
            && Objects.equals(version, other.version)
            && Objects.equals(images, other.images);
    }
}
