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
package com.chrisgahlert.gradledcomposeplugin.tasks

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

@CompileStatic
class DcomposeContainerCreateTask extends AbstractDcomposeTask {

    DcomposeContainerCreateTask() {
        dependsOn {
            container.links?.collect { it.container?.createTaskName }
        }
    }

    @Input
    String getImage() {
        container.image
    }

    @Input
    String getContainerName() {
        container.containerName
    }

    @Input
    @Optional
    List<String> getPortBindings() {
        container.portBindings
    }

    @Input
    @Optional
    List<String> getExposedPorts() {
        container.exposedPorts
    }

    @Input
    @Optional
    List<String> getCommand() {
        container.command
    }

    @Input
    @Optional
    List<String> getBinds() {
        container.binds
    }

    @Input
    @Optional
    List<String> getVolumes() {
        container.volumes
    }

    @Input
    boolean isPreserveVolumes() {
        container.preserveVolumes
    }

    @Input
    @Optional
    List<String> getLinks() {
        container.links?.collect {
            it as String
        }
    }

    // TODO: add cpu/mem options

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNewContainer() {
        runInDockerClasspath {
            def oldVolumes = [:]
            ignoreDockerException('NotFoundException') {
                if (preserveVolumes) {
                    client.inspectContainerCmd(containerName).exec().mounts.each { mount ->
                        oldVolumes.put(mount.destination, mount.name)
                    }
                }

                client.removeContainerCmd(containerName)
                        .withForce(true)
                        .withRemoveVolumes(!preserveVolumes)
                        .exec()
                logger.quiet("Removed container $containerName")
            }

            def cmd = client.createContainerCmd(image)

            if (portBindings) {
                def portParser = loadClass('com.github.dockerjava.api.model.PortBinding').getMethod('parse', String)
                cmd.withPortBindings(portBindings.collect { portParser.invoke(null, it) })
            }

            if (command) {
                cmd.withCmd(command)
            }

            def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
            if (volumes) {
                cmd.withVolumes(volumes.collect { volumeClass.newInstance(it) })
            }

            def allBinds = []
            def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)
            if (binds) {
                allBinds.addAll(binds.collect { bindParser.invoke(null, it) })
            }

            if (preserveVolumes) {
                def imageVolumes = []
                client.inspectImageCmd(image).exec().config.volumes?.keySet().each {
                    imageVolumes << volumeClass.newInstance(it)
                }

                def knownVolumes = new HashSet(imageVolumes)
                knownVolumes.addAll(cmd.volumes)

                for (def volume : knownVolumes) {
                    def bind = allBinds.find { volume == it.volume }

                    if (!bind) {
                        def volumeName
                        if (oldVolumes.containsKey(volume)) {
                            volumeName = oldVolumes.get(volume)
                        } else {
                            volumeName = containerName + '__' + GUtil.toLowerCamelCase(volume.path)
                        }
                        allBinds << bindParser.invoke(null, volumeName + ':' + volume.path)
                    }
                }
            }

            if (allBinds) {
                cmd.withBinds(allBinds)
            }

            if(links) {
                def linkParser = loadClass('com.github.dockerjava.api.model.Link').getMethod('parse', String)
                cmd.withLinks(links.collect { linkParser.invoke(null, it) })
            }

            if(exposedPorts) {
                def ePortParser = loadClass('com.github.dockerjava.api.model.ExposedPort').getMethod('parse', String)
                cmd.withExposedPorts(exposedPorts.collect { ePortParser.invoke(null, it) })
            }

            def result = cmd.withName(containerName).exec()
            logger.error("Created new container with id $result.id ($containerName)")
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            ignoreDockerException('NotFoundException') {
                def result = client.inspectContainerCmd(containerName).exec()
                result.state = null
                result.hostsPath = null
                result.resolvConfPath = null
                result.hostnamePath = null
                result.networkSettings = null

                result
            }
        }
    }

    @Input
    @TypeChecked(TypeCheckingMode.SKIP)
    def getImageState() {
        runInDockerClasspath {
            def result = client.inspectImageCmd(image).exec()
            toJson(result)
        }
    }
}
