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
package com.chrisgahlert.gradledcomposeplugin.tasks.network

import com.chrisgahlert.gradledcomposeplugin.extension.Network
import com.chrisgahlert.gradledcomposeplugin.extension.Network.IpamConfig
import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeNetworkTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@TypeChecked
class DcomposeNetworkCreateTask extends AbstractDcomposeNetworkTask {

    @Input
    String getNetworkName() {
        network.networkName
    }

    @Input
    @Optional
    String getDriver() {
        network.driver
    }

    @Input
    @Optional
    Map<String, String> getDriverOpts() {
        network.driverOpts
    }

    @Input
    @Optional
    Boolean getEnableIpv6() {
        network.enableIpv6
    }

    /**
     * Fix for older Gradle versions
     */
    @Input
    int getIpamHash() {
        network.ipam.hashCode()
    }

    Network.Ipam getIpam() {
        network.ipam
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNetwork() {
        dockerExecutor.runInDockerClasspath {
            ignoreDockerException('NotFoundException') {
                def connectedContainers = dockerExecutor.client
                        .inspectNetworkCmd()
                        .withNetworkId(networkName)
                        .exec()
                        .containers

                connectedContainers?.keySet().each { String containerName ->
                    stopContainer(containerName)

                    // Workaround bug in docker-java not returning a container's connected networks via inspect container command
                    dockerExecutor.client.removeContainerCmd(containerName).withRemoveVolumes(false).exec()
                }

                dockerExecutor.client.removeNetworkCmd().withNetworkId(networkName).exec()
            }

            try {
                def cmd = dockerExecutor.client.createNetworkCmd().withName(networkName)

                if (driver != null) {
                    cmd.withDriver(driver as String)
                }

                if (driverOpts != null) {
                    cmd.withOptions(driverOpts as Map<String, String>)
                }

                if (enableIpv6 != null) {
                    cmd.withEnableIpv6(enableIpv6 as boolean)
                }

                if (ipam != null) {
                    def dockerIpam = dockerExecutor.loadClass('com.github.dockerjava.api.model.Network$Ipam')
                            .newInstance()

                    if (ipam.driver != null) {
                        dockerIpam.driver = ipam.driver as String
                    }

                    if (ipam.options != null) {
                        dockerIpam.options = ipam.options as Map<String, String>
                    }

                    if (ipam.configs != null) {
                        List dockerConfigs = []

                        ipam.configs.each { IpamConfig config ->
                            def dockerConfig = dockerExecutor.loadClass('com.github.dockerjava.api.model.Network$Ipam$Config')
                                    .newInstance()

                            if (config.subnet != null) {
                                dockerConfig.withSubnet(config.subnet as String)
                            }

                            if (config.ipRange != null) {
                                dockerConfig.withIpRange(config.ipRange as String)
                            }

                            if (config.gateway != null) {
                                dockerConfig.withGateway(config.gateway as String)
                            }

                            dockerConfigs << dockerConfig
                        }

                        dockerIpam.withConfig(dockerConfigs)
                    }
                    cmd.withIpam(dockerIpam)
                }

                cmd.exec()
            } catch (Exception e) {
                if (e.getClass() == dockerExecutor.loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                        && e.message?.contains('waiting (1s) for it to exit...')) {
                    createNetwork()
                } else {
                    throw e
                }
            }
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getNetworkState() {
        dockerOutput('network-state') {
            ignoreDockerException('NotFoundException') {
                def result = dockerExecutor.client.inspectNetworkCmd().withNetworkId(networkName).exec()
                result.containers = [:]

                result
            }
        }
    }

}
