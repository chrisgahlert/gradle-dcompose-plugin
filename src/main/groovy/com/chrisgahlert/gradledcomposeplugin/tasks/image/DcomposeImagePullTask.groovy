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
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeImagePullTask extends AbstractDcomposeServiceTask {

    DcomposeImagePullTask() {
        onlyIf {
            !imageExists()
        }

        enabled = { service.hasImage() }
    }

    @Input
    String getImage() {
        service.image
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean imageExists() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                def result = client.inspectImageCmd(image).exec()
                service.imageId = result.id
                true
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pullImage() {
        runInDockerClasspath {
            def callback = loadClass('com.github.dockerjava.core.command.PullImageResultCallback').newInstance()
            def cmd = client.pullImageCmd(image)
            addAuthConfig(image, cmd)
            def result = cmd.exec(callback)
            result.awaitSuccess()
            logger.quiet("Successfully pulled image $image")

            service.imageId = client.inspectImageCmd(image).exec().id
        }
    }
}
