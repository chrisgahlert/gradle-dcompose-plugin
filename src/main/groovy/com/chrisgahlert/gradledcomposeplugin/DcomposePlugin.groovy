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
import com.chrisgahlert.gradledcomposeplugin.extension.Volume
import com.chrisgahlert.gradledcomposeplugin.tasks.*
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStartTask
import com.chrisgahlert.gradledcomposeplugin.tasks.container.DcomposeContainerStopTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageBuildTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImagePullTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImagePushTask
import com.chrisgahlert.gradledcomposeplugin.tasks.image.DcomposeImageRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.network.DcomposeNetworkCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.network.DcomposeNetworkRemoveTask
import com.chrisgahlert.gradledcomposeplugin.tasks.volume.DcomposeVolumeCreateTask
import com.chrisgahlert.gradledcomposeplugin.tasks.volume.DcomposeVolumeRemoveTask
import com.chrisgahlert.gradledcomposeplugin.utils.DcomposeUtils
import com.chrisgahlert.gradledcomposeplugin.utils.DockerClassLoaderFactory
import com.chrisgahlert.gradledcomposeplugin.utils.DockerExecutor
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskContainer

@TypeChecked
class DcomposePlugin implements Plugin<Project> {

    public static final String TASK_GROUP = "Dcompose Docker"
    public static final String TASK_GROUP_ALL = "$TASK_GROUP (all)"
    public static final String TASK_GROUP_DEPLOY = "$TASK_GROUP (deploy)"
    public static final String TASK_GROUP_NETWORKS = "$TASK_GROUP (networks)"
    public static final String TASK_GROUP_VOLUMES = "$TASK_GROUP (volumes)"
    public static final String TASK_GROUP_SERVICE_TEMPLATE = "$TASK_GROUP '%s' service"
    public static final String CONFIGURATION_NAME = "dcompose"
    public static final String EXTENSION_NAME = "dcompose"
    public static final String DOCKER_DEPENDENCY = 'com.github.docker-java:docker-java:3.2.1'
    public static final String SLF4J_DEPENDENCY = 'org.slf4j:slf4j-simple:1.7.30'
    public static final String SNAKEYAML_DEPENDENCY = 'org.yaml:snakeyaml:1.26'
    public static final String ACTIVATION_DEPENDENCY = 'javax.activation:activation:1.1.1'
    public static final String LEGACY_CLASSPATH_PROPERTY = 'com.chrisgahlert.gradledcomposeplugin.LEGACY_CLASSPATH'

    @Override
    @TypeChecked(TypeCheckingMode.SKIP)
    void apply(Project project) {
        def extension = createExtension(project)

        def classLoaderFactory = createOrGetClassLoaderFactory(project)
        def dockerExecutor = new DockerExecutor(classLoaderFactory, extension)
        extension.setDockerHostUri({ dockerExecutor.buildClientConfig().dockerHost })

        injectClassLoaderUtil(project, dockerExecutor)
        createAllTasks(project.tasks)
        createDeployTasks(project, extension)
        updateTaskGroups(project)
        injectExtraProperties(project)
        validateContainers(project, extension)
        createServiceTasks(project, extension)
        createNetworkTasks(project, extension)
        createVolumeTasks(project, extension)
    }

    private DcomposeExtension createExtension(Project project) {
        String dirHash = DcomposeUtils.sha1Hash(project.projectDir.canonicalPath)
        def instanceHash = dirHash.substring(0, 8)
        def namePrefix = "dcompose_${instanceHash}_"

        project.extensions.create(EXTENSION_NAME, DcomposeExtension, project, namePrefix as String)
    }

    private DockerClassLoaderFactory createOrGetClassLoaderFactory(Project project) {
        def useLegacyClasspath = Boolean.valueOf(System.getProperty(LEGACY_CLASSPATH_PROPERTY, 'false'))

        def config
        if (useLegacyClasspath) {
            config = createOrGetDockerConfiguration(project.configurations, project.dependencies)
        } else {
            def rootBuildscript = project.rootProject.buildscript
            config = createOrGetDockerConfiguration(rootBuildscript.configurations, rootBuildscript.dependencies)
        }

        return new DockerClassLoaderFactory(config)
    }

    private Configuration createOrGetDockerConfiguration(ConfigurationContainer configurations, DependencyHandler dependencies) {
        def config = configurations.findByName(CONFIGURATION_NAME)
        if (config != null) {
            return config
        }

        config = configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)

        config.setCanBeResolved(true)
        config.setCanBeConsumed(false)

