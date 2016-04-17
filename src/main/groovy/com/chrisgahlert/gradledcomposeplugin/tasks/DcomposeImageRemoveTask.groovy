package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.helpers.DockerOnlyIf
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerTaskAction
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input

/**
 * Created by chris on 17.04.16.
 */
class DcomposeImageRemoveTask extends AbstractDcomposeTask {

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

    @DockerOnlyIf
    @TypeChecked(TypeCheckingMode.SKIP)
    boolean doesExist() {
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            client.inspectImageCmd(image).exec()
            true
        }
    }

    @DockerTaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void removeImage() {
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            client.removeImageCmd(image)
                    .withForce(force)
                    .withNoPrune(noPrune)
                    .exec()
        }
    }
}
