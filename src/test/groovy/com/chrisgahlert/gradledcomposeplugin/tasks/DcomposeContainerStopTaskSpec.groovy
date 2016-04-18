package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
class DcomposeContainerStopTaskSpec extends AbstractDcomposeSpec {


    def 'stop should be skipped when not started'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'stopMainContainer'

        then:
        result.wasSkipped(':stopMainContainer')
    }

    def 'stop should run successfully in same gradle run'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'stopMainContainer'

        then:
        !result.wasUpToDate(':stopMainContainer')
        result.wasExecuted(':stopMainContainer')
    }

    def 'stop should run successfully in different gradle run'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'stopMainContainer'

        then:
        !result.wasUpToDate(':stopMainContainer')
        result.wasExecuted(':stopMainContainer')
    }

}
