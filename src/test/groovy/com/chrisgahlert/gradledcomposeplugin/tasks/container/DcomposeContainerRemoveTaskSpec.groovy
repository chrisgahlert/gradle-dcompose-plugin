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

class DcomposeContainerRemoveTaskSpec extends AbstractDcomposeSpec {

    def 'remove container should run successfully'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'removeMainContainer'

        then:
        result.wasExecuted(':startMainContainer')
        result.wasExecuted(':stopMainContainer')
        result.wasExecuted(':removeMainContainer')
    }

    def 'remove container should be skipped when not created'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'removeMainContainer'

        then:
        result.wasSkipped(':removeMainContainer')
    }

    def 'remove container should be skipped when already removed'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer', 'removeMainContainer'

        when:
        def result = runTasksSuccessfully 'removeMainContainer'

        then:
        result.wasSkipped(':removeMainContainer')
    }

    def 'remove should work for linked containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    links = [server.link()]
                }
            }
        """

        runTasksSuccessfully 'createClientContainer'

        when:
        def result = runTasksSuccessfully 'removeServerContainer'

        then:
        result.wasExecuted(':removeClientContainer')
        result.wasExecuted(':removeServerContainer')
    }

    def 'remove should work for containers with volumes from'() {
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

        runTasksSuccessfully 'createUserContainer'

        when:
        def result = runTasksSuccessfully 'removeDataContainer'

        then:
        result.wasExecuted(':removeUserContainer')
        result.wasExecuted(':removeDataContainer')
    }

    def 'remove should work for linked cross project containers'() {
        given:
        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    exposedPorts = ['8000']
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
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'createClientContainer'

        when:
        def result = runTasksSuccessfully 'removeServerContainer'

        then:
        result.wasExecuted(':subClient:removeClientContainer')
        result.wasExecuted(':subServer:removeServerContainer')
    }

    def 'remove should work for cross project containers with volumes from'() {
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
                    volumesFrom = [service(':subData:data')]
                }
            }
        """
        addSubproject 'other'

        runTasksSuccessfully 'createUserContainer'

        when:
        def result = runTasksSuccessfully 'removeDataContainer'

        then:
        result.wasExecuted(':subUser:removeUserContainer')
        result.wasExecuted(':subData:removeDataContainer')
    }
}
