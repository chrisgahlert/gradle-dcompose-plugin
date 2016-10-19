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

    String namePrefix

    Closure dockerClientConfig

    Map<String, Closure> registries = [:]

    DcomposeExtension(Project project, String namePrefix) {
        this.project = project
        this.namePrefix = namePrefix

        services = project.container(DefaultService, { String name ->
            def service = new DefaultService(name, project.path, { DcomposeExtension.this.namePrefix })
            def defaultNetwork = networks.findByName(Network.DEFAULT_NAME)
            if (defaultNetwork) {
                service.networks = [defaultNetwork]
            }
            service
        })

        networks = project.container(DefaultNetwork, { String name ->
            new DefaultNetwork(name, project.path, { DcomposeExtension.this.namePrefix })
        })
        networks.create(Network.DEFAULT_NAME)
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

        if (service && network) {
            throw new GradleException("The property '$name' is ambiguous - are you referring to the service or the network?\n" +
                    "Please specify by replacing $name with network('$name') or service('$name')!")
        }
        if (service) {
            return service
        }
        if (network) {
            return network
        }

        throw new MissingPropertyException(name, getClass())
    }
}
