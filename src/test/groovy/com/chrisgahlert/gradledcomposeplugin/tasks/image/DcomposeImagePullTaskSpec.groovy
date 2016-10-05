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

class DcomposeImagePullTaskSpec extends AbstractDcomposeSpec {

    private static final String PULL_IMAGE = 'busybox:1.24.1-musl'
    private static final String PULL_ALTERNATE_IMAGE = 'busybox:1.24.1-uclibc'

    def setup() {
        cleanupTasks = ['removeImages', 'removeNetworks']
    }

    def 'pull should work'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$PULL_IMAGE'
                }
            }
        """

        runTasks cleanupTasks

        when:
        def result = runTasksSuccessfully 'pullDbImage'

        then:
        !result.wasSkipped(':pullDbImage')
        result.wasExecuted(':pullDbImage')
    }

    def 'pull should be up-to-date when already pulled'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$PULL_IMAGE'
                }
            }
        """

        runTasksSuccessfully 'pullDbImage'

        when:
        def result = runTasksSuccessfully 'pullDbImage'

        then:
        result.wasUpToDate(':pullDbImage')
    }

    def 'pull should not be skipped when image changed'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$PULL_IMAGE'
                }
            }
        """

        runTasksSuccessfully 'pullDbImage'
        buildFile << "dcompose.db.image = '$PULL_ALTERNATE_IMAGE'"

        when:
        def result = runTasksSuccessfully 'pullDbImage'

        then:
        !result.wasSkipped(':pullDbImage')
        result.wasExecuted(':pullDbImage')
    }
}
