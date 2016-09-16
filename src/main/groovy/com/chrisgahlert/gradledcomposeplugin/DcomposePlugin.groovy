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
package com.chrisgahlert.gradledcomposeplugin

import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.extension.Network
import com.chrisgahlert.gradledcomposeplugin.extension.Service
import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeServiceTask
import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeTask
import com.chrisgahlert.gradledcomposeplugin.tasks.DcomposeComposeFileTask
import com.chrisgahlert.gradledcomposeplugin.tasks.DcomposeCopyFileFromContainerTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStartTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStopTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageBuildTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImagePullTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.network.DcomposeNetworkCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.network.DcomposeNetworkRemoveTask
import com.chrisgahlert.gradledcomposeplugin.utils.DcomposeUtils
import com.chrisgahlert.gradledcomposeplugin.utils.DockerClassLoaderFactory
import groovy.transform.TypeChecked
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

@TypeChecked
class DcomposePlugin implements Plugin<Project> {

    public static final String TASK_GROUP = "Dcompose Docker"
    public static final String TASK_GROUP_ALL = "$TASK_GROUP (all)"
    public static final String TASK_GROUP_SERVICE_TEMPLATE = "$TASK_GROUP '%s' service"
    public static final String CONFIGURATION_NAME = "dcompose"
    public static final String EXTENSION_NAME = "dcompose"
    public static final String DOCKER_DEPENDENCY = 'com.github.docker-java:docker-java:3.0.5'
    public static final String SLF4J_DEPENDENCY = 'org.slf4j:slf4j-simple:1.7.5'
    public static final String SNAKEYAML_DEPENDENCY = 'org.yaml:snakeyaml:1.17'

    @Override
    void apply(Project project) {
        def extension = createExtension(project)
        createDockerConfiguration(project)
        createAllTasks(project.tasks)
        createDeployTasks(project, extension)
        updateTaskGroups(project)
        injectExtraProperties(project)
        validateContainers(project, extension)
        createServiceTasks(project, extension)
        createNetworkTasks(project, extension)
    }

    private DcomposeExtension createExtension(Project project) {
        String dirHash = DcomposeUtils.sha1Hash(project.projectDir.canonicalPath)
        def instanceHash = dirHash.substring(0, 8)
        def namePrefix = "dcompose_${instanceHash}_"

        project.extensions.create(EXTENSION_NAME, DcomposeExtension, project, namePrefix as String)
    }

    private void createDockerConfiguration(Project project) {
        def config = project.configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)

        config.dependencies.add(project.dependencies.create(DOCKER_DEPENDENCY))
        config.dependencies.add(project.dependencies.create(SLF4J_DEPENDENCY))
        config.dependencies.add(project.dependencies.create(SNAKEYAML_DEPENDENCY))

        def classLoaderFactory = new DockerClassLoaderFactory(config)

        project.tasks.withType(AbstractDcomposeTask) { AbstractDcomposeTask task ->
            task.dockerClassLoaderFactory = classLoaderFactory
        }
    }

    private void createAllTasks(TaskContainer tasks) {
        def allTaskGroups = [
                'createContainers': DcomposeContainerCreateTask,
                'startContainers' : DcomposeContainerStartTask,
                'stopContainers'  : DcomposeContainerStopTask,
                'removeContainers': DcomposeContainerRemoveTask,
                'removeImages'    : DcomposeImageRemoveTask,
                'buildImages'     : DcomposeImageBuildTask,
                'pullImages'      : DcomposeImagePullTask,
                'createNetworks'  : DcomposeNetworkCreateTask,
                'removeNetworks'  : DcomposeNetworkRemoveTask,
        ]

        allTaskGroups.each { name, taskClass ->
            def allTask = tasks.create(name)
            allTask.group = TASK_GROUP_ALL

            tasks.withType(taskClass as Class) { Task task ->
                allTask.dependsOn task
            }
        }
    }

    private void createDeployTasks(Project project, DcomposeExtension extension) {
        def task = project.tasks.create("createComposeFile", DcomposeComposeFileTask)
        task.target = new File(project.buildDir, "docker-compose.yml")

        project.afterEvaluate {
            task.services = extension.services.findAll { Service service -> service.deploy }
        }
    }

    private void updateTaskGroups(Project project) {
        project.tasks.withType(AbstractDcomposeServiceTask) { AbstractDcomposeServiceTask task ->
            task.group = { String.format(TASK_GROUP_SERVICE_TEMPLATE, task.service.name) }
        }
    }

    private void injectExtraProperties(Project project) {
        project.extensions.extraProperties.set(
                DcomposeCopyFileFromContainerTask.simpleName,
                DcomposeCopyFileFromContainerTask
        )
    }

    private void validateContainers(Project project, DcomposeExtension extension) {
        project.gradle.taskGraph.whenReady {
            extension.services.all { Service service ->
                service.validate()
            }
        }
    }

    private void createServiceTasks(Project project, DcomposeExtension extension) {
        extension.services.all { Service service ->
            project.tasks.create(service.pullImageTaskName, DcomposeImagePullTask).service = service
            project.tasks.create(service.buildImageTaskName, DcomposeImageBuildTask).service = service
            project.tasks.create(service.createContainerTaskName, DcomposeContainerCreateTask).service = service
            project.tasks.create(service.startContainerTaskName, DcomposeContainerStartTask).service = service
            project.tasks.create(service.stopContainerTaskName, DcomposeContainerStopTask).service = service
            project.tasks.create(service.removeContainerTaskName, DcomposeContainerRemoveTask).service = service
            project.tasks.create(service.removeImageTaskName, DcomposeImageRemoveTask).service = service
        }
    }

    private void createNetworkTasks(Project project, DcomposeExtension extension) {
        extension.networks.all { Network network ->
            project.tasks.create(network.createTaskName, DcomposeNetworkCreateTask).network = network
            project.tasks.create(network.removeTaskName, DcomposeNetworkRemoveTask).network = network
        }
    }
}