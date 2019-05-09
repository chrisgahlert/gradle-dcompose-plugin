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

class DcomposeContainerStopTaskSpec extends AbstractDcomposeSpec {


    def 'stop should be skipped when not started'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'stopMainContainer'

        then:
        result.wasSkipped(':stopMainContainer')
    }

    def 'stop should run successfully in same gradle run'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'stopMainContainer'

        then:
        !result.wasUpToDate(':stopMainContainer')
        result.wasExecuted(':stopMainContainer')
    }

    def 'stop should run successfully in different gradle run'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'stopMainContainer'

        then:
        !result.wasUpToDate(':stopMainContainer')
        result.wasExecuted(':stopMainContainer')
    }

    def 'stop should work for linked containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
                    stopTimeout = 0
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    links = [server.link()]
                    stopTimeout = 0
                }
            }
        """

        runTasksSuccessfully 'startClientContainer'

        when:
        def result = runTasksSuccessfully 'stopServerContainer'

        then:
        result.wasExecuted(':stopClientContainer')
        result.wasExecuted(':stopServerContainer')
    }

    def 'stop should work for dependant containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
                    stopTimeout = 0
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    dependsOn = [server]
                    stopTimeout = 0
                }
            }
        """

        runTasksSuccessfully 'startClientContainer'

        when:
        def result = runTasksSuccessfully 'stopServerContainer'

        then:
        result.wasExecuted(':stopClientContainer')
        result.wasExecuted(':stopServerContainer')
    }

    def 'stop should work for containers with volumes from'() {
        given:
        buildFile << """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumes = ['/data']
                    stopTimeout = 0
                }
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumesFrom = [data]
                    stopTimeout = 0
                }
            }
        """

        runTasksSuccessfully 'startDataContainer', 'startUserContainer'

        when:
        def result = runTasksSuccessfully 'stopUserContainer'

        then:
        !result.wasExecuted(':stopDataContainer')
        result.wasExecuted(':stopUserContainer')
    }

    def 'stop should work for linked cross project containers'() {
        given:
        resetBuildFile()

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
                    stopTimeout = 0
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    links = [service(':subServer:server').link()]
                    stopTimeout = 0
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'startClientContainer'

        when:
        def result = runTasksSuccessfully 'stopServerContainer'

        then:
        result.wasExecuted(':subClient:stopClientContainer')
        result.wasExecuted(':subServer:stopServerContainer')
    }

    def 'stop should work for dependant cross project containers'() {
        given:
        resetBuildFile()

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
                    stopTimeout = 0
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    dependsOn = [service(':subServer:server')]
                    stopTimeout = 0
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'startClientContainer'

        when:
        def result = runTasksSuccessfully 'stopServerContainer'

        then:
        result.wasExecuted(':subClient:stopClientContainer')
        result.wasExecuted(':subServer:stopServerContainer')
    }

    def 'stop should work for cross project containers with volumes from'() {
        given:
        resetBuildFile()

        addSubproject 'subData', """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumes = ['/data']
                    stopTimeout = 0
                }
            }
        """
        addSubproject 'subUser', """
            dcompose {
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumesFrom = [service(':subData:data')]
                    stopTimeout = 0
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'startDataContainer', 'startUserContainer'

        when:
        def result = runTasksSuccessfully 'stopUserContainer'

        then:
        !result.wasExecuted(':subData:stopDataContainer')
        result.wasExecuted(':subUser:stopUserContainer')
    }

}
