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

    private registryUrl = System.getProperty('registry.url')
    private registryUser = System.getProperty('registry.user')
    private registryPass = System.getProperty('registry.pass')

    private dockerClientConfig = """
        registry ('$registryUrl') {
            withUsername '$registryUser'
            withPassword '$registryPass'
        }
    """

    def setup() {
        assert registryUrl && registryUser && registryPass
    }

    def 'should be able to push image from hub to private reg'() {
        given:
        buildFile << """
            dcompose {
                $dockerClientConfig

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

    def 'should be able to pull published image from private reg'() {
        given:
        buildFile << """
            dcompose {
                $dockerClientConfig

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
                $dockerClientConfig

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
        result.standardOutput.contains('Successfully pulled image dockerRegistry:5000/custom:latest')
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

}
