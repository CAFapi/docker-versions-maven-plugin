# Docker Versions Maven Plugin

This is a maven plugin that retags the Docker images that are used by a project, to a project specific name.  
The project specific name can then be used in place of the actual Docker image name.

## Goals overview
The `docker-versions` plugin has the following goals.

- docker-versions:populate-project-registry
- docker-versions:depopulate-project-registry
- docker-versions:help

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

### help

Displays the help information.

### For example:
```
<plugin>
    <groupId>com.github.cafapi.plugins.docker.versions</groupId>
    <artifactId>docker-versions-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>populate-project-registry</id>
            <phase>initialize</phase>
            <goals>
                <goal>populate-project-registry</goal>
            </goals>
        </execution>
        <execution>
            <id>depopulate-project-registry</id>
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

**Note:** The version need not be specified when referencing images in the project-specific registry.
This means that only the plugin configuration needs to be updated to update the Docker image versions.

### Authentication
The authentication information for the docker registries is looked up two places in this order:  
1. From the [Maven settings](https://maven.apache.org/settings.html#servers) stored typically in ~/.m2/settings.xml
2. From the [Docker settings](https://docs.docker.com/engine/reference/commandline/cli/#credential-store-options) stored in ~/.docker/config.json

The following properties are read:
- username: User to authenticate
- password: Password to authenticate
- email: Optional email address which is sent to the registry
- auth: Optional base64 encoded 'username:password' string, which can be set instead of username and password

### Configuration
Http connection timeout can be set in the plugin configuration. This configuration is optional and the values indicate time in seconds.

```
<httpConfiguration>
    <connectionTimout>30</connectionTimout>
    <responseTimout>45</responseTimout>
    <downloadImageTimout>100</downloadImageTimout>
</httpConfiguration>
```

The following configuration options can be set via environment variables.  

<table class="table">
  <tr>
    <th> Name        </th>
    <th> Description </th>
  </tr>
  <tr>
    <td> DOCKER_HOST </td>
    <td> The Docker Host URL, e.g. tcp://localhost:2376 or unix:///var/run/docker.sock. </td>
  </tr>
  <tr>
    <td> DOCKER_TLS_VERIFY </td>
    <td> Enable/disable TLS verification (switch between http and https protocol). </td>
  </tr>
  <tr>
    <td> DOCKER_CERT_PATH </td>
    <td> Path to the certificates needed for TLS verification. </td>
  </tr>
  <tr>
    <td> DOCKER_CONFIG </td>
    <td> Path for additional docker configuration files (like .dockercfg). </td>
  </tr>
  <tr>
    <td> CONNECTION_TIMEOUT_SECONDS </td>
    <td> Determines the timeout until a new connection to DOCKER_HOST is fully established, default is 30s. </td>
  </tr>
  <tr>
    <td> RESPONSE_TIMEOUT_SECONDS </td>
    <td> Determines the timeout until arrival of a response from the DOCKER_HOST, default is 45s. </td>
  </tr>
  <tr>
    <td> DOWNLOAD_IMAGE_TIMEOUT_SECONDS </td>
    <td> Determines the timeout for an image pull to be completed, default is 300s. </td>
  </tr>
</table>
