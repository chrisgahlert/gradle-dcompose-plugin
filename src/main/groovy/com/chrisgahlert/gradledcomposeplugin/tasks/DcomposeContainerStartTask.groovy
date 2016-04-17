package com.chrisgahlert.gradledcomposeplugin.tasks


import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class DcomposeContainerStartTask extends AbstractDcomposeTask {

    DcomposeContainerStartTask() {
        outputs.upToDateWhen {
            !container.waitForCommand
        }
    }

    @Input
    String getContainerName() {
        container.containerName
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void startContainer() {
        runInDockerClasspath {
            ignoreException('com.github.dockerjava.api.exception.NotModifiedException') {
                def result = client.startContainerCmd(containerName).exec()
            }

            if (container.waitForCommand) {
                def start = System.currentTimeMillis()

                while (container.waitTimeout <= 0 || start + 1000L * container.waitTimeout > System.currentTimeMillis()) {
                    def inspectResult = client.inspectContainerCmd(containerName).exec()
                    if (!inspectResult.state.running) {
                        return
                    }

                    Thread.sleep(1000)
                }

                throw new GradleException("Timed out waiting for command to finish after $container.waitTimeout seconds")
            }
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                client.inspectContainerCmd(containerName).exec()
            }
        }
    }

}
