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
import com.chrisgahlert.gradledcomposeplugin.utils.ImageRef
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeImagePullTask extends AbstractDcomposeServiceTask {

    DcomposeImagePullTask() {
        outputs.upToDateWhen { !service.forcePull }

        enabled = { service.hasImage() }

        onlyIf { getImageId() == null || service.forcePull }
    }

    @Input
    String getImage() {
        service.image
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pullImage() {
        dockerExecutor.runInDockerClasspath {
            def callback = dockerExecutor.loadClass('com.github.dockerjava.core.command.PullImageResultCallback')
                    .newInstance()
            def cmd = dockerExecutor.client.pullImageCmd(image)
            addAuthConfig(image, cmd)
            def result = cmd.exec(callback)
            result.awaitSuccess()
            logger.info("Successfully pulled image $image")
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getImageState() {
        dockerOutput('image-state') {
            getImageId()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected String getImageId() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                def result = dockerExecutor.client.inspectImageCmd(image).exec()
                service.imageId = result.id

                def repositoryRef = ImageRef.parse(service.repository)
                if (ImageRef.parse(service.image) == repositoryRef) {
                    def digest = result.repoDigests.find { it.startsWith(repositoryRef.registryWithRepository + '@') }
                    service.repositoryDigest = digest
                }

                result.id
            }
        }
    }
}
