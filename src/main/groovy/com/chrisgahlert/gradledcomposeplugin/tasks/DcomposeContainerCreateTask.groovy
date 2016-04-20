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

import com.chrisgahlert.gradledcomposeplugin.extension.Container
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
            container.linkDependencies.collect { it.createTaskName }
        }
        dependsOn {
            container.volumesFromDependencies.collect { it.createTaskName }
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

    @Input
    @Optional
    List<String> getVolumesFrom() {
        container.volumesFrom?.collect {
            it as String
        }
    }

    @Input
    @Optional
    List<String> getExtraHosts() {
        container.extraHosts
    }

    @Input
    @Optional
    String getWorkingDir() {
        container.workingDir
    }

    @Input
    @Optional
    List<String> getDns() {
        container.dns
    }

    @Input
    @Optional
    List<String> getDnsSearch() {
        container.dnsSearch
    }

    @Input
    @Optional
    String getHostName() {
        container.hostName
    }

    @Input
    @Optional
    List<String> getEntrypoints() {
        container.entrypoints
    }

    @Input
    @Optional
    List<String> getEnv() {
        container.env
    }

    @Input
    @Optional
    String getUser() {
        container.user
    }

    @Input
    @Optional
    Boolean getPublishAllPorts() {
        container.publishAllPorts
    }

    @Input
    @Optional
    Boolean getReadonlyRootfs() {
        container.readonlyRootfs
    }

    @Input
    @Optional
    Boolean getAttachStdin() {
        container.attachStdin
    }

    @Input
    @Optional
    Boolean getAttachStdout() {
        container.attachStdout
    }

    @Input
    @Optional
    Boolean getAttachStderr() {
        container.attachStderr
    }

    @Input
    @Optional
    Boolean getPrivileged() {
        container.privileged
    }

    @Input
    @Optional
    String getNetworkMode() {
        container.networkMode
    }

    // TODO: add cpu/mem options

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNewContainer() {
        runInDockerClasspath {
            removeOldContainer(container)

            def cmd = client.createContainerCmd(image)

            if (portBindings) {
                def portParser = loadClass('com.github.dockerjava.api.model.PortBinding').getMethod('parse', String)
                cmd.withPortBindings(portBindings.collect { portParser.invoke(null, it) })
            }

            if (command) {
                cmd.withCmd(command)
            }

            if (volumes) {
                def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
                cmd.withVolumes(volumes.collect { volumeClass.newInstance(it) })
            }

            def allBinds = []
            if (binds) {
                def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)
                allBinds.addAll(binds.collect { bindParser.invoke(null, it) })
            }

            if (preserveVolumes) {
                createMissingBinds(allBinds, cmd.volumes)
            }

            if (allBinds) {
                cmd.withBinds(allBinds)
            }

            if (links) {
                def linkParser = loadClass('com.github.dockerjava.api.model.Link').getMethod('parse', String)
                cmd.withLinks(links.collect { linkParser.invoke(null, it) })
            }

            if (exposedPorts) {
                def ePortParser = loadClass('com.github.dockerjava.api.model.ExposedPort').getMethod('parse', String)
                cmd.withExposedPorts(exposedPorts.collect { ePortParser.invoke(null, it) })
            }

            if (volumesFrom) {
                def volumesFromParser = loadClass('com.github.dockerjava.api.model.VolumesFrom').getMethod('parse', String)
                cmd.withVolumesFrom(volumesFrom.collect { volumesFromParser.invoke(null, it) })
            }

            if (extraHosts) {
                cmd.withExtraHosts(extraHosts)
            }

            if (workingDir) {
                cmd.withWorkingDir(workingDir)
            }

            if (dns) {
                cmd.withDns(dns)
            }

            if (dnsSearch) {
                cmd.withDnsSearch(dnsSearch)
            }

            if (hostName) {
                cmd.withHostName(hostName)
            }

            if (entrypoints) {
                cmd.withEntrypoint(entrypoints)
            }

            if (env) {
                cmd.withEnv(env)
            }

            if (user) {
                cmd.withUser(user)
            }

            if (publishAllPorts != null) {
                cmd.withPublishAllPorts(publishAllPorts)
            }

            if (readonlyRootfs != null) {
                cmd.withReadonlyRootfs(readonlyRootfs)
            }

            if (attachStdin != null) {
                cmd.withAttachStdin(attachStdin)
            }

            if (attachStdout != null) {
                cmd.withAttachStdout(attachStdout)
            }

            if (attachStderr != null) {
                cmd.withAttachStderr(attachStderr)
            }

            if (privileged != null) {
                cmd.withPrivileged(privileged)
            }

            if (networkMode) {
                cmd.withNetworkMode(networkMode)
            }

            def result = cmd.withName(containerName).exec()
            logger.quiet("Created new container with id $result.id ($containerName)")
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void createMissingBinds(ArrayList allBinds, commandVolumes) {
        def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
        def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)

        def imageVolumes = []
        client.inspectImageCmd(image).exec().config.volumes?.keySet().each {
            imageVolumes << volumeClass.newInstance(it)
        }

        def knownVolumes = new HashSet(imageVolumes)
        knownVolumes.addAll(commandVolumes)

        for (def volume : knownVolumes) {
            def bind = allBinds.find { volume == it.volume }

            if (!bind) {
                def volumeName = containerName + '__' + GUtil.toLowerCamelCase(volume.path)
                logger.info("Using named volume '$volumeName' (thus: persistent) for container $containerName")
                allBinds << bindParser.invoke(null, volumeName + ':' + volume.path)
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void removeOldContainer(Container oldContainer, Container linkedFromContainer = null) {
        ignoreDockerException('NotFoundException') {
            def result = client.inspectContainerCmd(oldContainer.containerName).exec()

            otherContainers.each {
                if (it.linkDependencies.contains(oldContainer) || it.volumesFromDependencies.contains(oldContainer)) {
                    removeOldContainer(it, oldContainer)
                }
            }

            client.removeContainerCmd(oldContainer.containerName)
                    .withForce(true)
                    .withRemoveVolumes(!oldContainer.preserveVolumes)
                    .exec()


            logger.quiet("Removed old container with id $result.id ($oldContainer.containerName)" +
                    (linkedFromContainer != null ? " as it depends on $linkedFromContainer.containerName" : ""))
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
