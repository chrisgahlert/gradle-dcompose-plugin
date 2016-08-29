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

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeTask
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GradleVersion

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TypeChecked
class DcomposeContainerStartTask extends AbstractDcomposeTask {

    int waitInterval = 1000
    InputStream stdIn = System.in
    OutputStream stdOut = System.out
    OutputStream stdErr = System.err

    DcomposeContainerStartTask() {
        outputs.upToDateWhen {
            !service.waitForCommand
        }

        dependsOn {
            service.linkDependencies.collect { "$it.projectPath:$it.startContainerTaskName" }
        }
    }

    @Input
    String getContainerName() {
        service.containerName
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
                    if(!outHandler.awaitStart(service.waitTimeout)) {
                        throw new GradleException("Could not attach streams to container $containerName")
                    }
                }

                logger.quiet("Starting Docker container with name $containerName")
                client.startContainerCmd(containerName).exec()

                outHandler?.awaitCompletion(service.waitTimeout)
            }

            if (service.waitForCommand) {
                while(true) {
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

                        // finished successfully
                        return
                    }

                    if(service.waitTimeout > 0 && start + 1000L * service.waitTimeout < System.currentTimeMillis()) {
                        // timeout exceeded -> fail
                        throw new GradleException("Timed out waiting for command to finish after ${System.currentTimeMillis() - start} ms")
                    }

                    // continue waiting
                    logger.debug("Waiting for Docker container with name $containerName to stop running")
                    Thread.sleep(waitInterval)
                }
            }
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    protected StreamOutputHandler attachStreams() {
        if(GradleVersion.current().compareTo(GradleVersion.version('2.5')) <= 0) {
            logger.warn('Output to StdOut/StdErr seems to be broken in Gradle Versions <= 2.5. If you need to use it ' +
                    'anyway, think about redirecting it through a ByteArrayOutputStream and using the Gradle logger ' +
                    'in an doLast action!')
        }

        def attachCmd = getClient(useNetty: true).attachContainerCmd(containerName)
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

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
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

            result?.mounts = result?.mounts?.sort { it.destination?.path }


            result
        }
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
            if(timeout > 0) {
                completed.await(timeout, TimeUnit.SECONDS)
            } else {
                completed.await()
            }

            if (firstError != null) {
                loadClass('com.google.common.base.Throwables').invokeMethod('propagate', [firstError] as Object[])
            }
        }

        boolean awaitStart(long timeout) {
            if(timeout > 0) {
                started.await(timeout, TimeUnit.SECONDS)
            } else {
                started.await()
                true
            }
        }
    }

}
