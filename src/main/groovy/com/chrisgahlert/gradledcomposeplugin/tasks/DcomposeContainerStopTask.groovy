package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.helpers.DockerOnlyIf
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerTaskAction
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

/**
 * Created by chris on 16.04.16.
 */
public class DcomposeContainerStopTask extends AbstractDcomposeTask {

    @DockerOnlyIf
    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerRunning() {
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            client.inspectContainerCmd(container.containerName).exec().state.running
        }
    }

    @DockerTaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void stopContainer() {
        ignoreExceptions(['com.github.dockerjava.api.exception.NotFoundException', 'com.github.dockerjava.api.exception.NotModifiedException']) {
            client.stopContainerCmd(container.containerName).exec()
        }
    }
}
