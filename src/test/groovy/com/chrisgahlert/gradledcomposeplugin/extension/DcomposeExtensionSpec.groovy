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
