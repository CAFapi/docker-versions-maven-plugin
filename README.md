# docker-versions-maven-plugin

This is a maven plugin that retags the Docker images that are used by a project, to a project specific name.  
The project specific name can then be used in place of the actual Docker image name.

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

`${homeDockerReleaseRegistry}/caf/search-and-analytics:8`

would be updated to reference the version from the project-specific registry instead:

`${projectDockerRegistry}/caf/search-and-analytics:latest`

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


