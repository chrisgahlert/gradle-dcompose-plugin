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
import com.chrisgahlert.gradledcomposeplugin.extension.Volume
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

@TypeChecked
class DcomposeComposeFileTask extends AbstractDcomposeTask {
    @Internal
    Collection<? extends Service> dcomposeServices = []

    @OutputFile
    File target

    /**
     * todo:
     */
    @Internal
    List<Closure> beforeSaves = []

    @Input
    @Optional
    Boolean useAWSCompat

    @Input
    boolean useTags = false

    @Input
    String version = '3'

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
    @TypeChecked(TypeCheckingMode.SKIP)
    void createComposeFile() {
        def yml = [
                version : version,
                services: [:],
                networks: [:],
                volumes : [:]
        ]

        def networks = new HashSet<Network>()
        def namedVolumes = new HashSet<String>()

        dcomposeServices.each { Service service ->
            networks.addAll service.networks
            def spec = [
                    image: useTags ? service.repository : service.repositoryDigest
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
                generateVolumes(service, spec.volumes as List<String>, namedVolumes)
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
                if (!spec.links) {
                    spec.links = []
                }

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
            if (service.logConfig) {
                spec.logging = [driver: service.logConfig]
                if (service.logOpts) spec.logging << [options: service.logOpts]
            }

            switch (version) {
                case '2':
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
                    if (service.memLimit) {
                        spec.mem_limit = service.memLimit
                    }
                    break;

                case '3':
                    spec.deploy = [
                            resources: [
                                    limits      : [:],
                                    reservations: [:]
                            ]
                    ]

                    if (service.volumesFrom) {
                        logger.warn('Warning: volumesFrom is not supported by version 3 compose files')
                    }
                    if (service.memLimit) {
                        spec.deploy.resources.limits.memory = service.memLimit.toString()
                    }
                    break;

                default:
                    throw new GradleException("Unrecognized compose file version: '$version'")
            }

            yml.services[service.name] = spec
        }

        namedVolumes.each {
            yml.volumes[it] = generateVolume(it)
        }

        networks.each {
            yml.networks[it.name] = generateNetwork(it.name)
        }


        writeYaml(yml)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Map<String, ?> generateVolume(String volumeName) {
        def result = [:]
        def volume = project.getExtensions().getByType(DcomposeExtension).volumes.findByName(volumeName)

        if (volume?.driver) {
            result.driver = volume.driver
        }

        if (volume?.driverOpts) {
            result.driver_opts = volume.driverOpts
        }

        result
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Map<String, ?> generateNetwork(String networkName) {
        def network = project.getExtensions().getByType(DcomposeExtension).networks.findByName(networkName)
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
                        if (version == '2') {
                            c.ip_range = config.ipRange
                        } else {
                            logger.warn('Network ipam config for "ip_range" is not supported by compose files version ' + version)
                        }
                    }

                    if (config.gateway != null) {
                        if (version == '2') {
                            c.gateway = config.gateway
                        } else {
                            logger.warn('Network ipam config for "gateway" is not supported by compose files version ' + version)
                        }
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
            dockerExecutor.runInDockerClasspath {
                def options = dockerExecutor.loadClass('org.yaml.snakeyaml.DumperOptions').newInstance()
                options.defaultFlowStyle = dockerExecutor.loadClass('org.yaml.snakeyaml.DumperOptions$FlowStyle').BLOCK
                def snakeYaml = dockerExecutor.loadClass('org.yaml.snakeyaml.Yaml').newInstance(options)
                snakeYaml.dump(yml, out);
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void generateVolumes(Service service, List<String> result, Set<String> namedVolumes) {
        dockerExecutor.runInDockerClasspath {
            def volumeClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.Volume')
            def bindParser = dockerExecutor.loadClass('com.github.dockerjava.api.model.Bind')
                    .getMethod('parse', String)

            def imageVolumes = []
            dockerExecutor.client.inspectImageCmd(service.imageId).exec().config.volumes?.keySet().each {
                imageVolumes << volumeClass.newInstance(it as String)
            }

            def knownVolumes = new HashSet(imageVolumes)
            knownVolumes.addAll(service.volumes.collect {
                volumeClass.newInstance(it as String)
            })

            def binds = service.binds.collect {
                bindParser.invoke(null, (it instanceof Volume.VolumeDependency ? it.serviceDefinition : it) as String)
            }

            for (def volume : knownVolumes) {
                def bind = binds.find { volume == it.volume }

                if (!bind) {
                    def volumeName = service.name + '__' + GUtil.toLowerCamelCase(volume.path)
                    binds << bindParser.invoke(null, "$volumeName:$volume.path" as String)
                }
            }

            result.addAll(binds.collect { it as String })
            binds.each {
                if (it.path =~ /^[^\.\/].*/) {
                    namedVolumes << it.path
                }
            }
        }
    }
}
