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

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.*

class DcomposeImageBuildTask extends AbstractDcomposeTask {

    @Input
    @Optional
    Boolean getNoCache() {
        service.buildNoCache
    }

    @Input
    @Optional
    Boolean getPull() {
        service.buildPull
    }

    @Input
    @Optional
    Boolean getRemove() {
        service.buildRemove
    }

    @Input
    String getTag() {
        service.tag
    }

    @Input
    @Optional
    Long getMemory() {
        service.memory
    }

    @Input
    @Optional
    Long getMemswap() {
        service.memswap
    }

    @Input
    @Optional
    String getCpushares() {
        service.cpushares
    }

    @Input
    @Optional
    String getCpusetcpus() {
        service.cpusetcpus
    }

    @InputDirectory
    File getBaseDir() {
        service.baseDir
    }

    @InputFile
    File getDockerFile() {
        new File(baseDir, service.dockerFilename ?: 'Dockerfile')
    }

    @Input
    @Optional
    Map<String, String> getBuildArgs() {
        service.buildArgs
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void buildImage() {
        runInDockerClasspath {
            def cmd = client.buildImageCmd(dockerFile)
                    .withBaseDirectory(baseDir)
                    .withTag(tag)

            if (noCache != null) {
                cmd.withNoCache(noCache)
            }

            if (remove != null) {
                cmd.withRemove(remove)
            }

            if (pull != null) {
                cmd.withPull(pull)
            }

            if (memory != null) {
                cmd.withMemory(memory)
            }
            if (memswap != null) {
                cmd.withMemswap(memswap)
            }
            if (cpushares != null) {
                cmd.withCpushares(cpushares)
            }
            if (cpusetcpus != null) {
                cmd.withCpusetcpus(cpusetcpus)
            }
            if (buildArgs) {
                buildArgs.each { key, value ->
                    cmd.withBuildArg(key, value)
                }
            }

            def callback = loadClass('com.github.dockerjava.core.command.BuildImageResultCallback').newInstance()
            def response = cmd.exec(callback)

            def imageId = response.awaitImageId()
            logger.quiet("Built Docker image with id $imageId and tagged as $tag")
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getImageState() {
        dockerOutput('image-state') {
            ignoreDockerException('NotFoundException') {
                def result = client.inspectImageCmd(tag).exec()
                result.repoTags = null
                result
            }
        }
    }
}
