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

import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.util.GUtil

@CompileStatic
class Container {
    /**
     * The internal dcompose container name, e.g. "database"
     */
    final String name

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


    Container(String name, Closure<String> dockerPrefix) {
        this.name = name
        this.dockerPrefix = dockerPrefix
    }

    String getContainerName() {
        dockerPrefix() + name
    }


    def methodMissing(String name, def args) {
        throw new MissingMethodException(name, Container, args as Object[])
    }

    String getPullTaskName() {
        "pull${taskLabel}Image"
    }

    String getCreateTaskName() {
        "create${taskLabel}Container"
    }

    String getStartTaskName() {
        "start${taskLabel}Container"
    }

    String getStopTaskName() {
        "stop${taskLabel}Container"
    }

    String getRemoveContainerTaskName() {
        "remove${taskLabel}Container"
    }

    String getRemoveImageTaskName() {
        "remove${taskLabel}Image"
    }

    String getBuildTaskName() {
        "build${taskLabel}Image"
    }

    private String getTaskLabel() {
        GUtil.toCamelCase(name)
    }

    String getTag() {
        tag ?: (dockerPrefix() + '/' + name).replace('_', '')
    }

    String getImage() {
        image ?: getTag()
    }

    ContainerDependency link(String alias = name) {
        new ContainerDependency({ "$containerName:$alias" }, this)
    }

    Set<Container> getLinkDependencies() {
        def result = new HashSet()

        links?.each { link ->
            if (link instanceof ContainerDependency) {
                result << ((ContainerDependency) link).container
            }
        }

        result
    }

    Set<Container> getVolumesFromDependencies() {
        def result = new HashSet()

        volumesFrom?.each { from ->
            if (from instanceof Container) {
                result << from
            }
        }

        result
    }

    @Override
    String toString() {
        containerName
    }

    boolean hasImage() {
        image
    }

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

    static class ContainerDependency {
        final Container container
        final private Closure definitionAction

        ContainerDependency(Closure definitionAction, Container container = null) {
            this.container = container
            this.definitionAction = definitionAction
        }

        String getDefinition() {
            definitionAction() as String
        }

        @Override
        String toString() {
            getDefinition()
        }
    }

}
