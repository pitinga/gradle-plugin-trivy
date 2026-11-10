package io.github.pitinga.trivy

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Installs an external executable into a mutable tool cache')
abstract class TrivyInstallTask extends TrivyTaskBase {
    @TaskAction
    void install() {
        File executable = resolveExecutable()
        logVersionHeader(executable)
        logger.lifecycle('Trivy executable: ' + executable.absolutePath)
    }
}
