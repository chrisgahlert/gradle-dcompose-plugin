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

import com.chrisgahlert.gradledcomposeplugin.utils.DcomposeUtils
import groovy.transform.TypeChecked
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Project
import org.gradle.util.GUtil

@TypeChecked
class DcomposeExtension implements NamedDomainObjectFactory<DefaultService> {
    final private Project project

    final private NamedDomainObjectContainer<DefaultService> services

    String namePrefix

    Closure dockerClientConfig

    DcomposeExtension(Project project) {
        this.project = project
        services = project.container(DefaultService, this)

        String hash = DcomposeUtils.sha1Hash(project.projectDir.canonicalPath)
        def pathHash = hash.substring(0, 8)
        namePrefix = "dcompose_${pathHash}_"
    }

    @Override
    DefaultService create(String name) {
        new DefaultService(name, project.path, { namePrefix })
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

    @Deprecated
    Set<DefaultService> getContainers() {
        project?.logger.warn 'Deprecation warning: Please use the services property instead of the containers property'
        services
    }

    ServiceReference service(String path) {
        def targetProject
        if (!path.contains(Project.PATH_SEPARATOR)) {
            targetProject = project
        } else {
            def projectPath = path.substring(0, path.lastIndexOf(Project.PATH_SEPARATOR));
            targetProject = project.findProject(!GUtil.isTrue(projectPath) ? Project.PATH_SEPARATOR : projectPath)

            if(targetProject == null) {
                throw new GradleException("Could not find project with path '$projectPath'")
            }
        }

        def name = path.tokenize(Project.PATH_SEPARATOR).last()
        new ServiceReference(name, targetProject)
    }

    @Deprecated
    ServiceReference container(String path) {
        project?.logger.warn 'Deprecation warning: Please use the service method instead of the container method'
        service(path)
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
        if(service == null) {
            throw new MissingPropertyException(name, getClass())
        }
        service
    }
}
