package com.chrisgahlert.gradledcomposeplugin

import spock.lang.IgnoreRest
import spock.lang.Unroll

/**
 * Created by chris on 18.04.16.
 */
class DcomposeTaskGraphSpec extends AbstractDcomposeSpec {

    @Unroll
    def 'should create the #allTask task'() {
        given:
        buildFile << """
            dcompose {
                db {
                    image = '$DEFAULT_IMAGE'
                    command = ['echo']
                }
                web {
                    baseDir = file('docker')
                    command = ['echo']
                }
            }
        """

        when:
        def result = runTasksSuccessfully allTask, '--dry-run'

        then:
        if (dbTask != null) {
            assert result.wasSkipped(dbTask)
        }
        if (webTask != null) {
            assert result.wasSkipped(webTask)
        }

        where:
        allTask            || dbTask              || webTask
        'createContainers' || 'createDbContainer' || 'createWebContainer'
        'removeContainers' || 'removeDbContainer' || 'removeWebContainer'
        'startContainers'  || 'startDbContainer'  || 'startWebContainer'
        'stopContainers'   || 'stopDbContainer'   || 'stopWebContainer'
        'buildImages'      || null                || 'buildWebImage'
        'pullImages'       || 'pullDbImage'       || null
        'removeImages'     || 'removeDbImage'     || 'removeWebImage'
    }

    @Unroll
    @IgnoreRest
    def 'should support using the #taskNameProperty property as a dependency for pull containers'() {
        given:
        buildFile << """
            $DEFAULT_BUILD_FILE

            task myTask {
                dependsOn dcompose.main.$taskNameProperty
            }
        """

        when:
        def result = runTasksSuccessfully 'myTask', '--dry-run'

        then:
        result.wasSkipped(":$taskName")

        where:
        taskNameProperty          || taskName
        'pullTaskName'            || 'pullMainImage'
        'createTaskName'          || 'createMainContainer'
        'startTaskName'           || 'startMainContainer'
        'stopTaskName'            || 'stopMainContainer'
        'removeContainerTaskName' || 'removeMainContainer'
        'removeImageTaskName'     || 'removeMainImage'
    }

    @Unroll
    @IgnoreRest
    def 'should support using the #taskNameProperty property as a dependency for build containers'() {
        given:
        buildFile << """
            dcompose {
                main {
                    baseDir = file('docker')
                }
            }

            task myTask {
                dependsOn dcompose.main.$taskNameProperty
            }
        """

        when:
        def result = runTasksSuccessfully 'myTask', '--dry-run'

        then:
        result.wasSkipped(":$taskName")

        where:
        taskNameProperty          || taskName
        'createTaskName'          || 'createMainContainer'
        'startTaskName'           || 'startMainContainer'
        'stopTaskName'            || 'stopMainContainer'
        'removeContainerTaskName' || 'removeMainContainer'
        'removeImageTaskName'     || 'removeMainImage'
        'buildTaskName'           || 'buildMainImage'
    }

}
