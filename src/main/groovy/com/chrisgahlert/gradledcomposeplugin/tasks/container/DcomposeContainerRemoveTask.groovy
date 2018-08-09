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

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeServiceTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class DcomposeContainerRemoveTask extends AbstractDcomposeServiceTask {

    DcomposeContainerRemoveTask() {
        dependsOn {
            otherServices.findAll { otherService ->
                otherService.linkDependencies.contains(service)
            }.collect { otherService ->
                "$otherService.projectPath:$otherService.removeContainerTaskName"
            }
        }

        dependsOn {
            otherServices.findAll { otherService ->
                otherService.volumesFromDependencies.contains(service)
            }.collect { otherService ->
                "$otherService.projectPath:$otherService.removeContainerTaskName"
            }
        }

        dependsOn {
            otherServices.findAll { otherService ->
                otherService.dependsOn.contains(service)
            }.collect { otherService ->
                "$otherService.projectPath:$otherService.removeContainerTaskName"
            }
        }

        dependsOn {
            service.stopContainerTaskName
        }

        onlyIf {
            containerExist() ?: false
        }
    }

    @Input
    boolean isPreserveVolumes() {
        service.preserveVolumes
    }

    @Input
    String getContainerName() {
        service.containerName
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerExist() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                dockerExecutor.client.inspectContainerCmd(containerName).exec()
                true
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeContainer() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerExceptions(['NotFoundException', 'NotModifiedException']) {
                dockerExecutor.client.removeContainerCmd(containerName)
                        .withRemoveVolumes(!preserveVolumes)
                        .exec()

                logger.info("Removed Docker container with name $containerName")
            }
        }
    }
}
