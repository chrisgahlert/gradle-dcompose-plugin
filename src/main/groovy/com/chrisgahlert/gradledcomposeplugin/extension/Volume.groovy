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
package com.chrisgahlert.gradledcomposeplugin.extension

import groovy.transform.TypeChecked

@TypeChecked
abstract class Volume extends AbstractEntity {
    Volume(String name, String projectPath) {
        super(name, projectPath)
    }

    String getCreateTaskName() {
        "create${nameCamelCase}Volume"
    }

    String getRemoveTaskName() {
        "remove${nameCamelCase}Volume"
    }

    abstract String getVolumeName()

    abstract String getDriver()

    abstract Map<String, String> getDriverOpts()

    VolumeDependency bind(String path) {
        new VolumeDependency(path, this)
    }

    static class VolumeDependency {
        final Volume volume
        final private String path

        VolumeDependency(String path, Volume volume) {
            this.path = path
            this.volume = volume
        }

        String getContainerDefinition() {
            "$volume.volumeName:$path" as String
        }

        String getServiceDefinition() {
            "$volume.name:$path" as String
        }

        @Override
        String toString() {
            getContainerDefinition()
        }

    }
}
