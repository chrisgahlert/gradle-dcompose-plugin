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

    String dockerFilename

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
        tag ?: (dockerPrefix() + '/' + name).replace('_', '')
    }

    String getImage() {
        image ?: getTag()
    }

    boolean hasImage() {
        image
    }

    void validate() {
        if (!(baseDir == null ^ image == null)) {
            throw new GradleException("Either dockerFile or image must be provided for dcompose container '$name'")
        }

        if (baseDir == null) {
            if (dockerFilename != null) {
                throw new GradleException("Cannot set baseDir when image in use for dcompose container '$name'")
            }
            if (tag != null) {
                throw new GradleException("Cannot set tag when image in use for dcompose container '$name'")
            }
        }
    }

}
