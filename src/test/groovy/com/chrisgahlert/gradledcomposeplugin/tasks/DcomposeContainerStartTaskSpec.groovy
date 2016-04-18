package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
class DcomposeContainerStartTaskSpec extends AbstractDcomposeSpec {


    def 'should start new container'() {
        given:
        buildFile << """
            dcompose {
                test {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo started > /test']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('test', '/test')}
        """

        when:
        def result = runTasksSuccessfully 'startTestContainer', 'copy'

        then:
        result.wasExecuted(':startTestContainer')
        file('build/copy/test').text.trim() == 'started'
    }

    def 'start should be up-to-date'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        result.wasUpToDate(':startMainContainer')
    }

    def 'start should not be up-to-date when create was not up-to-date'() {
        given:
        buildFile << DEFAULT_BUILD_FILE
        runTasksSuccessfully 'startMainContainer'
        buildFile << 'createMainContainer.outputs.upToDateWhen { false }'


        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        !result.wasUpToDate(':startMainContainer')
        result.wasExecuted(':startMainContainer')
    }

    def 'start should not be up-to-date when container stopped as expected'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '1']
                }
            }
        """

        runTasksSuccessfully 'startMainContainer'

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        !result.wasUpToDate(':startMainContainer')
        result.wasExecuted(':startMainContainer')
    }

    def 'should wait for command'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['/bin/sleep', '5']
                    waitForCommand = true
                    waitTimeout = 20
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'startMainContainer'

        then:
        result.wasExecuted(':startMainContainer')
    }

    def 'should timeout waiting for command'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['/bin/sleep', '10']
                    waitForCommand = true
                    waitTimeout = 5
                }
            }
        """

        when:
        def result = runTasksWithFailure 'startMainContainer'

        then:
        result.wasExecuted(':createMainContainer')
        result.wasExecuted(':startMainContainer')
    }
}
