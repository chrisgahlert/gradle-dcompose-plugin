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
package com.chrisgahlert.gradledcomposeplugin.extension

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec
import spock.lang.Unroll

class ServiceSpec extends AbstractDcomposeSpec {

    def 'should validate correctly on missing defintion'() {
        given:
        buildFile << """
            dcompose {
                main {
                }
            }
        """

        when:
        def result = runTasksWithFailure 'help'

        then:
        result.standardError.contains("At least one of the image, baseDir or buildFiles properties must be provided")
    }

    def 'should validate correctly on buildFiles defintion'() {
        given:
        buildFile << """
            dcompose {
                main {
                    buildFiles = project.copySpec {
                        from 'docker/'
                    }
                }
            }
        """
        file('docker/Dockerfile').text = "FROM $DEFAULT_IMAGE"

        when:
        def result = runTasksSuccessfully 'tasks'

        then:
        result.standardOutput.contains("copyMainBuildFiles")
    }

    def 'should validate correctly on duplicate defintion'() {
        given:
        buildFile << """
            dcompose {
                main {
                    baseDir = file('docker/')
                    image = 'abc'
                }
            }
        """

        when:
        def result = runTasksWithFailure 'help'

        then:
        result.standardError.contains("Either image or baseDir (but not both) can be provided")
    }

    def 'should validate direct container link'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = 'def'
                }
                client {
                    image = 'abc'
                    links = [server]
                }
            }
        """

        when:
        def result = runTasksWithFailure 'help'

        then:
        result.standardError.contains("Invalid service link from client to server")
    }

    @Unroll
    def 'should #successLabel finding the host port "#expectedLabel" for container port "#find"'() {
        given:
        buildFile << """
            dcompose {
              server {
                image = '$DEFAULT_IMAGE'
                command = ['sleep', '300']
                exposedPorts = ['1001', '1002', '1003', '1004']
                portBindings = [
                  '1001',
                  '10001:1001',
                  '127.0.0.1::1002',
                  '127.0.0.2:10002:1002',
                  '9002:1002',
                  '10003:1003',
                  '10005:1005'
                ]
              }
            }

            task findBindings(dependsOn: dcompose.server.startContainerTaskName) << {
                file('result').text = dcompose.server.findHostPort($find)
            }
        """

        when:
        def result = runTasks 'findBindings'

        then:
        assert result.success == success

        if (expectedPort == 'any') {
            assert file('result').text.toInteger() > 0
        } else if (expectedPort) {
            assert file('result').text == "$expectedPort"
        } else if (success) {
            assert file('result').text.isInteger()
        }
        if (errorMessage) {
            assert result.standardError.contains(errorMessage)
        }

        where:
        find                        || success || expectedPort || errorMessage
        1001                        || true    || 'any'        || null
        1002                        || false   || null         || 'server has multiple host ports bound'
        "1002, hostIp: '127.0.0.2'" || true    || 10002        || null
        "1002, hostIp: '127.0.0.1'" || true    || 'any'        || null
        "1002, hostIp: '0.0.0.0'"   || true    || 9002         || null
        1003                        || true    || 10003        || null
        1004                        || false   || null         || 'has not been bound to a host port'
        1005                        || false   || null         || 'Could not find container port 1005'

        expectedLabel = expectedPort ?: 'dynamic'
        successLabel = success ? 'succeed' : 'fail'
    }

    def 'should fail validation if waitForCommand and waitForHealthcheck have been enabled'() {
        given:
        buildFile << """
            dcompose {
                app {
                    image = '$DEFAULT_IMAGE'
                    waitForCommand = true
                    waitForHealthcheck = true
                }
            }
        """

        when:
        def result = runTasksWithFailure 'help'

        then:
        result.standardError.contains('Can either wait for the healthcheck to pass or the command to complete for dcompose service \'app\' - not both')
    }
}
