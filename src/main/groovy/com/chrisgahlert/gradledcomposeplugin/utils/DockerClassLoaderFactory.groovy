package com.chrisgahlert.gradledcomposeplugin.utils

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.Configuration

/**
 * Created by chris on 19.04.16.
 */
@CompileStatic
class DockerClassLoaderFactory {

    final private Configuration dockerConfiguration

    private ClassLoader instance

    DockerClassLoaderFactory(Configuration dockerConfiguration) {
        this.dockerConfiguration = dockerConfiguration
    }

    synchronized ClassLoader getDefaultInstance() {
        if (instance == null) {
            instance = createClassLoader()
        }

        instance
    }

    URLClassLoader createClassLoader() {
        new URLClassLoader(toURLArray(dockerConfiguration.files), ClassLoader.systemClassLoader.parent)
    }

    private URL[] toURLArray(Set<File> files) {
        files.collect { file -> file.toURI().toURL() } as URL[]
    }

}
