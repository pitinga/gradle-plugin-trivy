package io.github.pitinga.trivy

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class TrivyPluginTest {
    @TempDir Path testProjectDir

    private void writeProject(String extraConfiguration = '') {
        java.nio.file.Files.writeString(testProjectDir.resolve('settings.gradle'), "rootProject.name = 'test-app'\n")
        java.nio.file.Files.writeString(testProjectDir.resolve('build.gradle'), """plugins {
    id 'java'
    id 'io.github.pitinga.trivy'
}
${extraConfiguration}
""")
    }

    private def runGradle(String... arguments) {
        GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments(*(arguments.toList() + ['--offline', '--stacktrace']))
            .build()
    }

    @Test
    void registersInstallAndScanTasksWithoutDownloading() {
        writeProject()
        def result = runGradle('tasks', '--all')
        assertTrue(result.output.contains('trivyInstall'))
        assertTrue(result.output.contains('trivy'))
    }

    @Test
    void scansProjectFilesystemByDefaultWithoutBuildingJar() {
        writeProject("""
afterEvaluate {
    def scan = tasks.named('trivy').get()
    println 'TRIVY_MODE=' + scan.scanMode.get()
    println 'TRIVY_TARGET=' + scan.scanTarget.get()
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('TRIVY_MODE=fs'))
        assertTrue(result.output.contains('TRIVY_TARGET=.'))
        assertTrue(result.output.contains(':trivy SKIPPED'))
        assertFalse(result.output.contains(':jar SKIPPED'), 'A filesystem scan must not build the JAR')
    }

    @Test
    void acceptsExplicitJarTargetFromDsl() {
        writeProject("""
trivy {
    target = 'build/libs/app.jar'
}
afterEvaluate {
    println 'TRIVY_TARGET=' + tasks.named('trivy').get().scanTarget.get()
}
""")
        def result = runGradle('help')
        assertTrue(result.output.contains('TRIVY_TARGET=build/libs/app.jar'))
    }

    @Test
    void commandLineTargetOverridesDslTarget() {
        writeProject("""
trivy {
    target = 'build/libs/from-dsl.jar'
}
afterEvaluate {
    def scan = tasks.named('trivy').get()
    println 'TRIVY_MODE=' + scan.scanMode.get()
    println 'TRIVY_TARGET=' + scan.scanTarget.get()
}
""")
        def result = runGradle('help', '-PtrivyTarget=build/libs/from-cli.jar')
        assertTrue(result.output.contains('TRIVY_MODE=fs'))
        assertTrue(result.output.contains('TRIVY_TARGET=build/libs/from-cli.jar'))
        assertFalse(result.output.contains('TRIVY_TARGET=build/libs/from-dsl.jar'))
    }
}
