package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
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
