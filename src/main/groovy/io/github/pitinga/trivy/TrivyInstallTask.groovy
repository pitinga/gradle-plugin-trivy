package io.github.pitinga.trivy

import org.gradle.api.tasks.TaskAction

abstract class TrivyInstallTask extends TrivyTaskBase {
    @TaskAction
    void install() {
        File executable = resolveExecutable()
        logger.lifecycle('Trivy executable: ' + executable.absolutePath)
    }
}
