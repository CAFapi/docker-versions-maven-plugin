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
package com.github.cafapi.docker_versions.docker.client;

import com.fasterxml.jackson.annotation.JsonProperty;

final class DockerAuthResponse
{
    @JsonProperty("token")
    private String token;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("issued_at")
    private String issuedAt;

    public String getToken()
    {
        return token;
    }

    public void setToken(final String token)
    {
        this.token = token;
    }

    public String getAccessToken()
    {
        return accessToken;
    }

    public void setAccessToken(final String accessToken)
    {
        this.accessToken = accessToken;
    }

    public Integer getExpiresIn()
    {
        return expiresIn;
    }

    public void setExpiresIn(final Integer expiresIn)
    {
        this.expiresIn = expiresIn;
    }

    public String getIssuedAt()
    {
        return issuedAt;
    }

    public void setIssuedAt(final String issuedAt)
    {
        this.issuedAt = issuedAt;
    }
}
