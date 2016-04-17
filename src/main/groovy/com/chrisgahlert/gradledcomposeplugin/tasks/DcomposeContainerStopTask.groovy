package com.chrisgahlert.gradledcomposeplugin.tasks


import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 16.04.16.
 */
public class DcomposeContainerStopTask extends AbstractDcomposeTask {

    DcomposeContainerStopTask() {
        onlyIf {
            containerRunning()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean containerRunning() {
        runInDockerClasspath {
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                client.inspectContainerCmd(container.containerName).exec().state.running
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void stopContainer() {
        runInDockerClasspath {
            ignoreExceptions(['com.github.dockerjava.api.exception.NotFoundException',
                              'com.github.dockerjava.api.exception.NotModifiedException']) {
                client.stopContainerCmd(container.containerName).exec()
            }
        }
    }
}
