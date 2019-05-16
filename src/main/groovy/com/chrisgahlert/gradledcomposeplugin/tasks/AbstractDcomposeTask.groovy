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
import com.chrisgahlert.gradledcomposeplugin.utils.DockerExecutor
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

    private Set<String> initializedOutputs = []

    DockerExecutor dockerExecutor

    @Input
    def getDockerClientConfig() {
        dockerExecutor.runInDockerClasspath {
            dockerExecutor.buildClientConfig().toString()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void addAuthConfig(String image, cmd) {
        def imageRef = ImageRef.parse(image)
        def registryAddress = imageRef.registry ?: defaultRegistryAddress
        def extension = project.extensions.getByType(DcomposeExtension)

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

        if (!authConfig && extension.dockerConfigPath?.isDirectory()) {
            logger.info("Cannot find auth config for registry '$registryAddress' - trying to use auth config from $extension.dockerConfigPath.canonicalPath")

            def dockerConfigFileClass = dockerExecutor.loadClass('com.github.dockerjava.core.DockerConfigFile')
            def loadConfigMethod = dockerConfigFileClass.getMethod('loadConfig', String)
            def dockerConfigFile = loadConfigMethod.invoke(null, extension.dockerConfigPath.absolutePath)
            authConfig = dockerConfigFile.resolveAuthConfig(registryAddress)
        }

        if (!authConfig) {
            throw new GradleException("Cannot find auth config for registry '$registryAddress'")
        }

        cmd.withAuthConfig(authConfig)
    }

    protected String getDefaultRegistryAddress() {
        def authConfigClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.AuthConfig')
        authConfigClass.getDeclaredField('DEFAULT_SERVER_ADDRESS').get(null)
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected def getAuthConfigs() {
        def result = dockerExecutor.loadClass('com.github.dockerjava.api.model.AuthConfigurations')
                .newInstance()
        def extension = project.extensions.getByType(DcomposeExtension)
        def authConfigClass = dockerExecutor.loadClass('com.github.dockerjava.api.model.AuthConfig')

        def clientConfig = dockerExecutor.buildClientConfig()
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

    protected def ignoreDockerException(String exceptionClassName, Closure action) {
        ignoreDockerExceptions([exceptionClassName], action)
    }

    protected def ignoreDockerExceptions(List<String> exceptionClassNames, Closure action) {
        try {
            return action()
        } catch (Throwable t) {
            def exceptionMatched = exceptionClassNames.find { exceptionClassName ->
                t.getClass().name == "com.github.dockerjava.api.exception.$exceptionClassName"
            }

            if (exceptionMatched) {
                logger.debug("Caught expected Docker exception:", t)
                return null
            }

            throw t
        }
    }

    protected String toJson(Object input) {
        new JsonBuilder(input).toPrettyString()
    }

    protected File dockerOutput(String name, Closure value) {
        def outputFile = new File(temporaryDir, "output-${name}.json")

        if (!initializedOutputs.contains(name)) {
            initializedOutputs << name

            outputFile.text = toJson(dockerExecutor.runInDockerClasspath(value))
            saveDebugOutput(outputFile, "before")
            logger.debug("Initialzed Docker output file $outputFile for coming up-to-date checks")

            doLast {
                outputFile.text = toJson(dockerExecutor.runInDockerClasspath(value))
                saveDebugOutput(outputFile, "after")
                logger.debug("Updated Docker output file $outputFile for persisting output state")
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
            def cmd = dockerExecutor.client.stopContainerCmd(containerName)

            def service = allServices.find { it.containerName == containerName }
            if (service && service.stopTimeout != null) {
                cmd.withTimeout(service.stopTimeout)
            }

            try {
                cmd.exec()
            } catch (Exception e) {
                if (e.getClass() != dockerExecutor.loadClass('com.github.dockerjava.api.exception.InternalServerErrorException')
                        || !e.message?.contains('Container does not exist: container destroyed')) {
                    throw e
                }
            }

            logger.info("Stopped Docker container named $containerName")
        }
    }

}
