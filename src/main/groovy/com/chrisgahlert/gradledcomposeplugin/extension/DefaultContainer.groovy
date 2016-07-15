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
class DefaultContainer extends Container {

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
     * How long should it wait for the command to exit
     */
    int waitTimeout = 0

    /**
     * Whether a containers volumes should be preserved between
     */
    boolean preserveVolumes = false

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

    DefaultContainer(String name, String projectPath, Closure<String> dockerPrefix) {
        super(name, projectPath)
        this.dockerPrefix = dockerPrefix
    }

    @Override
    String getContainerName() {
        dockerPrefix() + name
    }

    @Override
    String getTag() {
        tag ?: (dockerPrefix() + '/' + name).replace('_', '')
    }

    @Override
    String getImage() {
        image ?: getTag()
    }

    @Override
    Set<Container> getLinkDependencies() {
        def result = new HashSet()

        links?.each { link ->
            if (link instanceof ContainerDependency) {
                result << ((ContainerDependency) link).container
            }
        }

        result
    }

    @Override
    Set<Container> getVolumesFromDependencies() {
        def result = new HashSet()

        volumesFrom?.each { from ->
            if (from instanceof Container) {
                result << from
            }
        }

        result
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    def findHostPort(Map<String, String> properties = [:], int containerPort) {
        if(hostPortBindings == null) {
            throw new GradleException("Host port bindings not available for $name - has it been started?")
        }

        def exposedPorts = hostPortBindings.findAll { exposedPort, bindings ->
            if(exposedPort.port == containerPort) {
                if(properties.protocol) {
                    return exposedPort.protocol as String == properties.protocol.toLowerCase()
                }

                return true
            }
        }

        if(exposedPorts.size() == 0) {
            throw new GradleException("Could not find container port $containerPort for container $name")
        }
        if(exposedPorts.size() > 1) {
            throw new GradleException("The port number for container port $containerPort is ambigous for container " +
                    "$name - please specify a protocol with findHostPort(port, protocol: 'tcp or udp')")
        }

        def bindings = exposedPorts.values().first()?.findAll { binding ->
            !properties.containsKey('hostIp') || binding.hostIp == properties.hostIp
        }

        if(!bindings) {
            throw new GradleException("The container port $containerPort for container $name has not been bound to a host port")
        }

        def possibleIps = bindings.collect { it.hostIp }.unique()
        if(bindings.size() > 1 && !properties.hostIp && possibleIps.size() > 1) {
            throw new GradleException("The container port $containerPort for container $name has multiple host ports bound - " +
                    "please specify a hostIp with findHostPort(port, hostIp: '127.0.0.1')! " +
                    "Possible values: " + possibleIps)
        }

        bindings[0].hostPortSpec
    }

    @Override
    String getDockerHost() {
        if(!dockerHost) {
            throw new GradleException("Docker hostname not available for container $name - has it been started?")
        }

        return dockerHost
    }

    @Override
    void setDockerHost(URI uri) {
        if(uri == null) {
            dockerHost = null
        } else if(uri.scheme == 'unix') {
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
            throw new GradleException("Either dockerFile or image must be provided for dcompose container '$name'")
        }

        if (baseDir == null) {
            if (dockerFilename != null) {
                throw new GradleException("Cannot set baseDir when image in use for dcompose container '$name'")
            }
            if (tag != null) {
                throw new GradleException("Cannot set tag when image in use for dcompose container '$name'")
            }
        }

        links?.each {
            if (it instanceof Container) {
                throw new GradleException("Invalid container link from $name to $it.name: Please use ${it.name}.link()")
            }
        }
    }

}
