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
    void loadsPluginVersionFromItsOwnPackagedMetadata() {
        String version = TrivyTaskBase.pluginArtifactVersion()
        assertNotEquals('unknown', version, 'The plugin artifact version must be embedded into its resources')
        assertTrue(version ==~ /\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?/, 'Unexpected plugin version: ' + version)
    }

    @Test
    void extractsRealTrivyCliVersionFromStandardVersionOutput() {
        assertEquals('0.76.2', TrivyTaskBase.parseTrivyCliVersion('Version: 0.76.2\n'))
        assertEquals('0.76.2', TrivyTaskBase.parseTrivyCliVersion('warning\r\nVersion: 0.76.2\r\n'))
        assertEquals('unknown', TrivyTaskBase.parseTrivyCliVersion(''))
        assertEquals('unknown', TrivyTaskBase.parseTrivyCliVersion('invalid output'))
    }

    @Test
    void keepsOriginalCustomDirectConsoleDefaults() {
        writeProject("""
afterEvaluate {
    def scan = tasks.named('trivy').get()
    println 'LAB_FORMAT=' + scan.reportFormat.get()
    println 'LAB_STRATEGY=' + scan.reportOutputStrategy.get()
    println 'LAB_FILE=' + scan.reportOutputFile.get()
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('LAB_FORMAT=custom'))
        assertTrue(result.output.contains('LAB_STRATEGY=direct'))
        assertTrue(result.output.contains('LAB_FILE='))
        assertTrue(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void acceptsFileFormatAndStrategyThroughPowerShellCompatibleProperties() {
        writeProject("""
trivy {
    format = 'custom'
    outputStrategy = 'direct'
}
afterEvaluate {
    def scan = tasks.named('trivy').get()
    println 'LAB_FORMAT=' + scan.reportFormat.get()
    println 'LAB_STRATEGY=' + scan.reportOutputStrategy.get()
    println 'LAB_FILE=' + scan.reportOutputFile.get()
}
""")
        def result = runGradle('trivy', '--dry-run',
            '-PtrivyFormat=json', '-PtrivyOutput=build/reports/result.json',
            '-PtrivyOutputStrategy=capture')
        assertTrue(result.output.contains('LAB_FORMAT=json'))
        assertTrue(result.output.contains('LAB_STRATEGY=capture'))
        assertTrue(result.output.contains('LAB_FILE=build/reports/result.json'))
        assertTrue(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void registersInstallAndScanTasksWithoutDownloading() {
        writeProject()
        def result = runGradle('tasks', '--all')
        assertTrue(result.output.contains('trivyInstall'))
        assertTrue(result.output.contains('trivy'))
    }

    @Test
    void defaultsToRootfsOnBuiltJar() {
        writeProject("""
afterEvaluate {
    def scan = tasks.named('trivy').get()
    println 'TRIVY_MODE=' + scan.scanMode.get()
    println 'TRIVY_TARGET=' + scan.scanTarget.get()
    println 'TRIVY_ARTIFACT=' + scan.artifactFile.get().asFile.name
    println 'TRIVY_SKIP_DIRS=' + scan.skipDirs.get()
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('TRIVY_MODE=rootfs'))
        assertTrue(result.output.contains('TRIVY_TARGET='))
        assertTrue(result.output.contains('TRIVY_ARTIFACT=test-app.jar'))
        assertTrue(result.output.contains('TRIVY_SKIP_DIRS=[]'))
        assertTrue(result.output.contains(':jar SKIPPED'), 'Default scan must build the application JAR')
        assertTrue(result.output.contains(':trivy SKIPPED'))
    }

    @Test
    void explicitFsDotScansProjectWithoutBuildingJar() {
        writeProject("""
trivy {
    scanMode = 'fs'
    target = '.'
}
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
        assertFalse(result.output.contains(':jar SKIPPED'), 'Explicit filesystem scan must not build the JAR')
    }

    @Test
    void explicitRootfsDotDoesNotBuildJar() {
        writeProject("""
trivy {
    target = '.'
}
afterEvaluate {
    println 'TRIVY_MODE=' + tasks.named('trivy').get().scanMode.get()
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('TRIVY_MODE=rootfs'))
        assertFalse(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void acceptsExplicitJarTargetFromDslWithoutBuildingJar() {
        writeProject("""
trivy {
    target = 'build/libs/app.jar'
}
afterEvaluate {
    println 'TRIVY_TARGET=' + tasks.named('trivy').get().scanTarget.get()
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('TRIVY_TARGET=build/libs/app.jar'))
        assertFalse(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void commandLineOverridesModeAndTarget() {
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
        def result = runGradle('trivy', '--dry-run', '-PtrivyTarget=.', '-PtrivyScanMode=fs')
        assertTrue(result.output.contains('TRIVY_MODE=fs'))
        assertTrue(result.output.contains('TRIVY_TARGET=.'))
        assertFalse(result.output.contains('TRIVY_TARGET=build/libs/from-dsl.jar'))
        assertFalse(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void supportsDslTargetAndMultipleSkipDirs() {
        writeProject("""
trivy {
    target = '.'
    scanMode = 'rootfs'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
afterEvaluate {
    def task = tasks.named('trivy').get()
    println 'TRIVY_TARGET=' + task.scanTarget.get()
    println 'TRIVY_SKIP_DIRS=' + task.skipDirs.get().join('|')
}
""")
        def result = runGradle('trivy', '--dry-run')
        assertTrue(result.output.contains('TRIVY_TARGET=.'))
        assertTrue(result.output.contains('TRIVY_SKIP_DIRS=.gradle/trivy|build/tmp|**/node_modules'))
        assertFalse(result.output.contains(':jar SKIPPED'))
    }

    @Test
    void commandLineSkipDirsOverridesDslWithCommaSeparatedValues() {
        writeProject("""
trivy {
    target = '.'
    skipDirs = ['dont-use-me']
}
afterEvaluate {
    println 'TRIVY_SKIP_DIRS=' + tasks.named('trivy').get().skipDirs.get().join('|')
}
""")
        def result = runGradle('trivy', '--dry-run',
            '-PtrivySkipDirs=./.gradle/trivy, build/tmp, **/node_modules')
        assertTrue(result.output.contains('TRIVY_SKIP_DIRS=./.gradle/trivy|build/tmp|**/node_modules'))
        assertFalse(result.output.contains('dont-use-me'))
    }

    @Test
    void automaticSkipExcludesOnlyPluginCacheInsideDirectoryTarget() {
        Path root = testProjectDir.resolve('scan-root')
        Path localTrivy = root.resolve('.gradle/trivy')
        java.nio.file.Files.createDirectories(localTrivy)
        Path externalGradleHome = testProjectDir.resolve('external-gradle-home')
        java.nio.file.Files.createDirectories(externalGradleHome.resolve('trivy'))
        def result = TrivyScanTask.effectiveSkipDirs(
            root.toFile(), root.toFile(), externalGradleHome.toFile(), ['build/tmp'])
        assertEquals(['build/tmp', './.gradle/trivy'], result)
    }

    @Test
    void explicitSkipDirAvoidsDuplicateAutomaticExclusion() {
        Path root = testProjectDir.resolve('scan-root')
        java.nio.file.Files.createDirectories(root.resolve('.gradle/trivy'))
        Path externalGradleHome = testProjectDir.resolve('external-gradle-home')
        def result = TrivyScanTask.effectiveSkipDirs(
            root.toFile(), root.toFile(), externalGradleHome.toFile(), ['./.gradle/trivy', 'dist'])
        assertEquals(['./.gradle/trivy', 'dist'], result)
    }

    @Test
    void scanningSingleJarKeepsExactlyTheSelectedSkipDirs() {
        Path root = testProjectDir.resolve('scan-root')
        java.nio.file.Files.createDirectories(root.resolve('.gradle/trivy'))
        Path jar = root.resolve('app.jar')
        java.nio.file.Files.write(jar, new byte[] { 1, 2, 3 })
        def result = TrivyScanTask.effectiveSkipDirs(
            jar.toFile(), root.toFile(), testProjectDir.resolve('external-gradle-home').toFile(),
            ['build/tmp'])
        assertEquals(['build/tmp'], result)
    }

    @Test
    void directoryScanAlsoOmitsGradleUserHomeCacheIfItIsInsideTarget() {
        Path root = testProjectDir.resolve('scan-root')
        Path embeddedGradleHome = root.resolve('gradle-home')
        java.nio.file.Files.createDirectories(embeddedGradleHome.resolve('trivy'))
        def result = TrivyScanTask.effectiveSkipDirs(
            root.toFile(), root.toFile(), embeddedGradleHome.toFile(), [])
        assertEquals(['./gradle-home/trivy'], result)
    }
}
