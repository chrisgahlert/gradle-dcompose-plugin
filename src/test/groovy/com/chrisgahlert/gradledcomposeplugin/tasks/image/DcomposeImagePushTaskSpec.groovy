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

class DcomposeImagePushTaskSpec extends AbstractDcomposeSpec {

    def setup() {
        assert registryUrl && registryUser && registryPass
    }

    def 'should be able to push image from hub to private reg'() {
        given:
        buildFile << """
            dcompose {
                $registryClientConfig

                main {
                    image = '$DEFAULT_IMAGE'
                    repository = '$registryUrl/myapp:pushsimple'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'pushMainImage'

        then:
        result.wasExecuted(':pushMainImage')
        !result.wasSkipped(':pushMainImage')
        !result.wasUpToDate(':pushMainImage')
    }

    def 'should be able to push image from hub to private reg with custom url'() {
        given:
        buildFile << """
            dcompose {
                registry ('https://$registryUrl') {
                    withUsername '$registryUser'
                    withPassword '$registryPass'
                }

                main {
                    image = '$DEFAULT_IMAGE'
                    repository = '$registryUrl/oapp:pushsimple'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'pushMainImage'

        then:
        result.wasExecuted(':pushMainImage')
        !result.wasSkipped(':pushMainImage')
        !result.wasUpToDate(':pushMainImage')
    }

    def 'should be able to pull published image from private reg'() {
        given:
        buildFile << """
            dcompose {
                $registryClientConfig

                main {
                    baseDir = file('src/main/docker')
                    repository = '$registryUrl/custom'
                }
            }
        """

        file('src/main/docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            RUN echo hello custom image for pushing > /test.txt
            CMD ["cat", "/test.txt"]
        """.stripIndent()

        runTasksSuccessfully 'pushMainImage', 'removeMainImage'

        and:
        buildFile.text = """
            $DEFAULT_PLUGIN_INIT

            dcompose {
                $registryClientConfig

                pulled {
                    image = '$registryUrl/custom:latest'
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('pulled', '/test.txt')}
        """

        when:
        def result = runTasksSuccessfully 'startPulledContainer', 'copy'
        def testFile = file('build/copy/test.txt')

        then:
        result.wasExecuted(':pullPulledImage')
        !result.wasSkipped(':pullPulledImage')
        !result.wasUpToDate(':pullPulledImage')
        testFile.text.trim() == 'hello custom image for pushing'
        result.standardOutput.contains("Successfully pulled image $registryUrl/custom:latest")
    }

    def 'should be able to pull published image from private reg during build'() {
        given:
        buildFile << """
            dcompose {
                $registryClientConfig

                main {
                    baseDir = file('src/main/docker')
                    repository = '$registryUrl/customforbuilding:abc'
                }
            }
        """


        def dockerFile = file('src/main/docker/Dockerfile')
        dockerFile.text = """
            FROM $DEFAULT_IMAGE
            CMD ["cat", "/test.txt"]
        """.stripIndent()

        runTasksSuccessfully 'pushMainImage', 'removeMainImage'

        and:
        dockerFile.text = """
            FROM $registryUrl/customforbuilding:abc
            RUN echo building from private registry > /test.txt
        """.stripIndent()

        buildFile.text = """
            $DEFAULT_PLUGIN_INIT

            dcompose {
                $registryClientConfig

                built {
                    baseDir = file('src/main/docker')
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('built', '/test.txt')}
        """

        when:
        def result = runTasksSuccessfully 'startBuiltContainer', 'copy'
        def testFile = file('build/copy/test.txt')

        then:
        !result.wasExecuted(':pullBuiltImage')
        result.wasExecuted(':buildBuiltImage')
        !result.wasSkipped(':buildBuiltImage')
        !result.wasUpToDate(':buildBuiltImage')
        testFile.text.trim() == 'building from private registry'
        result.standardOutput.contains("Built Docker image with id")
    }

    def 'push should not push back already existing images'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'pushImages'

        then:
        result.wasSkipped(':pushMainImage')
    }

    def 'push should try to push back already existing images under new name'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    repository = 'user/image'
                }
            }
        """

        when:
        def result = runTasks 'pushImages'

        then:
        result.wasExecuted(':pushMainImage')
        result.standardError.contains('Could not push image: denied: requested access to the resource is denied') ||
                result.standardError.contains('Could not push image: unauthorized: authentication required')
    }

}
