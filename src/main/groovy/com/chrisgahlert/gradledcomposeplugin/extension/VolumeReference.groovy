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
import org.gradle.api.GradleException
import org.gradle.api.Project

@TypeChecked
class VolumeReference extends Volume {

    private Project targetProject
    private Volume resolvedVolume

    VolumeReference(String name, Project targetProject) {
        super(name, targetProject.path)
        this.targetProject = targetProject
    }

    Volume getResolved() {
        if (resolvedVolume == null) {
            def extension = targetProject.extensions.findByType(DcomposeExtension)
            if (extension == null) {
                throw new GradleException("Could not find dcompose extension on $targetProject - has the dcompose plugin been applied?")
            }

            resolvedVolume = extension.volumes.maybeCreate(name)
        }

        resolvedVolume
    }

    @Override
    String getVolumeName() {
        resolved.volumeName
    }

    @Override
    String getDriver() {
        resolved.driver
    }

    @Override
    Map<String, String> getDriverOpts() {
        resolved.driverOpts
    }
}
