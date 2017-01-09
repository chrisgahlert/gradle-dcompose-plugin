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

    def 'should be able to re-create network'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    frontend
                }
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo jessie pinkman | nc -l -p 8000']
                    exposedPorts = ['8000']
                    networks << frontend
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'nc server 8000 > /result.txt']
                    waitForCommand = true
                    dependsOn = [server]
                    networks = [frontend]
                }
            }

            ${copyTaskConfig('client', '/result.txt', 'copy')}

            createDefaultNetwork.outputs.upToDateWhen { false }
            createFrontendNetwork.outputs.upToDateWhen { false }
        """

        runTasksSuccessfully 'startServerContainer'

        when:
        def result = runTasksSuccessfully 'startClientContainer', 'copy'

        then:
        result.wasExecuted(':createDefaultNetwork')
        result.wasExecuted(':createFrontendNetwork')
        result.wasExecuted(':createServerContainer')
        result.wasUpToDate(':createServerContainer')
        result.wasExecuted(':startServerContainer')
        !result.wasUpToDate(':startServerContainer')
        result.wasExecuted(':createClientContainer')
        !result.wasUpToDate(':createClientContainer')
        result.wasExecuted(':startClientContainer')
        !result.wasUpToDate(':startClientContainer')

        file('build/copy/result.txt').text.trim() == 'jessie pinkman'
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
                    command = ['sh', '-c', 'echo -e "HTTP/1.1 200 OK\\\\n\\\\ntuco" | nc -l -p 1500']
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

    def 'should fail for unknown network driver'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    customDriver {
                        driver = 'other'
                    }
                }
            }
        """

        when:
        def result = runTasks 'createCustomDriverNetwork'

        then:
        result.standardError.contains('plugin not found')
    }

    def 'should fail adding multiple subnets'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    custom {
                        ipam {
                            config {
                                subnet = '10.0.1.0/24'
                            }
                            config {
                                subnet = '10.0.2.0/24'
                            }
                        }
                    }
                }
            }
        """

        when:
        def result = runTasksWithFailure 'createCustomNetwork'

        then:
        result.standardError =~ /bridge driver doesn'?t support multiple subnets/
    }

    def 'should support other network driver options'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    custom {
                        driverOpts = [test: 'hello']
                        enableIpv6 = false
                        ipam {
                            driver = 'default'
                            options = [other: 'hello']
                            config {
                                subnet = '10.0.1.0/24'
                                gateway = '10.0.1.138'
                                ipRange = '10.0.1.128/25'
                            }
                        }
                    }
                }

                ipecho {
                    image = '$DEFAULT_IMAGE'
                    setCommand 'ifconfig > /test.txt && route >> /test.txt'
                    waitForCommand = true
                    networks = [custom]
                }
            }

            ${copyTaskConfig('ipecho', '/test.txt')}
        """

        when:
        runTasksSuccessfully 'startContainers', 'copy'
        def netout = file('build/copy/test.txt').text

        then:
        netout.contains('inet addr:10.0.1.128')
        netout.contains('''
            Kernel IP routing table
            Destination     Gateway         Genmask         Flags Metric Ref    Use Iface
            default         10.0.1.138      0.0.0.0         UG    0      0        0 eth0
            10.0.1.0        *               255.255.255.0   U     0      0        0 eth0
        '''.stripIndent())
    }

    def 'should support changing the default subnet'() {
        given:
        buildFile << """
            dcompose {
                network('default').ipam.config { subnet = '10.0.17.0/24' }

                ipecho {
                    image = '$DEFAULT_IMAGE'
                    setCommand 'ifconfig > /test.txt'
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('ipecho', '/test.txt')}
        """

        when:
        runTasksSuccessfully 'startContainers', 'copy'

        then:
        file('build/copy/test.txt').text.contains('inet addr:10.0.17.2')
    }
}
