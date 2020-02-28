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
    private static final String ALTERNATE_IMAGE = 'busybox:taskgraph-alt'

    @Unroll
    def 'should create the #allTask task'() {
        given:
        cleanupTasks << 'removeImages'

        assert registryUrl && registryUser && registryPass

        buildFile << """
            dcompose {
                $registryClientConfig

                volumes {
                    data
                    other
                }

                networks {
                    frontend
                }

                db {
                    image = '$ALTERNATE_IMAGE'
                    repository = '$registryUrl/all-db'
                    command = ['echo']
                    binds = [data.bind('/home')]
                }
                web {
                    baseDir = file('docker')
                    repository = '$registryUrl/all-web'
                    command = ['echo']
                    networks = [frontend]
                }
            }
        """

        file('docker/Dockerfile') << """
            FROM $ALTERNATE_IMAGE
            RUN echo abc > /test.txt
        """.stripIndent()

        when:
        def result = runTasksSuccessfully allTask

        then:
        expectedTasks.each {
            assert result.wasExecuted(it)
        }

        where:
        allTask            || expectedTasks
        'createContainers' || ['pullDbImage', 'createDbContainer', 'createWebContainer', 'createDataVolume', 'createDefaultNetwork', 'createFrontendNetwork']
        'removeContainers' || ['stopWebContainer', 'stopDbContainer', 'removeDbContainer', 'removeWebContainer']
        'startContainers'  || ['pullDbImage', 'startDbContainer', 'startWebContainer', 'createDataVolume', 'createDefaultNetwork', 'createFrontendNetwork']
        'stopContainers'   || ['stopDbContainer', 'stopWebContainer']
        'buildImages'      || ['buildWebImage']
        'pullImages'       || ['pullDbImage',]
        'pushImages'       || ['buildWebImage', 'pullDbImage', 'pushDbImage', 'pushWebImage']
        'removeImages'     || ['removeDbImage', 'removeWebImage', 'removeDbContainer', 'removeWebContainer']
        'createNetworks'   || ['createDefaultNetwork', 'createFrontendNetwork']
        'removeNetworks'   || ['removeDbContainer', 'removeWebContainer', 'removeDefaultNetwork', 'removeFrontendNetwork']
        'createVolumes'    || ['createDataVolume', 'createOtherVolume']
        'removeVolumes'    || ['removeDbContainer', 'removeDataVolume', 'removeOtherVolume']
    }

    @Unroll
    def 'should support using the #taskNameProperty property as a dependency for pull containers'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$ALTERNATE_IMAGE'
                    command = ['/bin/sleep', '300']
                    stopTimeout = 0
                }
            }

            task myTask {
                dependsOn dcompose.main.$taskNameProperty
            }
        """

        when:
        def result = runTasksSuccessfully 'myTask'

        then:
        result.wasExecuted(":$taskName")

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
        file('docker/Dockerfile').text = "FROM $ALTERNATE_IMAGE"

        when:
        def result = runTasksSuccessfully 'myTask'

        then:
        result.wasExecuted(":$taskName")

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
            $DEFAULT_REPOSITORY_INIT
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
                    dependsOn = [service(':C:main')]
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

    def 'tasks description should be correct'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    v1
                }
                networks {
                    n1
                }
                main {
                    buildFiles = project.copySpec {
                        from 'src/main/docker'
                    }
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'tasks'

        then:
        result.standardOutput.replaceAll("\r\n", "\n").contains '''
            Dcompose Docker 'main' service tasks
            ------------------------------------
            buildMainImage
            copyMainBuildFiles
            createMainContainer
            pullMainImage
            pushMainImage
            removeMainContainer
            removeMainImage
            startMainContainer
            stopMainContainer

            Dcompose Docker (all) tasks
            ---------------------------
            buildImages
            createContainers
            createNetworks
            createVolumes
            pullImages
            pushImages
            removeContainers
            removeImages
            removeNetworks
            removeVolumes
            startContainers
            stopContainers

            Dcompose Docker (deploy) tasks
            ------------------------------
            createComposeFile

            Dcompose Docker (networks) tasks
            --------------------------------
            createDefaultNetwork
            createN1Network
            removeDefaultNetwork
            removeN1Network

            Dcompose Docker (volumes) tasks
            -------------------------------
            createV1Volume
            removeV1Volume
        '''.stripIndent()
    }

}
