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


import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class DcomposeContainerRemoveTask extends AbstractDcomposeTask {

    DcomposeContainerRemoveTask() {
        project.afterEvaluate {
            otherContainers.each { otherContainer ->
                otherContainer.links?.each { otherLink ->
                    if(otherLink.container == container) {
                        dependsOn otherContainer.removeContainerTaskName
                    }
                }
            }
        }

        onlyIf {
            containerExist()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerExist() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.inspectContainerCmd(container.containerName).exec()
                true
            }
        }
    }

    @Input
    boolean isPreserveVolumes() {
        container.preserveVolumes
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeContainer() {
        runInDockerClasspath {
            ignoreDockerExceptions(['NotFoundException', 'NotModifiedException']) {
                client.removeContainerCmd(container.containerName)
                        .withRemoveVolumes(!preserveVolumes)
                        .exec()
            }
        }
    }
}
