package com.chrisgahlert.gradledcomposeplugin.extension

import com.chrisgahlert.gradledcomposeplugin.tasks.*
import groovy.transform.CompileStatic
import org.gradle.api.tasks.TaskContainer
import org.gradle.util.ConfigureUtil

import java.security.MessageDigest

/**
 * Created by chris on 14.04.16.
 */
@CompileStatic
class DcomposeExtension {
    final private TaskContainer tasks

    final private Set<Container> containers = new HashSet<>()

    String containerNamePrefix

    DcomposeExtension(TaskContainer tasks, File rootProjectDir) {
        this.tasks = tasks

        def sha1 = MessageDigest.getInstance("SHA1")
        def digest = sha1.digest(rootProjectDir.canonicalPath.bytes)
        def pathHash = new BigInteger(1, digest).toString(16).substring(0, 7)
        containerNamePrefix = "dcompose_${pathHash}"
    }

    Container getByNameOrCreate(String name, Closure config) {
        def container = findByName(name)

        if (container == null) {
            container = new Container(name, { containerNamePrefix })
            ConfigureUtil.configure(config, container)
            container.validate()

            createContainerTasks(container)
            containers << container
        } else {
            ConfigureUtil.configure(config, container)
        }

        return container
    }

    private void createContainerTasks(Container container) {
        def initImage;
        if (container.hasImage()) {
            initImage = tasks.create(container.pullTaskName, DcomposeImagePullTask)
        } else {
            initImage = tasks.create(container.buildTaskName, DcomposeImageBuildTask)
        }
        initImage.container = container

        def create = tasks.create(container.createTaskName, DcomposeContainerCreateTask)
        create.container = container
        create.dependsOn initImage

        def start = tasks.create(container.startTaskName, DcomposeContainerStartTask)
        start.container = container
        start.dependsOn create

        def stop = tasks.create(container.stopTaskName, DcomposeContainerStopTask)
        stop.container = container

        def removeContainer = tasks.create(container.removeContainerTaskName, DcomposeContainerRemoveTask)
        removeContainer.container = container
        removeContainer.dependsOn stop

        def removeImage = tasks.create(container.removeImageTaskName, DcomposeImageRemoveTask)
        removeImage.container = container
        removeImage.dependsOn removeContainer
    }

    Container findByName(String name) {
        return containers.find { it.name == name }
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

        if (container == null) {
            throw new MissingPropertyException(name, DcomposeExtension)
        }

        container
    }

}
