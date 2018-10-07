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
package com.chrisgahlert.gradledcomposeplugin.tasks.container

import com.chrisgahlert.gradledcomposeplugin.extension.Network
import com.chrisgahlert.gradledcomposeplugin.extension.Service
import com.chrisgahlert.gradledcomposeplugin.extension.Volume
import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeServiceTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

@TypeChecked
class DcomposeContainerCreateTask extends AbstractDcomposeServiceTask {

    DcomposeContainerCreateTask() {
        dependsOn {
            service.linkDependencies.collect { "$it.projectPath:$it.createContainerTaskName" }
        }

        dependsOn {
            service.volumesFromDependencies.collect { "$it.projectPath:$it.createContainerTaskName" }
        }

        dependsOn {
            service.dependsOn.collect { "$it.projectPath:$it.createContainerTaskName" }
        }

        dependsOn {
            if (service.hasImage()) {
                service.pullImageTaskName
            } else {
                service.buildImageTaskName
            }
        }

        dependsOn {
            service.networks.collect { "$it.projectPath:$it.createTaskName" }
        }

        dependsOn {
            service.binds.findAll {
                it instanceof Volume.VolumeDependency
            }.collect {
                def bind = it as Volume.VolumeDependency
                "$bind.volume.projectPath:$bind.volume.createTaskName"
            }
        }
    }

    @Input
    String getContainerName() {
        service.containerName
    }

    @Input
    @Optional
    List<String> getPortBindings() {
        service.portBindings
    }

    @Input
    @Optional
    List<String> getExposedPorts() {
        service.exposedPorts
    }

    @Input
    @Optional
    List<String> getCommand() {
        service.command
    }

    @Input
    @Optional
    List<String> getBinds() {
        service.binds.collect { it.toString() }
    }

    @Input
    @Optional
    List<String> getVolumes() {
        service.volumes
    }

    @Input
    boolean isPreserveVolumes() {
        service.preserveVolumes
    }

    @Input
    @Optional
    List<String> getLinks() {
        service.links?.collect {
            it as String
        }
    }

    @Input
    @Optional
    List<String> getVolumesFrom() {
        service.volumesFrom?.collect {
            it as String
        }
    }

    @Input
    @Optional
    List<String> getExtraHosts() {
        service.extraHosts
    }

    @Input
    @Optional
    String getWorkingDir() {
        service.workingDir
    }

    @Input
    @Optional
    List<String> getDns() {
        service.dns
    }

    @Input
    @Optional
    List<String> getDnsSearch() {
        service.dnsSearch
    }

    @Input
    @Optional
    String getHostName() {
        service.hostName
    }

    @Input
    @Optional
    List<String> getEntrypoints() {
        service.entrypoints
    }

    @Input
    @Optional
    List<String> getEnv() {
        service.env
    }

    @Input
    @Optional
    String getUser() {
        service.user
    }

    @Input
    @Optional
    Boolean getPublishAllPorts() {
        service.publishAllPorts
    }

    @Input
    @Optional
    Boolean getReadonlyRootfs() {
        service.readonlyRootfs
    }

    @Input
    @Optional
    Boolean getAttachStdin() {
        service.attachStdin
    }

    @Input
    @Optional
    Boolean getAttachStdout() {
        service.attachStdout
    }

    @Input
    @Optional
    Boolean getAttachStderr() {
        service.attachStderr
    }

    @Input
    @Optional
    Boolean getPrivileged() {
        service.privileged
    }

    @Input
    @Optional
    String getNetworkMode() {
        service.networkMode
    }

    @Input
    boolean isStdinOpen() {
        attachStdin && service.waitForCommand
    }

    @Input
    @Optional
    List<String> getNetworkNames() {
        service.networks.collect { it.networkName }
    }

    @Input
    @Optional
    List<String> getAliases() {
        service.aliases
    }

    @Input
    @Optional
    String getRestart() {
        service.restart
    }

    @Input
    String getImageId() {
        service.imageId
    }

    @Input
    @Optional
    Long getMemory() {
        service.memory
    }

    @Input
    @Optional
    Long getMemswap() {
        service.memswap
    }

    @Input
    @Optional
    Integer getCpushares() {
        service.cpushares
    }

    @Input
    @Optional
    String getCpusetcpus() {
        service.cpusetcpus
    }

    @Input
    @Optional
    String getLogConfig() {
        service.logConfig
    }

