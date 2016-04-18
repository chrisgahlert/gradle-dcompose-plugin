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
package com.chrisgahlert.gradledcomposeplugin

import groovy.transform.CompileStatic
import nebula.test.IntegrationSpec

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
