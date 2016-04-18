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
                    .withNoCache(noCache)
                    .withRemove(remove)
                    .withPull(pull)

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
