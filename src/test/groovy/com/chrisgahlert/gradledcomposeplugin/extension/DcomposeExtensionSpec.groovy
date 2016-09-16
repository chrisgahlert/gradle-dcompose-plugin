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

class DcomposeExtensionSpec extends AbstractDcomposeSpec {

    def 'should support custom client settings'() {
        given:
        buildFile << """
            dcompose {
              main {
                image = '$DEFAULT_IMAGE'
              }

              dockerClientConfig = {
                withDockerHost 'ftp://abc'
              }
            }
        """

        when:
        def result = runTasksWithFailure 'createMainContainer'

        then:
        result.standardError.contains 'Unsupported protocol scheme found: \'ftp://abc'
    }

    def 'should be able to reference parent properties'() {
        given:
        buildFile << """
            def test = 'helllo'
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', "echo '\${test}1' > /test.txt"]
                    waitForCommand = true
                    logger.warn "\$project.buildDir"
                }
                logger.warn "\${test}2"
                logger.warn "\$buildDir"
            }
            
            ${copyTaskConfig('main', '/test.txt')}
        """

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'copy'

        then:
        file('build/copy/test.txt').text.contains 'helllo1'
        result.standardOutput.contains 'helllo2'
    }

    def 'should be able to reference containers by property name'() {
        given:
        buildFile << """
            dcompose {
                check {
                    image = 'foobar'
                }
            }

            logger.warn dcompose.check.image

        """

        when:
        def result = runTasksSuccessfully 'help'

        then:
        result.standardOutput.contains 'foobar'
    }

    def 'should validate correctly when linking services on different networks'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    other
                }
                server {
                    image = '$DEFAULT_IMAGE'
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    links = [ server.link() ]
                    networks = [other]
                }
            }
        """

        when:
        def result = runTasksWithFailure 'help'

        then:
        result.standardError.contains('Please make sure they are on the same network')
    }

    @Unroll
    def 'should #successText when container name is #containerName and network name is #networkName'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    $networkName
                }
                $containerName {
                    image = '$DEFAULT_IMAGE'
                    networks = [$networkReference]
                }
            }
        """

        when:
        def result = runTasks 'help'

        then:
        if (error) {
            assert !result.success
            assert result.standardError.contains(error)
        } else {
            assert result.success
        }



        where:
        containerName || networkName || networkReference   || error
        'main'        || 'main'      || 'main'             || "The property 'main' is ambiguous"
        'main'        || 'main'      || 'network("main")'  || false
        'main'        || 'main'      || 'network(":main")' || false

        successText = error ? 'fail' : 'succeed'
    }
}
