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
package com.chrisgahlert.gradledcomposeplugin.tasks

import com.chrisgahlert.gradledcomposeplugin.extension.Service
import groovy.transform.TypeChecked
import org.gradle.api.tasks.Internal

@TypeChecked
class AbstractDcomposeServiceTask extends AbstractDcomposeTask {

    private Service service

    @Internal
    void setService(Service service) {
        if (this.service != null) {
            throw new ReadOnlyPropertyException("service", this.class)
        }

        this.service = service
    }

    @Internal
    Service getService() {
        return service
    }

    @Deprecated
    @Internal
    Service getContainer() {
        logger.warn 'Deprecation warning: Please use the service property instead of the container property'
        service
    }

    @Internal
    protected Set<Service> getOtherServices() {
        new HashSet<>(allServices.findAll { it != service })
    }

}
