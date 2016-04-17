package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.helpers.DockerOnlyIf
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerTaskAction
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input

/**
 * Created by chris on 16.04.16.
 */
class DcomposeContainerRemoveTask extends AbstractDcomposeTask {

    @DockerOnlyIf
    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerExist() {
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            client.inspectContainerCmd(container.containerName).exec()
            true
        }
    }

    @Input
    boolean isPreserveVolumes() {
        container.preserveVolumes
    }

    @DockerTaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeContainer() {
        ignoreExceptions(['com.github.dockerjava.api.exception.NotFoundException', 'com.github.dockerjava.api.exception.NotModifiedException']) {
            client.removeContainerCmd(container.containerName)
                    .withRemoveVolumes(!preserveVolumes)
                    .exec()
        }
    }
}
