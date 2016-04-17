package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.extension.Container
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class AbstractDcomposeTask extends DefaultTask {

    @Input
    @Optional
    String host

    @InputDirectory
    @Optional
    File certPath

    @Input
    @Optional
    String apiVersion

    private Container container

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
        def ConfigClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
        def configBuilder = ConfigClass.getMethod('createDefaultConfigBuilder').invoke(null)

        if (host) {
            configBuilder.withDockerHost(host)
        }

        if (certPath) {
            configBuilder.withDockerCertPath(certPath.canonicalPath)
        }

        if (apiVersion) {
            configBuilder.withApiVersion(apiVersion)
        }

        def clientBuilderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
        clientBuilderClass.getMethod('getInstance', ConfigClass).invoke(null, configBuilder.build()).build()
    }

    protected Class loadClass(String name) {
        Thread.currentThread().contextClassLoader.loadClass(name)
    }

    protected def ignoreException(String exceptionClassName, Closure action) {
        ignoreExceptions([exceptionClassName], action)
    }

    protected def ignoreExceptions(List<String> exceptionClassNames, Closure action) {
        try {
            return action()
        } catch (Throwable t) {
            def exceptionMatched = exceptionClassNames.find { exceptionClassName ->
                t.getClass() == loadClass(exceptionClassName)
            }

            if(exceptionMatched) {
                logger.debug("Caught expected docker exception:", t)
                return null
            }

            throw t
        }
    }
}
