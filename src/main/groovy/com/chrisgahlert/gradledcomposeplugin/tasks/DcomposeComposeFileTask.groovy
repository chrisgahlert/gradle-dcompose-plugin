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

import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.extension.Network
import com.chrisgahlert.gradledcomposeplugin.extension.Service
import com.chrisgahlert.gradledcomposeplugin.utils.ImageRef
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

@TypeChecked
class DcomposeComposeFileTask extends AbstractDcomposeTask {
    Collection<? extends Service> dcomposeServices = []

    @OutputFile
    File target

    List<Closure> beforeSaves = []

    Boolean useAWSCompat

    DcomposeComposeFileTask() {
        dependsOn {
            dcomposeServices?.collect { Service service ->
                if (service.hasImage()) {
                    "$service.projectPath:$service.pullImageTaskName"
                } else {
                    "$service.projectPath:$service.buildImageTaskName"
                }
            }
        }

        outputs.upToDateWhen { false }
    }

    void setServices(Collection<? extends Service> dcomposeServices) {
        this.dcomposeServices = dcomposeServices
    }

    void beforeSave(Closure beforeSave) {
        this.beforeSaves << beforeSave
    }

    void beforeSave(Action<Map<String, Object>> beforeSave) {
        this.beforeSaves << { Map<String, Object> config ->
            beforeSave.execute(config)
        }
    }

