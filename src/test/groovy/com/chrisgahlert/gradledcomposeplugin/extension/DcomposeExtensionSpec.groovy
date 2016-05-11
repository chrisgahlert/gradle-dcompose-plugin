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

import com.chrisgahlert.gradledcomposeplugin.AbstractDcomposeSpec

class DcomposeExtensionSpec extends AbstractDcomposeSpec {

    def 'should support custom client settings'() {
        given:
        buildFile << """
            dcompose {
              main {
                image = '$DEFAULT_IMAGE'
              }

              dockerClientConfig = {
                withDockerHost 'ftp://abc'
              }
            }
        """

        when:
        def result = runTasksWithFailure 'createMainContainer'

        then:
        result.standardError.contains 'Unsupported protocol scheme found: \'ftp://abc'
    }
}
