# docker-versions-maven-plugin

This is a maven plugin that retags the Docker images that are used by a project, to a project specific name.  
The project specific name can then be used in place of the actual Docker image name.

## Goals overview:

### populate-project-registry

Retags the Docker images that are used by a project, to a project specific name.

#### Required Parameters

<table class="table">
  <tr>
    <th> Name        </th>
    <th> Type        </th>
    <th> Description </th>
  </tr>
  <tr>
    <td> &lt;repository&gt; </td>
    <td> String  </td>
    <td> The repository and name of the docker image in the format &lt;repository&gt;/&lt;name&gt;. </td>
  </tr>
  <tr>
    <td> &lt;tag&gt; </td>
    <td> String </td>
    <td> The docker image tag. </td>
  </tr>
</table>

#### Optional Parameters

<table class="table">
  <tr>
    <th> Name        </th>
    <th> Type        </th>
    <th> Description </th>
  </tr>
  <tr>
    <td> &lt;digest&gt; </td>
    <td> String  </td>
    <td> The digest value of docker image. It will be used to ensure that the correct image is being tagged. </td>
  </tr>
  <tr>
    <td> &lt;targetRepository&gt; </td>
    <td> String </td>
    <td> This string will be used in the project specific name tag instead of the repository value. </td>
  </tr>
</table>

### depopulate-project-registry

Removes the project specific name tag from the Docker images that are used by a project.

#### Required Parameters

<table class="table">
  <tr>
    <th> Name        </th>
    <th> Type        </th>
    <th> Description </th>
  </tr>
  <tr>
    <td> &lt;repository&gt; </td>
    <td> String  </td>
    <td> The repository and name of the docker image in the format &lt;repository&gt;/&lt;name&gt;. </td>
  </tr>
</table>

#### Optional Parameters

<table class="table">
  <tr>
    <th> Name        </th>
    <th> Type        </th>
    <th> Description </th>
  </tr>
  <tr>
    <td> &lt;targetRepository&gt; </td>
    <td> String </td>
    <td> This string will be used in the project specific name tag instead of the repository value. </td>
  </tr>
</table>

### For example:
```
<plugin>
    <groupId>com.github.cafapi.plugins.docker.versions</groupId>
    <artifactId>docker-versions-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <extensions>true</extensions>
    <executions>
        <execution>
            <id>retag</id>
            <phase>pre-integration-test</phase>
            <goals>
                <goal>populate-project-registry</goal>
            </goals>
        </execution>
        <execution>
            <id>untag</id>
            <phase>post-integration-test</phase>
            <goals>
                <goal>depopulate-project-registry</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <imageManagement>
            <image>
                <repository>${dockerHubPublic}/cafapi/opensuse-jre17</repository>
                <tag>1.4.3</tag>
                <digest>sha256:76b8dc916151a5ede5d8a999fcd0929ca3cd3a9dbf67085f65ef98b5279359f4</digest>
            </image>
            <image>
                <repository>${dockerHubPublic}/cafapi/prereleases</repository>
                <targetRepository>cafapi/opensuse-tomcat-jre17</targetRepository>
                <tag>opensuse-tomcat-jre17-2.0.0-SNAPSHOT</tag>
            </image>
        </imageManagement>
    </configuration>
</plugin>
```

A Maven property could be used to specify the project registry:

```
<properties>
    <projectDockerRegistry>${project.name}-${project.version}.project-registries.local</projectDockerRegistry>
</properties>
```
The Maven plugin will pull the images if necessary, and then retag them into the project registry that is specified by the property.

Source code references to:

`${dockerHubPublic}/cafapi/opensuse-jre17:1`

would be updated to reference the version from the project-specific registry instead:

`${projectDockerRegistry}/cafapi/opensuse-jre17:latest`

Source code references to:

`${dockerHubPublic}/cafapi/prereleases:opensuse-tomcat-jre17-2.0.0-SNAPSHOT`

would be updated to reference the version from the project-specific registry instead:

`${projectDockerRegistry}/cafapi/opensuse-tomcat-jre17`

**Note** The version need not be specified when referencing images in the project-specific registry.
This means that only the plugin configuration needs to be updated to update the Docker image versions.

### Configuration
The following configuration options can be set via environment variables.  

- **DOCKER_HOST**: The Docker Host URL, e.g. tcp://localhost:2376 or unix:///var/run/docker.sock.
- **DOCKER_TLS_VERIFY**: Enable/disable TLS verification (switch between http and https protocol).
- **DOCKER_CERT_PATH**: Path to the certificates needed for TLS verification.
- **DOCKER_CONFIG**: Path for additional docker configuration files (like .dockercfg).
- **CONNECTION_TIMEOUT_SECONDS**: Determines the timeout until a new connection to DOCKER_HOST is fully established, default is 30s.
- **RESPONSE_TIMEOUT_SECONDS**: Determines the timeout until arrival of a response from the DOCKER_HOST, default is 45s.
- **DOWNLOAD_IMAGE_TIMEOUT_SECONDS**: Determines the timeout for an image pull to be completed, default is 300s.
