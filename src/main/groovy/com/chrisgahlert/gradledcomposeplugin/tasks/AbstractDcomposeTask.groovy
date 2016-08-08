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
package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.DcomposePlugin
import com.chrisgahlert.gradledcomposeplugin.extension.Container
import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.extension.DefaultContainer
import com.chrisgahlert.gradledcomposeplugin.utils.DockerClassLoaderFactory
import groovy.json.JsonBuilder
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.Input
import org.gradle.util.ConfigureUtil

@TypeChecked
class AbstractDcomposeTask extends DefaultTask {

    private Set<String> initializedOutputs = []

    private Container container

    DockerClassLoaderFactory dockerClassLoaderFactory

    @Input
    def getDockerClientConfig() {
        runInDockerClasspath {
            def config = buildClientConfig()
            toJson(config)
        }
    }

    void setContainer(Container container) {
        if (this.container != null) {
            throw new ReadOnlyPropertyException("container", this.class)
        }

        this.container = container
    }

    Container getContainer() {
        return container
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def getClient() {
        def clientConfig = buildClientConfig()
        def clientConfigClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
        def clientBuilderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
        def getInstanceMethod = clientBuilderClass.getMethod('getInstance', clientConfigClass)
        getInstanceMethod.invoke(null, clientConfig).build()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def buildClientConfig() {
        def configBuilderClass = loadClass('com.github.dockerjava.core.DefaultDockerClientConfig')
        def configBuilder = configBuilderClass.getMethod('createDefaultConfigBuilder').invoke(null)

        def extension = project.extensions.getByType(DcomposeExtension)
        if(extension.dockerClientConfig != null) {
            ConfigureUtil.configure(extension.dockerClientConfig, configBuilder)
        }

        configBuilder.build()
    }

    protected Class loadClass(String name) {
        Thread.currentThread().contextClassLoader.loadClass(name)
    }

    protected def ignoreDockerException(String exceptionClassName, Closure action) {
        ignoreDockerExceptions([exceptionClassName], action)
    }

    protected def ignoreDockerExceptions(List<String> exceptionClassNames, Closure action) {
        try {
            return action()
        } catch (Throwable t) {
            def exceptionMatched = exceptionClassNames.find { exceptionClassName ->
                t.getClass() == loadClass("com.github.dockerjava.api.exception.$exceptionClassName")
            }

            if (exceptionMatched) {
                logger.debug("Caught expected Docker exception:", t)
                return null
            }

            throw t
        }
    }

    protected def runInDockerClasspath(Closure action) {
        def originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = dockerClassLoaderFactory.getDefaultInstance()

            return action()
        } catch (Exception e) {
            throw new GradleException("Docker command failed: $e.message", e)
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }

        return null
    }

    protected String toJson(Object input) {
        new JsonBuilder(input).toPrettyString()
    }

    protected File dockerOutput(String name, Closure value) {
        def outputFile = new File(temporaryDir, "output-${name}.json")

        if (!initializedOutputs.contains(name)) {
            initializedOutputs << name

            runInDockerClasspath {
                outputFile.text = toJson(value())
                logger.debug("Initialzed Docker output file $outputFile for coming up-to-date checks")
            }
            doLast {
                runInDockerClasspath {
                    outputFile.text = toJson(value())
                    logger.debug("Updated Docker output file $outputFile for persisting output state")
                }
            }
        }

        outputFile
    }

    protected Set<Container> getOtherContainers() {
        Set<DefaultContainer> result = new HashSet<>()

        project.rootProject.allprojects.each { prj ->
            ((ProjectInternal) prj).evaluate()

            if(prj.plugins.hasPlugin(DcomposePlugin)) {
                result.addAll prj.extensions.getByType(DcomposeExtension).containers
            }
        }

        new HashSet<>(result.findAll { it != container })
    }

}
