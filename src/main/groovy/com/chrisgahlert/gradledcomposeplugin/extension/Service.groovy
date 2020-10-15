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
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskDependency

import javax.annotation.Nullable

@TypeChecked
abstract class Service extends AbstractEntity implements TaskDependency {
    Service(String name, String projectPath) {
        super(name, projectPath)
    }

    String getPullImageTaskName() {
        "pull${nameCamelCase}Image"
    }

    @Deprecated
    String getPullTaskName() {
        pullImageTaskName
    }

    String getCreateContainerTaskName() {
        "create${nameCamelCase}Container"
    }

    @Deprecated
    String getCreateTaskName() {
        createContainerTaskName
    }

    String getStartContainerTaskName() {
        "start${nameCamelCase}Container"
    }

    @Deprecated
    String getStartTaskName() {
        startContainerTaskName
    }

    String getStopContainerTaskName() {
        "stop${nameCamelCase}Container"
    }

    @Deprecated
    String getStopTaskName() {
        stopContainerTaskName
    }

    String getRemoveContainerTaskName() {
        "remove${nameCamelCase}Container"
    }

    String getRemoveImageTaskName() {
        "remove${nameCamelCase}Image"
    }

    String getBuildImageTaskName() {
        "build${nameCamelCase}Image"
    }

    String getPushImageTaskName() {
        "push${nameCamelCase}Image"
    }

    @Deprecated
    String getBuildTaskName() {
        buildImageTaskName
    }

    String getCopyBuildFilesTaskName() {
        "copy${nameCamelCase}BuildFiles"
    }

    @Override
    String toString() {
        containerName
    }

    @Override
    @TypeChecked(TypeCheckingMode.SKIP)
    Set<? extends Task> getDependencies(@Nullable Task task) {
        if (task == null) {
            return [].toSet()
        } else {
            Set<Service> services = []
            calculateRuntimeDependencies(this, services)

            return services
                    .collect {
                        task.project.rootProject.tasks.getByPath("$it.projectPath:$it.startContainerTaskName")
                    }
                    .toSet()
        }
    }

    private static void calculateRuntimeDependencies(Service service, Set<Service> result) {
        result.add(service)

        (service.dependsOn + service.dependsOnRuntime + service.linkDependencies).each {
            if (result.add(it)) {
                calculateRuntimeDependencies(it, result)
            }
        }
    }

    abstract String getContainerName()

    abstract boolean isWaitForCommand()

    abstract boolean isWaitForHealthcheck()

    abstract Long getHealthcheckStartPeriod()

    abstract Long getHealthcheckInterval()

    abstract Long getHealthcheckTimeout()

    abstract Integer getHealthcheckRetries()

    abstract List<String> getHealthcheckTest()

    abstract boolean isIgnoreExitCode()

    abstract int getWaitTimeout()

    abstract boolean isPreserveVolumes()

    abstract List<String> getCommand()

    abstract List<String> getEntrypoints()

    abstract List<String> getEnv()

    abstract String getWorkingDir()

    abstract String getUser()

    abstract Boolean getReadonlyRootfs()

    abstract List<String> getVolumes()

    abstract List getBinds()

    abstract List getVolumesFrom()

    abstract List<String> getExposedPorts()

    abstract List<String> getPortBindings()

    abstract Boolean getPublishAllPorts()

    abstract List getLinks()

    abstract String getHostName()

    abstract List<String> getDns()

    abstract List<String> getDnsSearch()

    abstract List<String> getExtraHosts()

    abstract String getNetworkMode()

    abstract Boolean getAttachStdin()

    abstract Boolean getAttachStdout()

    abstract Boolean getAttachStderr()

    abstract Boolean getPrivileged()

    abstract File getBaseDir()

    abstract CopySpec getBuildFiles()

    abstract String getDockerFilename()

    abstract Long getMemory()

    abstract Long getMemswap()

    abstract Integer getCpushares()

    abstract String getCpusetcpus()

    abstract Map<String, String> getBuildArgs()

    abstract Boolean getForceRemoveImage()

    abstract Boolean getNoPruneParentImages()

    abstract Boolean getBuildNoCache()

    abstract Boolean getBuildRemove()

    abstract Boolean getLogBuildStatus()

    abstract LogLevel getBuildLogLevel()

    abstract File getBuildLogFile()

    @Deprecated
    final Boolean getBuildPull() {
        forcePull
    }

    @Deprecated
    final void setBuildPull(boolean buildPull) {
        forcePull = buildPull
    }

    abstract void setForcePull(boolean forcePull)

    abstract boolean isForcePull()

    abstract void setHostPortBindings(Map hostPortBindings)

    abstract def findHostPort(Map<String, String> properties = [:], int containerPort)

    abstract String getDockerHost()

    abstract Set<Service> getLinkDependencies()

    abstract Set<Service> getVolumesFromDependencies()

    abstract String getImage()

    abstract boolean hasImage()

    abstract void validate()

    abstract List<Network> getNetworks()

    abstract List<String> getExternalNetworks()

    abstract List<String> getAliases()

    abstract Integer getStopTimeout()

    abstract void setExitCode(int exitCode)

    abstract int getExitCode()

    abstract void setImageId(String imageId)

    abstract String getImageId()

    abstract void setContainerId(String containerId)

    abstract String getContainerId()

    abstract boolean isDeploy()

    abstract void setDeploy(boolean enabled)

    abstract List<Service> getDependsOn()

    abstract void setDependsOn(List<Service> dependencies)

    abstract List<Service> getDependsOnRuntime()

    abstract void setDependsOnRuntime(List<Service> dependencies)

    abstract String getRestart()

    abstract void setRestart(String restart)

    abstract String getRepository()

    abstract void setRepository(String repository)

    abstract List<String> getAdditionalRepositories()

    abstract void setAdditionalRepositories(List<String> additionalRepositories)

    abstract String getRepositoryDigest()

    abstract void setRepositoryDigest(String repositoryDigest)

    abstract Long getMemLimit()

    abstract void setMemLimit(Long memLimit)

    abstract String getLogConfig()

    abstract void setLogConfig(String logConfig)

    abstract Map<String, String> getLogOpts()

    abstract void setLogOpts(Map<String, String> logOpts)

    ServiceDependency link(String alias = null) {
        new ServiceDependency(alias ?: name, this)
    }

    static class ServiceDependency {
        final Service service
        final private String alias

        ServiceDependency(String alias, Service service) {
            this.alias = alias
            this.service = service
        }

        String getContainerDefinition() {
            "$service.containerName:$alias" as String
        }

        String getServiceDefinition() {
            "$service.name:$alias" as String
        }

        @Override
        String toString() {
            getContainerDefinition()
        }
    }
}
