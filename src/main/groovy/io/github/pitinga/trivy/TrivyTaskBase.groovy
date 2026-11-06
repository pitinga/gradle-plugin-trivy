package io.github.pitinga.trivy

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Provides Trivy configuration without accessing Project at task execution time.
 */
abstract class TrivyTaskBase extends DefaultTask {
    @Input
    abstract Property<String> getTrivyVersion()

    @Input
    abstract Property<Boolean> getAutoInstall()

    @Input
    abstract Property<Boolean> getPreferInstalled()

    @Internal
    abstract DirectoryProperty getGradleHomeDirectory()

    @Internal
    abstract DirectoryProperty getRootDirectory()

    protected File resolveExecutable() {
        // Task inputs are snapshots of the extension values configured by the build.
        TrivyExtension options = new TrivyExtension()
        options.version = trivyVersion.get()
        options.autoInstall = autoInstall.get()
        options.preferInstalled = preferInstalled.get()
        return new TrivyInstaller(gradleHomeDirectory.get().asFile, rootDirectory.get().asFile, logger, options).resolve()
    }
}
