package io.github.pitinga.trivy

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.process.ExecOperations

abstract class TrivyScanTask extends DefaultTask {
    @Inject
    abstract ExecOperations getExecOperations()

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
        def archive = project.tasks.findByName('bootJar') ?: project.tasks.findByName('jar')
        if (!(archive instanceof AbstractArchiveTask)) {
            throw new GradleException('Neither bootJar nor jar was found. Apply the Java or Spring Boot plugin.')
        }

        File jar = archive.archiveFile.get().asFile
        if (!jar.isFile()) {
            throw new GradleException('JAR does not exist: ' + jar.absolutePath)
        }

        TrivyExtension config = project.extensions.getByType(TrivyExtension)
        if (!(config.scanMode in ['rootfs', 'fs'])) {
            throw new GradleException('trivy.scanMode must be rootfs or fs')
        }
        File executable = new TrivyInstaller(project, config).resolve()

        List<String> arguments = [
            config.scanMode,
            '--scanners', config.scanners,
            '--skip-version-check', '--no-progress',
            '--format', 'template', '--template', CONSOLE_TEMPLATE,
            '--exit-code', config.failOnVulnerability ? '1' : '0'
        ]
        if (config.severity?.trim()) arguments.addAll(['--severity', config.severity.trim()])
        if (config.ignoreUnfixed) arguments.add('--ignore-unfixed')
        arguments.add(jar.absolutePath)

        logger.lifecycle('Scanning artifact with Trivy: ' + jar.absolutePath)
        execOperations.exec { spec ->
            spec.executable = executable.absolutePath
            spec.args(arguments)
            spec.workingDir = project.projectDir
        }
    }
}
