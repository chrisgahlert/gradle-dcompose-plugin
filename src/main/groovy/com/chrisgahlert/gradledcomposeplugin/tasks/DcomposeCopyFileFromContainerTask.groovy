package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.extension.Container
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Created by chris on 18.04.16.
 */
@CompileStatic
class DcomposeCopyFileFromContainerTask extends AbstractDcomposeTask {

    @Override
    void setContainer(Container container) {
        super.setContainer(container)

        dependsOn container.createTaskName
    }

    File destinationDir

    @Input
    String containerPath

    @Input
    boolean cleanDestinationDir = false

    @Input
    String getContainerName() {
        container.containerName
    }

    @OutputDirectory
    File getDestinationDir() {
        destinationDir ?: new File(project.buildDir, name)
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void copyFromContainer() {
        runInDockerClasspath {
            def cmd = client.copyFileFromContainerCmd(containerName, containerPath)

            def tarStream
            try {
                tarStream = cmd.exec()

                def tarFile = new File(temporaryDir, 'docker-copy-stream.tar')
                if(tarFile.exists() && !tarFile.delete()) {
                    throw new GradleException("Could not delete $tarFile")
                }

                tarFile.withOutputStream { it << tarStream }

                def tarDir = new File(temporaryDir, 'extracted/')
                if(tarDir.exists() && !project.delete(tarDir)) {
                    throw new GradleException("Could not delete $tarDir")
                }

                project.copy {
                    from project.tarTree(tarFile)
                    into tarDir
                }

                if(cleanDestinationDir) {
                    project.delete(getDestinationDir())
                }

                def sourcePath = new File(tarDir, containerPath)
                project.copy {
                    from sourcePath.isDirectory() ? sourcePath : tarDir
                    into getDestinationDir()
                }
            } finally {
                tarStream?.close()
            }
        }
    }
}