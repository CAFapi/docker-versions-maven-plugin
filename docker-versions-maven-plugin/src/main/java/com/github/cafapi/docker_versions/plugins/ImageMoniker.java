/*
 * Copyright 2024-2025 Open Text.
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

import com.github.cafapi.docker_versions.docker.auth.Constants;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.NameParser.HostnameReposName;
import org.apache.commons.lang3.StringUtils;

final class ImageMoniker
{
    private final String registry;

    private final String repositorySansRegistry;

    private final String repositoryFromConfigSansRegistry;

    private final String tag;

    private final String digest;

    private final String fullImageNameWithTag;

    private final String fullImageNameWithoutTag;

    public ImageMoniker(final String repository, final String tag, final String digest)
    {
        if (StringUtils.isBlank(repository)) {
            throw new IllegalArgumentException("Repository not specified for image: " + repository);
        }

        if (StringUtils.isBlank(tag)) {
            throw new IllegalArgumentException("Tag not specified for image " + repository);
        }

        final HostnameReposName hostRepoName = NameParser.resolveRepositoryName(repository);

        this.registry = getRegistry(hostRepoName);
        this.repositoryFromConfigSansRegistry = hostRepoName.reposName;
        this.repositorySansRegistry = getRepositorySansRegistry(hostRepoName);
        this.tag = tag;
        this.digest = digest;
        this.fullImageNameWithTag = repository + ":" + tag;
        this.fullImageNameWithoutTag = repository;
    }

    private static String getRegistry(final HostnameReposName hostRepoName)
    {
        final String hostname = hostRepoName.hostname;
        return AuthConfig.DEFAULT_SERVER_ADDRESS.equals(hostname)
            ? Constants.DEFAULT_REGISTRY
            : hostname;
    }

    private static String getRepositorySansRegistry(final HostnameReposName hostRepoName)
    {
        final String repoName = hostRepoName.reposName;
        return repoName.contains("/")
            ? repoName
            : "library/" + repoName;
    }

    public String getRegistry()
    {
        return registry;
    }

    public String getRepositoryFromConfigSansRegistry()
    {
        return repositoryFromConfigSansRegistry;
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
