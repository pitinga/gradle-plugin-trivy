package io.github.pitinga.trivy

import org.gradle.api.Plugin
import org.gradle.api.Project

class TrivyPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        TrivyExtension settings = project.extensions.create('trivy', TrivyExtension)

        def install = project.tasks.register('trivyInstall', TrivyInstallTask) { task ->
            task.group = 'verification'
            task.description = 'Find or download a verified Trivy executable'
        }

        def scan = project.tasks.register('trivy', TrivyScanTask) { task ->
            task.group = 'verification'
            task.description = 'Scan the project directory (or a configured target) for vulnerabilities with Trivy'
            task.outputs.upToDateWhen { false } // vulnerability databases can change between runs
        }

        // All Project access and extension reads stay within Gradle's configuration phase.
        // Task actions consume only their explicitly configured properties.
        project.afterEvaluate {
            install.configure { task ->
                configureInstaller(task, project, settings)
            }

            scan.configure { task ->
                configureInstaller(task, project, settings)
                task.workingDirectory.set(project.layout.projectDirectory)
                task.scanMode.set(project.providers.gradleProperty('trivyScanMode').getOrElse(settings.scanMode))
                task.scanTarget.set(project.providers.gradleProperty('trivyTarget').getOrElse(settings.target))
                task.scanners.set(settings.scanners)
                task.severity.set(settings.severity)
                task.ignoreUnfixed.set(settings.ignoreUnfixed)
                task.failOnVulnerability.set(settings.failOnVulnerability)
                // Scanning "." must not implicitly build bootJar or jar.
                // For an explicit JAR target, users may run bootJar or jar first.
            }
        }
    }

    private static void configureInstaller(TrivyTaskBase task, Project project, TrivyExtension options) {
        task.trivyVersion.set(options.version)
        task.autoInstall.set(options.autoInstall)
        task.preferInstalled.set(options.preferInstalled)
        task.gradleHomeDirectory.set(project.gradle.gradleUserHomeDir)
        task.rootDirectory.set(project.rootProject.layout.projectDirectory)
    }
}
