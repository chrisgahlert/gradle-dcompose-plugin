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
}
