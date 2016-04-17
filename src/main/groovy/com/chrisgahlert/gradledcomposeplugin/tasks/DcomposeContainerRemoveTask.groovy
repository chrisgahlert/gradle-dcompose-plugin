package com.chrisgahlert.gradledcomposeplugin.tasks


import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 16.04.16.
 */
class DcomposeContainerRemoveTask extends AbstractDcomposeTask {

    DcomposeContainerRemoveTask() {
        onlyIf {
            containerExist()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerExist() {
        runInDockerClasspath {
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                client.inspectContainerCmd(container.containerName).exec()
                true
            }
        }
    }

    @Input
    boolean isPreserveVolumes() {
        container.preserveVolumes
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeContainer() {
        runInDockerClasspath {
            ignoreExceptions(['com.github.dockerjava.api.exception.NotFoundException',
                              'com.github.dockerjava.api.exception.NotModifiedException']) {
                client.removeContainerCmd(container.containerName)
                        .withRemoveVolumes(!preserveVolumes)
                        .exec()
            }
        }
    }
}
