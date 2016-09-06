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

import spock.lang.Unroll

class DcomposeTaskGraphSpec extends AbstractDcomposeSpec {

    @Unroll
    def 'should create the #allTask task'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo']
                }
                web {
                    baseDir = file('docker')
                    command = ['echo']
                }
            }
        """

        when:
        def result = runTasksSuccessfully allTask, '--dry-run'

        then:
        if (dbTask != null) {
            assert result.wasSkipped(dbTask)
        }
        if (webTask != null) {
            assert result.wasSkipped(webTask)
        }

        where:
        allTask            || dbTask              || webTask
        'createContainers' || 'createDbContainer' || 'createWebContainer'
        'removeContainers' || 'removeDbContainer' || 'removeWebContainer'
        'startContainers'  || 'startDbContainer'  || 'startWebContainer'
        'stopContainers'   || 'stopDbContainer'   || 'stopWebContainer'
        'buildImages'      || null                || 'buildWebImage'
        'pullImages'       || 'pullDbImage'       || null
        'removeImages'     || 'removeDbImage'     || 'removeWebImage'
    }

    @Unroll
    def 'should support using the #taskNameProperty property as a dependency for pull containers'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task myTask {
                dependsOn dcompose.main.$taskNameProperty
            }
        """

        when:
        def result = runTasksSuccessfully 'myTask', '--dry-run'

        then:
        result.wasSkipped(":$taskName")

        where:
        taskNameProperty          || taskName
        'pullImageTaskName'       || 'pullMainImage'
        'createContainerTaskName' || 'createMainContainer'
        'startContainerTaskName'  || 'startMainContainer'
        'stopContainerTaskName'   || 'stopMainContainer'
        'removeContainerTaskName' || 'removeMainContainer'
        'removeImageTaskName'     || 'removeMainImage'
    }

    @Unroll
    def 'should support using the #taskNameProperty property as a dependency for build containers'() {
        given:
        buildFile << """
            dcompose {
                main {
                    baseDir = file('docker')
                }
            }

            task myTask {
                dependsOn dcompose.main.$taskNameProperty
            }
        """

        when:
        def result = runTasksSuccessfully 'myTask', '--dry-run'

        then:
        result.wasSkipped(":$taskName")

        where:
        taskNameProperty          || taskName
        'createContainerTaskName' || 'createMainContainer'
        'startContainerTaskName'  || 'startMainContainer'
        'stopContainerTaskName'   || 'stopMainContainer'
        'removeContainerTaskName' || 'removeMainContainer'
        'removeImageTaskName'     || 'removeMainImage'
        'buildImageTaskName'      || 'buildMainImage'
    }

    @Unroll
    def 'executing #task should work with configuration-on-demand'() {
        given:
        buildFile.text = """
            subprojects {
                afterEvaluate {
                    logger.warn "#eval \$it.name#"
                }
            }
        """

        addSubproject 'A', """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    networks = [ network(':D:default') ]
                }
            }
        """

        addSubproject 'B', """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    links = [service(':C:main').link()]
                    networks = [ network(':D:default') ]
                }
            }
        """

        addSubproject 'C', """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sleep', '300']
                    links = [service(':A:main').link()]
                    networks = [ network(':D:default') ]
                }
            }
        """

        addSubproject 'D', ''

        when:
        def result = runTasksSuccessfully task, '--configure-on-demand', '--dry-run'
        println result.standardOutput
        println result.standardError

        then:
        evalA == result.standardOutput.contains('#eval A#')
        evalB == result.standardOutput.contains('#eval B#')
        evalC == result.standardOutput.contains('#eval C#')
        evalD == result.standardOutput.contains('#eval D#')

        where:
        task                      || evalA || evalB || evalC || evalD
        ':A:startMainContainer'   || true  || false || false || true
        ':B:startMainContainer'   || true  || true  || true  || true
        ':C:startMainContainer'   || true  || false || true  || true
        ':A:stopMainContainer'    || true  || true  || true  || true
        ':B:stopMainContainer'    || true  || true  || true  || true
        ':C:stopMainContainer'    || true  || true  || true  || true
        ':B:removeDefaultNetwork' || true  || true  || true  || true
        ':D:tasks'                || false || false || false || true
    }

}
