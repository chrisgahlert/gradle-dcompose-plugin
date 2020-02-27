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

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec
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
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'hello world']
                    waitForCommand = true
                    attachStdout = true
                }
            }

            startMainContainer{
                def out = file('out.txt')
                outputs.file out
                doFirst {
                    stdOut = out.newOutputStream()
                }
                doLast {
                    stdOut.close()
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'
        def outFile = file('out.txt')
        assert outFile.text.trim() == 'hello world'

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        result.wasUpToDate(':startMainContainer')
        outFile.text.trim() == 'hello world'
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
        Thread.sleep(10000)

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

    def 'start should work for containers connected via external network'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    custom 
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    networks = []
                    externalNetworks = [project.getProperty('myExternalNetworkName')]
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [server.link()]
                    waitForCommand = true
                    networks = []
                    externalNetworks = [project.getProperty('myExternalNetworkName')]
                }
            }

            ${copyTaskConfig('client', '/transfer')}
            
            createCustomNetwork.doLast {
                println 'customNetworkName: ' + networkName 
            }
            removeServerContainer.dependsOn removeCustomNetwork
            removeClientContainer.dependsOn removeCustomNetwork
        """

        def createNetworkResult = runTasksSuccessfully 'createCustomNetwork', '-PmyExternalNetworkName=foobar'
        def networkName = (createNetworkResult.standardOutput =~ /customNetworkName: (.+)\r?\n/)[0][1]

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy', "-PmyExternalNetworkName=$networkName"

        then:
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':startClientContainer')
        file('build/copy/transfer').text.trim() == 'linkcool'
    }

    def 'start should work for dependant containers'() {
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
                    dependsOn = [server]
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

    def 'start should work for runtime dependant containers'() {
        given:
        buildFile << """
            dcompose {
                firstServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo -n yee | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                secondServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 5 && (nc first-server 8000 && echo haw) | nc -l -p 8000']
                    exposedPorts = ['8000']
                    dependsOnRuntime = [firstServer]
                }
                xClient {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 10 && nc second-server 8000 > /transfer']
                    dependsOnRuntime = [secondServer]
                    waitForCommand = true
                }
            }
            
            task test(dependsOn: dcompose.xClient) {}

            ${copyTaskConfig('xClient', '/transfer')}
        """

        when:
        def result = runTasksSuccessfully 'test', 'copy'

        then:
        result.wasExecuted(':startFirstServerContainer')
        result.wasExecuted(':startSecondServerContainer')
        result.wasExecuted(':startXClientContainer')
        file('build/copy/transfer').text.trim() == 'yeehaw'
    }

    def 'start should work for runtime also with normal dependencies and runtime dependant containers'() {
        given:
        buildFile << """
            dcompose {
                firstServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo -n yeehaw | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                secondServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 5 && (nc first-server 8000 && echo haw) | nc -l -p 8000']
                    exposedPorts = ['8000']
                    dependsOnRuntime = [firstServer]
                }
                xClient {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 10 && nc second-server 8000 > /transfer']
                    dependsOn = [secondServer]
                    waitForCommand = true
                }
            }
            
            task test(dependsOn: dcompose.xClient) {}

            ${copyTaskConfig('xClient', '/transfer')}
        """

        when:
        def result = runTasksSuccessfully 'test', 'copy'

        then:
        result.wasExecuted(':startFirstServerContainer')
        result.wasExecuted(':startSecondServerContainer')
        result.wasExecuted(':startXClientContainer')
        file('build/copy/transfer').text.trim() == 'yeehawhaw'
    }

    def 'start should work for runtime also with circular runtime dependant containers'() {
        given:
        buildFile << """
            dcompose {
                firstServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo -n walter | nc -l -p 8000']
                    exposedPorts = ['8000']
                    dependsOnRuntime = [service('xClient')]
                }
                secondServer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 5 && (nc first-server 8000 && echo white) | nc -l -p 8000']
                    exposedPorts = ['8000']
                    dependsOnRuntime = [service('firstServer')]
                }
                xClient {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 10 && nc second-server 8000 > /transfer']
                    dependsOnRuntime = [service('secondServer')]
                    waitForCommand = true
                }
            }
            
            task test(dependsOn: dcompose.xClient) {}

            ${copyTaskConfig('xClient', '/transfer')}
        """

        when:
        def result = runTasksSuccessfully 'test', 'copy'

        then:
        result.wasExecuted(':startFirstServerContainer')
        result.wasExecuted(':startSecondServerContainer')
        result.wasExecuted(':startXClientContainer')
        file('build/copy/transfer').text.trim() == 'walterwhite'
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
                    waitForCommand = true
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
        resetBuildFile()

        addSubproject 'subServer', """
            dcompose {
                networks {
                    backend
                }

                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [backend]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [service(':subServer:server').link()]
                    waitForCommand = true
                    networks = [ network(':subServer:backend') ]
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

    def 'start should work for dependant cross project containers'() {
        given:
        resetBuildFile()

        addSubproject 'subServer', """
            dcompose {
                networks {
                    backend
                }

                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo depcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [backend]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    dependsOn = [service(':subServer:server')]
                    waitForCommand = true
                    networks = [ network(':subServer:backend') ]
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
        file('subClient/build/copy/transfer').text.trim() == 'depcool'
    }

    def 'start should work for linked cross project containers with alias'() {
        given:
        resetBuildFile()

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
                    links = [service(':subServer:server').link('alias')]
                    waitForCommand = true
                    networks = [ network(':subServer:default') ]
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
        resetBuildFile()

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
                    volumesFrom = [service(':subData:data')]
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
        resetBuildFile()

        def serverDir = addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo linkcool | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [ network(':subClient:frontend'), network('default') ]
                }
            }
        """
        def serverBuildFile = new File(serverDir, 'build.gradle')

        addSubproject 'subClient', """
            dcompose {
                networks {
                    frontend
                }

                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /transfer']
                    links = [service(':subServer:server').link()]
                    waitForCommand = true
                    networks << frontend
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
        resetBuildFile()

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
                    volumesFrom = [service(':subData:data')]
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
        fork = true

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

            startAppContainer {
                stdErr = new PipedOutputStream()

                doFirst {
                    // redirect stream to see, if it was actually attached to stdErr
                    new Thread({
                        def bfReader = new BufferedReader(new InputStreamReader(new PipedInputStream(stdErr)))
                        def line
                        while((line = bfReader.readLine()) != null) {
                            println "err: \$line"
                        }
                        println 'err: done forwarding'
                    }).start()
                }

                doLast {
                    stdErr.close()
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        stdout == (result.standardOutput ==~ /(?ms).*^rid.+^dick.*/)
        stderr == result.standardOutput.contains('err: kicksass')

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
                    logger.warn "#received: \$startAppContainer.exitCode#"
                }
            }
        """

        when:
        def outFile = file('out.txt')
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        outFile.size() == size
        result.standardOutput.contains("#received: 0#")
    }

    def 'should attach to stdin'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo `wc -w` words > /test.txt']
                    waitForCommand = true
                    attachStdin = true
                }
            }

            startAppContainer.stdIn = new ByteArrayInputStream("walter white".bytes)

            ${copyTaskConfig('app', '/test.txt')}
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer', 'copy'

        then:
        file('build/copy/test.txt').text.trim() == '2 words'
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
        (result.standardOutput + result.standardError).contains("did not return with a '0' exit code")
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
                    logger.warn "#received: \$startAppContainer.exitCode#"
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'validateExitCode'

        then:
        result.standardOutput.contains("#received: 7#")
    }

    def 'should pass waiting for healthcheck if none provided'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'sleep 10 && echo wadewilson > /test.txt']
                    waitForHealthcheck = true
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        result.standardOutput.contains('_app doesn\'t provide a health state - ignoring')
    }

    def 'should pass waiting for healthcheck if specifying NONE over dockerfile'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            HEALTHCHECK --interval=1s --timeout=1s --retries=3 CMD grep wadewilson /test.txt
            CMD ["sh", "-c", "sleep 10 && echo wadewilson > /test.txt && sleep 300"]
        """.stripIndent()

        buildFile << """
            dcompose {
                app {
                    baseDir = file('docker/')
                    waitForHealthcheck = true
                    healthcheckTest = ['NONE']
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        result.standardOutput.contains('_app doesn\'t provide a health state - ignoring')
    }

    def 'should fail waiting for dockerfile healthcheck if timeout exceeded'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            HEALTHCHECK --interval=1s --timeout=1s --retries=3 CMD grep wadewilson /test.txt
            CMD ["sh", "-c", "sleep 10 && echo wadewilson > /test.txt && sleep 300"]
        """.stripIndent()

        buildFile << """
            dcompose {
                app {
                    baseDir = file('docker/')
                    waitForHealthcheck = true
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        (result.standardOutput + result.standardError).contains('_app failed it\'s healthcheck')
    }

    def 'should succeed waiting for dockerfile healthcheck'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            HEALTHCHECK --interval=1s --timeout=1s --retries=20 CMD grep wadewilson /test.txt
            CMD ["sh", "-c", "sleep 10 && echo wadewilson > /test.txt && sleep 300"]
        """.stripIndent()

        buildFile << """
            dcompose {
                app {
                    baseDir = file('docker/')
                    waitForHealthcheck = true
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        result.standardOutput.contains('_app passed it\'s healthcheck')
    }

    def 'should fail waiting for dcompose healthcheck if timeout exceeded'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            HEALTHCHECK --interval=1s --timeout=1s --retries=3 CMD exit 1
            CMD ["sh", "-c", "sleep 10 && echo wadewilson > /test.txt && sleep 300"]
        """.stripIndent()

        buildFile << """
            dcompose {
                app {
                    baseDir = file('docker/')
                    waitForHealthcheck = true
                    healthcheckStartPeriod = 1L
                    healthcheckInterval = 1000L
                    healthcheckTimeout = 1000L
                    healthcheckRetries = 3
                    healthcheckTest = ['CMD', 'grep', 'wadewilson', '/test.txt']
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        (result.standardOutput + result.standardError).contains('_app failed it\'s healthcheck')
    }

    def 'should succeed waiting for dcompose healthcheck'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            HEALTHCHECK --interval=1s --timeout=1s --retries=20 CMD exit 1
            CMD ["sh", "-c", "sleep 10 && echo wadewilson > /test.txt && sleep 300"]
        """.stripIndent()

        buildFile << """
            dcompose {
                app {
                    baseDir = file('docker/')
                    waitForHealthcheck = true
                    healthcheckInterval = 1000L
                    healthcheckTimeout = 1000L
                    healthcheckRetries = 20
                    healthcheckTest = ['CMD', 'grep', 'wadewilson', '/test.txt']
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startAppContainer'

        then:
        result.wasExecuted('startAppContainer')
        result.standardOutput.contains('_app passed it\'s healthcheck')
    }
}
