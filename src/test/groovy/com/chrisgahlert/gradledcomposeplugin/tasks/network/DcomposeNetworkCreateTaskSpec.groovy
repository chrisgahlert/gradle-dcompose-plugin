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
package com.chrisgahlert.gradledcomposeplugin.tasks.network

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeNetworkCreateTaskSpec extends AbstractDcomposeSpec {

    def 'should successfully create network'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    test
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'createTestNetwork'

        then:
        result.wasExecuted(':createTestNetwork')
    }

    def 'should create default network on container create'() {
        given:
        fork = true

        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo jessie pinkman | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000']
                    waitForCommand = true
                    attachStdout = true
                    attachStderr = true
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startServerContainer', 'startClientContainer'

        then:
        result.standardOutput.contains('jessie pinkman')
        result.wasExecuted(':createDefaultNetwork')
        result.wasExecuted(':createServerContainer')
        result.wasExecuted(':createClientContainer')
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':startClientContainer')
    }

    def 'create default network should be up-to-date'() {
        given:
        fork = true

        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo jessie pinkman | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000']
                    waitForCommand = true
                    attachStdout = true
                    attachStderr = true
                }
            }
        """

        runTasksSuccessfully 'startServerContainer', 'startClientContainer'

        when:
        def result = runTasksSuccessfully 'startServerContainer', 'startClientContainer'

        then:
        result.standardOutput.contains('jessie pinkman')
        result.wasUpToDate(':createDefaultNetwork')
        result.wasUpToDate(':createServerContainer')
        result.wasUpToDate(':createClientContainer')
        result.wasExecuted(':startServerContainer')
        result.wasExecuted(':startClientContainer')
    }

    def 'different networks should not be able to connect to each other'() {
        given:
        fork = true
        
        buildFile << """
            dcompose {
                networks {
                    other
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo jessie pinkman | nc -l -p 8000']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000']
                    waitForCommand = true
                    attachStderr = true
                    networks = [other]
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startServerContainer', 'startClientContainer'

        then:
        result.standardError.contains("nc: bad address 'server'")
    }

    def 'custom networks should be able to connect to each other'() {
        given:
        fork = true

        buildFile << """
            dcompose {
                networks {
                    frontend
                    backend
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo gus fring goodman | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [backend]
                    aliases = ['other']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000']
                    waitForCommand = true
                    attachStdout = true
                    networks = [frontend, backend]
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startServerContainer', 'startClientContainer'

        then:
        result.standardOutput.contains('gus fring')
    }

    def 'custom networks should be able to connect to each other with aliases'() {
        given:
        fork = true

        buildFile << """
            dcompose {
                networks {
                    frontend
                    backend
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo saul goodman | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [backend]
                    aliases = ['methlab', 'restaurant']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc methlab 8000']
                    waitForCommand = true
                    attachStdout = true
                    networks = [frontend, backend]
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startServerContainer', 'startClientContainer'

        then:
        result.standardOutput.contains('saul goodman')
    }

    def 'create should work for networked cross project containers'() {
        given:
        fork = true
        
        buildFile.text = ''

        addSubproject 'subDatabase', """
            dcompose {
                networks {
                    backend
                }
                database {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo los pollos | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [backend]
                }
            }
        """
        addSubproject 'subServer', """
            dcompose {
                appserver {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo `nc database 8000` hermanos | nc -l -p 8001']
                    exposedPorts = ['8001']
                    aliases = ['middletier']
                    networks = [ network(':subDatabase:backend'), network(':subClient:default') ]
                }
            }
            startAppserverContainer.dependsOn ':subDatabase:startDatabaseContainer'
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc middletier 8001']
                    waitForCommand = true
                    attachStdout = true
                }
            }
            startClientContainer.dependsOn ':subServer:startAppserverContainer'
        """

        when:
        def result = runTasksSuccessfully ':subClient:startClientContainer'

        then:
        result.standardOutput.contains('los pollos hermanos')

        result.wasExecuted(':subDatabase:createBackendNetwork')
        !result.wasExecuted(':subDatabase:createDefaultNetwork')
        !result.wasExecuted(':subServer:createDefaultNetwork')
        result.wasExecuted(':subClient:createDefaultNetwork')
        result.wasExecuted(':subDatabase:startDatabaseContainer')
        result.wasExecuted(':subServer:startAppserverContainer')
        result.wasExecuted(':subClient:startClientContainer')
    }

    def 'create should not work for cross project containers on different networks'() {
        given:
        fork = true

        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                networks {
                    other
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo jessie pinkman | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks = [other]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000']
                    waitForCommand = true
                    attachStderr = true
                    ignoreExitCode = true
                }
            }
            startClientContainer.dependsOn ':subServer:startServerContainer'
        """

        when:
        def result = runTasksSuccessfully ':subClient:startClientContainer'

        then:
        result.standardError.contains("nc: bad address 'server'")

        result.wasExecuted(':subServer:createOtherNetwork')
        result.wasExecuted(':subClient:createDefaultNetwork')
        result.wasExecuted(':subServer:startServerContainer')
        result.wasExecuted(':subClient:startClientContainer')
    }

    def 'host port should be accessible'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = 'echo -e "HTTP/1.1 200 OK\\\\n\\\\ntuco" | nc -l -p 1500'
                    portBindings = ['1500:1500']
                    exposedPorts = ['1500']
                }
            }

            task url(dependsOn: startServerContainer) << {
                logger.warn "URL: http://\${dcompose.server.dockerHost}:\${dcompose.server.findHostPort(1500)}"
            }
        """

        when:
        def result = runTasksSuccessfully 'url'
        def directUrl = (result.standardOutput =~ /(?m)^URL: (.*)$/)[0][1]
        def url = System.getProperty('networkCreateTaskSpec.testUrl', directUrl)
        System.err.println url

        then:
        url.toURL().text.trim() == 'tuco'
    }
}
