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
            ignoreDockerException('NotFoundException') {
                client.inspectContainerCmd(container.containerName).exec().state.running
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void stopContainer() {
        runInDockerClasspath {
            ignoreDockerExceptions(['NotFoundException', 'NotModifiedException']) {
                client.stopContainerCmd(container.containerName).exec()
            }
        }
    }
}
