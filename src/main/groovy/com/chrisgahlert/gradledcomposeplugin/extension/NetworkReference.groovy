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
class NetworkReference extends Network {

    private Project targetProject
    private Network resolvedNetwork

    NetworkReference(String name, Project targetProject) {
        super(name, targetProject.path)
        this.targetProject = targetProject
    }

    Network getResolved() {
        if (resolvedNetwork == null) {
            def extension = targetProject.extensions.findByType(DcomposeExtension)
            if (extension == null) {
                throw new GradleException("Could not find dcompose extension on $targetProject - has the dcompose plugin been applied?")
            }

            resolvedNetwork = extension.networks.maybeCreate(name)
        }

        resolvedNetwork
    }

    @Override
    String getNetworkName() {
        resolved.networkName
    }

    @Override
    String getDriver() {
        resolved.driver
    }

    @Override
    Map<String, String> getDriverOpts() {
        resolved.driverOpts
    }

    @Override
    Ipam getIpam() {
        resolved.ipam
    }

    @Override
    Boolean getEnableIpv6() {
        resolved.enableIpv6
    }
}
