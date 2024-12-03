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

public final class ImageConfiguration
{
    @Parameter(required = true)
    private String repository;

    @Parameter(required = true)
    private String tag;

    @Parameter
    private String digest;

    @Parameter
    private String latestTag;

    @Parameter(defaultValue = "false")
    private boolean skipPull;

    @Parameter
    private String targetRepository;

    public String getRepository()
    {
        return repository;
    }

    public void setRepository(final String repository)
    {
        this.repository = repository;
    }

    public String getTag()
    {
        return tag;
    }

    public void setTag(final String tag)
    {
        this.tag = tag;
    }

    public String getDigest()
    {
        return digest;
    }

    public void setDigest(final String digest)
    {
        this.digest = digest;
    }

    public String getLatestTag()
    {
        return latestTag;
    }

    public void setLatestTag(final String latestTag)
    {
        this.latestTag = latestTag;
    }

    public boolean isSkipPull()
    {
        return skipPull;
    }

    public void setSkipPull(final boolean skipPull)
    {
        this.skipPull = skipPull;
    }

    public String getTargetRepository()
    {
        return targetRepository;
    }

    public void setTargetRepository(final String targetRepository)
    {
        this.targetRepository = targetRepository;
    }

    @Override
    public String toString()
    {
        return "ImageConfiguration ["
            + "repository=" + repository
            + ", tag=" + tag
            + ", digest=" + digest
            + ", latestTag=" + latestTag
            + ", skipPull=" + skipPull
            + ", targetRepository=" + targetRepository
            + "]";
    }
}
