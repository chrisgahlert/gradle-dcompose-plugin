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

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException

@TypeChecked
class DefaultService extends Service {

    /**
     * The prefix for the actual name used in Docker later, e.g. "dcompose_12345_"
     */
    final Closure<String> dockerPrefix

    /**
     * The name of the pre-existing image that should be pulled and used for creating containers.
     * Cannot be used in combination with baseDir (for building images).
     */
    private String image

    /**
     * Whether the "start<Name>Container" command should wait for the container to exit before continuing
     */
    boolean waitForCommand

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
     * Create container specific properties. Properties are optional by default.
     */
    List<String> command
    List<String> entrypoints
    List<String> env
    String workingDir
    String user
    Boolean readonlyRootfs
    List<String> volumes
    List<String> binds
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

    /**
     * Build image specific properties (can only be used when no image is defined). Properties are optional by default.
     */
    File baseDir          // Required
    String dockerFilename // optional, Default: "Dockerfile"
    String tag            // The image tag name, tag should be used
    Long memory
    Long memswap
    String cpushares
    String cpusetcpus
    Map<String, String> buildArgs
    Boolean forceRemoveImage
    Boolean noPruneParentImages
    Boolean buildNoCache
    Boolean buildRemove
    Boolean buildPull

    /**
     * Results populated after starting a container
     */
    Map hostPortBindings
    String dockerHost
    Integer exitCode
    String imageId

    DefaultService(String name, String projectPath, Closure<String> dockerPrefix) {
        super(name, projectPath)
        this.dockerPrefix = dockerPrefix
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
    String getTag() {
        tag ?: 'latest'
    }

    @Override
    String getImage() {
        image
    }

    @Override
    String getRepository() {
        repository ?: (dockerPrefix() + '/' + name).replace('_', '')
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
    String getDockerHost() {
        if (!dockerHost) {
            throw new GradleException("Docker hostname not available for service $name - has it been started?")
        }

        return dockerHost
    }

    @Override
    void setDockerHost(URI uri) {
        if (uri == null) {
            dockerHost = null
        } else if (uri.scheme == 'unix') {
            dockerHost = 'localhost'
        } else {
            dockerHost = uri.host
        }
    }

    @Override
    boolean hasImage() {
        image
    }

    @Override
    void validate() {
        if (!(baseDir == null ^ image == null)) {
            throw new GradleException("Either dockerFile or image must be provided for dcompose service '$name'")
        }

        if (baseDir == null) {
            if (dockerFilename != null) {
                throw new GradleException("Cannot set baseDir when image in use for dcompose service '$name'")
            }
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

        if (dockerHost != null) {
            throw new ReadOnlyPropertyException('dockerHost', getClass())
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
}
