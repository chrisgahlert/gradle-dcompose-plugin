package com.chrisgahlert.gradledcomposeplugin

import nebula.test.IntegrationSpec

/**
 * Created by chris on 14.04.16.
 */
class SimpleContainerTest extends IntegrationSpec {

    def setup() {
        buildFile << '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    image = 'busybox:1.24.2-musl'
                    command = ['/bin/sleep', '300']
                }
            }
        '''
    }

    def cleanup() {
        try {
            runTasks 'removeContainers'
        } catch (Exception e) {
        }
    }

    def 'should support using container properties as task dependencies'() {
        given:
        buildFile << '''
            task test(dependsOn: dcompose.db.createTaskName) {}
        '''

        when:
        def result = runTasksSuccessfully 'test'
        println result.standardOutput

        then:
        result.wasExecuted(':createDbContainer')
    }

    def 'should create the create all containers task'() {
        when:
        def result = runTasksSuccessfully 'createContainers'
        println result.standardOutput

        then:
        result.wasExecuted(':createContainers')
    }

    def 'create should be up-to-date'() {
        when:
        runTasksSuccessfully 'createContainers'
        def result = runTasksSuccessfully 'createContainers'
        println result.standardOutput

        then:
        result.wasUpToDate(':createDbContainer')
    }

    def 'pull should be skipped'() {
        when:
        runTasksSuccessfully 'pullDbImage'
        def result = runTasksSuccessfully 'pullDbImage'
        println result.standardOutput

        then:
        result.wasSkipped(':pullDbImage')
    }

    def 'create should not be up-to-date when inputs change'() {
        when:
        runTasksSuccessfully 'createContainers'
        buildFile.text = buildFile.text.replace("'busybox:1.24.2-musl'", "'busybox:1.24.2-glibc'")

        def result = runTasksSuccessfully 'createContainers'
        println result.standardOutput

        then:
        !result.wasUpToDate(':createDbContainer')
    }

    def 'should start new container'() {
        when:
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        result.wasExecuted(':startDbContainer')
    }

    def 'start should be up-to-date'() {
        when:
        runTasksSuccessfully 'startDbContainer'
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        result.wasUpToDate(':startDbContainer')
        result.standardOutput.contains("Skipping task ':startDbContainer' as it is up-to-date")
    }

    def 'stop should be skipped when not started'() {
        when:
        def result = runTasksSuccessfully 'stopDbContainer'
        println result.standardOutput

        then:
        result.wasSkipped(':stopDbContainer')
    }

    def 'stop should run successfully'() {
        when:
        def result = runTasksSuccessfully 'startDbContainer', 'stopDbContainer'
        println result.standardOutput

        then:
        !result.wasUpToDate(':stopDbContainer')
    }

    def 'remove container should run successfully'() {
        when:
        def result = runTasksSuccessfully 'startDbContainer', 'removeDbContainer'
        println result.standardOutput

        then:
        result.wasExecuted(':startDbContainer')
        result.wasExecuted(':stopDbContainer')
        result.wasExecuted(':removeDbContainer')
    }

    def 'remove container should be skipped when not created'() {
        when:
        def result = runTasksSuccessfully 'removeDbContainer'
        println result.standardOutput

        then:
        result.wasSkipped(':removeDbContainer')
    }

    def 'should wait for command'() {
        given:
        buildFile.text = '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    image = 'busybox:1.24.2-musl'
                    command = ['/bin/sleep', '5']
                    waitForCommand = true
                    waitTimeout = 20
                }
            }
        '''

        when:
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        result.wasExecuted(':startDbContainer')
    }

    def 'should timeout waiting for command'() {
        given:
        buildFile.text = '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    image = 'busybox:1.24.2-musl'
                    command = ['/bin/sleep', '10']
                    waitForCommand = true
                    waitTimeout = 5
                }
            }
        '''

        when:
        def result = runTasksWithFailure 'startDbContainer'
        println result.standardOutput

        then:
        result.wasExecuted(':createDbContainer')
        result.wasExecuted(':startDbContainer')
    }

    def 'remove image should run successfully'() {
        given:
        buildFile.text = buildFile.text.replace "'busybox:1.24.2-musl'", "'busybox:1.24.1-musl'"

        when:
        def result = runTasksSuccessfully 'startDbContainer', 'removeDbImage'
        println result.standardOutput

        then:
        result.wasExecuted(':pullDbImage')
        result.wasExecuted(':createDbContainer')
        result.wasExecuted(':startDbContainer')
        result.wasExecuted(':stopDbContainer')
        result.wasExecuted(':removeDbContainer')
        result.wasExecuted(':removeDbImage')
    }

    def 'remove image should be skipped when not created'() {
        when:
        buildFile.text = buildFile.text.replace "'busybox:1.24.2-musl'", "'busybox:1.24.2-uclibc'"
        def result = runTasksSuccessfully 'removeDbImage'
        println result.standardOutput

        then:
        result.wasSkipped(':stopDbContainer')
        result.wasSkipped(':removeDbContainer')
        result.wasSkipped(':removeDbImage')
    }

    def 'should be able to build simple image'() {
        given:
        buildFile.text = '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    dockerFile = file('docker/Dockerfile')
                }
            }
        '''

        file('docker/Dockerfile').text = '''
            FROM busybox:1.24.2-musl
            CMD ["/bin/sleep", "300"]
        '''.stripIndent()

        when:
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        result.wasExecuted(':buildDbImage')
        result.wasExecuted(':createDbContainer')
        result.wasExecuted(':startDbContainer')

        cleanup:
        runTasksSuccessfully 'removeDbImage'
    }

    def 'build simple image should be up-to-date'() {
        given:
        buildFile.text = '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    dockerFile = file('docker/Dockerfile')
                }
            }
        '''

        file('docker/Dockerfile').text = '''
            FROM busybox:1.24.2-musl
            CMD ["/bin/sleep", "300"]
        '''.stripIndent()

        when:
        runTasksSuccessfully 'startDbContainer'
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        result.wasUpToDate(':buildDbImage')
        result.wasUpToDate(':createDbContainer')
        result.wasUpToDate(':startDbContainer')

        cleanup:
        runTasksSuccessfully 'removeDbImage'
    }

    def 'build simple image should not be up-to-date on change'() {
        given:
        buildFile.text = '''
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"

            dcompose {
                db {
                    dockerFile = file('docker/Dockerfile')
                }
            }
        '''

        def dockerfile = file('docker/Dockerfile')
        dockerfile.text = '''
            FROM busybox:1.24.2-musl
            CMD ["/bin/sleep", "300"]
        '''.stripIndent()

        when:
        runTasksSuccessfully 'startDbContainer'
        dockerfile.text = dockerfile.text.replace('"300"', '"301"')
        def result = runTasksSuccessfully 'startDbContainer'
        println result.standardOutput

        then:
        !result.wasUpToDate(':buildDbImage')
        !result.wasUpToDate(':createDbContainer')
        !result.wasUpToDate(':startDbContainer')

        cleanup:
        runTasksSuccessfully 'removeDbImage'
    }
}
