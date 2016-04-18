package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
class DcomposeImagePullTaskSpec extends AbstractDcomposeSpec {

    private static final String PULL_IMAGE = 'busybox:1.24.1-musl'
    private static final String PULL_ALTERNATE_IMAGE = 'busybox:1.24.1-uclibc'

    def setup() {
        cleanupTask = 'removeImages'
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

        runTasks cleanupTask

        when:
        def result = runTasksSuccessfully 'pullDbImage'

        then:
        !result.wasSkipped(':pullDbImage')
        result.wasExecuted(':pullDbImage')
    }

    def 'pull should be skipped when already pulled'() {
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
        result.wasSkipped(':pullDbImage')
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