    @Input
    @Optional
    Map<String, String> getLogOpts() {
        service.logOpts
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNewContainer() {
        dockerExecutor.runInDockerClasspath {
            removeOldContainer(service)

            def cmd = dockerExecutor.client.createContainerCmd(imageId)

            if (portBindings) {
                def portParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.PortBinding')
                        .getMethod('parse', String)
                cmd.withPortBindings(portBindings.collect { portParser.invoke(null, it as String) })
            }

            if (command) {
                cmd.withCmd(command as String[])
            }

            if (volumes) {
                def volumeClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.Volume')
                cmd.withVolumes(volumes.collect { volumeClass.newInstance(it as String) })
            }

            def allBinds = []
            if (binds) {
                def bindParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.Bind')
                        .getMethod('parse', String)
                allBinds.addAll(binds.collect { bindParser.invoke(null, it as String) })
            }

            if (preserveVolumes) {
                createMissingBinds(allBinds, cmd.volumes)
            }

            if (allBinds) {
                cmd.withBinds(allBinds)
            }

            if (exposedPorts) {
                def ePortParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.ExposedPort')
                        .getMethod('parse', String)
                cmd.withExposedPorts(exposedPorts.collect { ePortParser.invoke(null, it as String) })
            }

            if (volumesFrom) {
                def volumesFromParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.VolumesFrom')
                        .getMethod('parse', String)
                cmd.withVolumesFrom(volumesFrom.collect { volumesFromParser.invoke(null, it as String) })
            }

            if (extraHosts) {
                cmd.withExtraHosts(extraHosts as String[])
            }

            if (workingDir) {
                cmd.withWorkingDir(workingDir as String)
            }

            if (dns) {
                cmd.withDns(dns as String[])
            }

            if (dnsSearch) {
                cmd.withDnsSearch(dnsSearch as String[])
            }

            if (hostName) {
                cmd.withHostName(hostName as String)
            }

            if (entrypoints) {
                cmd.withEntrypoint(entrypoints as String[])
            }

            if (env) {
                cmd.withEnv(env as String[])
            }

            if (user) {
                cmd.withUser(user as String)
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

            if (stdinOpen) {
                cmd.withStdInOnce(stdinOpen).withStdinOpen(stdinOpen)
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

            if (restart != null) {
                def policyParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.RestartPolicy')
                        .getMethod('parse', String)
                cmd.withRestartPolicy(policyParser.invoke(null, restart as String))
            }

            if (memory != null) {
                cmd.withMemory(memory)
            }

            if (memswap != null) {
                cmd.withMemorySwap(memswap)
            }

            if (cpusetcpus != null) {
                cmd.withCpusetCpus(cpusetcpus as String)
            }

            if (cpushares != null) {
                cmd.withCpuShares(cpushares)
            }

            if (networkMode) {
                cmd.withNetworkMode(networkMode)
            }

            if (logConfig) {
                def loggingType = dockerExecutor.loadClass('com.github.dockerjava.api.model.LogConfig$LoggingType').values().find {
                    it.type == logConfig
                }
                def logConfClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.LogConfig')
                def args = logOpts ? [loggingType, logOpts] : [loggingType]
                cmd.withLogConfig(logConfClass.newInstance(*args))
            }

            def result = cmd.withName(containerName).exec()
            service.containerId = result.id
            logger.info("Created new container with id $result.id ($containerName)")

            if (!networkMode) {
                ignoreDockerException('NotFoundException') {
                    dockerExecutor.client.disconnectFromNetworkCmd()
                            .withNetworkId('bridge')
                            .withContainerId(service.containerId)
                            .exec()
                }

                service.networks?.each { Network network ->
                    def defaultAliases = [service.name, service.nameCamelCase, service.nameDashed]
                    def serviceAliases = service.aliases ?: []
                    def aliases = (serviceAliases + defaultAliases).unique()

                    def networkSettings = dockerExecutor.loadClass('com.github.dockerjava.api.model.ContainerNetwork')
                            .newInstance()
                            .withAliases(aliases.collect { it as String })

                    if (links) {
                        def linkParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.Link')
                                .getMethod('parse', String)
                        networkSettings.withLinks(links.collect { linkParser.invoke(null, it as String) })
                    }

                    dockerExecutor.client.connectToNetworkCmd()
                            .withNetworkId(network.networkName)
                            .withContainerId(service.containerId)
                            .withContainerNetwork(networkSettings)
                            .exec()

                    logger.info("Connected container $containerName to network $network")
                }
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void createMissingBinds(ArrayList allBinds, commandVolumes) {
        def volumeClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.Volume')
        def bindParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.Bind')
                .getMethod('parse', String)

        def imageVolumes = []
        dockerExecutor.client.inspectImageCmd(imageId).exec().config.volumes?.keySet().each {
            imageVolumes << volumeClass.newInstance(it)
        }

        def knownVolumes = new HashSet(imageVolumes)
        knownVolumes.addAll(commandVolumes)

        for (def volume : knownVolumes) {
            def bind = allBinds.find { volume == it.volume }

            if (!bind) {
                def volumeName = containerName + '__' + GUtil.toLowerCamelCase(volume.path)
                logger.info("Using named volume '$volumeName' (thus: persistent) for container $containerName")
                allBinds << bindParser.invoke(null, "$volumeName:$volume.path" as String)
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void removeOldContainer(Service oldContainer, Service linkedFromContainer = null) {
        ignoreDockerException('NotFoundException') {
            def result = dockerExecutor.client.inspectContainerCmd(oldContainer.containerName).exec()

            otherServices.each {
                if (it.linkDependencies.contains(oldContainer) || it.volumesFromDependencies.contains(oldContainer)) {
                    removeOldContainer(it, oldContainer)
                }
            }

            dockerExecutor.client.removeContainerCmd(oldContainer.containerName)
                    .withForce(true)
                    .withRemoveVolumes(!oldContainer.preserveVolumes)
                    .exec()


            logger.info("Removed old container with id $result.id ($oldContainer.containerName)" +
                    (linkedFromContainer != null ? " as it depends on $linkedFromContainer.containerName" : ""))
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            ignoreDockerException('NotFoundException') {
                def result = dockerExecutor.client.inspectContainerCmd(containerName).exec()
                service.containerId = result.id

                def networkData = result.networkSettings?.networks?.collect { name, props ->
                    [name, props.aliases]
                }

                def hostConf = result.hostConfig
                [result.id, networkData, hostConf.memory, hostConf.memorySwap, hostConf.cpuShares, hostConf.cpusetCpus]
            }
        }
    }

}
