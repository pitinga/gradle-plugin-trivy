package io.github.pitinga.trivy

import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = 'Vulnerability results depend on continuously updated external databases')
abstract class TrivyScanTask extends TrivyTaskBase {
    @Inject
    abstract ExecOperations getExecOperations()

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract RegularFileProperty getArtifactFile()

    @Internal
    abstract DirectoryProperty getWorkingDirectory()

    @Input
    abstract Property<String> getScanMode()

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
        File jar = artifactFile.get().asFile
        if (!jar.isFile()) {
            throw new GradleException('JAR does not exist: ' + jar.absolutePath)
        }

        String mode = scanMode.get()
        if (!(mode in ['rootfs', 'fs'])) {
            throw new GradleException('trivy.scanMode must be rootfs or fs')
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
        arguments.add(jar.absolutePath)

        logger.lifecycle('Scanning artifact with Trivy: ' + jar.absolutePath)
        execOperations.exec { spec ->
            spec.executable = executable.absolutePath
            spec.args(arguments)
            spec.workingDir = workingDirectory.get().asFile
        }
    }
}
