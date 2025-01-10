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
package com.github.cafapi.docker_versions.docker.auth;

public final class DockerRegistryAuthConfig
{
    private final String username;
    private final String password;
    private final String email;
    private final String auth;
    private final String identityToken;

    public DockerRegistryAuthConfig(
        final String username,
        final String password,
        final String email,
        final String auth,
        final String identityToken)
    {
        this.username = username;
        this.password = password;
        this.email = email;
        this.auth = auth;
        this.identityToken = identityToken;
    }

    public DockerRegistryAuthConfig(final String username, final String password, final String email, final String auth)
    {
        this(username, password, email, auth, null);
    }

    public String getUsername()
    {
        return username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getEmail()
    {
        return email;
    }

    public String getAuth()
    {
        return auth;
    }

    public String getIdentityToken()
    {
        return identityToken;
    }

    @Override
    public String toString()
    {
        return "DockerRegistryAuthConfig [username=" + username
            + ", email=" + email
            + "]";
    }
}