        config.dependencies.addAll([
                dependencies.create(DOCKER_DEPENDENCY),
                dependencies.create(SLF4J_DEPENDENCY),
                dependencies.create(SNAKEYAML_DEPENDENCY),
                dependencies.create(ACTIVATION_DEPENDENCY)
        ])

        return config
    }

    private injectClassLoaderUtil(Project project, DockerExecutor dockerExecutor) {
        def taskGraph = project.gradle.taskGraph

        project.tasks.withType(AbstractDcomposeTask) { AbstractDcomposeTask task ->
            task.dockerExecutor = dockerExecutor

            // FIXME: fixes resource lock exception for Gradle 4+ - should be more elegant
            taskGraph.whenReady {
                if (taskGraph.hasTask(task)) {
                    dockerExecutor.getDockerClassLoader()
                }
            }
        }
    }

    private void createAllTasks(TaskContainer tasks) {
        def allTaskGroups = [
                'createContainers': DcomposeContainerCreateTask,
                'startContainers' : DcomposeContainerStartTask,
                'stopContainers'  : DcomposeContainerStopTask,
                'removeContainers': DcomposeContainerRemoveTask,
                'removeImages'    : DcomposeImageRemoveTask,
                'createNetworks'  : DcomposeNetworkCreateTask,
                'removeNetworks'  : DcomposeNetworkRemoveTask,
                'pushImages'      : DcomposeImagePushTask,
                'createVolumes'   : DcomposeVolumeCreateTask,
                'removeVolumes'   : DcomposeVolumeRemoveTask,
        ]

        allTaskGroups.each { name, taskClass ->
            def allTask = tasks.create(name)
            allTask.group = TASK_GROUP_ALL

            tasks.withType(taskClass as Class) { Task task ->
                allTask.dependsOn task
            }
        }

        tasks.create('buildImages') { Task allTask ->
            allTask.group = TASK_GROUP_ALL

            allTask.dependsOn {
                tasks.withType(DcomposeImageBuildTask).findAll { DcomposeImageBuildTask task ->
                    !task.service.hasImage()
                }
            }
        }
        tasks.create('pullImages') { Task allTask ->
            allTask.group = TASK_GROUP_ALL

            allTask.dependsOn {
                tasks.withType(DcomposeImagePullTask).findAll { DcomposeImagePullTask task ->
                    task.service.hasImage()
                }
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
        project.afterEvaluate {
            project.tasks.withType(AbstractDcomposeServiceTask) { AbstractDcomposeServiceTask task ->
                task.group = String.format(TASK_GROUP_SERVICE_TEMPLATE, task.service?.name)
            }
            project.tasks.withType(AbstractDcomposeNetworkTask) { AbstractDcomposeNetworkTask task ->
                task.group = TASK_GROUP_NETWORKS
            }
            project.tasks.withType(AbstractDcomposeVolumeTask) { AbstractDcomposeVolumeTask task ->
                task.group = TASK_GROUP_VOLUMES
            }
            project.tasks.withType(DcomposeComposeFileTask) { DcomposeComposeFileTask task ->
                task.group = TASK_GROUP_DEPLOY
            }
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
            project.tasks.create(service.pushImageTaskName, DcomposeImagePushTask).service = service
        }

        project.afterEvaluate {
            extension.services.all { Service service ->
                if (!service.hasImage()) {
                    if (service.buildFiles != null) {
                        project.tasks.create(service.copyBuildFilesTaskName, Sync) { Sync task ->
                            task.group = String.format(TASK_GROUP_SERVICE_TEMPLATE, service.name)
                            task.into service.baseDir ?: DcomposeUtils.getDefaultBaseDir(service, project)
                            task.with service.buildFiles
                        }

                        project.tasks.getByName(service.buildImageTaskName).dependsOn service.copyBuildFilesTaskName
                    }
                }
            }
        }
    }

    private void createNetworkTasks(Project project, DcomposeExtension extension) {
        extension.networks.all { Network network ->
            project.tasks.create(network.createTaskName, DcomposeNetworkCreateTask).network = network
            project.tasks.create(network.removeTaskName, DcomposeNetworkRemoveTask).network = network
        }
    }

    private void createVolumeTasks(Project project, DcomposeExtension extension) {
        extension.volumes.all { Volume volume ->
            project.tasks.create(volume.createTaskName, DcomposeVolumeCreateTask).volume = volume
            project.tasks.create(volume.removeTaskName, DcomposeVolumeRemoveTask).volume = volume
        }
    }
}
