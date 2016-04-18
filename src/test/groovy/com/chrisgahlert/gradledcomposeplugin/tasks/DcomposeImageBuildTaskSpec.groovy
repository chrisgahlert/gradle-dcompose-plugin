package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
class DcomposeImageBuildTaskSpec extends AbstractDcomposeSpec {

    def setup() {
        cleanupTask = 'removeImages'
    }

    def 'should be able to build image'() {
        given:
        buildFile << """
            dcompose {
                buildimg {
                    baseDir = file('docker/')
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('buildimg', '/test')}
        """

        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["sh", "-c", "echo built > /test"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'startBuildimgContainer', 'copy'

        then:
        result.wasExecuted(':buildBuildimgImage')
        result.wasExecuted(':createBuildimgContainer')
        result.wasExecuted(':startBuildimgContainer')
        file('build/copy/test').text.trim() == 'built'
    }

    def 'should be able to build image with custom dockerfile name'() {
        given:
        buildFile << """
            dcompose {
                buildimg {
                    dockerFilename = 'testabc'
                    baseDir = file('docker/')
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('buildimg', '/test')}
        """

        file('docker/testabc').text = """
            FROM $DEFAULT_IMAGE
            CMD ["sh", "-c", "echo built > /test"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'startBuildimgContainer', 'copy'

        then:
        result.wasExecuted(':buildBuildimgImage')
        result.wasExecuted(':createBuildimgContainer')
        result.wasExecuted(':startBuildimgContainer')
        file('build/copy/test').text.trim() == 'built'
    }

    def 'build image should be up-to-date'() {
        given:
        buildFile << '''
            dcompose {
                db {
                    baseDir = file('docker/')
                }
            }
        '''

        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["/bin/sleep", "300"]
        """.stripIndent()

        runTasksSuccessfully 'startDbContainer'

        when:
        def result = runTasksSuccessfully 'startDbContainer'

        then:
        result.wasUpToDate(':buildDbImage')
        result.wasUpToDate(':createDbContainer')
        result.wasUpToDate(':startDbContainer')
    }

    def 'build image should not be up-to-date on change'() {
        given:
        buildFile << '''
            dcompose {
                db {
                    baseDir = file('docker/')
                }
            }
        '''

        def dockerfile = file('docker/Dockerfile')
        dockerfile.text = """
            FROM $DEFAULT_IMAGE
            CMD ["/bin/sleep", "300"]
        """.stripIndent()

        runTasksSuccessfully 'startDbContainer'
        dockerfile.text = dockerfile.text.replace('"300"', '"301"')

        when:
        def result = runTasksSuccessfully 'startDbContainer'

        then:
        !result.wasUpToDate(':buildDbImage')
        !result.wasUpToDate(':createDbContainer')
        !result.wasUpToDate(':startDbContainer')
    }
}
