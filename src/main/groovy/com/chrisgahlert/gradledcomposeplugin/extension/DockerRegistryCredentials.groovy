package com.chrisgahlert.gradledcomposeplugin.extension

import groovy.transform.CompileStatic
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class DockerRegistryCredentials {
    public static final String DEFAULT_URL = 'https://index.docker.io/v1/'

    @Input
    String url = DEFAULT_URL

    @Input
    @Optional
    String username

    @Input
    @Optional
    String password

    @Input
    @Optional
    String email

}
