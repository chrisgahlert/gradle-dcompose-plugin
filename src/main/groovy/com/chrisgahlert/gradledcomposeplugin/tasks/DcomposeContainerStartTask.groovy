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

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch

@TypeChecked
class DcomposeContainerStartTask extends AbstractDcomposeTask {

    int waitInterval = 1000
    InputStream stdIn = System.in
    OutputStream stdOut = System.out
    OutputStream stdErr = System.err

    DcomposeContainerStartTask() {
        outputs.upToDateWhen {
            !container.waitForCommand
        }

        dependsOn {
            container.linkDependencies.collect { "$it.projectPath:$it.startTaskName" }
        }
    }

    @Input
    String getContainerName() {
        container.containerName
    }

    @Input
    @Optional
    Boolean getAttachStdout() {
        container.attachStdout
    }

    @Input
    @Optional
    Boolean getAttachStdin() {
        container.attachStdin
    }

    @Input
    @Optional
    Boolean getAttachStderr() {
        container.attachStderr
    }

    @Input
    boolean isIgnoreExitCode() {
        container.ignoreExitCode
    }

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void startContainer() {
        runInDockerClasspath {
            def start = System.currentTimeMillis()

            ignoreDockerException('NotModifiedException') {
                client.startContainerCmd(containerName).exec()
                logger.quiet("Started Docker container with name $containerName")

                if (attachStdin || attachStdout || attachStderr) {
                    attachStreams()
                }
            }

            if (container.waitForCommand) {
                while (container.waitTimeout <= 0 || start + 1000L * container.waitTimeout > System.currentTimeMillis()) {
                    def inspectResult = client.inspectContainerCmd(containerName).exec()
                    if (!inspectResult.state.running) {
                        logger.info("Docker container with name $containerName is not running anymore")

                        extensions.exitCode = inspectResult.state.exitCode
                        logger.info("Docker container with name $containerName returned with a " +
                                "'$extensions.exitCode' exit code")

                        if (extensions.exitCode != 0 && !ignoreExitCode) {
                            throw new GradleException("Container $containerName did not return with a '0' exit code. " +
                                    "(Use dcompose.${containerName}.ignoreExitCode = true to disable this check!)")
                        }
                        return
                    }

                    logger.debug("Waiting for Docker container with name $containerName to stop running")
                    Thread.sleep(waitInterval)
                }

                throw new GradleException("Timed out waiting for command to finish after ${System.currentTimeMillis() - start} ms")
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected void attachStreams() {
        def logCmd = client.logContainerCmd(containerName)
                .withFollowStream(container.waitForCommand)

        if (attachStdout) {
            logCmd.withStdOut(attachStdout)
        }
        if (attachStdin) {
            logger.warn("Attaching stdIn to a running container is currently not supported by the docker library")
        }
        if (attachStderr) {
            logCmd.withStdErr(attachStderr)
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

        logCmd.exec(proxy)
        outHandler.awaitCompletion()
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            def result = ignoreDockerException('NotFoundException') {
                client.inspectContainerCmd(containerName).exec()
            }

            if (result.state.running) {
                container.hostPortBindings = result?.networkSettings?.ports?.bindings
                container.dockerHost = buildClientConfig().dockerHost
            } else {
                container.hostPortBindings = null
                container.dockerHost = null
            }


            result
        }
    }


    @TypeChecked(TypeCheckingMode.SKIP)
    private class StreamOutputHandler {
        def stream
        Throwable firstError
        CountDownLatch completed = new CountDownLatch(1)
        boolean closed

        def onStart(stream) {
            this.stream = stream
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

        void awaitCompletion() {
            completed.await()

            if (firstError != null) {
                loadClass('com.google.common.base.Throwables').invokeMethod('propagate', [firstError] as Object[])
            }
        }
    }

}
