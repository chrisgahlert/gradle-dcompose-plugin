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
package com.chrisgahlert.gradledcomposeplugin.tasks.container

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeServiceTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TypeChecked
class DcomposeContainerStartTask extends AbstractDcomposeServiceTask {

    InputStream stdIn = System.in
    OutputStream stdOut = System.out
    OutputStream stdErr = System.err

    DcomposeContainerStartTask() {
        dependsOn {
            service.linkDependencies.collect { "$it.projectPath:$it.startContainerTaskName" }
        }

        dependsOn {
            service.dependsOn.collect { "$it.projectPath:$it.startContainerTaskName" }
        }

        dependsOn {
            service.createContainerTaskName
        }
    }

    @Input
    String getContainerName() {
        service.containerName
    }

    @Input
    String getContainerId() {
        service.containerId
    }

    @Input
    @Optional
    Boolean getAttachStdout() {
        service.attachStdout
    }

    @Input
    @Optional
    Boolean getAttachStdin() {
        service.attachStdin
    }

    @Input
    @Optional
    Boolean getAttachStderr() {
        service.attachStderr
    }

    @Input
    boolean isIgnoreExitCode() {
        service.ignoreExitCode
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void startContainer() {
        runInDockerClasspath {
            def start = System.currentTimeMillis()

            ignoreDockerException('NotModifiedException') {
                def outHandler
                if (service.waitForCommand && (attachStdin || attachStdout || attachStderr)) {
                    outHandler = attachStreams()
                    if (!outHandler.awaitStart(service.waitTimeout)) {
                        throw new GradleException("Could not attach streams to container $containerName")
                    }
                }

                logger.info "Starting Docker container with name $containerName"
                client.startContainerCmd(containerId).exec()

                outHandler?.awaitCompletion(service.waitTimeout)
            }

            if (service.waitForCommand) {
                service.exitCode = waitForExitCode(start)
                extensions.exitCode = service.exitCode
                logger.info "Docker container with name $containerName returned with a '$service.exitCode' exit code"

                if (service.exitCode != 0 && !ignoreExitCode) {
                    throw new GradleException("Container $containerName did not return with a '0' exit code. " +
                            "(Use dcompose.${service.name}.ignoreExitCode = true to disable this check!)")
                }
            }

            if (service.waitForHealthcheck) {
                def healthStatus = waitForHealthcheck()
                if (!healthStatus) {
                    logger.warn("Docker container with name $containerName doesn't provide a health state - ignoring")
                } else if (healthStatus == 'unhealthy') {
                    throw new GradleException("Container $containerName failed it's healthcheck")
                } else if (healthStatus == 'healthy') {
                    logger.info("Docker container with name $containerName passed it's healthcheck")
                } else {
                    logger.warn("Unknown health status '$healthStatus' for container with name $containerName")
                }
            }

            updateServiceState()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected Integer waitForExitCode(long startTime) {
        def callbackClass = loadClass('com.github.dockerjava.core.command.WaitContainerResultCallback')
        def callback = client.waitContainerCmd(containerId).exec(callbackClass.newInstance())

        if (service.waitTimeout > 0) {
            def timeout = service.waitTimeout * 1000L - System.currentTimeMillis() + startTime
            callback.awaitStatusCode(timeout, TimeUnit.MILLISECONDS)
        } else {
            callback.awaitStatusCode()
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected String waitForHealthcheck() {
        while (true) {
            logger.debug("Checking health state for container $containerName")
            def result = client.inspectContainerCmd(containerName).exec()
            def status = result.state.health?.status

            if (!status || status != 'starting') {
                return status
            }

            logger.debug("Health state for $containerName is '$status' - continue waiting...")
            sleep(1000)
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected StreamOutputHandler attachStreams() {
        def attachCmd = getClient(useNetty: true).attachContainerCmd(containerId)
                .withFollowStream(true)

        if (attachStdout) {
            attachCmd.withStdOut(attachStdout)
        }
        if (attachStdin) {
            attachCmd.withStdIn(stdIn)
        }
        if (attachStderr) {
            attachCmd.withStdErr(attachStderr)
        }

        def outHandler = new StreamOutputHandler()

        def proxy = Proxy.newProxyInstance(
                dockerClassLoaderFactory.getDefaultInstance(),
                [loadClass('com.github.dockerjava.api.async.ResultCallback')] as Class[],
                new InvocationHandler() {
                    @Override
                    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        outHandler.invokeMethod(method.name, args)
                    }
                }
        )

        attachCmd.exec(proxy)
        outHandler
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    @Input
    boolean updateServiceState() {
        def result = ignoreDockerException('NotFoundException') {
            client.inspectContainerCmd(containerName).exec()
        }

        if (result?.state?.running) {
            service.hostPortBindings = result?.networkSettings?.ports?.bindings
            service.dockerHost = buildClientConfig().dockerHost
        } else {
            service.hostPortBindings = null
            service.dockerHost = null
        }

        // Always return true - just hijacking this method to update container state
        true
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private class StreamOutputHandler {
        def stream
        Throwable firstError
        CountDownLatch started = new CountDownLatch(1)
        CountDownLatch completed = new CountDownLatch(1)
        boolean closed

        def onStart(stream) {
            this.stream = stream
            started.countDown()
        }

        def onNext(item) {
            switch (item.streamType.name()) {
                case 'STDOUT':
                    stdOut.write(item.payload as byte[])
                    break;

                case 'STDERR':
                    stdErr.write(item.payload as byte[])
                    break;

                default:
                    throw new UnsupportedOperationException("Stream type $item.streamType is not supported")
            }
        }

        def onError(e) {
            if (firstError == null) {
                firstError = e
            }

            logger.error("Error receiving stream data from container $containerName", e)
            close()
        }

        def onComplete() {
            close()
        }

        def close() {
            if (!closed) {
                closed = true
                completed.countDown()
                stream.close()
            }

            stdOut?.flush()
            stdErr?.flush()
        }

        void awaitCompletion(long timeout) {
            if (timeout > 0) {
                completed.await(timeout, TimeUnit.SECONDS)
            } else {
                completed.await()
            }

            if (firstError != null) {
                loadClass('com.google.common.base.Throwables').invokeMethod('propagate', [firstError] as Object[])
            }
        }

        boolean awaitStart(long timeout) {
            if (timeout > 0) {
                started.await(timeout, TimeUnit.SECONDS)
            } else {
                started.await()
                true
            }
        }
    }

}
