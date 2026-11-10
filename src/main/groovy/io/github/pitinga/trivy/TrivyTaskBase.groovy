package io.github.pitinga.trivy

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import org.gradle.util.GradleVersion
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Provides Trivy configuration without accessing Project at task execution time.
 */
@DisableCachingByDefault(because = 'Trivy tasks resolve external tools and do not declare cacheable output artifacts')
abstract class TrivyTaskBase extends DefaultTask {
    @Inject
    abstract ExecOperations getExecOperations()

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

    /**
     * Version is embedded into the distributed plugin artifact during processResources.
     * It must not be inferred from the consuming project's version or the Trivy CLI.
     */
    static String pluginArtifactVersion() {
        InputStream input = TrivyTaskBase.class.getResourceAsStream('plugin.properties')
        if (input == null) return 'unknown'
        Properties metadata = new Properties()
        input.withCloseable { metadata.load(it) }
        String version = metadata.getProperty('version', '').trim()
        return version ?: 'unknown'
    }

    static String parseTrivyCliVersion(String output) {
        def matcher = (output ?: '') =~ /(?m)^\s*Version:\s*(\S+)/
        return matcher.find() ? matcher.group(1) : 'unknown'
    }

    /**
     * Query the actual resolved executable, which may differ from trivy.version
     * when preferInstalled=true selects a binary from PATH.
     * Version reporting is informational and never hides a scan error.
     */
    protected String detectedTrivyCliVersion(File executable) {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        ByteArrayOutputStream standardError = new ByteArrayOutputStream()
        try {
            def result = execOperations.exec { spec ->
                spec.executable = executable.absolutePath
                spec.args('--version')
                spec.standardOutput = standardOutput
                spec.errorOutput = standardError
                spec.ignoreExitValue = true
            }
            if (result.exitValue != 0) {
                logger.warn('Unable to determine Trivy CLI version: --version exited with code ' + result.exitValue)
                return 'unknown'
            }
            return parseTrivyCliVersion(standardOutput.toString('UTF-8'))
        } catch (Exception ex) {
            logger.warn('Unable to determine Trivy CLI version: ' + ex.message)
            return 'unknown'
        }
    }

    protected void logVersionHeader(File executable) {
        String line = '=================================================='
        logger.lifecycle(line)
        logger.lifecycle('Gradle Trivy Plugin : ' + pluginArtifactVersion())
        logger.lifecycle('Trivy CLI           : ' + detectedTrivyCliVersion(executable))
        logger.lifecycle('Gradle              : ' + GradleVersion.current().version)
        logger.lifecycle('Java                : ' + System.getProperty('java.version', 'unknown'))
        logger.lifecycle(line)
    }
}
