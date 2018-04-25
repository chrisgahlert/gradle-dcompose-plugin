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
import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import com.chrisgahlert.gradledcomposeplugin.extension.DefaultService
import com.chrisgahlert.gradledcomposeplugin.extension.Service
import com.chrisgahlert.gradledcomposeplugin.utils.DockerClassLoaderFactory
import com.chrisgahlert.gradledcomposeplugin.utils.ImageRef
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
    public static final String DOCKER_API_VERSION = '1.20'

    private Set<String> initializedOutputs = []

    DockerClassLoaderFactory dockerClassLoaderFactory

    @Input
    def getDockerClientConfig() {
        runInDockerClasspath {
            buildClientConfig().toString()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def getClient(def properties = [:]) {
        def clientConfig = buildClientConfig()
        def clientConfigClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
        def clientBuilderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
        def getInstanceMethod = clientBuilderClass.getMethod('getInstance', clientConfigClass)
        def clientBuilder = getInstanceMethod.invoke(null, clientConfig)

        def execFactory
        if (properties.useNetty) {
            execFactory = loadClass('com.github.dockerjava.netty.NettyDockerCmdExecFactory').newInstance()
        } else {
            execFactory = clientBuilder.getDefaultDockerCmdExecFactory()
        }
        clientBuilder.withDockerCmdExecFactory(execFactory)

        clientBuilder.build()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def buildClientConfig() {
        def configBuilderClass = loadClass('com.github.dockerjava.core.DefaultDockerClientConfig')
        def configBuilder = configBuilderClass.getMethod('createDefaultConfigBuilder').invoke(null)
        configBuilder.withApiVersion(DOCKER_API_VERSION)

        def extension = project.extensions.getByType(DcomposeExtension)
        if (extension.dockerClientConfig != null) {
            ConfigureUtil.configure(extension.dockerClientConfig, configBuilder)
        }

        configBuilder.build()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void addAuthConfig(String image, cmd) {
        def imageRef = ImageRef.parse(image)
        def registryAddress = imageRef.registry ?: defaultRegistryAddress

        def authConfig = getAuthConfigs().configs.find { authRegistryAddress, authConfig ->
            def registryUri
            try {
                registryUri = URI.create(authRegistryAddress)
            } catch (Throwable t) {
                logger.debug("Could not parse registry address '$authRegistryAddress' to URI", t)
            }

            return authRegistryAddress == registryAddress ||
                    (registryUri?.host == registryAddress && registryUri?.port == 80) ||
                    registryUri?.host + ':' + registryUri?.port == registryAddress
        }?.value

        if (authConfig) {
            cmd.withAuthConfig(authConfig)
        } else {
            logger.info("Cannot find auth config for registry '$registryAddress' - trying to use config.json")
        }
    }

    protected String getDefaultRegistryAddress() {
        def authConfigClass = loadClass('com.github.dockerjava.api.model.AuthConfig')
        authConfigClass.getDeclaredField('DEFAULT_SERVER_ADDRESS').get(null)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def getAuthConfigs() {
        def result = loadClass('com.github.dockerjava.api.model.AuthConfigurations').newInstance()
        def extension = project.extensions.getByType(DcomposeExtension)
        def authConfigClass = loadClass('com.github.dockerjava.api.model.AuthConfig')

        def clientConfig = buildClientConfig()
        def clientConfigAuth = authConfigClass.newInstance().withRegistryAddress(defaultRegistryAddress)
        if (clientConfig.registryUsername) clientConfigAuth.withUsername(clientConfig.registryUsername)
        if (clientConfig.registryPassword) clientConfigAuth.withPassword(clientConfig.registryPassword)
        if (clientConfig.registryEmail) clientConfigAuth.withEmail(clientConfig.registryEmail)
        if (clientConfig.registryUrl) clientConfigAuth.withRegistryAddress(clientConfig.registryUrl)
        result.addConfig(clientConfigAuth)

        extension.registries.each { String name, Closure config ->
            def authConfig = authConfigClass.newInstance()
            authConfig.withRegistryAddress(name)
            ConfigureUtil.configure(config, authConfig)

            result.addConfig(authConfig)
        }

        result
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
                saveDebugOutput(outputFile, "before")
                logger.debug("Initialzed Docker output file $outputFile for coming up-to-date checks")
            }
            doLast {
                runInDockerClasspath {
                    outputFile.text = toJson(value())
                    saveDebugOutput(outputFile, "after")
                    logger.debug("Updated Docker output file $outputFile for persisting output state")
                }
            }
        }

        outputFile
    }

    /**
     * For debugging purposes
     */
    protected void saveDebugOutput(File outputFile, String suffix) {
        if (!System.getProperties().containsKey('com.chrisgahlert.gradledcomposeplugin.debugUpToDate')) {
            return
        }

        File debugOutputFile
        def i = 1
        while ((debugOutputFile = new File(outputFile.parentFile, "${outputFile.name}.${i}.${suffix}")).exists()) {
            i++
        }

        debugOutputFile.bytes = outputFile.bytes
    }

    protected Set<Service> getAllServices() {
        Set<DefaultService> result = new HashSet<>()

        project.rootProject.allprojects.each { prj ->
            ((ProjectInternal) prj).evaluate()

            if (prj.plugins.hasPlugin(DcomposePlugin)) {
                result.addAll prj.extensions.getByType(DcomposeExtension).services
            }
        }

        new HashSet<>(result)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void stopContainer(String containerName) {
        ignoreDockerExceptions(['NotFoundException', 'NotModifiedException']) {
            def cmd = client.stopContainerCmd(containerName)

            def service = allServices.find { it.containerName == containerName }
            if (service && service.stopTimeout != null) {
                cmd.withTimeout(service.stopTimeout)
            }

            try {
                cmd.exec()
            } catch (Exception e) {
                if (e.getClass() != loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                        || !e.message?.contains('Container does not exist: container destroyed')) {
                    throw e
                }
            }

            logger.quiet("Stopped Docker container named $containerName")
        }
    }

}
