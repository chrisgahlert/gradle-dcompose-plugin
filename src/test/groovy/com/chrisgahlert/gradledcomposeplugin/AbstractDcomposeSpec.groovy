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

import groovy.transform.TypeChecked
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

@TypeChecked
abstract class AbstractDcomposeSpec extends IntegrationSpec {
    protected static final String DEFAULT_IMAGE = 'busybox:default'
    protected static final String ALTERNATE_IMAGE = 'busybox:alt'
    protected static final String DEFAULT_BUILD_FILE = """
        dcompose {
            main {
                image = '$DEFAULT_IMAGE'
                command = ['/bin/sleep', '300']
                stopTimeout = 0
            }
        }

    """

    public static final String DEFAULT_PLUGIN_INIT = '''
        repositories { mavenCentral() }
        apply plugin: "com.chrisgahlert.gradle-dcompose-plugin"
    '''

    protected List<String> cleanupTasks = ['removeContainers', 'removeNetworks', 'removeVolumes']

    protected int logCounter = 1

    String copyTaskConfig(String containerName, String containerPath, String name = 'copy') {
        """
            task $name(type: com.chrisgahlert.gradledcomposeplugin.tasks.DcomposeCopyFileFromContainerTask) {
                service = dcompose.$containerName
                containerPath = '$containerPath'
            }
        """
    }

    protected registryUrl = System.getProperty('testreg.url')
    protected registryUser = System.getProperty('testreg.user')
    protected registryPass = System.getProperty('testreg.pass')

    protected registryClientConfig = """
        registry ('$registryUrl') {
            withUsername '$registryUser'
            withPassword '$registryPass'
        }
    """

    def setup() {
        buildFile << DEFAULT_PLUGIN_INIT
        file('gradle.properties').text = '''
            org.gradle.daemon=true
            org.gradle.jvmargs=-Xmx128m
        '''.stripIndent()

        gradleVersion = System.getProperty('gradleVersion')

        // Fix for Windows?
//        classpathFilter = new Predicate<URL>() {
//            @Override
//            boolean apply(URL url) {
//                File userDir = new File(StandardSystemProperty.USER_DIR.value())
//                url.path.substring(1).startsWith(userDir.path.replace('\\', '/'))
//            }
//        }
    }

    @Override
    protected File addSubproject(String subprojectName, String subBuildGradleText) {
        return super.addSubproject(subprojectName, """
            $DEFAULT_PLUGIN_INIT
            $subBuildGradleText
        """)
    }

    @Override
    protected ExecutionResult runTasks(String... tasks) {
        def result = super.runTasks(tasks)

        file("build-${logCounter++}.log").text = """\
Running: $tasks
#####################################

$result.standardOutput

#####################################
############### STDERR ##############
#####################################

$result.standardError
        """

        result
    }

    def cleanup() {
        buildFile << """
            allprojects {
                plugins.withType(${DcomposePlugin.class.canonicalName}) {
                    dcompose.services.all {
                        stopTimeout = 0
                    }
                }
            }
        """

        runTasks cleanupTasks as String[]
    }
}
