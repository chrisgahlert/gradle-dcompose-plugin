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

import com.chrisgahlert.gradledcomposeplugin.tasks.*
import com.chrisgahlert.gradledcomposeplugin.utils.DcomposeUtils
import groovy.transform.TypeChecked
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.gradle.util.GUtil

@TypeChecked
class DcomposeExtension {
    final private Project project

    final private Set<DefaultContainer> containers = new HashSet<>()

    String namePrefix

    Closure dockerClientConfig

    DcomposeExtension(Project project) {
        this.project = project

        String hash = DcomposeUtils.sha1Hash(project.rootDir.canonicalPath)
        def pathHash = hash.substring(0, 8)
        namePrefix = "dcompose_${pathHash}_"
    }

    Container getByNameOrCreate(String name, Closure config) {
        def container = findByName(name)

        if (container == null) {
            container = new DefaultContainer(name, project.path, { namePrefix })
            ConfigureUtil.configure(config, container)
            container.validate()

            createContainerTasks(container)
            containers << container
        } else {
            ConfigureUtil.configure(config, container)
        }

        return container
    }

    private void createContainerTasks(DefaultContainer container) {
        def initImage;
        if (container.hasImage()) {
            initImage = project.tasks.create(container.pullTaskName, DcomposeImagePullTask)
        } else {
            initImage = project.tasks.create(container.buildTaskName, DcomposeImageBuildTask)
        }
        initImage.container = container

        def create = project.tasks.create(container.createTaskName, DcomposeContainerCreateTask)
        create.container = container
        create.dependsOn initImage

        def start = project.tasks.create(container.startTaskName, DcomposeContainerStartTask)
        start.container = container
        start.dependsOn create

        def stop = project.tasks.create(container.stopTaskName, DcomposeContainerStopTask)
        stop.container = container

        def removeContainer = project.tasks.create(container.removeContainerTaskName, DcomposeContainerRemoveTask)
        removeContainer.container = container
        removeContainer.dependsOn stop

        def removeImage = project.tasks.create(container.removeImageTaskName, DcomposeImageRemoveTask)
        removeImage.container = container
        removeImage.dependsOn removeContainer
    }

    DefaultContainer findByName(String name) {
        return containers.find { it.name == name }
    }

    DefaultContainer getByName(String name) {
        def container = findByName(name)

        if (container == null) {
            throw new GradleException("dcompose container with name '$name' not found")
        }

        container
    }

    Set<DefaultContainer> getContainers() {
        containers
    }

    ContainerReference container(String path) {
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
        new ContainerReference(name, targetProject)
    }

    def methodMissing(String name, def args) {
        def argsAr = args as Object[]

        if (argsAr.size() != 1 || !(argsAr[0] instanceof Closure)) {
            throw new MissingMethodException(name, DcomposeExtension, argsAr)
        }

        getByNameOrCreate(name, argsAr[0] as Closure)
    }

    def propertyMissing(String name) {
        getByName(name)
    }

}
