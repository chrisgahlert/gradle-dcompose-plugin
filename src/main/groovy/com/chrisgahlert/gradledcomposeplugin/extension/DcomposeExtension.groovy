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
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.GUtil

@TypeChecked
class DcomposeExtension {
    final private Project project

    final private NamedDomainObjectContainer<DefaultService> services

    final private NamedDomainObjectContainer<DefaultNetwork> networks

    final private NamedDomainObjectContainer<DefaultVolume> volumes

    String namePrefix

    Closure dockerClientConfig

    Map<String, Closure> registries = [:]

    File dockerAuthFile = new File('~/.docker/config.json')

    DcomposeExtension(Project project, String namePrefix) {
        this.project = project
        this.namePrefix = namePrefix

        services = project.container(DefaultService) { String name ->
            def service = new DefaultService(name, project.path, { getNamePrefix() })
            def defaultNetwork = networks.findByName(Network.DEFAULT_NAME)
            if (defaultNetwork) {
                service.networks = [defaultNetwork]
            }
            service
        }

        networks = project.container(DefaultNetwork) { String name ->
            new DefaultNetwork(name, project.path, { getNamePrefix() })
        }
        networks.create(Network.DEFAULT_NAME)

        volumes = project.container(DefaultVolume) { String name ->
            new DefaultVolume(name, project.path, { getNamePrefix() })
        }
    }

    void setDockerClientConfig(Action dockerClientConfig) {
        this.dockerClientConfig = { dockerClientConfig.execute(delegate) }
    }

    void setDockerClientConfig(Closure dockerClientConfig) {
        this.dockerClientConfig = dockerClientConfig
    }

    @Deprecated
    Service getByNameOrCreate(String name, Closure config) {
        services.maybeCreate(name)
        services.getByName(name, config)
    }

    @Deprecated
    DefaultService findByName(String name) {
        services.findByName(name)
    }

    @Deprecated
    DefaultService getByName(String name) {
        services.getByName(name)
    }

    NamedDomainObjectContainer<DefaultService> getServices() {
        services
    }

    NamedDomainObjectContainer<DefaultService> services(Closure config) {
        services.configure config
    }

    @Deprecated
    Set<DefaultService> getContainers() {
        project?.logger.warn 'Deprecation warning: Please use the services property instead of the containers property'
        services
    }

    ServiceReference service(String path) {
        def name = path.tokenize(Project.PATH_SEPARATOR).last()
        new ServiceReference(name, parseProjectPath(path))
    }

    private Project parseProjectPath(String path) {
        def targetProject
        if (!path.contains(Project.PATH_SEPARATOR)) {
            targetProject = project
        } else {
            def projectPath = path.substring(0, path.lastIndexOf(Project.PATH_SEPARATOR));
            targetProject = project.findProject(!GUtil.isTrue(projectPath) ? Project.PATH_SEPARATOR : projectPath)

            if (targetProject == null) {
                throw new GradleException("Could not find project with path '$projectPath'")
            }
        }
        targetProject
    }

    @Deprecated
    ServiceReference container(String path) {
        project?.logger.warn 'Deprecation warning: Please use the service method instead of the container method'
        service(path)
    }

    NamedDomainObjectContainer<DefaultNetwork> getNetworks() {
        networks
    }

    NamedDomainObjectContainer<DefaultNetwork> networks(Closure config) {
        networks.configure config
    }

    Network network(String path) {
        def name = path.tokenize(Project.PATH_SEPARATOR).last()
        new NetworkReference(name, parseProjectPath(path))
    }

    NamedDomainObjectContainer<DefaultVolume> getVolumes() {
        volumes
    }

    NamedDomainObjectContainer<DefaultVolume> volumes(Closure config) {
        volumes.configure config
    }

    Volume volume(String path) {
        def name = path.tokenize(Project.PATH_SEPARATOR).last()
        new VolumeReference(name, parseProjectPath(path))
    }

    String getNamePrefix() {
        namePrefix
    }

    void registry(String name, Closure authConfig) {
        registries[name] = authConfig
    }

    def methodMissing(String name, def args) {
        def argsAr = args as Object[]

        if (argsAr.size() != 1 || !(argsAr[0] instanceof Closure)) {
            throw new MissingMethodException(name, DcomposeExtension, argsAr)
        }

        services.maybeCreate(name)
        services.getByName(name, argsAr[0] as Closure)
    }

    def propertyMissing(String name) {
        def service = services.findByName(name)
        def network = networks.findByName(name)
        def volume = volumes.findByName(name)

        if (!service && !network && !volume) {
            throw new MissingPropertyException(name, getClass())
        }

        if (!(service != null ^ network != null ^ volume != null)) {
            throw new GradleException("The property '$name' is ambiguous - are you referring to " +
                    "the service, the network or the volume?\nPlease specify by replacing $name with " +
                    "service('$name'), network('$name') or volume('$name')!")
        }

        service ?: network ?: volume
    }
}
