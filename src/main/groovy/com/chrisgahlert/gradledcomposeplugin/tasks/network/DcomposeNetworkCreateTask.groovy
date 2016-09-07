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
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeNetworkCreateTask extends AbstractDcomposeNetworkTask {

    @Input
    String getNetworkName() {
        network.networkName
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNetwork() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.removeNetworkCmd().withNetworkId(networkName).exec()
            }

            try {
                client.createNetworkCmd().withName(networkName).exec()
            } catch (Exception e) {
                if (e.getClass() == loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                        && e.message?.trim().endsWith('waiting (1s) for it to exit...')) {
                    createNetwork()
                } else {
                    throw e
                }
            }
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getNetworkState() {
        dockerOutput('network-state') {
            ignoreDockerException('NotFoundException') {
                def result = client.inspectNetworkCmd().withNetworkId(networkName).exec()
                result.containers = [:]

                result
            }
        }
    }
}
