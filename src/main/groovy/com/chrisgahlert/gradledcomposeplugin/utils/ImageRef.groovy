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
package com.chrisgahlert.gradledcomposeplugin.utils

import org.gradle.api.GradleException

class ImageRef implements Serializable {
    final String registry
    final String repository
    final String tag

    ImageRef(String registry, String repository, String tag) {
        this.registry = registry
        this.repository = repository
        this.tag = tag
    }

    static ImageRef parse(String image) {
        def parts = image.split('/')
        def registry, repositoryWithTag
        if (parts.length == 3) {
            registry = parts[0]
            repositoryWithTag = parts[1] + '/' + parts[2]
        } else if (parts.length == 2) {
            if (parts[0].contains(':') || parts[0].contains('.')) {
                registry = parts[0]
                repositoryWithTag = parts[1]
            } else {
                repositoryWithTag = image
            }
        } else if (parts.length == 1) {
            repositoryWithTag = image
        } else {
            throw new GradleException("Cannot parse image with name '$image'")
        }

        def repoParts = repositoryWithTag.split(':')
        def repository, tag
        if (repoParts.length < 1 || repoParts.length > 2) {
            throw new GradleException("Cannot parse tag from '$repositoryWithTag'")
        }
        repository = repoParts[0]
        if (repoParts.length > 1) {
            tag = repoParts[1]
        } else {
            tag = 'latest'
        }

        new ImageRef(registry, repository, tag)
    }

    @Override
    String toString() {
        "$registryWithRepository:$tag"
    }

    @Override
    boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ImageRef)) {
            return false
        }

        return Objects.equals(this.registry, obj.registry) &&
                Objects.equals(this.repository, obj.repository) &&
                Objects.equals(this.tag, obj.tag)
    }

    @Override
    int hashCode() {
        registry?.hashCode() + repository?.hashCode() + tag?.hashCode()
    }

    String getRegistryWithRepository() {
        (registry ? "$registry/" : '') + repository
    }
}
