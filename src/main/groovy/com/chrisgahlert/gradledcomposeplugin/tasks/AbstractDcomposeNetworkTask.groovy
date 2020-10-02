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

import com.chrisgahlert.gradledcomposeplugin.DcomposePlugin
import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.extension.DefaultService
import com.chrisgahlert.gradledcomposeplugin.extension.Network
import groovy.transform.TypeChecked
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.Internal

@TypeChecked
class AbstractDcomposeNetworkTask extends AbstractDcomposeTask {
    private Network network

    @Internal
    void setNetwork(Network network) {
        if (this.network != null) {
            throw new ReadOnlyPropertyException("network", this.class)
        }

        this.network = network
    }

    @Internal
    Network getNetwork() {
        return network
    }

    @Internal
    protected Set<DefaultService> getServicesUsingNetwork() {
        Set<DefaultService> result = []

        project.rootProject.allprojects.each { prj ->
            ((ProjectInternal) prj).evaluate()

            if (prj.plugins.hasPlugin(DcomposePlugin)) {
                result.addAll prj.extensions.getByType(DcomposeExtension).services
            }
        }

        new HashSet<>(result.findAll { it.networks.contains(network) })
    }
}
