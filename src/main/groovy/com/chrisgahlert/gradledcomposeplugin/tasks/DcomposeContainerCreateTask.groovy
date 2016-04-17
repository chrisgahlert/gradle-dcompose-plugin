package com.chrisgahlert.gradledcomposeplugin.tasks

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GUtil

/**
 * Created by chris on 14.04.16.
 */
@CompileStatic
class DcomposeContainerCreateTask extends AbstractDcomposeTask {


    @Input
    String getImage() {
        container.image
    }

    @Input
    String getContainerName() {
        container.containerName
    }

    @Input
    @Optional
    List<String> getPortBindings() {
        container.portBindings
    }

    @Input
    @Optional
    List<String> getCommand() {
        container.command
    }

    @Input
    @Optional
    List<String> getBinds() {
        container.binds
    }

    @Input
    @Optional
    List<String> getVolumes() {
        container.volumes
    }

    @Input
    boolean isPreserveVolumes() {
        container.preserveVolumes
    }

    // TODO: add cpu/mem options

    @TaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNewContainer() {
        runInDockerClasspath {
            def oldVolumes = [:]
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                if (preserveVolumes) {
                    client.inspectContainerCmd(containerName).exec().mounts.each { mount ->
                        oldVolumes.put(mount.destination, mount.name)
                    }
                }

                client.removeContainerCmd(containerName)
                        .withForce(true)
                        .withRemoveVolumes(!preserveVolumes)
                        .exec()
                logger.quiet("Removed container $containerName")
            }

            def cmd = client.createContainerCmd(image)

            if (portBindings) {
                def portParser = loadClass('com.github.dockerjava.api.model.PortBinding').getMethod('parse', String)
                cmd.withPortBindings(portBindings.collect { portParser.invoke(null, it) })
            }

            if (command) {
                cmd.withCmd(command)
            }

            def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
            if (volumes) {
                cmd.withVolumes(volumes.collect { volumeClass.newInstance(it) })
            }

            def allBinds = []
            def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)
            if (binds) {
                allBinds.addAll(binds.collect { bindParser.invoke(null, it) })
            }

            if (preserveVolumes) {
                def imageVolumes = client.inspectImageCmd(image).exec().config.volumes.keySet().collect {
                    volumeClass.newInstance(it)
                }
                def knownVolumes = new HashSet(imageVolumes)
                knownVolumes.addAll(cmd.volumes)

                for (def volume : knownVolumes) {
                    def bind = allBinds.find { volume == it.volume }

                    if (!bind) {
                        if (oldVolumes.containsKey(volume)) {
                            allBinds << oldVolumes.get(volume) + ':' + volume.path
                        } else {
                            allBinds << containerName + '__' + GUtil.toLowerCamelCase(volume.path) + ':' + volume.path
                        }
                    }
                }
            }

            if (allBinds) {
                cmd.withBinds(allBinds.collect { bindParser.invoke(null, it as String) })
            }

            def result = cmd.withName(containerName).exec()
            logger.error("Created new container with id $result.id ($containerName)")
        }
    }

    @OutputFile
    @TypeChecked(TypeCheckingMode.SKIP)
    File getContainerState() {
        dockerOutput('container-state') {
            ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
                def result = client.inspectContainerCmd(containerName).exec()
                result.state = null
                result.hostsPath = null
                result.resolvConfPath = null
                result.hostnamePath = null
                result.networkSettings = null

                result
            }
        }
    }

    @Input
    @TypeChecked(TypeCheckingMode.SKIP)
    def getImageState() {
        runInDockerClasspath {
            def result = client.inspectImageCmd(image).exec()
            toJson(result)
        }
    }
}
