package com.chrisgahlert.gradledcomposeplugin.tasks


import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class DcomposeImagePullTask extends AbstractDcomposeTask {

    DcomposeImagePullTask() {
        onlyIf {
            imageNotExists()
        }
    }

    @Input
    String getImage() {
        container.image
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    boolean imageNotExists() {
        def exists = runInDockerClasspath {
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                client.inspectImageCmd(image).exec()
                true
            }
        }

        !exists
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void pullImage() {
        runInDockerClasspath {
            def callback = loadClass('com.github.dockerjava.core.command.PullImageResultCallback').newInstance()
            def result = client.pullImageCmd(image).exec(callback)
            result.awaitCompletion()
        }
    }
}
