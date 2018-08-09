package com.chrisgahlert.gradledcomposeplugin.utils

import com.chrisgahlert.gradledcomposeplugin.extension.DcomposeExtension
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.util.ConfigureUtil

@TypeChecked
class DockerExecutor {
    public static final String DOCKER_API_VERSION = '1.20'

    private DockerClassLoaderFactory dockerClassLoaderFactory

    private DcomposeExtension extension

    DockerExecutor(DockerClassLoaderFactory dockerClassLoaderFactory, DcomposeExtension extension) {
        this.dockerClassLoaderFactory = dockerClassLoaderFactory
        this.extension = extension
    }

    ClassLoader getDockerClassLoader() {
        return dockerClassLoaderFactory.defaultInstance
    }

    def runInDockerClasspath(Closure action) {
        def originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = dockerClassLoader

            return action()
        } catch (Exception e) {
            throw new GradleException("Docker command failed: $e.message", e)
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    def buildClientConfig() {
        def configBuilderClass = loadClass('com.github.dockerjava.core.DefaultDockerClientConfig')
        def configBuilder = configBuilderClass.getMethod('createDefaultConfigBuilder').invoke(null)
        configBuilder.withApiVersion(DOCKER_API_VERSION)

        if (extension.dockerClientConfig != null) {
            ConfigureUtil.configure(extension.dockerClientConfig, configBuilder)
        }

        configBuilder.build()
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    def getClient(def properties = [:]) {
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

    Class loadClass(String name) {
        dockerClassLoader.loadClass(name)
    }
}
