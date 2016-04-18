package com.chrisgahlert.gradledcomposeplugin.tasks


import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 17.04.16.
 */
class DcomposeImageRemoveTask extends AbstractDcomposeTask {

    DcomposeImageRemoveTask() {
        onlyIf {
            doesExist()
        }
    }

    @Input
    String getImage() {
        container.image
    }

    @Input
    boolean isForce() {
        container.forceRemoveImage
    }

    @Input
    boolean isNoPrune() {
        container.noPruneParentImages
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean doesExist() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.inspectImageCmd(image).exec()
                true
            }
        }
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeImage() {
        runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                client.removeImageCmd(image)
                        .withForce(force)
                        .withNoPrune(noPrune)
                        .exec()
            }
        }
    }
}
