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
package com.chrisgahlert.gradledcomposeplugin.tasks.image

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeImageBuildTaskSpec extends AbstractDcomposeSpec {

    def setup() {
        cleanupTasks = ['removeImages', 'removeNetworks']
    }

    def 'should be able to build image'() {
        given:
        buildFile << """
            dcompose {
                buildimg {
                    baseDir = file('docker/')
                    waitForCommand = true
                    memory = 512 * 1024 * 1024
                    memswap = 768 * 1024 * 1024
                    cpushares = 10
                    cpusetcpus = '0'
                }
            }

            ${copyTaskConfig('buildimg', '/test')}
        """

        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["sh", "-c", "echo built > /test"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'startBuildimgContainer', 'copy'

        then:
        result.wasExecuted(':buildBuildimgImage')
        result.wasExecuted(':createBuildimgContainer')
        result.wasExecuted(':startBuildimgContainer')
        file('build/copy/test').text.trim() == 'built'
    }

    def 'should be able to build image with buildFiles'() {
        given:
        buildFile << """
            dcompose {
                buildimg {
                    buildFiles = project.copySpec {
                        from 'docker/'
                    }
                    waitForCommand = true
                    memory = 512 * 1024 * 1024
                    memswap = 768 * 1024 * 1024
                    cpushares = 10
                    cpusetcpus = '0'
                }
            }

            ${copyTaskConfig('buildimg', '/test')}
        """

        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["sh", "-c", "echo built > /test"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'startBuildimgContainer', 'copy'

        then:
        result.wasExecuted(':copyBuildimgBuildFiles')
        result.wasExecuted(':buildBuildimgImage')
        result.wasExecuted(':createBuildimgContainer')
        result.wasExecuted(':startBuildimgContainer')
        file('build/copy/test').text.trim() == 'built'
    }

    def 'should be able to build image with custom dockerfile name'() {
        given:
        buildFile << """
            dcompose {
                buildimg {
                    dockerFilename = 'testabc'
                    baseDir = file('docker/')
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('buildimg', '/test')}
        """

        file('docker/testabc').text = """
            FROM $DEFAULT_IMAGE
            CMD ["sh", "-c", "echo built > /test"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'startBuildimgContainer', 'copy'

        then:
        result.wasExecuted(':buildBuildimgImage')
        result.wasExecuted(':createBuildimgContainer')
        result.wasExecuted(':startBuildimgContainer')
        file('build/copy/test').text.trim() == 'built'
    }

    def 'build image should be up-to-date'() {
        given:
        buildFile << '''
            dcompose {
                db {
                    baseDir = file('docker/')
                }
            }
        '''

        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["/bin/sleep", "300"]
        """.stripIndent()

        runTasksSuccessfully 'startDbContainer'

        when:
        def result = runTasksSuccessfully 'startDbContainer'

        then:
        result.wasUpToDate(':buildDbImage')
        result.wasUpToDate(':createDbContainer')
        result.wasExecuted(':startDbContainer')
    }

    def 'build image should not be up-to-date on change'() {
        given:
        buildFile << '''
            dcompose {
                db {
                    baseDir = file('docker/')
                }
            }
        '''

        def dockerfile = file('docker/Dockerfile')
        dockerfile.text = """
            FROM $DEFAULT_IMAGE
            CMD ["/bin/sleep", "300"]
        """.stripIndent()

        runTasksSuccessfully 'startDbContainer'
        dockerfile.text = dockerfile.text.replace('"300"', '"301"')

        when:
        def result = runTasksSuccessfully 'startDbContainer'

        then:
        !result.wasUpToDate(':buildDbImage')
        !result.wasUpToDate(':createDbContainer')
        !result.wasUpToDate(':startDbContainer')
    }
}
