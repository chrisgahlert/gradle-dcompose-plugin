/*
 * Copyright 2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chrisgahlert.gradledcomposeplugin.extension

import com.chrisgahlert.gradledcomposeplugin.utils.ImageRef
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.file.CopySpec

@TypeChecked
class DefaultService extends Service {

    /**
     * The prefix for the actual name used in Docker later, e.g. "dcompose_12345_"
     */
    final Closure<String> dockerPrefix

    @Deprecated
    private final Closure<String> dockerHost

    /**
     * The name of the pre-existing image that should be pulled and used for creating containers.
     * Cannot be used in combination with baseDir (for building images).
     */
    String image

    /**
     * Whether the "start<Name>Container" command should wait for the container to exit before continuing
     */
    boolean waitForCommand

    /**
     * Whether we should wait for the health check to succeed (if any) before continuing
     */
    boolean waitForHealthcheck

    /**
     * Whether the exit code will be checked after running the container. (Only applies if waitForCommand is true.)
     */
    boolean ignoreExitCode

    /**
     * How long should it wait for the command to exit
     */
    int waitTimeout = 0

    /**
     * How long should it wait for the command to exit
     */
    Integer stopTimeout

    /**
     * Whether a containers volumes should be preserved between
     */
    boolean preserveVolumes = false

    /**
     * Whether the service should be included when creating the docker-compose.yml
     */
    boolean deploy = true

    /**
     * Services that the current service depends on
     */
    List<Service> dependsOn

    /**
     * The name of the repository for publishing
     */
    String repository

    /**
     * Whether the image pull should be forced when pulling/building an image
     */
    boolean forcePull = false

    /**
     * Create container specific properties. Properties are optional by default.
     */
    List<String> command
    List<String> entrypoints
    List<String> env
    String workingDir
    String user
    Boolean readonlyRootfs
    List<String> volumes
    List binds
    List volumesFrom
    List<String> exposedPorts
    List<String> portBindings
    Boolean publishAllPorts
    List links
    String hostName
    List<String> dns
    List<String> dnsSearch
    List<String> extraHosts
    String networkMode
    Boolean attachStdin
    Boolean attachStdout
    Boolean attachStderr
    Boolean privileged
    List<? extends Network> networks
    List<String> aliases
    String restart
    Long memLimit
    String logConfig

    /**
     * Build image specific properties (can only be used when no image is defined). Properties are optional by default.
     */
    File baseDir          // Required
    CopySpec buildFiles
    String dockerFilename // optional, Default: "Dockerfile"
    Long memory
    Long memswap
    Integer cpushares
    String cpusetcpus
    Map<String, String> buildArgs
    Boolean forceRemoveImage
    Boolean noPruneParentImages
    Boolean buildNoCache
    Boolean buildRemove

    /**
     * Results populated after starting a container
     */
    Map hostPortBindings
    Integer exitCode
    String imageId
    String containerId
    String repositoryDigest

    DefaultService(String name, String projectPath, Closure<String> dockerPrefix, Closure<String> dockerHost) {
        super(name, projectPath)
        this.dockerPrefix = dockerPrefix
        this.dockerHost = dockerHost
    }

    void setCommand(String command) {
        this.command = ['sh', '-c', command]
    }

    void setCommand(List<String> command) {
        this.command = command
    }

    void setEntrypoints(List<String> entrypoints) {
        this.entrypoints = entrypoints
    }

    void setEntrypoints(String entrypoint) {
        this.entrypoints = ['sh', '-c', entrypoint]
    }

    @Override
    String getContainerName() {
        dockerPrefix() + name
    }

    @Override
    String getImage() {
        ImageRef.parse(image).toString()
    }

    @Override
    String getRepository() {
        ImageRef.parse(repository ?: (hasImage() ? image : (dockerPrefix() + '/' + name).replace('_', '')))
                .toString()
                .toLowerCase()
    }

    @Override
    String getRepositoryDigest() {
        if (!repositoryDigest) {
            throw new GradleException("Cannot determine image digest for service '$name' - has it been pulled/pushed yet? " +
                    "Try running the $pushImageTaskName task first or use 'createComposeFile.useTags = true' to use tags instead of digests!")
        }

        repositoryDigest
    }

    @Deprecated
    void setTag(String tag) {
        throw new GradleException("Setting tag is no longer supported. Please include it in the repository defintion: " +
                "dcompose.${name}.repository = 'repository:$tag'")
    }

    @Override
    Set<Service> getLinkDependencies() {
        def result = new HashSet()

        links?.each { link ->
            if (link instanceof ServiceDependency) {
                result << ((ServiceDependency) link).service
            }
        }

        result
    }

    @Override
    Set<Service> getVolumesFromDependencies() {
        def result = new HashSet()

        volumesFrom?.each { from ->
            if (from instanceof Service) {
                result << from
            }
        }

        result
    }

    @Override
    List<Service> getDependsOn() {
        dependsOn ?: [] as List<Service>
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    def findHostPort(Map<String, String> properties = [:], int containerPort) {
        if (hostPortBindings == null) {
            throw new GradleException("Host port bindings not available for $name - has it been started?")
        }

        def exposedPorts = hostPortBindings.findAll { exposedPort, bindings ->
            if (exposedPort.port == containerPort) {
                if (properties.protocol) {
                    return exposedPort.protocol as String == properties.protocol.toLowerCase()
                }

                return true
            }
        }

        if (exposedPorts.size() == 0) {
            throw new GradleException("Could not find container port $containerPort for service $name")
        }
        if (exposedPorts.size() > 1) {
            throw new GradleException("The port number for container port $containerPort is ambigous for service " +
                    "$name - please specify a protocol with findHostPort(port, protocol: 'tcp or udp')")
        }

        def bindings = exposedPorts.values().first()?.findAll { binding ->
            !properties.containsKey('hostIp') || binding.hostIp == properties.hostIp
        }

        if (!bindings) {
            throw new GradleException("The container port $containerPort for service $name has not been bound to a host port")
        }

        def possibleIps = bindings.collect { it.hostIp }.unique()
        if (bindings.size() > 1 && !properties.hostIp && possibleIps.size() > 1) {
            throw new GradleException("The container port $containerPort for service $name has multiple host ports bound - " +
                    "please specify a hostIp with findHostPort(port, hostIp: '127.0.0.1')! " +
                    "Possible values: " + possibleIps)
        }

        bindings[0].hostPortSpec
    }

    @Override
    @Deprecated
    String getDockerHost() {
        dockerHost()
    }

    @Override
    String getContainerId() {
        if (!containerId) {
            throw new GradleException("Container id not available for service $name - has it been started?")
        }

        containerId
    }

    @Override
    boolean hasImage() {
        image
    }

    @Override
    void validate() {
        if (baseDir != null && image != null) {
            throw new GradleException("Either image or baseDir (but not both) can be provided for dcompose service '$name'")
        }

        if (baseDir == null && image == null && buildFiles == null) {
            throw new GradleException("At least one of the image, baseDir or buildFiles properties must be provided for dcompose service '$name'")
        }

        if (image != null) {
            if (dockerFilename != null) {
                throw new GradleException("Cannot set baseDir when image is used for dcompose service '$name'")
            }

            if (buildFiles != null) {
                throw new GradleException("Cannot set buildFiles, when using image property for dcompose service '$name'")
            }
        }

        if (waitForHealthcheck && waitForCommand) {
            throw new GradleException("Can either wait for the healthcheck to pass or the command to complete for dcompose service '$name' - not both")
        }


        links?.each {
            if (it instanceof Service) {
                throw new GradleException("Invalid service link from $name to $it.name: Please use ${it.name}.link()")
            }

            if (it instanceof ServiceDependency) {
                def dep = it as ServiceDependency

                def sharedNetworks = new HashSet<>(networks)
                sharedNetworks.retainAll(dep.service.networks)
                if (sharedNetworks.size() == 0) {
                    throw new GradleException("Cannot create link from $projectPath:$name " +
                            "to $dep.service.projectPath:$dep.service.name: " +
                            "They don't share any networks. Please make sure they are on the same network")
                }
            }
        }

        binds?.each {
            if (it instanceof Volume) {
                throw new GradleException("Invalid bind in $name for volume $it.name: Please use ${it.name}.bind('/path')")
            }
        }

        if (networkMode && networks.find { Network net -> net.name != Network.DEFAULT_NAME }) {
            throw new GradleException("Cannot combine networkMode and networks for service $name")
        }

        if (imageId != null) {
            throw new ReadOnlyPropertyException('imageId', getClass())
        }

        if (exitCode != null) {
            throw new ReadOnlyPropertyException('exitCode', getClass())
        }

        if (hostPortBindings != null) {
            throw new ReadOnlyPropertyException('hostPortBindings', getClass())
        }

        if (networks.find { !(it instanceof Network) }) {
            throw new GradleException("Can only set instances of Network on dcompose.${name}.networks - " +
                    "please use networkName or network('networkName') instead")
        }
    }

    String getDockerFilename() {
        dockerFilename ?: 'Dockerfile'
    }

    int getExitCode() {
        if (exitCode == null) {
            throw new GradleException("Cannot get exitCode of service $name - has it been started (with waitForCommand = true)?")
        }
        exitCode
    }

    @Override
    void setExitCode(int exitCode) {
        this.exitCode = exitCode
    }

    String getImageId() {
        if (imageId == null) {
            throw new GradleException("Cannot get imageId of service $name - has it been built/pulled?")
        }
        imageId
    }

    void setImageId(String imageId) {
        this.imageId = imageId?.replaceFirst(/^sha256:/, '')
    }
}
