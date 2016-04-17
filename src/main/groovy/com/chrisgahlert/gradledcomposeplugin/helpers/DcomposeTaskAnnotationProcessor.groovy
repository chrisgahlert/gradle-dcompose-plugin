package com.chrisgahlert.gradledcomposeplugin.helpers

import com.chrisgahlert.gradledcomposeplugin.tasks.AbstractDcomposeTask
import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Created by chris on 15.04.16.
 */
@CompileStatic
class DcomposeTaskAnnotationProcessor implements TaskExecutionListener {
    final private Configuration config

    DcomposeTaskAnnotationProcessor(Configuration config) {
        this.config = config
    }

    @Override
    void beforeExecute(Task task) {
        if (!(task instanceof AbstractDcomposeTask)) {
            return
        }

        addActions(task)
        addOnlyIf(task)
        addOutputs(task)
        addInputs(task)

        task.outputs.files.each {
            println "OUTPUT: $it.canonicalPath"
        }
    }

    private void addOnlyIf(Task task) {
        task.class.methods.each { Method method ->
            if (method.isAnnotationPresent(DockerOnlyIf)) {
                task.onlyIf {
                    boolean result = runInDockerClasspath {
                        method.invoke(task)
                    }

                    task.logger.warn("@DockerOnlyIf: Received $result from '${method.declaringClass.canonicalName}#$method.name'")
                    result
                }

            }
        }
    }

    void addActions(Task task) {
        task.class.methods.each { Method method ->
            if (method.isAnnotationPresent(DockerTaskAction)) {
                task.doLast {
                    runInDockerClasspath {
                        method.invoke(task)
                    }
                }
            }
        }
    }

    private void addOutputs(Task task) {
        def outputs = getFiles(task, DockerOutput)

        if (outputs.size() > 0) {
            def save = {
                runInDockerClasspath {
                    outputs.each { File file, Closure value ->
                        file.text = new JsonBuilder(value()).toPrettyString()
                    }
                }
            }

            task.doLast {
                save()
            }
            save()

            outputs.keySet().each {

                task.outputs.file(it)
            }
        }
    }

    private void addInputs(Task task) {
        def inputs = getFiles(task, DockerInput).values()

        if (inputs.size() > 0) {
            def values = new TreeSet()
            runInDockerClasspath {
                inputs.each { Closure value ->
                    values << new JsonBuilder(value()).toPrettyString()
                }
            }

            task.inputs.property("__dockerinputs__", values)
        }
    }

    private File getOutputFile(Field field, Task task) {
        new File(task.temporaryDir, "output-field-${field.name}.json")
    }

    private File getOutputFile(Method method, Task task) {
        new File(task.temporaryDir, "output-method-${method.name}.json")
    }

    private Map<File, Closure> getFiles(final Task task, final Class<? extends Annotation> markerAnnotation) {
        Map<File, Closure> result = new HashMap()

        def originalClassLoader = Thread.currentThread().contextClassLoader

        try {
            Thread.currentThread().contextClassLoader = task.class.classLoader
            task.class.methods.each { Method method ->
                if (method.isAnnotationPresent(markerAnnotation)) {
                    if (method.getParameterTypes().size() != 1 || method.getParameterTypes()[0] != Closure) {
                        throw new RuntimeException("Method ${method.declaringClass.canonicalName}#$method.name needs to have only a single Closure parameter")
                    }

                    result.put(getOutputFile(method, task), {
                        def methodResult = null
                        method.invoke(task, { methodResult = it })
                        methodResult
                    })
                }
            }

            task.class.fields.each { Field field ->
                if (field.isAnnotationPresent(markerAnnotation)) {
                    result.put(getOutputFile(field, task), { field.get(task) })
                }
            }
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }

        return result
    }

    private def runInDockerClasspath(Closure action) {
        def originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = createClassLoader()

            action.resolveStrategy = Closure.DELEGATE_FIRST
            return action()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }

        return null
    }

    private ClassLoader createClassLoader() {
        new URLClassLoader(toURLArray(config.files), ClassLoader.systemClassLoader.parent)
    }

    private URL[] toURLArray(Set<File> files) {
        files.collect { file -> file.toURI().toURL() } as URL[]
    }

    @Override
    void afterExecute(Task task, TaskState state) {
    }
}
