package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

/**
 * Created by chris on 18.04.16.
 */
class DcomposeCopyFileFromContainerTaskSpec extends AbstractDcomposeSpec {

    def 'should support copying file from not running container'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task cp(type: DcomposeCopyFileFromContainerTask) {
                container = dcompose.main
                containerPath = '/etc/group'
            }
        """

        when:
        runTasksSuccessfully 'cp'

        then:
        file('build/cp/group').text.startsWith('root:x:0:')
    }

    def 'should fail copying non existing file'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task cp(type: DcomposeCopyFileFromContainerTask) {
                container = dcompose.main
                containerPath = '/nonexisting'
            }
        """

        when:
        runTasksWithFailure 'cp'

        then:
        !new File(projectDir, 'build/cp/nonexisting').exists()
    }

    def 'should succeed copying directory from running container with slash'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir -p /test/other && echo running > /test/abc && echo other > /test/other/sub && sleep 300']
                }
            }

            task copyAll(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                container = dcompose.writer
                containerPath = '/test/'
            }
        """

        when:
        def result = runTasksSuccessfully 'startWriterContainer', 'copyAll'

        then:
        file('build/copyAll/abc').text.trim() == 'running'
        file('build/copyAll/other/sub').text.trim() == 'other'
    }

    def 'should succeed copying directory from running container without slash'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir -p /test/other && echo running > /test/abc && echo other > /test/other/sub && sleep 300']
                }
            }

            task copyAll(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                container = dcompose.writer
                containerPath = 'test'
            }
        """

        when:
        def result = runTasksSuccessfully 'startWriterContainer', 'copyAll'

        then:
        file('build/copyAll/abc').text.trim() == 'running'
        file('build/copyAll/other/sub').text.trim() == 'other'
    }

    def 'should support copying single file from running container'() {
        given:
        buildFile << """
            dcompose {
                writer {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c',
                        'mkdir /test && echo single > /test/abc && sleep 300']
                }
            }

            task copySingle(type: DcomposeCopyFileFromContainerTask) {
                doFirst {
                    Thread.sleep(2000)
                }

                container = dcompose.writer
                containerPath = 'test/abc'
            }
        """

        when:
        runTasksSuccessfully 'startWriterContainer', 'copySingle'

        then:
        file('build/copySingle/abc').text.trim() == 'single'
    }

    def 'should support custom destination dir'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task copyCustom(type: DcomposeCopyFileFromContainerTask) {
                container = dcompose.main
                containerPath = '/etc/group'
                destinationDir = file('otherdir')
            }
        """

        when:
        runTasksSuccessfully 'copyCustom'

        then:
        file('otherdir/group').text.startsWith('root:x:0:')
    }
}
