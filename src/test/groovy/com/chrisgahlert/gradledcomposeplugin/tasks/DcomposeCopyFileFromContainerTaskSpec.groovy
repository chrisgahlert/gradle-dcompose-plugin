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

class DcomposeCopyFileFromContainerTaskSpec extends AbstractDcomposeSpec {

    def 'should support copying file from not running container'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task cp(type: DcomposeCopyFileFromContainerTask) {
                service = dcompose.main
                containerPath = '/etc/group'
            }
        """

        when:
        def result = runTasksSuccessfully 'cp'

        then:
        result.wasExecuted(':createMainContainer')
        file('build/cp/group').text.startsWith('root:x:0:')
    }

    def 'should support copying file from not running cross project container'() {
        given:
        buildFile << """
            $DEFAULT_PLUGIN_INIT

            task cp(type: DcomposeCopyFileFromContainerTask) {
                service = dcompose.service(':sub:main')
                containerPath = '/etc/group'
            }
        """

        addSubproject 'sub', """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    volumes = ['/data']
                }
            }
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'cp'

        then:
        result.wasExecuted(':sub:createMainContainer')
        file('build/cp/group').text.startsWith('root:x:0:')
    }

    def 'should fail copying non existing file'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task cp(type: DcomposeCopyFileFromContainerTask) {
                service = dcompose.main
                containerPath = '/nonexisting'
            }
        """

        when:
        runTasksWithFailure 'cp'

        then:
        !new File(projectDir, 'build/cp/nonexisting').exists()
    }

    def 'should succeed copying directory from running container with slash'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir -p /test/other && echo running > /test/abc && echo other > /test/other/sub && sleep 300']
                }
            }

            task copyAll(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                service = dcompose.writer
                containerPath = '/test/'
            }
        """

        when:
        def result = runTasksSuccessfully 'startWriterContainer', 'copyAll'

        then:
        file('build/copyAll/abc').text.trim() == 'running'
        file('build/copyAll/other/sub').text.trim() == 'other'
    }

    def 'should succeed copying directory from running container without slash'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir -p /test/other && echo running > /test/abc && echo other > /test/other/sub && sleep 300']
                }
            }

            task copyAll(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                service = dcompose.writer
                containerPath = 'test'
            }
        """

        when:
        def result = runTasksSuccessfully 'startWriterContainer', 'copyAll'

        then:
        file('build/copyAll/abc').text.trim() == 'running'
        file('build/copyAll/other/sub').text.trim() == 'other'
    }

    def 'should support copying single file from running container'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir /test && echo single > /test/abc && sleep 300']
                }
            }

            task copySingle(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                service = dcompose.writer
                containerPath = 'test/abc'
            }
        """

        when:
        runTasksSuccessfully 'startWriterContainer', 'copySingle'

        then:
        file('build/copySingle/abc').text.trim() == 'single'
    }

    def 'should support custom destination dir'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task copyCustom(type: DcomposeCopyFileFromContainerTask) {
                service = dcompose.main
                containerPath = '/etc/group'
                destinationDir = file('otherdir')
            }
        """

        when:
        runTasksSuccessfully 'copyCustom'

        then:
        file('otherdir/group').text.startsWith('root:x:0:')
    }
}