    @TaskAction
    void createComposeFile() {
        def yml = [
                version : '2',
                services: [:],
                networks: [:],
                volumes : [:]
        ]

        def networks = new HashSet<Network>()

        dcomposeServices.each { Service service ->
            networks.addAll service.networks
            def imageRef = ImageRef.parse(service.repository)
            def spec = [
                    image: useAWSCompat ? imageRef.toString() : "$imageRef.registryWithRepository@sha256:$service.imageId" as String
            ]

            if (service.dependsOn) {
                if (useAWSCompat) {
                    spec.links = service.dependsOn.collect { it.name }
                } else {
                    spec.depends_on = service.dependsOn.collect { it.name }
                }
            }
            if (service.command) {
                spec.command = service.command
            }
            if (service.entrypoints) {
                spec.entrypoint = service.entrypoints
            }
            if (service.env) {
                spec.environment = service.env
            }
            if (service.workingDir) {
                spec.working_dir = service.workingDir
            }
            if (service.user) {
                spec.user = service.user
            }
            if (service.readonlyRootfs) {
                logger.warn("Warning: readonlyRootfs is not supported by docker-compose")
            }
            if (service.volumes || service.binds || service.preserveVolumes) {
                spec.volumes = []
                generateVolumes(service, spec.volumes as List<String>, yml.volumes as Map<String, ?>)
            }
            if (service.volumesFrom) {
                spec.volumes_from = []
                service.volumesFrom.each {
                    if (it instanceof Service) {
                        spec.volumes_from << it.name
                    } else {
                        logger.warn("Warning: volumesFrom to a non-managed service is not supported by docker-compose")
                    }
                }
            }
            if (service.exposedPorts) {
                spec.expose = service.exposedPorts
            }
            if (service.portBindings) {
                spec.ports = service.portBindings
            }
            if (service.publishAllPorts) {
                logger.warn("Warning: publishAllPorts is not supported by docker-compose")
            }
            if (service.links) {
                spec.links = []
                service.links.each {
                    if (it instanceof Service.ServiceDependency) {
                        spec.links << (it as Service.ServiceDependency).serviceDefinition
                    } else {
                        logger.warn("Warning: linking to a non-managed service is not supported by docker-compose")
                    }
                }
            }
            if (service.hostName) {
                spec.hostname = service.hostName
            }
            if (service.dns) {
                spec.dns = service.dns
            }
            if (service.dnsSearch) {
                spec.dns_search = service.dnsSearch
            }
            if (service.extraHosts) {
                spec.extra_hosts = service.extraHosts
            }
            if (service.networkMode) {
                spec.network_mode = service.networkMode
            }
            if (service.attachStdin) {
                logger.warn("Warning: attachStdin is not supported by docker-compose")
            }
            if (service.attachStdout) {
                logger.warn("Warning: attachStdout is not supported by docker-compose")
            }
            if (service.attachStderr) {
                logger.warn("Warning: attachStderr is not supported by docker-compose")
            }
            if (service.privileged) {
                spec.privileged = service.privileged
            }
            if (service.restart) {
                spec.restart = service.restart
            }
            if (service.networks) {
                def networksSpec = [:]
                service.networks.each { network ->
                    if (!service.networkMode || network.name != Network.DEFAULT_NAME) {
                        def networkSpec = [
                                aliases: (service.aliases ?: []) as String[]
                        ]
                        networksSpec[network.name] = networkSpec
                    }
                }
                if (networksSpec) {
                    spec.networks = networksSpec
                }
            }

            if (service.memLimit) {
                spec.mem_limit = service.memLimit
            }

            yml.services[service.name] = spec
        }

        networks.each {
            yml.networks[it.name] = generateNetwork(it.name)
        }


        writeYaml(yml)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Map<String, ?> generateNetwork(String networkName) {
        def network = project.getExtensions().getByType(DcomposeExtension).networks.find { it.name == networkName }
        def result = [:]

        if (!network) {
            logger.error("Error: Could not find unknown network named $networkName")
            return result
        }

        if (network.driver != null) {
            result.driver = network.driver
        }

        if (network.driverOpts != null) {
            result.driver_opts = network.driverOpts
        }

        if (network.enableIpv6) {
            logger.warn('Warning: Enabling ipv6 is not supported at the moment')
        }

        if (network.ipam != null) {
            result.ipam = [:]

            if (network.ipam.driver != null) {
                result.ipam.driver = network.ipam.driver
            }

            if (network.ipam.options != null) {
                logger.warn('Warning: Ipam driver options are not supported at the moment')
            }

            if (network.ipam.configs != null) {
                result.ipam.config = network.ipam.configs.collect { config ->
                    def c = [:]

                    if (config.subnet != null) {
                        c.subnet = config.subnet
                    }

                    if (config.ipRange != null) {
                        c.ip_range = config.ipRange
                    }

                    if (config.gateway != null) {
                        c.gateway = config.gateway
                    }

                    c
                }
            }
        }

        result
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    private void writeYaml(yml) {
        beforeSaves?.each { Closure config ->
            config(yml)
        }

        target.withWriter { out ->
            runInDockerClasspath {
                def options = loadClass('org.yaml.snakeyaml.DumperOptions').newInstance()
                options.defaultFlowStyle = loadClass('org.yaml.snakeyaml.DumperOptions$FlowStyle').BLOCK
                def snakeYaml = loadClass('org.yaml.snakeyaml.Yaml').newInstance(options)
                snakeYaml.dump(yml, out);
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void generateVolumes(Service service, List<String> result, Map<String, ?> namedVolumes) {
        runInDockerClasspath {
            def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
            def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)

            def imageVolumes = []
            client.inspectImageCmd(service.imageId).exec().config.volumes?.keySet().each {
                imageVolumes << volumeClass.newInstance(it as String)
            }

            def knownVolumes = new HashSet(imageVolumes)
            knownVolumes.addAll(service.volumes.collect {
                volumeClass.newInstance(it as String)
            })

            def binds = service.binds.collect {
                bindParser.invoke(null, it)
            }

            for (def volume : knownVolumes) {
                def bind = binds.find { volume == it.volume }

                if (!bind) {
                    def volumeName = service.name + '__' + GUtil.toLowerCamelCase(volume.path)
                    binds << bindParser.invoke(null, "$volumeName:$volume.path" as String)
                }
            }

            result.addAll(binds.collect { it as String })
            binds.findAll {
                it.path ==~ /^[^\.\/].*/
            }.each {
                namedVolumes[it.path] = [:]
            }
        }
    }
}
