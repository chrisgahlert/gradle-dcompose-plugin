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
import org.gradle.util.ConfigureUtil

@TypeChecked
public class DefaultNetwork extends Network {

    final transient Closure<String> dockerPrefix

    String driver
    Map<String, String> driverOpts
    Ipam ipam
    Boolean enableIpv6

    DefaultNetwork(String name, String projectPath, Closure<String> dockerPrefix) {
        super(name, projectPath)
        this.dockerPrefix = dockerPrefix
    }

    @Override
    String getNetworkName() {
        dockerPrefix() + name
    }

    Ipam ipam(Closure config) {
        ConfigureUtil.configure(config, getIpam())
    }

    Ipam getIpam() {
        if (ipam == null) {
            ipam = new Ipam()
        }

        ipam
    }
}