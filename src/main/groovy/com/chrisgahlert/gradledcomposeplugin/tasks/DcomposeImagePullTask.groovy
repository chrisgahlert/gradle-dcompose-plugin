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
            ignoreDockerException('NotFoundException') {
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
