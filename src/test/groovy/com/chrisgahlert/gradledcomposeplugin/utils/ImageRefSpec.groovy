package com.chrisgahlert.gradledcomposeplugin.utils

import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.Unroll

class ImageRefSpec extends Specification {

    @Unroll
    def "should parse #image"() {
        when:
        def ref = ImageRef.parse(image)

        then:
        notThrown(GradleException)
        ref.registry == registry
        ref.repository == repository
        ref.tag == tag

        where:
        image                                || registry      | repository           | tag
        'nginx:latest'                       || null          | 'nginx'              | 'latest'
        'registry:2.7.1'                     || null          | 'registry'           | '2.7.1'
        "example.com/stuff/java:15"          || 'example.com' | 'stuff/java'         | '15'
        "example.com/proxy/stuff/server:3.4" || 'example.com' | 'proxy/stuff/server' | '3.4'
    }
}
