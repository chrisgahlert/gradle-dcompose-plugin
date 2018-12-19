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
package com.chrisgahlert.gradledcomposeplugin.tasks.network

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeNetworkTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeNetworkRemoveTask extends AbstractDcomposeNetworkTask {

    DcomposeNetworkRemoveTask() {
        onlyIf { networkExists() }

        dependsOn {
            servicesUsingNetwork.collect { service ->
                "$service.projectPath:$service.removeContainerTaskName"
            }
        }
    }

    @Input
    String getNetworkName() {
        network.networkName
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeNetwork() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                try {
                    dockerExecutor.client.removeNetworkCmd().withNetworkId(networkName).exec()
                } catch (Exception e) {
                    if (e.getClass() == dockerExecutor.loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                            && e.message?.contains('waiting (1s) for it to exit...')) {
                        removeNetwork()
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean networkExists() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                dockerExecutor.client.inspectNetworkCmd().withNetworkId(networkName).exec()
                true
            }
        } ?: false
    }
}
