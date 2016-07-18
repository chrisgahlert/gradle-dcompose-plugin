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

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec
import spock.lang.Ignore
import spock.lang.Unroll

class DcomposeContainerStartTaskSpec extends AbstractDcomposeSpec {


    def 'should start new container'() {
        given:
        buildFile << """
            dcompose {
                test {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo started > /test']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('test', '/test')}
        """

        when:
        def result = runTasksSuccessfully 'startTestContainer', 'copy'

        then:
        result.wasExecuted(':startTestContainer')
        file('build/copy/test').text.trim() == 'started'
    }

    def 'start should be up-to-date'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        result.wasUpToDate(':startMainContainer')
    }

    def 'start should not be up-to-date when create was not up-to-date'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'
        buildFile << 'createMainContainer.outputs.upToDateWhen { false }'


        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        !result.wasUpToDate(':startMainContainer')
        result.wasExecuted(':startMainContainer')
    }

    def 'start should not be up-to-date when container stopped as expected'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '1']
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'
        Thread.sleep(5000)

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        !result.wasUpToDate(':startMainContainer')
        result.wasExecuted(':startMainContainer')
    }

    def 'should wait for command'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['/bin/sleep', '5']
                    waitForCommand = true
                    waitTimeout = 20
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        result.wasExecuted(':startMainContainer')
    }

    def 'should timeout waiting for command'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['/bin/sleep', '10']
                    waitForCommand = true
                    waitTimeout = 5
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startMainContainer'

        then:
        result.wasExecuted(':createMainContainer')
        result.wasExecuted(':startMainContainer')
    }

    def 'start should work for linked containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [server.link()]
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':startClientContainer')
        file('build/copy/transfer').text.trim() == 'linkcool'
    }

    def 'start should work for linked containers with alias'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc alias 8000 > /transfer']
                    links = [server.link('alias')]
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':startClientContainer')
        file('build/copy/transfer').text.trim() == 'linkcool'
    }

    def 'start should work for containers with volumes from'() {
        given:
        buildFile << """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumes = ['/data']
                }
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumesFrom = [data]
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startUserContainer'

        then:
        result.wasExecuted(':createDataContainer')
        !result.wasExecuted(':startDataContainer')
        result.wasExecuted(':startUserContainer')
    }

    def 'start should work for linked containers on update'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [server.link()]
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """

        runTasksSuccessfully 'startClientContainer'
        buildFile.text = buildFile.text.replace('linkcool', 'linkverycool')

        when:
        def result = runTasks 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':createServerContainer')
        !result.wasUpToDate(':createServerContainer')
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':createClientContainer')
        !result.wasUpToDate(':createClientContainer')
        result.wasExecuted(':startClientContainer')
        file('build/copy/transfer').text.trim() == 'linkverycool'
    }

    def 'start should work for containers with volumes from on update'() {
        given:
        buildFile << """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                    volumes = ['/data']
                }
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumesFrom = [data]
                }

            }
        """

        runTasksSuccessfully 'createUserContainer'
        buildFile << "dcompose.data.command = ['echo', 'def']"

        when:
        def result = runTasksSuccessfully 'startUserContainer'

        then:
        result.wasExecuted(':createDataContainer')
        !result.wasUpToDate(':createDataContainer')
        result.wasExecuted(':createUserContainer')
        !result.wasUpToDate(':createUserContainer')
        result.wasExecuted(':startUserContainer')
    }

    def 'start should work for linked cross project containers'() {
        given:
        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [container(':subServer:server').link()]
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':subServer:startServerContainer')
        result.wasExecuted(':subClient:startClientContainer')
        file('subClient/build/copy/transfer').text.trim() == 'linkcool'
    }

    def 'start should work for linked cross project containers with alias'() {
        given:
        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc alias 8000 > /transfer']
                    links = [container(':subServer:server').link('alias')]
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':subServer:startServerContainer')
        result.wasExecuted(':subClient:startClientContainer')
        file('subClient/build/copy/transfer').text.trim() == 'linkcool'
    }

    def 'start should work for cross project containers with volumes from'() {
        given:
        buildFile.text = ''

        addSubproject 'subData', """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumes = ['/data']
                }
            }
        """
        addSubproject 'subUser', """
            dcompose {
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumesFrom = [container(':subData:data')]
                }
            }
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'startUserContainer'

        then:
        result.wasExecuted(':subData:createDataContainer')
        !result.wasExecuted(':subData:startDataContainer')
        result.wasExecuted(':subUser:startUserContainer')
    }

    def 'start should work for linked cross project containers on update'() {
        given:
        buildFile.text = ''

        def serverDir = addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
            }
        """
        def serverBuildFile = new File(serverDir, 'build.gradle')

        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [container(':subServer:server').link()]
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('client', '/transfer')}
        """
        addSubproject 'other'

        runTasksSuccessfully 'startClientContainer'
        serverBuildFile.text = serverBuildFile.text.replace('linkcool', 'linkverycool')

        when:
        def result = runTasks 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':subServer:createServerContainer')
        !result.wasUpToDate(':subServer:createServerContainer')
        result.wasExecuted(':subServer:startServerContainer')
        result.wasExecuted(':subClient:createClientContainer')
        !result.wasUpToDate(':subClient:createClientContainer')
        result.wasExecuted(':subClient:startClientContainer')
        file('subClient/build/copy/transfer').text.trim() == 'linkverycool'
    }

    def 'start should work for cross project containers with volumes from on update'() {
        given:
        buildFile.text = ''

        def dataDir = addSubproject 'subData', """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumes = ['/data']
                }
            }
        """
        def dataBuildFile = new File(dataDir, 'build.gradle')

        addSubproject 'subUser', """
            dcompose {
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumesFrom = [container(':subData:data')]
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'createUserContainer'
        dataBuildFile << "dcompose.data.command = ['echo', 'def']"

        when:
        def result = runTasksSuccessfully 'startUserContainer'

        then:
        result.wasExecuted(':subData:createDataContainer')
        !result.wasUpToDate(':subData:createDataContainer')
        result.wasExecuted(':subUser:createUserContainer')
        !result.wasUpToDate(':subUser:createUserContainer')
        result.wasExecuted(':subUser:startUserContainer')
    }

    @Unroll
    def 'should #outText attach to stdout and should #errText attach to stderr'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo rid && (echo kicksass 1>&2) && sleep 1 && echo dick']
                    waitForCommand = true
                    attachStdout = $stdout
                    attachStderr = $stderr
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        stdout == result.standardOutput.contains('rid\ndick')
        stderr == result.standardError.contains('kicksass')

        where:
        stderr || stdout
        true   || false
        true   || true
        false  || true
        false  || false

        outText = stdout ? '' : ' NOT'
        errText = stderr ? '' : ' NOT'
    }

    def 'should support reading large file from stdout'() {
        given:
        def size = 2 * 1024 * 1024
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'cat /dev/urandom | tr -dc A-Za-z0-9 | head -c $size']
                    waitForCommand = true
                    attachStdout = true
                }
            }

            startAppContainer {
                doFirst { stdOut = new FileOutputStream(file('out.txt')) }
                doLast { stdOut.close() }
                doLast {
                    println "#received: \$startAppContainer.exitCode#"
                }
            }
        """

        when:
        def outFile = file('out.txt')
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        outFile.size() == size + 1 // size + LF
        result.standardOutput.contains("#received: 0#")
    }

    @Ignore("Not yet supported by docker library")
    def 'should attach to stdin'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'read \$input && echo \$input > /test.txt']
                    waitForCommand = true
                    attachStdin = true
                }
            }

            startAppContainer.stdIn = new ByteArrayInputStream("walter white\\n".bytes)

            ${copyTaskConfig('app', '/text.txt')}
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        file('build/copy/test.txt').text == 'walter white'
    }

    def 'should react to return code when waiting for command'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'exit 1']
                    waitForCommand = true
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startAppContainer'

        then:
        result.standardError.contains("did non return with a '0' exit code")
    }

    def 'should not react to return code when ignored'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'exit 7']
                    waitForCommand = true
                    ignoreExitCode = true
                }
            }

            task validateExitCode {
                dependsOn startAppContainer
                doFirst {
                    println "#received: \$startAppContainer.exitCode#"
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'validateExitCode'

        then:
        result.standardOutput.contains("#received: 7#")
    }

}
