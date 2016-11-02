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

    def 'should #successText when container name is #containerName and network name is #networkName'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    main
                }
                volumes {
                    main
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    $property
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
        property                               || error
        'networks = [main]'                    || "The property 'main' is ambiguous"
        'binds = [main.bind("/a")]'            || "The property 'main' is ambiguous"
        'networks = [network("main")]'         || false
        'networks = [network(":main")]'        || false
        'binds = [volume("main").bind("/a")]'  || false
        'binds = [volume(":main").bind("/a")]' || false
        'networks = ["main"]'                  || 'Can only set instances of Network on dcompose.main.networks'
        'binds = [volume("main")]'             || 'Invalid bind in main for volume main'

        successText = error ? 'fail' : 'succeed'
    }

    def 'should be able to change container name prefix'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                }
                namePrefix = 'custom_prefix_'
            }
        """

        when:
        def result = runTasksSuccessfully 'createServerContainer'

        then:
        result.standardOutput.contains('custom_prefix_server')
    }

}
