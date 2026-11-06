package io.github.pitinga.trivy

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class TrivyPluginTest {
    @TempDir Path testProjectDir

    @Test
    void registersInstallAndScanTasksWithoutDownloading() {
        Path settings = testProjectDir.resolve('settings.gradle')
        Path build = testProjectDir.resolve('build.gradle')
        java.nio.file.Files.writeString(settings, "rootProject.name = 'test-app'\n")
        java.nio.file.Files.writeString(build, """plugins {
    id 'java'
    id 'io.github.pitinga.trivy'
}
""")
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all', '--offline', '--stacktrace')
            .build()

        assertTrue(result.output.contains('trivyInstall'))
        assertTrue(result.output.contains('trivy'))
    }
}
