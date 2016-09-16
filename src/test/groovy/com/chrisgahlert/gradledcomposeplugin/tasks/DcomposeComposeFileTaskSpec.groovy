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
package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeComposeFileTaskSpec extends AbstractDcomposeSpec {

    def 'should create compose file with all possible properties'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    backend
                }
                third {
                    image = "$DEFAULT_IMAGE"
                    deploy = false
                }
                other {
                    baseDir = file('src/main/docker')
                    dockerFilename = 'Dockerfile.custom'
                }
                main {
                    image = "$DEFAULT_IMAGE"
                    command = ["hello", "world"]
                    entrypoints = ['/entrypoint.sh']
                    env = ['TESTENV=yes', 'OTHER=no']
                    workingDir = '/work'
                    user = 'nobody'
                    readonlyRootfs = true
                    volumes = ['/data']
                    binds = ['/hostpath:/containerpath']
                    volumesFrom = [other, 'non_managed']
                    exposedPorts = ['8081', '8082-8083']
                    portBindings = ['8081:8081', '8082']
                    publishAllPorts = true
                    links = [other.link('linkalias'), other.link(), 'non_managed']
                    hostName = 'specialhost'
                    dns = ['1.2.3.4', '8.8.4.4']
                    dnsSearch = ['somedomain', 'otherdomain']
                    extraHosts = ['test:1.4.7.8', 'otheraa:1.2.3.4']
                    attachStdin = true
                    attachStdout = true
                    attachStderr = true
                    privileged = true
                    networks += backend
                    aliases = ['netalias', 'netalias2']
                }
            }
        """

        file('src/main/docker/Dockerfile.custom').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: sha256:...
                command:
                - hello
                - world
                entrypoint:
                - /entrypoint.sh
                environment:
                - TESTENV=yes
                - OTHER=no
                working_dir: /work
                user: nobody
                volumes:
                - /hostpath:/containerpath:rw
                - main__data:/data:rw
                volumes_from:
                - other
                expose:
                - '8081'
                - 8082-8083
                ports:
                - 8081:8081
                - '8082'
                links:
                - other:linkalias
                - other:other
                hostname: specialhost
                dns:
                - 1.2.3.4
                - 8.8.4.4
                dns_search:
                - somedomain
                - otherdomain
                extra_hosts:
                - test:1.4.7.8
                - otheraa:1.2.3.4
                privileged: true
                networks:
                  default:
                    aliases:
                    - netalias
                    - netalias2
                  backend:
                    aliases:
                    - netalias
                    - netalias2
              other:
                image: sha256:...
                networks:
                  default:
                    aliases: []
            networks:
              backend: {}
            volumes:
              main__data: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should create compose file with network mode'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = "$DEFAULT_IMAGE"
                    networkMode = 'bridge'
                }
            }
        """

        when:
        def result = runTasksSuccessfully 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: sha256:...
                network_mode: bridge
            networks: {}
            volumes: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should fail combining network mode and networks'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    custom
                }
                main {
                    image = "$DEFAULT_IMAGE"
                    networkMode = 'bridge'
                    networks = [custom]
                }
            }
        """

        when:
        def result = runTasksWithFailure 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        result.standardError.contains('Cannot combine networkMode and networks for service main')
    }

    def 'should support preserved volumes and special binds'() {
        given:
        buildFile << """
            dcompose {
                main {
                    baseDir = file('src/main/docker')
                    volumes = ['/data']
                    binds = [
                        '.:/data:ro',
                        'namedv:/data2:rw',
                        '/home:/data3:ro'
                    ]
                    preserveVolumes = true
                }
            }
        """

        file('src/main/docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
            VOLUME /other
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: sha256:...
                volumes:
                - .:/data:ro
                - namedv:/data2:rw
                - /home:/data3:ro
                - main__other:/other:rw
                networks:
                  default:
                    aliases: []
            networks: {}
            volumes:
              namedv: {}
              main__other: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    private String readNormalizedFile(String s) {
        file(s).text.replaceAll(/sha256:[a-f0-9]{64}/, 'sha256:...').trim()
    }

    private boolean validateComposeFile(String f) {
        def file = file(f)
        def composeResult = "docker-compose -f $file.name config".execute([], file.parentFile)
        composeResult.errorStream.eachLine { println it }
        composeResult.waitFor()
        composeResult.exitValue() == 0
    }
}
