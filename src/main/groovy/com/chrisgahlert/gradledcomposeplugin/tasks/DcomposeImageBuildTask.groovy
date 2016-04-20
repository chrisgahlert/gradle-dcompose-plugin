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
import org.gradle.api.tasks.*

class DcomposeImageBuildTask extends AbstractDcomposeTask {

    @Input
    @Optional
    Boolean getNoCache() {
        container.buildNoCache
    }

    @Input
    @Optional
    Boolean getPull() {
        container.buildPull
    }

    @Input
    @Optional
    Boolean getRemove() {
        container.buildRemove
    }

    @Input
    String getTag() {
        container.tag
    }

    @Input
    @Optional
    Long getMemory() {
        container.memory
    }

    @Input
    @Optional
    Long getMemswap() {
        container.memswap
    }

    @Input
    @Optional
    String getCpushares() {
        container.cpushares
    }

    @Input
    @Optional
    String getCpusetcpus() {
        container.cpusetcpus
    }

    @InputDirectory
    File getBaseDir() {
        container.baseDir
    }

    @InputFile
    File getDockerFile() {
        new File(baseDir, container.dockerFilename ?: 'Dockerfile')
    }

    @Input
    @Optional
    Map<String, String> getBuildArgs() {
        container.buildArgs
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
            logger.error "Created $imageId"
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getImageState() {
        dockerOutput('image-state') {
            ignoreDockerException('NotFoundException') {
                client.inspectImageCmd(tag).exec()
            }
        }
    }
}
