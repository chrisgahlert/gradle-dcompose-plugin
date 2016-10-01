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
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeImagePushTask extends AbstractDcomposeServiceTask {

    @Input
    String getImageId() {
        service.imageId?.replaceFirst(/^sha256:/, '')
    }

    @Input
    ImageRef getRepositoryRef() {
        ImageRef.parse(service.repository)
    }

    DcomposeImagePushTask() {
        dependsOn {
            if (service.hasImage()) {
                "$service.projectPath:$service.pullImageTaskName"
            } else {
                "$service.projectPath:$service.buildImageTaskName"
            }
        }

        onlyIf {
            !service.hasImage() || ImageRef.parse(service.image) != ImageRef.parse(service.repository)
        }

        outputs.upToDateWhen { false }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pushImage() {
        runInDockerClasspath {
            logger.quiet("Tagging image $imageId with $repositoryRef")
            client.tagImageCmd(imageId, repositoryRef.registryWithRepository, repositoryRef.tag).exec()

            def pushCmd = client.pushImageCmd(imageId)
                    .withName(repositoryRef.registryWithRepository)
                    .withTag(repositoryRef.tag)

            addAuthConfig(repositoryRef.toString(), pushCmd)

            def callback = loadClass('com.github.dockerjava.core.command.PushImageResultCallback').newInstance()
            logger.quiet("Pushing image $imageId to $repositoryRef")
            pushCmd.exec(callback)
            callback.awaitSuccess()
        }
    }

}
