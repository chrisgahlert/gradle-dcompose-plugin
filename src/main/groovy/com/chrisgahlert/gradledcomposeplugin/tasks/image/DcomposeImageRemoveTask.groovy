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
package com.chrisgahlert.gradledcomposeplugin.tasks.image

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeServiceTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class DcomposeImageRemoveTask extends AbstractDcomposeServiceTask {

    DcomposeImageRemoveTask() {
        onlyIf {
            imageExists()
        }

        dependsOn {
            service.removeContainerTaskName
        }
    }

    @Input
    String getImageRef() {
        service.hasImage() ? service.image : service.repository
    }

    @Input
    @Optional
    Boolean getForce() {
        service.forceRemoveImage
    }

    @Input
    @Optional
    Boolean getNoPrune() {
        service.noPruneParentImages
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean imageExists() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.inspectImageCmd(imageRef).exec()
                true
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeImage() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                def cmd = client.removeImageCmd(imageRef)

                if (force != null) {
                    cmd.withForce(force)
                }

                if (noPrune != null) {
                    cmd.withNoPrune(noPrune)
                }

                cmd.exec()
                logger.info("Successfully removed image $imageRef")
            }
        }
    }
}
