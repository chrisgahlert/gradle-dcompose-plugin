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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeImagePushTask extends AbstractDcomposeServiceTask {

    @Input
    String getImageId() {
        service.imageId
    }

    @Input
    ImageRef getRepositoryRef() {
        ImageRef.parse(service.repository)
    }

    @Input
    @Optional
    List<ImageRef> getAdditionalRepositoryRefs() {
        service.additionalRepositories?.collect { ImageRef.parse(it) }
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
            (!service.hasImage() || ImageRef.parse(service.image) != ImageRef.parse(service.repository)) && service.deploy
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pushImage() {
        dockerExecutor.runInDockerClasspath {
            if (service.hasImage()) {
                // Tagging only needed when image has been pulled, as building it automatically sets the tag
                tagImageInternal(repositoryRef)

                additionalRepositoryRefs?.each {
                    tagImageInternal(it)
                }
            }

            pushImageInternal(repositoryRef)
            additionalRepositoryRefs?.each {
                pushImageInternal(it)
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void tagImageInternal(ImageRef repo) {
        logger.info("Tagging image $imageId with $repo")
        dockerExecutor.client.tagImageCmd(imageId, repo.registryWithRepository, repo.tag).exec()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void pushImageInternal(ImageRef repo) {
        def pushCmd = dockerExecutor.client.pushImageCmd(imageId)
                .withName(repo.registryWithRepository)
                .withTag(repo.tag)

        addAuthConfig(repo.toString(), pushCmd)

        def callback = dockerExecutor.loadClass('com.github.dockerjava.core.command.PushImageResultCallback')
                .newInstance()
        logger.info("Pushing image $imageId to $repo")
        pushCmd.exec(callback)
        callback.awaitSuccess()
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getRepositoryDigest() {
        dockerOutput('repository-digest') {
            ignoreDockerException('NotFoundException') {
                def result = dockerExecutor.client.inspectImageCmd(imageId).exec()
                def digest = result.repoDigests.find { it.startsWith(repositoryRef.registryWithRepository + '@') }
                service.repositoryDigest = digest

                digest
            }
        }
    }

}
