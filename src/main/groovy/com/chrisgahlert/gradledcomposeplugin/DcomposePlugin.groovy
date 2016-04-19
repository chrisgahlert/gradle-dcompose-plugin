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
import com.chrisgahlert.gradledcomposeplugin.tasks.*
import com.chrisgahlert.gradledcomposeplugin.utils.DockerClassLoaderFactory
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

@CompileStatic
class DcomposePlugin implements Plugin<Project> {


    public static final String TASK_GROUP = "Dcompose Docker"
    public static final String TASK_GROUP_ALL = "$TASK_GROUP (all)"
    public static final String TASK_GROUP_CONTAINER_TEMPLATE = "$TASK_GROUP '%s' container"
    public static final String CONFIGURATION_NAME = "dcompose"
    public static final String EXTENSION_NAME = "dcompose"
    public static final String DOCKER_DEPENDENCY = 'com.github.docker-java:docker-java:3.0.0-RC4'

    @Override
    void apply(Project project) {
        createExtension(project)
        createDockerConfiguration(project)
        createAllTasks(project.tasks)
        updateTaskGroups(project)
        injectExtraProperties(project)
    }

    private DcomposeExtension createExtension(Project project) {
        project.extensions.create(EXTENSION_NAME, DcomposeExtension, project.tasks, project.rootDir)
    }

    private void createDockerConfiguration(Project project) {
        def config = project.configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)

        config.dependencies.add(project.dependencies.create(DOCKER_DEPENDENCY))

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
        ]

        allTaskGroups.each { name, taskClass ->
            def allTask = tasks.create(name)
            allTask.group = TASK_GROUP_ALL

            tasks.withType(taskClass as Class) { Task task ->
                allTask.dependsOn task
            }
        }
    }

    private void updateTaskGroups(Project project) {
        project.afterEvaluate {
            project.tasks.withType(AbstractDcomposeTask) { AbstractDcomposeTask task ->
                task.group = String.format(TASK_GROUP_CONTAINER_TEMPLATE, task.container.name)
            }
        }
    }

    private void injectExtraProperties(Project project) {
        project.extensions.extraProperties.set(
                DcomposeCopyFileFromContainerTask.simpleName,
                DcomposeCopyFileFromContainerTask
        )
    }
}