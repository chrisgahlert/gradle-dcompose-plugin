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
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeContainerStartTask extends AbstractDcomposeTask {

    int waitInterval = 1000

    DcomposeContainerStartTask() {
        outputs.upToDateWhen {
            !container.waitForCommand
        }

        dependsOn {
            container.linkDependencies.collect { "$it.projectPath:$it.startTaskName" }
        }
    }

    @Input
    String getContainerName() {
        container.containerName
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void startContainer() {
        runInDockerClasspath {
            ignoreDockerException('NotModifiedException') {
                def result = client.startContainerCmd(containerName).exec()
                logger.quiet("Started Docker container with name $containerName")
            }

            if (container.waitForCommand) {
                def start = System.currentTimeMillis()

                while (container.waitTimeout <= 0 || start + 1000L * container.waitTimeout > System.currentTimeMillis()) {
                    def inspectResult = client.inspectContainerCmd(containerName).exec()
                    if (!inspectResult.state.running) {
                        logger.info("Docker container with name $containerName is not running anymore")
                        return
                    }

                    logger.debug("Waiting for Docker container with name $containerName to stop running")
                    Thread.sleep(waitInterval)
                }

                throw new GradleException("Timed out waiting for command to finish after ${System.currentTimeMillis() - start} ms")
            }
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            def result = ignoreDockerException('NotFoundException') {
                client.inspectContainerCmd(containerName).exec()
            }

            if(result.state.running) {
                container.hostPortBindings = result?.networkSettings?.ports?.bindings
            } else {
                container.hostPortBindings = null
            }


            result
        }
    }

}
