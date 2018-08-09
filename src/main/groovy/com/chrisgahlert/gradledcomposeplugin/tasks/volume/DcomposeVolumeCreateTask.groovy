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
package com.chrisgahlert.gradledcomposeplugin.tasks.volume

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeVolumeTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeVolumeCreateTask extends AbstractDcomposeVolumeTask {

    @Input
    String getVolumeName() {
        volume.volumeName
    }

    @Input
    @Optional
    String getDriver() {
        volume.driver
    }

    @Input
    @Optional
    Map<String, String> getDriverOpts() {
        volume.driverOpts
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createVolume() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                dockerExecutor.client.inspectVolumeCmd(volumeName).exec()
                servicesUsingVolume.each {
                    stopContainer(it.containerName)
                }
                dockerExecutor.client.removeVolumeCmd(volumeName).exec()
            }

            try {
                def cmd = dockerExecutor.client.createVolumeCmd().withName(volumeName)

                if (driver != null) {
                    cmd.withDriver(driver as String)
                }

                if (driverOpts != null) {
                    cmd.withDriverOpts(driverOpts as Map<String, String>)
                }

                cmd.exec()
            } catch (Exception e) {
                if (e.getClass() == dockerExecutor.loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                        && e.message?.contains('waiting (1s) for it to exit...')) {
                    createVolume()
                } else {
                    throw e
                }
            }
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getVolumeState() {
        dockerOutput('volume-state') {
            ignoreDockerException('NotFoundException') {
                dockerExecutor.client.inspectVolumeCmd(volumeName).exec()
            }
        }
    }
}
