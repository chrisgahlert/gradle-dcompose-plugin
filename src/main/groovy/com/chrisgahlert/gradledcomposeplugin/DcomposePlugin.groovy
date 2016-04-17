package com.chrisgahlert.gradledcomposeplugin

import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.helpers.DcomposeTaskAnnotationProcessor
import com.chrisgahlert.gradledcomposeplugin.tasks.*
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

@CompileStatic
class DcomposePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
//        project.extensions.create("dcompose", DcomposeExtension, project.tasks)
        def extension = project.extensions.create("dcompose", DcomposeExtension, project.tasks, project.rootDir)


        def config = project.configurations.create("dcompose")
            .setVisible(false)
            .setTransitive(true);

        config.dependencies.add(project.dependencies.create('com.github.docker-java:docker-java:3.0.0-RC4'))
//            dependencies.add(project.dependencies.create('org.slf4j:slf4j-simple:1.7.5'))
//            dependencies.add(project.dependencies.create('cglib:cglib:3.2.0'))

//        project.buildscript.dependencies {
//            classpath ('com.github.docker-java:docker-java:3.0.0-RC4') {
//                transitive = false
//            }
//        }


        def createAll = project.tasks.create("createContainers")
        project.tasks.withType(DcomposeContainerCreateTask) {
            createAll.dependsOn it
        }

        def startAll = project.tasks.create("startContainers")
        project.tasks.withType(DcomposeContainerStartTask) {
            startAll.dependsOn it
        }

        def stopAll = project.tasks.create("stopContainers")
        project.tasks.withType(DcomposeContainerStopTask) {
            stopAll.dependsOn it
        }

        def removeAllContainers = project.tasks.create("removeContainers")
        project.tasks.withType(DcomposeContainerRemoveTask) {
            removeAllContainers.dependsOn it
        }

        def removeAllImages = project.tasks.create("removeImages")
        project.tasks.withType(DcomposeImageRemoveTask) {
            removeAllImages.dependsOn it
        }

        project.gradle.taskGraph.addTaskExecutionListener(new DcomposeTaskAnnotationProcessor(config))

        project.afterEvaluate {
            project.tasks.withType(DcomposeImagePullTask) { DcomposeImagePullTask task ->
                task.registry = extension.registry
            }
        }


        Thread.currentThread().contextClassLoader = new MyCl(Thread.currentThread().contextClassLoader)
    }

    public static class MyCl extends ClassLoader {
        MyCl(ClassLoader var1) {
            super(var1)
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(name .equals('javax.ws.rs.client.ClientBuilder')) {
                println 'here'
            }
            return super.loadClass(name, resolve)
        }
    }
}