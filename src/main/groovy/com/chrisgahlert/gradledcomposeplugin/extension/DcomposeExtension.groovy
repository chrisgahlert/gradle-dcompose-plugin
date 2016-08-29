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

import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStartTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStopTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageBuildTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImagePullTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageRemoveTask
import com.chrisgahlert.gradledcomposeplugin.utils.DcomposeUtils
import groovy.transform.TypeChecked
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.gradle.util.GUtil

@TypeChecked
class DcomposeExtension {
    final private Project project

    final private Set<DefaultService> services = new HashSet<>()

    String namePrefix

    Closure dockerClientConfig

    DcomposeExtension(Project project) {
        this.project = project

        String hash = DcomposeUtils.sha1Hash(project.projectDir.canonicalPath)
        def pathHash = hash.substring(0, 8)
        namePrefix = "dcompose_${pathHash}_"
    }

    Service getByNameOrCreate(String name, Closure config) {
        def container = findByName(name)

        if (container == null) {
            container = new DefaultService(name, project.path, { namePrefix })
            ConfigureUtil.configure(config, container)
            container.validate()

            createContainerTasks(container)
            services << container
        } else {
            ConfigureUtil.configure(config, container)
        }

        return container
    }

    private void createContainerTasks(DefaultService service) {
        def initImage;
        if (service.hasImage()) {
            initImage = project.tasks.create(service.pullImageTaskName, DcomposeImagePullTask)
        } else {
            initImage = project.tasks.create(service.buildImageTaskName, DcomposeImageBuildTask)
        }
        initImage.service = service

        def create = project.tasks.create(service.createContainerTaskName, DcomposeContainerCreateTask)
        create.service = service
        create.dependsOn initImage

        def start = project.tasks.create(service.startContainerTaskName, DcomposeContainerStartTask)
        start.service = service
        start.dependsOn create

        def stop = project.tasks.create(service.stopContainerTaskName, DcomposeContainerStopTask)
        stop.service = service

        def removeContainer = project.tasks.create(service.removeContainerTaskName, DcomposeContainerRemoveTask)
        removeContainer.service = service
        removeContainer.dependsOn stop

        def removeImage = project.tasks.create(service.removeImageTaskName, DcomposeImageRemoveTask)
        removeImage.service = service
        removeImage.dependsOn removeContainer
    }

    DefaultService findByName(String name) {
        return services.find { it.name == name }
    }

    DefaultService getByName(String name) {
        def container = findByName(name)

        if (container == null) {
            throw new GradleException("dcompose service with name '$name' not found")
        }

        container
    }

    Set<DefaultService> getServices() {
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

        getByNameOrCreate(name, argsAr[0] as Closure)
    }

    def propertyMissing(String name) {
        def container = findByName(name)
        if(container == null) {
            throw new MissingPropertyException(name, getClass())
        }
        container
    }

}
