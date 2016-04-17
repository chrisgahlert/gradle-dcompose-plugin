package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.extension.DockerRegistryCredentials
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerOnlyIf
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerTaskAction
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class DcomposeImagePullTask extends AbstractDcomposeTask {

    DockerRegistryCredentials registry

    @Input
    String getImage() {
        container.image
    }

    @DockerOnlyIf
    @TypeChecked(TypeCheckingMode.SKIP)
    boolean imageNotExists() {
        def exists = ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            client.inspectImageCmd(image).exec()
            true
        }

        !exists
    }

    @DockerTaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pullImage() {
        def callback = loadClass('com.github.dockerjava.core.command.PullImageResultCallback').newInstance()
        def result = client.pullImageCmd(image).exec(callback)
        result.awaitCompletion()
    }
}
