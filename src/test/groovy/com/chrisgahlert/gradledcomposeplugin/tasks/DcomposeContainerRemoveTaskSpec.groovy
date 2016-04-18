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

/**
 * Created by chris on 18.04.16.
 */
class DcomposeContainerRemoveTaskSpec extends AbstractDcomposeSpec {

    def 'remove container should run successfully'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'removeMainContainer'

        then:
        result.wasExecuted(':startMainContainer')
        result.wasExecuted(':stopMainContainer')
        result.wasExecuted(':removeMainContainer')
    }

    def 'remove container should be skipped when not created'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'removeMainContainer'

        then:
        result.wasSkipped(':removeMainContainer')
    }

    def 'remove container should be skipped when already removed'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer', 'removeMainContainer'

        when:
        def result = runTasksSuccessfully 'removeMainContainer'

        then:
        result.wasSkipped(':removeMainContainer')
    }
}
