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

import groovy.transform.TypeChecked
import org.gradle.util.GUtil

@TypeChecked
class AbstractEntity {
    final String name
    final String projectPath

    AbstractEntity(String name, String projectPath) {
        this.name = name
        this.projectPath = projectPath
    }

    String getNameCamelCase() {
        GUtil.toCamelCase(name)
    }

    String getNameDashed() {
        GUtil.toWords(name, '-' as char)
    }

    @Override
    boolean equals(Object obj) {
        if (obj == null || !(obj instanceof AbstractEntity)) {
            return false
        }

        def other = (AbstractEntity) obj
        return Objects.equals(this.name, other.name) && Objects.equals(this.projectPath, other.projectPath)
    }

    @Override
    int hashCode() {
        return Objects.hash(name, projectPath)
    }
}
