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
package com.chrisgahlert.gradledcomposeplugin.tasks.volume

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeVolumeRemoveTaskSpec extends AbstractDcomposeSpec {

    def 'should be able to remove volume'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = ['/host:/container', test.bind('/other')]
                }
            }
        """

        runTasksSuccessfully 'createMainContainer'

        when:
        def result = runTasksSuccessfully 'removeTestVolume'

        then:
        result.wasExecuted(':removeTestVolume')
        !result.wasUpToDate(':removeTestVolume')
        !result.wasSkipped(':removeTestVolume')
    }

    def 'should be skipped when already removed'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = ['/etc:/container', test.bind('/other')]
                }
            }
        """

        runTasksSuccessfully 'createMainContainer'
        runTasksSuccessfully 'removeTestVolume'

        when:
        def result = runTasksSuccessfully 'removeTestVolume'

        then:
        result.wasSkipped(':removeTestVolume')
    }

    def 'should stop container before removing volume'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = ['/etc:/container', test.bind('/other')]
                    command = ['sleep', '300']
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'removeTestVolume'

        then:
        result.wasExecuted(':stopMainContainer')
        result.wasExecuted(':removeMainContainer')
        result.wasExecuted(':removeTestVolume')
        !result.wasSkipped(':removeTestVolume')
    }

    def 'should fail for unknown attached container'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = ['/etc:/container', test.bind('/other')]
                    command = ['sleep', '300']
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'

        when:
        buildFile.text = """
            $DEFAULT_REPOSITORY_INIT
            $DEFAULT_PLUGIN_INIT
            dcompose {
                volumes {
                    test
                }
            }
        """
        def result = runTasksWithFailure 'removeTestVolume'

        then:
        result.standardOutput =~ /volume (still|is) in use/
    }
}
