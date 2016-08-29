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

class DcomposeImageRemoveTaskSpec extends AbstractDcomposeSpec {

    private static final String REMOVE_IMAGE = 'busybox:1.24.2-glibc'
    private static final String REMOVE_ALTERNATE_IMAGE = 'busybox:1.24.1-glibc'

    def 'remove image should run successfully'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$REMOVE_IMAGE'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startDbContainer', 'removeDbImage'

        then:
        result.wasExecuted(':pullDbImage')
        result.wasExecuted(':createDbContainer')
        result.wasExecuted(':startDbContainer')
        result.wasExecuted(':stopDbContainer')
        result.wasExecuted(':removeDbContainer')
        result.wasExecuted(':removeDbImage')
    }

    def 'remove image should be skipped when already removed'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$REMOVE_ALTERNATE_IMAGE'
                }
            }
        """

        runTasks 'removeImages'

        when:
        def result = runTasksSuccessfully 'removeDbImage'

        then:
        result.wasSkipped(':stopDbContainer')
        result.wasSkipped(':removeDbContainer')
        result.wasSkipped(':removeDbImage')
    }

    def 'remove image should be skipped when not created'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$REMOVE_ALTERNATE_IMAGE'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'removeDbImage'

        then:
        result.wasSkipped(':stopDbContainer')
        result.wasSkipped(':removeDbContainer')
        result.wasSkipped(':removeDbImage')
    }
}
