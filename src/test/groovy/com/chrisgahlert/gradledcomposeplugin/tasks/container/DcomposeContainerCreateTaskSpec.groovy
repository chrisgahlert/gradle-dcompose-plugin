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
package com.chrisgahlert.gradledcomposeplugin.tasks.container

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeContainerCreateTaskSpec extends AbstractDcomposeSpec {

    def 'create should work successfully'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            ${copyTaskConfig('main', '/etc/group')}
        """

        when:
        def result = runTasksSuccessfully 'copy'

        then:
        result.wasExecuted(':createMainContainer')
        file('build/copy/group').text.startsWith('root:x:0:')
    }

    def 'create should be up-to-date'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE
            dcompose.main.aliases = ['hello']
        """
        runTasksSuccessfully 'createMainContainer'

        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        result.wasUpToDate(':createMainContainer')
    }

    def 'create should not be up-to-date when inputs change'() {
        given:
        buildFile << DEFAULT_BUILD_FILE

        runTasksSuccessfully 'createMainContainer'
        buildFile << "dcompose.main.command = ['echo', 'yeehaw']"

        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        !result.wasUpToDate(':createMainContainer')
        result.wasExecuted(':createMainContainer')
    }

    def 'create should not be up-to-date when underlying image changed'() {
        given:
        def dockerFile = file('docker/Dockerfile')
        dockerFile.text = 'FROM busybox:1.24.2-musl'

        buildFile << """
            dcompose {
                main {
                    baseDir = file('docker/')
                    command = ['echo', 'test']
                    tag = 'createtestimage'
                }
            }
        """

        runTasksSuccessfully 'createMainContainer'
        dockerFile << '\nCMD ["/bin/sleep", "300"]'


        when:
        def result = runTasksSuccessfully 'createMainContainer'

        then:
        !result.wasUpToDate(':createMainContainer')
        result.wasExecuted(':createMainContainer')
    }

    def 'create should not preserve volumes by default'() {
        given:
        buildFile << """
            dcompose {
                preserve {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo ptest > /test/content']
                    waitForCommand = true
                    volumes = ['/test']
                }
            }

            ${copyTaskConfig('preserve', '/test')}
        """

        runTasksSuccessfully 'startPreserveContainer', 'removePreserveContainer'

        when:
        runTasksSuccessfully 'copy'

        then:
        file('build/copy/content').text.isEmpty()
    }

    def 'create should preserve volumes after remove when enabled'() {
        given:
        buildFile << """
            dcompose {
                preserve {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo ptest > /test/content']
                    waitForCommand = true
                    volumes = ['/test']
                    preserveVolumes = true
                }
            }

            ${copyTaskConfig('preserve', '/test')}
        """

        runTasksSuccessfully 'startPreserveContainer', 'removePreserveContainer'

        when:
        runTasksSuccessfully 'copy'

        then:
        file('build/copy/content').text.trim() == 'ptest'
    }

    def 'create should not preserve image-provided volumes by default'() {
        given:
        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            VOLUME /myvol
        """.stripIndent()

        buildFile << """
            dcompose {
                preserve {
                    baseDir = file('docker')
                    command = ['sh', '-c', 'echo ptest > /myvol/content']
                    waitForCommand = true
                }
            }

            ${copyTaskConfig('preserve', '/myvol')}
        """

        runTasksSuccessfully 'startPreserveContainer', 'removePreserveContainer'

        when:
        runTasksSuccessfully 'copy'

        then:
        file('build/copy/content').text.isEmpty()
    }

    def 'create should preserve image-provided volumes after remove when enabled'() {
        given:
        file('docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            VOLUME /myvol
        """.stripIndent()

        buildFile << """
            dcompose {
                preserve {
                    baseDir = file('docker')
                    command = ['sh', '-c', 'echo ptest > /myvol/content']
                    waitForCommand = true
                    preserveVolumes = true
                }
            }

            ${copyTaskConfig('preserve', '/myvol')}
        """

        runTasksSuccessfully 'startPreserveContainer', 'removePreserveContainer'

        when:
        runTasksSuccessfully 'copy'

        then:
        file('build/copy/content').text.trim() == 'ptest'
    }

    def 'create should not preserve volumes on update by default'() {
        given:
        buildFile << """
            dcompose {
                preserve {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo ptest > /test/content']
                    waitForCommand = true
                    volumes = ['/test']
                }
            }

            ${copyTaskConfig('preserve', '/test')}
        """

        runTasksSuccessfully 'startPreserveContainer'
        buildFile << "dcompose.preserve.volumes << '/test2'"

        when:
        def result = runTasksSuccessfully 'copy'

        then:
        !result.wasUpToDate(':createPreserveContainer')
        file('build/copy/content').text.isEmpty()
    }

    def 'create should preserve volumes on update when enabled'() {
        given:
        buildFile << """
            dcompose {
                preserve {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo ptest > /test/content']
                    waitForCommand = true
                    volumes = ['/test']
                    preserveVolumes = true
                }
            }

            ${copyTaskConfig('preserve', '/test')}
        """

        runTasksSuccessfully 'startPreserveContainer'
        buildFile << "dcompose.preserve.volumes << '/test2'"

        when:
        def result = runTasksSuccessfully 'copy'

        then:
        !result.wasUpToDate(':createPreserveContainer')
        file('build/copy/content').text.trim() == 'ptest'
    }

    def 'create should work for linked containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                }
                other {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    links = [server.link(), "\$other.containerName:manual"]
                }
            }
        """

        runTasksSuccessfully 'createOtherContainer'

        when:
        def result = runTasksSuccessfully 'createClientContainer'

        then:
        result.wasExecuted(':createServerContainer')
        result.wasExecuted(':createClientContainer')
    }

    def 'create should work for dependant containers'() {
        given:
        buildFile << """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                }
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    dependsOn = [server]
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'createClientContainer'

        then:
        result.wasExecuted(':createServerContainer')
        result.wasExecuted(':createClientContainer')
    }

    def 'create should work for containers with volumes from'() {
        given:
        buildFile << """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                    volumes = ['/data']
                }
                other {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo test123 > /other/content']
                    volumes = ['/other']
                    waitForCommand = true
                }
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumesFrom = [data, other.containerName]
                }

            }

            ${copyTaskConfig('user', '/other/content')}
        """

        runTasksSuccessfully 'startOtherContainer'

        when:
        def result = runTasksSuccessfully 'createUserContainer', 'copy'

        then:
        result.wasExecuted(':createDataContainer')
        result.wasExecuted(':createUserContainer')
        file('build/copy/content').text.trim() == 'test123'
    }

    def 'create should work for linked cross project containers'() {
        given:
        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subDatabase', """
            dcompose {
                db {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8001']
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    links = [service(':subServer:server').link(), service(':subDatabase:db').link('alias')]
                }
            }
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'createClientContainer'

        then:
        result.wasExecuted(':subServer:createServerContainer')
        result.wasExecuted(':subDatabase:createDbContainer')
        result.wasExecuted(':subClient:createClientContainer')
    }

    def 'create should work for dependant cross project containers'() {
        given:
        buildFile.text = ''

        addSubproject 'subServer', """
            dcompose {
                server {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8000']
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subDatabase', """
            dcompose {
                db {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    exposedPorts = ['8001']
                    networks = [ network(':subClient:default') ]
                }
            }
        """
        addSubproject 'subClient', """
            dcompose {
                client {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    dependsOn = [service(':subServer:server'), service(':subDatabase:db')]
                }
            }
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'createClientContainer'

        then:
        result.wasExecuted(':subServer:createServerContainer')
        result.wasExecuted(':subDatabase:createDbContainer')
        result.wasExecuted(':subClient:createClientContainer')
    }

    def 'create should work for cross-project containers with volumes from'() {
        given:
        buildFile.text = ''

        addSubproject 'subData', """
            dcompose {
                data {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumes = ['/data']
                }
            }
        """
        addSubproject 'subUser', """
            dcompose {
                user {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'abc']
                    volumesFrom = [service(':subData:data')]
                }
            }
        """
        addSubproject 'other'

        when:
        def result = runTasksSuccessfully 'createUserContainer'

        then:
        result.wasExecuted(':subData:createDataContainer')
        result.wasExecuted(':subUser:createUserContainer')
    }

    def 'create should support all other optional properties'() {
        given:
        fork = true

        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['sh', '-c', 'echo "\$TESTENV\\n\$(pwd)" > /data/result && echo abc > /etc/test']
                    waitForCommand = true
                    ignoreExitCode = true

                    env = ['TESTENV=yes']
                    workingDir = '/somewhere'
                    user = 'root'
                    readonlyRootfs = true
                    volumes = ['/data']
                    publishAllPorts = true
                    hostName = 'yeehaw'
                    dns = ['1.2.3.4']
                    dnsSearch = ['somedomain']
                    extraHosts = ['test:1.4.7.8']
                    networkMode = 'bridge'
                    attachStdin = false
                    attachStdout = true
                    attachStderr = true
                    privileged = true
                    networks = null
                }
            }

            ${copyTaskConfig('main', '/data/result', 'copyResult')}
            ${copyTaskConfig('main', '/etc', 'copy')}
        """

        when:
        def result = runTasksSuccessfully 'startMainContainer', 'copy', 'copyResult'

        then:
        result.wasExecuted(':createMainContainer')
        result.wasExecuted(':startMainContainer')
        result.standardError.contains("sh: can't create /etc/test: Read-only file system")
        file('build/copyResult/result').text.trim() == "yes\n/somewhere"
        file('build/copy/hostname').text.trim() == 'yeehaw'
        file('build/copy/resolv.conf').text.contains('search somedomain')
        file('build/copy/resolv.conf').text.contains('nameserver 1.2.3.4')
        file('build/copy/hosts').text.contains('1.4.7.8\ttest')
    }

    def 'create should support entrypoint'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo', 'hello']
                    entrypoints = ['/entry.sh']
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
