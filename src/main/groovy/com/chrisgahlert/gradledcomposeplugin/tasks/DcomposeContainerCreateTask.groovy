package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.helpers.DockerInput
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerOutput
import com.chrisgahlert.gradledcomposeplugin.helpers.DockerTaskAction
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.util.GUtil

/**
 * Created by chris on 14.04.16.
 */
//@TypeChecked
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

    @Input
    @Optional
    String getCpushares() {
        container.cpushares
    }

    @Input
    @Optional
    Long getCpusetcpus() {
        container.cpusetcpus
    }

    @DockerTaskAction
    @TypeChecked(TypeCheckingMode.SKIP)
    void createNewContainer() {
        def oldVolumes = [:]
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            if(preserveVolumes) {
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

        if(command) {
            cmd.withCmd(command)
        }

        def volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
        if(volumes) {
            cmd.withVolumes(volumes.collect { volumeClass.newInstance(it) })
        }

        def allBinds = []
        def bindParser = loadClass('com.github.dockerjava.api.model.Bind').getMethod('parse', String)
        if(binds) {
            allBinds.addAll(binds.collect { bindParser.invoke(null, it) })
        }

        if(preserveVolumes) {
            def imageVolumes = client.inspectImageCmd(image).exec().config.volumes.keySet().collect {
                volumeClass.newInstance(it)
            }
            def knownVolumes = new HashSet(imageVolumes)
            knownVolumes.addAll(cmd.volumes)

            for(def volume : knownVolumes) {
                def bind = allBinds.find { volume == it.volume }

                if(!bind) {
                    if(oldVolumes.containsKey(volume)) {
                        allBinds << oldVolumes.get(volume) + ':' + volume.path
                    } else {
                        allBinds << containerName + '__' + GUtil.toLowerCamelCase(volume.path) + ':' + volume.path
                    }
                }
            }
        }

        if(allBinds) {
            cmd.withBinds(allBinds.collect { bindParser.invoke(null, it as String) })
        }

        def result = cmd.withName(containerName).exec()
        logger.error("Created new container with id $result.id ($containerName)")
    }

    @DockerOutput
    @TypeChecked(TypeCheckingMode.SKIP)
    void getContainerState(Closure callback) {
        ignoreException('com.github.dockerjava.api.exception.NotFoundException') {
            def result = client.inspectContainerCmd(containerName).exec()
            result.state = null
            result.hostsPath = null
            result.resolvConfPath = null
            result.hostnamePath = null
            result.networkSettings = null

            callback(result)
        }
    }

    @DockerInput
    @TypeChecked(TypeCheckingMode.SKIP)
    void getImageState(Closure callback) {
        def result = client.inspectImageCmd(image).exec()
        callback(result)
    }
}
