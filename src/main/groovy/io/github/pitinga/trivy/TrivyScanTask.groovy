package io.github.pitinga.trivy

import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Vulnerability results depend on continuously updated external databases')
abstract class TrivyScanTask extends TrivyTaskBase {
    @Inject
    abstract ExecOperations getExecOperations()

    // Optional only for explicit target scans; by default this is bootJar/jar.
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getArtifactFile()

    @Internal
    abstract DirectoryProperty getWorkingDirectory()

    @Input
    abstract Property<String> getScanMode()

    @Input
    abstract Property<String> getScanTarget()

    @Input
    abstract Property<String> getScanners()

    @Input
    abstract Property<String> getSeverity()

    @Input
    abstract Property<Boolean> getIgnoreUnfixed()

    @Input
    abstract Property<Boolean> getFailOnVulnerability()

    // ASCII-only output avoids Unicode box-drawing corruption in Eclipse on Windows.
    private static final String CONSOLE_TEMPLATE = '''
{{ range . -}}
{{ if .Vulnerabilities -}}
Target: {{ .Target }}
Type: {{ .Type }}

{{ range .Vulnerabilities -}}
- [{{ .Severity }}] {{ .VulnerabilityID }} | {{ .PkgName }} {{ .InstalledVersion }}
  {{ .Title }}
  Fixed version: {{ if .FixedVersion }}{{ .FixedVersion }}{{ else }}not available{{ end }}

{{ end -}}
{{ else -}}
Target: {{ .Target }}
Status: CLEAN - no vulnerabilities found

{{ end -}}
{{ end -}}
'''

    @TaskAction
    void scan() {
        String mode = scanMode.get()
        if (!(mode in ['rootfs', 'fs'])) {
            throw new GradleException('trivy.scanMode must be rootfs or fs')
        }

        String requestedTarget = scanTarget.get()
        boolean defaultArtifact = !requestedTarget?.trim()
        File baseDir = workingDirectory.get().asFile
        if (defaultArtifact) {
            File archive = artifactFile.orNull?.asFile
            if (archive == null) {
                throw new GradleException('No bootJar/jar task found. Apply a Java plugin, or configure trivy.target.')
            }
            requestedTarget = archive.absolutePath
        }

        File targetFile = new File(requestedTarget)
        if (!targetFile.isAbsolute()) {
            targetFile = new File(baseDir, requestedTarget)
        }
        if (!targetFile.exists()) {
            throw new GradleException('Trivy scan target does not exist: ' + targetFile.absolutePath +
                '. Build the artifact first if you specified a JAR.')
        }
        if (!targetFile.isFile() && !targetFile.isDirectory()) {
            throw new GradleException('Trivy scan target is not a regular file or directory: ' + targetFile.absolutePath)
        }
        if (mode == 'fs' && targetFile.isFile() && targetFile.name.toLowerCase(Locale.ROOT).endsWith('.jar')) {
            logger.warn('Trivy fs may not detect vulnerabilities inside compiled JARs. ' +
                'Use trivy rootfs for a packaged JAR, or fs for project source/dependency metadata.')
        }

        File executable = resolveExecutable()
        List<String> arguments = [
            mode,
            '--scanners', scanners.get(),
            '--skip-version-check', '--no-progress',
            '--format', 'template', '--template', CONSOLE_TEMPLATE,
            '--exit-code', failOnVulnerability.get() ? '1' : '0'
        ]
        if (severity.get().trim()) arguments.addAll(['--severity', severity.get().trim()])
        if (ignoreUnfixed.get()) arguments.add('--ignore-unfixed')
        // For explicit targets, keep relative paths relative to the Gradle project directory.
        arguments.add(requestedTarget)

        logger.lifecycle((defaultArtifact ? 'Scanning artifact with Trivy: ' : 'Scanning target with Trivy (' + mode + '): ') + targetFile.absolutePath)
        execOperations.exec { spec ->
            spec.executable = executable.absolutePath
            spec.args(arguments)
            spec.workingDir = baseDir
        }
    }
}
