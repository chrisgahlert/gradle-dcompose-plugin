package com.chrisgahlert.gradledcomposeplugin

import groovy.transform.CompileStatic
import nebula.test.IntegrationSpec

/**
 * Created by chris on 17.04.16.
 */
@CompileStatic
abstract class AbstractDcomposeSpec extends IntegrationSpec {
    protected static final String DEFAULT_IMAGE = 'busybox:1.24.2-musl'
    protected static final String ALTERNATE_IMAGE = 'busybox:1.24.2-glibc'
    protected static final String DEFAULT_BUILD_FILE = """
        dcompose {
            main {
                image = '$DEFAULT_IMAGE'
                command = ['/bin/sleep', '300']
            }
        }

    """

    protected String cleanupTask = 'removeContainers'

    String copyTaskConfig(String containerName, String containerPath) {
        """
            task copy(type: com.chrisgahlert.gradledcomposeplugin.tasks.DcomposeCopyFileFromContainerTask) {
                container = dcompose.$containerName
                containerPath = '$containerPath'
            }
        """
    }

    def setup() {
        buildFile << """
            repositories { mavenCentral() }
            apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"
        """
    }

    def cleanup() {
        runTasks cleanupTask
    }

}
