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

    def setup() {
        assert registryUrl && registryUser && registryPass

        buildFile << """
            dcompose {
                registry ('https://$registryUrl') {
                    withUsername '$registryUser'
                    withPassword '$registryPass'
                }
            }
        """
    }

    def 'should create v2 compose file with all possible properties'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    backend {
                        driver = 'other'
                        driverOpts = [test: 'hello']
                        enableIpv6 = true
                        ipam {
                            driver = 'special'
                            options = [other: 'hello']
                            config {
                                subnet = '10.0.1.0/24'
                                gateway = '10.0.1.138'
                                ipRange = '10.0.1.128/25'
                            }
                        }
                    }
                }
                third {
                    image = "$DEFAULT_IMAGE"
                    deploy = false
                }
                other {
                    baseDir = file('src/main/docker')
                    dockerFilename = 'Dockerfile.custom'
                    repository = '$registryUrl/comfil-all:test'
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
                    restart = 'always'
                    dependsOn = [other]
                    memLimit = 1000000000L
                    logConfig = 'syslog'
                    logOpts = ['syslog-address': 'tcp://192.168.0.42:123']
                }
            }
            createComposeFile.version = '2'
        """

        file('src/main/docker/Dockerfile.custom').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'pushOtherImage', 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: busybox@sha256:...
                depends_on:
                - other
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
                restart: always
                networks:
                  default:
                    aliases:
                    - netalias
                    - netalias2
                  backend:
                    aliases:
                    - netalias
                    - netalias2
                logging:
                  driver: syslog
                  options:
                    syslog-address: tcp://192.168.0.42:123
                volumes_from:
                - other
                mem_limit: 1000000000
              other:
                image: $registryUrl/comfil-all@sha256:...
                networks:
                  default:
                    aliases: []
            networks:
              backend:
                driver: other
                driver_opts:
                  test: hello
                ipam:
                  driver: special
                  config:
                  - subnet: 10.0.1.0/24
                    ip_range: 10.0.1.128/25
                    gateway: 10.0.1.138
              default:
                ipam:
                  config: []
            volumes:
              main__data: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should create v3 compose file with all possible properties'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    backend {
                        driver = 'other'
                        driverOpts = [test: 'hello']
                        enableIpv6 = true
                        ipam {
                            driver = 'special'
                            options = [other: 'hello']
                            config {
                                subnet = '10.0.1.0/24'
                                gateway = '10.0.1.138'
                                ipRange = '10.0.1.128/25'
                            }
                        }
                    }
                }
                third {
                    image = "$DEFAULT_IMAGE"
                    deploy = false
                }
                other {
                    baseDir = file('src/main/docker')
                    dockerFilename = 'Dockerfile.custom'
                    repository = '$registryUrl/comfil-all:test'
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
                    restart = 'always'
                    dependsOn = [other]
                    memLimit = 1000000000L
                    logConfig = 'fluentd'
                    logOpts = ['fluentd-address': 'fluentdhost:24224']
                }
            }
        """

        file('src/main/docker/Dockerfile.custom').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'pushOtherImage', 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '3'
            services:
              main:
                image: busybox@sha256:...
                depends_on:
                - other
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
                restart: always
                networks:
                  default:
                    aliases:
                    - netalias
                    - netalias2
                  backend:
                    aliases:
                    - netalias
                    - netalias2
                logging:
                  driver: fluentd
                  options:
                    fluentd-address: fluentdhost:24224
                deploy:
                  resources:
                    limits:
                      memory: '1000000000'
                    reservations: {}
              other:
                image: $registryUrl/comfil-all@sha256:...
                networks:
                  default:
                    aliases: []
                deploy:
                  resources:
                    limits: {}
                    reservations: {}
            networks:
              backend:
                driver: other
                driver_opts:
                  test: hello
                ipam:
                  driver: special
                  config:
                  - subnet: 10.0.1.0/24
              default:
                ipam:
                  config: []
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
            createComposeFile.version = '2'
        """

        when:
        def result = runTasksSuccessfully 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: busybox@sha256:...
                network_mode: bridge
            networks:
              default:
                ipam:
                  config: []
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
            createComposeFile.version = '2'
        """

        when:
        def result = runTasksWithFailure 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        result.standardOutput.contains('Cannot combine networkMode and networks for service main')
    }

    def 'should support preserved volumes and special binds'() {
        given:
        buildFile << """
            dcompose {
                volumes {
                    test {
                        driver = 'abc'
                        driverOpts = [a: 'aa', b: 'bb']
                    }
                    other
                }
                main {
                    baseDir = file('src/main/docker')
                    repository = '$registryUrl/comfil-special:binds'
                    volumes = ['/data']
                    binds = [
                        '.:/data:ro',
                        'namedv:/data2:rw',
                        '/home:/data3:ro',
                        test.bind('/data4:ro'),
                        other.bind('/data5')
                    ]
                    preserveVolumes = true
                }
            }
            createComposeFile.version = '2'
        """

        file('src/main/docker/Dockerfile').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
            VOLUME /other
        """.stripIndent()

        when:
        def result = runTasksSuccessfully 'pushMainImage', 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: $registryUrl/comfil-special@sha256:...
                volumes:
                - .:/data:ro
                - namedv:/data2:rw
                - /home:/data3:ro
                - test:/data4:ro
                - other:/data5:rw
                - main__other:/other:rw
                networks:
                  default:
                    aliases: []
            networks:
              default:
                ipam:
                  config: []
            volumes:
              namedv: {}
              other: {}
              main__other: {}
              test:
                driver: abc
                driver_opts:
                  a: aa
                  b: bb
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should support customizing the compose file'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    othernet
                }

                main {
                    image = "$DEFAULT_IMAGE"
                    networks = [othernet]
                }
            }

            createComposeFile {
                version = '2'
                
                beforeSave { config ->
                    config.networks.othernet.driver = 'overlay'
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
                image: busybox@sha256:...
                networks:
                  othernet:
                    aliases: []
            networks:
              othernet:
                ipam:
                  config: []
                driver: overlay
            volumes: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should support customizing the compose file with java syntax'() {
        given:
        buildFile << """
            dcompose {
                main {
                    image = "$DEFAULT_IMAGE"
                }
            }
            createComposeFile.version = '2'

            createComposeFile {
                beforeSave new Action<Map<String, Object>>() {
                    void execute(Map<String, Object> item) {
                        item.networks.default = [
                            driver: 'other'
                        ]
                        item.volumes.special = [:]
                    }
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
                image: busybox@sha256:...
                networks:
                  default:
                    aliases: []
            networks:
              default:
                driver: other
            volumes:
              special: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    def 'should support AWS compatibility flag'() {
        given:
        buildFile << """
            dcompose {
                networks {
                    backend
                }
                third {
                    image = "$DEFAULT_IMAGE"
                }
                main {
                    image = "$DEFAULT_IMAGE"
                    command = ["hello", "world"]
                    dependsOn = [third]
                    repository = '$registryUrl/comfil-test/main:with-tag'
                    memLimit = 1000000000L
                    logConfig = 'json-file'
                    logOpts = ['max-size': '200k', 'max-file': '10']
                }
            }
            createComposeFile {
                version = '2'
                useAWSCompat = true
            }
        """

        when:
        def result = runTasksSuccessfully 'pushMainImage', 'createComposeFile'
        def composeFile = readNormalizedFile('build/docker-compose.yml')

        then:
        composeFile == """
            version: '2'
            services:
              main:
                image: $registryUrl/comfil-test/main@sha256:...
                links:
                - third
                command:
                - hello
                - world
                networks:
                  default:
                    aliases: []
                logging:
                  driver: json-file
                  options:
                    max-size: 200k
                    max-file: '10'
                mem_limit: 1000000000
              third:
                image: busybox@sha256:...
                networks:
                  default:
                    aliases: []
            networks:
              default:
                ipam:
                  config: []
            volumes: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'

    }

    def 'should fail when image has not been pushed'() {
        given:
        buildFile << """
            dcompose {
                other {
                    baseDir = file('src/main/docker')
                    dockerFilename = 'Dockerfile.custom'
                }
            }
        """

        file('src/main/docker/Dockerfile.custom').text = """
            FROM $DEFAULT_IMAGE
            CMD ["echo", "hello"]
        """.stripIndent()

        when:
        def result = runTasksWithFailure 'createComposeFile'

        then:
        result.standardOutput.contains "Cannot determine image digest for service 'other'"
    }

    def 'should succeed when using tags instead of digests'() {
        given:
        buildFile << """
            dcompose {
                other {
                    baseDir = file('src/main/docker')
                    dockerFilename = 'Dockerfile.custom'
                }
            }
            createComposeFile.useTags = true
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
            version: '3'
            services:
              other:
                image: dcompose.../other:latest
                networks:
                  default:
                    aliases: []
                deploy:
                  resources:
                    limits: {}
                    reservations: {}
            networks:
              default:
                ipam:
                  config: []
            volumes: {}
        """.stripIndent().trim()

        validateComposeFile 'build/docker-compose.yml'
    }

    private String readNormalizedFile(String s) {
        file(s).text
                .replaceAll(/sha256:[a-f0-9]{64}/, 'sha256:...')
                .replaceAll(/dcompose[a-f0-9]{8}/, 'dcompose...')
                .trim()
    }

    private void validateComposeFile(String f) {
        def file = file(f)
        def composeResult = "docker-compose -f $file.name config".execute([], file.parentFile)
        composeResult.errorStream.eachLine { println it }
        composeResult.waitFor()
        assert composeResult.exitValue() == 0
    }
}
