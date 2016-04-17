package com.chrisgahlert.gradledcomposeplugin.extension

import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.util.GUtil

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class Container {
    final String name
    final Closure<String> dockerPrefix

    private String image

    List<String> portBindings

    boolean waitForCommand

    int waitTimeout

    List<String> command

    List<String> binds

    List<String> volumes

    private String tag

    File dockerFile

    File baseDir

    Long memory

    Long memswap

    String cpushares

    String cpusetcpus

    Map<String, String> buildArgs

    boolean forceRemoveImage

    boolean noPruneParentImages

    boolean preserveVolumes

    boolean buildNoCache = false

    boolean buildRemove = true

    boolean buildPull = false

    Container(String name, Closure<String> dockerPrefix) {
        this.name = name
        this.dockerPrefix = dockerPrefix
    }

    String getContainerName() {
        dockerPrefix() + name
    }


    def methodMissing(String name, def args) {
        throw new MissingMethodException(name, Container, args as Object[])
    }

    def propertyMissing(String name) {
        throw new MissingPropertyException(name, Container)
    }

    String getPullTaskName() {
        "pull${taskLabel}Image"
    }

    String getCreateTaskName() {
        "create${taskLabel}Container"
    }

    String getStartTaskName() {
        "start${taskLabel}Container"
    }

    String getStopTaskName() {
        "stop${taskLabel}Container"
    }

    String getRemoveContainerTaskName() {
        "remove${taskLabel}Container"
    }

    String getRemoveImageTaskName() {
        "remove${taskLabel}Image"
    }

    String getBuildTaskName() {
        "build${taskLabel}Image"
    }

    private String getTaskLabel() {
        GUtil.toCamelCase(name)
    }

    String getTag() {
        tag ?: "dcompose/" + (containerName.startsWith('dcompose_') ? containerName.substring(9) : containerName).replace('_', '')
    }

    String getImage() {
        image ?: getTag()
    }

    boolean hasImage() {
        image
    }

    void validate() {
        if (!(dockerFile == null ^ image == null)) {
            throw new GradleException("Either dockerFile or image must be provided for dcompose container '$name'")
        }

        if (dockerFile == null) {
            if (baseDir != null) {
                throw new GradleException("Cannot set baseDir when image in use for dcompose container '$name'")
            }
            if (tag != null) {
                throw new GradleException("Cannot set tag when image in use for dcompose container '$name'")
            }
        }
    }

}
