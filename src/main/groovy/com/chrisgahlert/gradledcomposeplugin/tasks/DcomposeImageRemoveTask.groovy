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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class DcomposeImageRemoveTask extends AbstractDcomposeTask {

    DcomposeImageRemoveTask() {
        onlyIf {
            doesExist()
        }
    }

    @Input
    String getImage() {
        container.image
    }

    @Input
    @Optional
    Boolean getForce() {
        container.forceRemoveImage
    }

    @Input
    @Optional
    Boolean getNoPrune() {
        container.noPruneParentImages
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean doesExist() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.inspectImageCmd(image).exec()
                true
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeImage() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                def cmd = client.removeImageCmd(image)

                if (force != null) {
                    cmd.withForce(force)
                }

                if (noPrune != null) {
                    cmd.withNoPrune(noPrune)
                }

                cmd.exec()
                logger.quiet("Successfully removed image $image")
            }
        }
    }
}
