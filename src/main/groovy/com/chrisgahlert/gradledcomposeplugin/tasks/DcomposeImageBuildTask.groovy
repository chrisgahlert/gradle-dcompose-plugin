package com.chrisgahlert.gradledcomposeplugin.tasks

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.*

/**
 * Created by chris on 17.04.16.
 */
class DcomposeImageBuildTask extends AbstractDcomposeTask {

    @Input
    boolean isNoCache() {
        container.buildNoCache
    }

    @Input
    boolean isPull() {
        container.buildPull
    }

    @Input
    boolean isRemove() {
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

    @InputFile
    File getDockerFile() {
        container.dockerFile
    }

    @InputDirectory
    @Optional
    File getBaseDir() {
        container.baseDir
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
                    .withTag(tag)
                    .withNoCache(noCache)
                    .withRemove(remove)
                    .withPull(pull)
                    .withBaseDirectory(baseDir ?: dockerFile.parentFile)

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
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                client.inspectImageCmd(tag).exec()
            }
        }
    }
}
