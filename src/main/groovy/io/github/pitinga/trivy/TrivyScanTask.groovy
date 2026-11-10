package io.github.pitinga.trivy

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.charset.StandardCharsets

@DisableCachingByDefault(because = 'Vulnerability results depend on continuously updated external databases')
abstract class TrivyScanTask extends TrivyTaskBase {
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
    abstract ListProperty<String> getSkipDirs()

    @Input
    abstract Property<String> getScanners()

    @Input
    abstract Property<String> getSeverity()

    @Input
    abstract Property<Boolean> getIgnoreUnfixed()

    @Input
    abstract Property<Boolean> getFailOnVulnerability()

    @Input
    abstract Property<String> getReportFormat()

    @Input
    abstract Property<String> getReportOutputFile()

    @Input
    abstract Property<String> getReportOutputStrategy()

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

    /**
     * Directory scans may include a locally cached Trivy binary, which is not part
     * of the application being scanned. Skip only caches physically inside the
     * requested directory, and keep user-configured glob expressions unchanged.
     * A normal single-JAR scan does not gain any implicit exclusions.
     */
    static List<String> effectiveSkipDirs(File target, File rootProjectDir,
                                           File gradleHomeDir, List<String> configured) {
        LinkedHashSet<String> excludes = new LinkedHashSet<>()
        (configured ?: []).each { String value ->
            if (value?.trim()) excludes.add(value.trim())
        }
        if (target.isDirectory()) {
            def targetRoot = target.canonicalFile.toPath()
            List<File> caches = [
                new File(rootProjectDir, '.gradle/trivy'),
                new File(gradleHomeDir, 'trivy')
            ]
            caches.each { File cache ->
                if (cache.isDirectory()) {
                    def cachePath = cache.canonicalFile.toPath()
                    if (cachePath.startsWith(targetRoot)) {
                        String relative = targetRoot.relativize(cachePath).toString().replace('\\', '/')
                        String path = './' + relative
                        if (!excludes.contains(relative) && !excludes.contains(path)) {
                            excludes.add(path)
                        }
                    }
                }
            }
        }
        new ArrayList<String>(excludes)
    }

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

        // Parse all options before downloading/running an external executable.
        String chosenFormat = TrivyReportOutput.checkedFormat(reportFormat.get())
        String strategy = TrivyReportOutput.checkedStrategy(reportOutputStrategy.get())
        File userReport = TrivyReportOutput.destination(baseDir, reportOutputFile.get())
        if (userReport != null && userReport.canonicalFile == targetFile.canonicalFile) {
            throw new GradleException('Trivy outputFile must not overwrite the scanned target: ' + targetFile.absolutePath)
        }

        File executable = resolveExecutable()
        logVersionHeader(executable)
        List<String> arguments = [
            mode,
            '--scanners', scanners.get(),
            '--skip-version-check', '--no-progress'
        ]
        arguments.addAll(TrivyReportOutput.formatArguments(chosenFormat, CONSOLE_TEMPLATE))
        arguments.addAll(['--exit-code', failOnVulnerability.get() ? '1' : '0'])
        if (severity.get().trim()) arguments.addAll(['--severity', severity.get().trim()])
        if (ignoreUnfixed.get()) arguments.add('--ignore-unfixed')

        List<String> excludedDirs = effectiveSkipDirs(targetFile,
            rootDirectory.get().asFile, gradleHomeDirectory.get().asFile, skipDirs.get())
        excludedDirs.each { pattern -> arguments.addAll(['--skip-dirs', pattern]) }

        if (!excludedDirs.isEmpty()) {
            logger.lifecycle('Skipping Trivy directories: ' + excludedDirs.join(', '))
        }
        logger.lifecycle((defaultArtifact ? 'Scanning artifact with Trivy: ' :
            'Scanning target with Trivy (' + mode + '): ') + targetFile.absolutePath)
        logger.lifecycle('Report output: ' + chosenFormat + ' / ' + strategy +
            (userReport == null ? ' / console' : ' / ' + userReport.absolutePath))

        // Important: the original custom/direct/no-file path is deliberately the
        // same as the published plugin. Alternative paths are experiments.
        if (strategy == 'direct') {
            if (userReport != null) {
                ensureParentDirectory(userReport)
                arguments.addAll(['--output', userReport.absolutePath])
            }
            arguments.add(requestedTarget)
            execOperations.exec { spec ->
                spec.executable = executable.absolutePath
                spec.args(arguments)
                spec.workingDir = baseDir
            }
            if (userReport != null) logger.lifecycle('Report saved: ' + userReport.absolutePath)
            return
        }

        File report = userReport
        boolean temporary = false
        if (strategy == 'file' && report == null) {
            report = Files.createTempFile('gradle-trivy-output-', chosenFormat == 'json' ? '.json' : '.txt').toFile()
            temporary = true
        }

        try {
            ByteArrayOutputStream captured = new ByteArrayOutputStream()
            if (strategy == 'file') {
                ensureParentDirectory(report)
                // Prevent stale output from being mistaken for this scan's report.
                Files.deleteIfExists(report.toPath())
                arguments.addAll(['--output', report.absolutePath])
            }
            arguments.add(requestedTarget)

            def result = execOperations.exec { spec ->
                spec.executable = executable.absolutePath
                spec.args(arguments)
                spec.workingDir = baseDir
                spec.ignoreExitValue = true
                if (strategy != 'file') spec.standardOutput = captured
            }

            byte[] bytes = strategy == 'file' ?
                (report.isFile() ? Files.readAllBytes(report.toPath()) : new byte[0]) :
                captured.toByteArray()

            if (strategy != 'file' && userReport != null) {
                TrivyReportOutput.saveExactBytes(userReport, bytes)
            }
            if (userReport != null && (strategy != 'file' || userReport.isFile())) {
                logger.lifecycle('Report saved: ' + userReport.absolutePath)
            }

            String text = TrivyReportOutput.fromUtf8(bytes)
            if (strategy == 'ascii' && chosenFormat == 'table') {
                text = TrivyReportOutput.toAsciiTable(text)
            }
            if (!text.isEmpty()) logger.lifecycle(text)

            if (result.exitValue != 0) {
                throw new GradleException('Trivy scan exited with code ' + result.exitValue +
                    (userReport != null ? '; report: ' + userReport.absolutePath : ''))
            }
            if (strategy == 'file' && !report.isFile()) {
                throw new GradleException('Trivy did not create the expected report: ' + report.absolutePath)
            }
        } finally {
            if (temporary && report != null) Files.deleteIfExists(report.toPath())
        }
    }

    private static void ensureParentDirectory(File file) {
        File parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new GradleException('Could not create Trivy output directory: ' + parent.absolutePath)
        }
    }
}
