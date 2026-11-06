package io.github.pitinga.trivy

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class TrivyPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        TrivyExtension settings = project.extensions.create('trivy', TrivyExtension)

        project.tasks.register('trivyInstall') { task ->
            task.group = 'verification'
            task.description = 'Find or download a verified Trivy executable'
            task.doLast {
                File executable = new TrivyInstaller(project, settings).resolve()
                project.logger.lifecycle('Trivy executable: ' + executable.absolutePath)
            }
        }

        def scan = project.tasks.register('trivy', TrivyScanTask) { task ->
            task.group = 'verification'
            task.description = 'Build the JAR and scan it for vulnerabilities with Trivy'
            task.outputs.upToDateWhen { false } // vulnerability databases can change between runs
        }

        project.afterEvaluate {
            def archive = project.tasks.findByName('bootJar') ?: project.tasks.findByName('jar')
            if (archive instanceof AbstractArchiveTask) {
                scan.configure { task -> task.dependsOn(archive) }
            }
        }
    }
}
