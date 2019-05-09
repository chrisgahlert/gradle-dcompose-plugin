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

class DcomposeNetworkRemoveTaskSpec extends AbstractDcomposeSpec {

    def 'should successfully remove network'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '60']
                    networks = [test]
                    stopTimeout = 0
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'removeTestNetwork'

        then:
        result.wasExecuted(':stopMainContainer')
        result.wasExecuted(':removeMainContainer')
        result.wasExecuted(':removeTestNetwork')
        !result.wasSkipped(':removeTestNetwork')
        !result.wasUpToDate(':removeTestNetwork')
        !result.wasExecuted(':removeMainImage')
    }

    def 'remove network should be skipped when not created'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    test
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'removeTestNetwork'

        then:
        result.wasSkipped(':removeTestNetwork')
    }

    def 'remove network should be skipped when already removed'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '60']
                    networks = [test]
                    stopTimeout = 0
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'
        runTasksSuccessfully 'removeTestNetwork'

        when:
        def result = runTasksSuccessfully 'removeTestNetwork'

        then:
        result.wasSkipped(':stopMainContainer')
        result.wasSkipped(':removeMainContainer')
        result.wasSkipped(':removeTestNetwork')
        !result.wasExecuted(':removeMainImage')
    }

    def 'remove should work for networked cross project containers'() {
        given:
        resetBuildFile()

        addSubproject 'subDatabase', """
            dcompose {
                networks {
                    backend
                }
                database {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '60']
                    networks = [backend]
                    stopTimeout = 0
                }
            }
        """
        addSubproject 'subServer', """
            dcompose {
                appserver {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '60']
                    networks = [ network(':subDatabase:backend'), network(':subClient:default') ]
                    stopTimeout = 0
                }
            }
            startAppserverContainer.dependsOn ':subDatabase:startDatabaseContainer'
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '60']
                }
            }
            startClientContainer.dependsOn ':subServer:startAppserverContainer'
        """

        runTasksSuccessfully ':subClient:startClientContainer'

        when:
        def result = runTasksSuccessfully ':subDatabase:removeBackendNetwork'

        then:
        result.wasExecuted(':subDatabase:removeBackendNetwork')
        !result.wasExecuted(':subDatabase:removeDefaultNetwork')
        !result.wasExecuted(':subServer:removeDefaultNetwork')
        !result.wasExecuted(':subClient:removeDefaultNetwork')
        result.wasExecuted(':subDatabase:stopDatabaseContainer')
        result.wasExecuted(':subDatabase:removeDatabaseContainer')
        result.wasExecuted(':subServer:stopAppserverContainer')
        result.wasExecuted(':subServer:removeAppserverContainer')
        !result.wasExecuted(':subClient:stopClientContainer')
        !result.wasExecuted(':subClient:removeClientContainer')
    }

}
