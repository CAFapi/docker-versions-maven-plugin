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

import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.NameParser.HostnameReposName;

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

        final HostnameReposName hostRepoName = NameParser.resolveRepositoryName(repository);
        registry = hostRepoName.hostname;
        repositorySansRegistry = hostRepoName.reposName;

        if (StringUtils.isBlank(tag)) {
            throw new IllegalArgumentException("Tag not specified for image " + repository);
        }

        this.tag = tag;
        this.digest = digest;
        this.fullImageNameWithTag = repository + ":" + tag;
        this.fullImageNameWithoutTag = repository;
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

    @Override
    public String toString()
    {
        return "ImageMoniker [registry=" + registry
            + ", repositorySansRegistry=" + repositorySansRegistry
            + ", tag=" + tag
            + ", digest=" + digest
            + ", fullImageNameWithTag=" + fullImageNameWithTag
            + ", fullImageNameWithoutTag=" + fullImageNameWithoutTag
            + "]";
    }
}
