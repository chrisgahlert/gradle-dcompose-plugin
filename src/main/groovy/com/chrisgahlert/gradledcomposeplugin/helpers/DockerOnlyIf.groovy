package com.chrisgahlert.gradledcomposeplugin.helpers

import java.lang.annotation.*

/**
 * Created by chris on 16.04.16.
 */

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@interface DockerOnlyIf {
}