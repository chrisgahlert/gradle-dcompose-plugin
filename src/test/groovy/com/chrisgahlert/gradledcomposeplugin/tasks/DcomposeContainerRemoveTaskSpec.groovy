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
