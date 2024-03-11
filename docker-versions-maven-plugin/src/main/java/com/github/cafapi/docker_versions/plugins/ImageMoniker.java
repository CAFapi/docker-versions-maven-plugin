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

import org.apache.commons.lang3.StringUtils;

final class ImageMoniker
{
    private final String registry;

    private final String repositorySansRegistry;

    private final String tag;

    private final String digest;

    private final String fullImageNameWithTag;

    private final String fullImageNameWithoutTag;

    public ImageMoniker(final String repository, final String tag, final String digest)
    {
        if (StringUtils.isBlank(repository)) {
            throw new IllegalArgumentException("Repository not specified for image: " + repository);
        }

        final String[] repositoryInfo = repository.split("/", 2);

        if (repositoryInfo.length == 1) {
            registry = null;
            repositorySansRegistry = repositoryInfo[0];
        }
        else {
            if (isRegistry(repositoryInfo[0])) {
                registry = repositoryInfo[0];
                repositorySansRegistry = repositoryInfo[1];
            }
            else {
                registry = null;
                repositorySansRegistry = repository;
            }
        }

        if (StringUtils.isBlank(tag)) {
            throw new IllegalArgumentException("Tag not specified for image " + repository);
        }

        this.tag = tag;
        this.digest = digest;
        this.fullImageNameWithTag = repository + ":" + tag;
        this.fullImageNameWithoutTag = repository;
    }

    private static boolean isRegistry(final String part) {
        return part.contains(".") || part.contains(":");
    }

    public String getRegistry()
    {
        return registry;
    }

    public String getRepositoryWithoutRegistry()
    {
        return repositorySansRegistry;
    }

    public String getTag()
    {
        return tag;
    }

    public String getDigest()
    {
        return digest;
    }

    public boolean hasDigest()
    {
        return !StringUtils.isBlank(digest);
    }

    public String getFullImageNameWithTag()
    {
        return fullImageNameWithTag;
    }

    public String getFullImageNameWithoutTag()
    {
        return fullImageNameWithoutTag;
    }
}
