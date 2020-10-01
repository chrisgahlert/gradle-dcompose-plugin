/*
 * Copyright 2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.extension.Service
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeCopyFileFromContainerTask extends AbstractDcomposeServiceTask {

    DcomposeCopyFileFromContainerTask() {
        outputs.upToDateWhen { false }
        dependsOn {
            if (!service) {
                throw new GradleException("The task $path is missing the 'service' property")
            }

            "$service.projectPath:$service.createContainerTaskName"
        }
    }

    File destinationDir

    @Input
    String containerPath

    @Input
    boolean cleanDestinationDir = false

    @Deprecated
    public void setContainer(Service service) {
        this.service = service

        logger.warn 'Deprecation warning: Please use the service property instead of the container property'
    }

    @Input
    String getContainerName() {
        service.containerName
    }

    @OutputDirectory
    File getDestinationDir() {
        destinationDir ?: new File(project.buildDir, name)
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void copyFromContainer() {
        dockerExecutor.runInDockerClasspath {
            def cmd = dockerExecutor.client.copyArchiveFromContainerCmd(containerName, containerPath)

            def tarStream
            try {
                tarStream = cmd.exec()

                def tarFile = new File(temporaryDir, 'docker-copy-stream.tar')
                if (tarFile.exists() && !tarFile.delete()) {
                    throw new GradleException("Could not delete $tarFile")
                }

                tarFile.withOutputStream { it << tarStream }

                def tarDir = new File(temporaryDir, 'extracted/')
                if (tarDir.exists() && !project.delete(tarDir)) {
                    throw new GradleException("Could not delete $tarDir")
                }

                project.copy {
                    from project.tarTree(tarFile)
                    into tarDir
                }

                if (cleanDestinationDir) {
                    project.delete(getDestinationDir())
                }

                def sourcePath = new File(tarDir, containerPath)
                project.copy {
                    from sourcePath.isDirectory() ? sourcePath : tarDir
                    into getDestinationDir()
                }

                logger.info("Copied files from Docker container with name $containerName into ${getDestinationDir()}")
            } finally {
                tarStream?.close()
            }
        }
    }
}
