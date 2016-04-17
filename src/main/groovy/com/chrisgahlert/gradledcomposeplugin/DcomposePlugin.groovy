package com.chrisgahlert.gradledcomposeplugin

import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.tasks.*
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer

@CompileStatic
class DcomposePlugin implements Plugin<Project> {


    public static final String TASK_GROUP = "Dcompose Docker"
    public static final String TASK_GROUP_ALL = "$TASK_GROUP (all)"
    public static final String TASK_GROUP_CONTAINER = "$TASK_GROUP '%s' container"
    public static final String CONFIGURATION_NAME = "dcompose"

    @Override
    void apply(Project project) {
        def extension = project.extensions.create("dcompose", DcomposeExtension, project.tasks, project.rootDir)

        def config = project.configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true);

        config.dependencies.add(project.dependencies.create('com.github.docker-java:docker-java:3.0.0-RC4'))

        createAllTask(project.tasks, "createContainers", DcomposeContainerCreateTask)
        createAllTask(project.tasks, "startContainers", DcomposeContainerStartTask)
        createAllTask(project.tasks, "stopContainers", DcomposeContainerStopTask)
        createAllTask(project.tasks, "removeContainers", DcomposeContainerRemoveTask)
        createAllTask(project.tasks, "removeImages", DcomposeImageRemoveTask)
        createAllTask(project.tasks, "buildImages", DcomposeImageBuildTask)
        createAllTask(project.tasks, "pullImages", DcomposeImagePullTask)

        project.afterEvaluate {
            project.tasks.withType(AbstractDcomposeTask) { AbstractDcomposeTask task ->
                task.group = String.format(TASK_GROUP_CONTAINER, task.container.name)
            }
        }

    }

    private void createAllTask(TaskContainer tasks, String name, Class taskClass) {
        def allTask = tasks.create(name)
        allTask.group = TASK_GROUP_ALL

        tasks.withType(taskClass) { Task task ->
            allTask.dependsOn task
        }
    }
}