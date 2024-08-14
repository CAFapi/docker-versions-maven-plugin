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
package com.github.cafapi.docker_versions.plugins.extension;

import org.apache.maven.MavenExecutionException;

final class ProjectRegistryPropertySetException extends MavenExecutionException
{
    private static final long serialVersionUID = -7252047895418654391L;

    public ProjectRegistryPropertySetException()
    {
        super("'projectDockerRegistry' is expected to be set as a configuration parameter in the plugin, not as a property.",
            (Exception)null);
    }
}
