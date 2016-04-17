package com.chrisgahlert.gradledcomposeplugin.helpers

import java.lang.annotation.*

/**
 * Created by chris on 15.04.16.
 */

@Target([ElementType.FIELD, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@interface DockerInput {
}