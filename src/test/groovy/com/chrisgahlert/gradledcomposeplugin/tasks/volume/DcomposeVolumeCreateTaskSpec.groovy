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

class DcomposeVolumeCreateTaskSpec extends AbstractDcomposeSpec {

    def 'should be able to create volume'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    sample {
                        driver = 'local'
                    }
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [sample.bind('/home')]
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        result.wasExecuted(':createSampleVolume')
    }

    def 'should support custom driver opts'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    sample {
                        driverOpts = [opt1: 'value1']
                    }
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [sample.bind('/home')]
                }
            }
        """

        when:
        def result = runTasks 'createMainContainer'

        then:
        if (result.failure) {
            assert result.standardOutput.contains('invalid option key: "opt1"')
        }
    }

    def 'should support custom driver'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    sample {
                        driver = 'custom'
                    }
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [sample.bind('/home')]
                }
            }
        """

        when:
        def result = runTasksWithFailure 'createMainContainer'

        then:
        result.standardOutput.toLowerCase().contains 'error looking up volume plugin custom:'
    }

    def 'create volume should be up-to-date'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    sample
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [sample.bind('/home')]
                }
            }
        """

        runTasksSuccessfully 'createSampleVolume'

        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        result.wasExecuted(':createSampleVolume')
        result.wasUpToDate(':createSampleVolume')
    }

    def 'create volume should not be up-to-date when inputs changed'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    sample
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [sample.bind('/home')]
                }
            }
        """

        runTasksSuccessfully 'createSampleVolume'

        buildFile << 'dcompose.volumes.sample.driver = "local"'

        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        result.wasExecuted(':createSampleVolume')
        !result.wasUpToDate(':createSampleVolume')
    }

    def 'should be able to share volume accross containers'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                first {
                    image = '$DEFAULT_IMAGE'
                    binds = [test.bind('/data')]
                    command = ['sh', '-c', 'echo abc >> /data/test.txt && sleep 300']
                }
                second {
                    image = '$DEFAULT_IMAGE'
                    binds = [test.bind('/data2')]
                    command = ['sh', '-c', 'echo def >> /data2/test.txt']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('first', '/data')}
        """

        when:
        runTasksSuccessfully 'startFirstContainer', 'startSecondContainer', 'copy'

        then:
        file('build/copy/test.txt').text.trim() == 'abc\ndef'
    }

    def 'should be able to share volumes between cross-project containers'() {
        given:
        addSubproject 'sub1', """
            dcompose {
                volumes {
                    test
                }
                first {
                    image = '$DEFAULT_IMAGE'
                    binds = [test.bind('/data')]
                    command = ['sh', '-c', 'echo first1 >> /data/res']
                    waitForCommand = true
                }
                second {
                    image = '$DEFAULT_IMAGE'
                    binds = [volume(':sub2:test').bind('/data')]
                    command = ['sh', '-c', 'echo second1 >> /data/res']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('first', '/data')}
        """

        addSubproject 'sub2', """
            dcompose {
                volumes {
                    test
                }
                first {
                    image = '$DEFAULT_IMAGE'
                    binds = [volume('test').bind('/data'), volume(':sub1:test').bind('/data2')]
                    command = ['sh', '-c', 'echo first2 >> /data/res && echo first2 >> /data2/res']
                    waitForCommand = true
                }
                second {
                    image = '$DEFAULT_IMAGE'
                    binds = [test.bind('/data2')]
                    command = ['sh', '-c', 'echo second2 >> /data2/res']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('first', '/data')}
        """

        when:
        runTasksSuccessfully 'startContainers', 'copy'
        def res1 = file('sub1/build/copy/res').text
        def res2 = file('sub2/build/copy/res').text

        then:
        res1.contains 'first1'
        res1.contains 'first2'
        res2.contains 'second1'
        res2.contains 'first2'
        res2.contains 'second2'
    }

    def 'volumes should be persistent'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test
                }
                main {
                    image = '$DEFAULT_IMAGE'
                    binds = [volume('test').bind('/d')]
                    command = ['sh', '-c', 'echo aaa >> /d/test.txt']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('main', '/d')}
        """

        runTasksSuccessfully 'startMainContainer', 'removeMainContainer'

        when:
        runTasksSuccessfully 'startMainContainer', 'copy'

        then:
        file('build/copy/test.txt').text.trim() == 'aaa\naaa'
    }

}
