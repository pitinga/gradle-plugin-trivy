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

        // Validate format and destination before launching/downloading anything.
        String chosenFormat = TrivyReportOutput.checkedFormat(reportFormat.get())
        File userReport = TrivyReportOutput.destination(baseDir, reportOutputFile.get())
        if (userReport != null && userReport.canonicalFile == targetFile.canonicalFile) {
            throw new GradleException('Trivy outputFile must not overwrite the scanned target: ' + targetFile.absolutePath)
        }

        File executable = resolveExecutable()
        logVersionHeader(executable)
        List<String> arguments = [
            mode, '--scanners', scanners.get(), '--skip-version-check', '--no-progress'
        ]
        arguments.addAll(TrivyReportOutput.formatArguments(chosenFormat))
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
        logger.lifecycle('Report format: ' + chosenFormat +
            (userReport == null ? ' (console)' : ' | ' + userReport.absolutePath))

        if (userReport != null) {
            // Preserve native Trivy UTF-8 reports exactly; no console conversion.
            ensureParentDirectory(userReport)
            Files.deleteIfExists(userReport.toPath())
            arguments.addAll(['--output', userReport.absolutePath, requestedTarget])
            int exitCode = executeTrivy(executable, arguments, baseDir)
            if (exitCode != 0) {
                throw new GradleException('Trivy scan exited with code ' + exitCode +
                    '; report destination: ' + userReport.absolutePath)
            }
            if (!userReport.isFile()) {
                throw new GradleException('Trivy did not create the report: ' + userReport.absolutePath)
            }
            logger.lifecycle('Report saved: ' + userReport.absolutePath)
            return
        }

        // Unlike Windows codepage changes, a safe ASCII console cannot break
        // Eclipse, Gradle Tooling API, PowerShell, cmd, or another user's terminal.
        // The Trivy report is still rendered by Trivy itself, not by a template.
        // Gradle's stdout transport may be UTF-8 while the Windows console is CP850.
        boolean windows = System.getProperty('os.name', '').toLowerCase(Locale.ROOT).contains('win')
        if (!windows) {
            // On platforms where UTF-8 is the usual console encoding, use exact
            // native table/json output without intermediary processing.
            arguments.add(requestedTarget)
            int exitCode = executeTrivy(executable, arguments, baseDir)
            if (exitCode != 0) {
                throw new GradleException('Trivy scan exited with code ' + exitCode)
            }
            return
        }

        // Windows: let Trivy render its native format to a temporary UTF-8 file.
        // Read it back and present a codepage-independent ASCII representation.
        // Only the *console* text is adapted; on-disk reports are always original.
        File temporary = Files.createTempFile('gradle-trivy-report-',
            chosenFormat == 'json' ? '.json' : '.txt').toFile()
        try {
            arguments.addAll(['--output', temporary.absolutePath, requestedTarget])
            int exitCode = executeTrivy(executable, arguments, baseDir)
            if (temporary.isFile()) {
                // Stream the native Trivy report line-by-line instead of holding a
                // potentially large JSON/table report in the Gradle daemon heap.
                Files.newBufferedReader(temporary.toPath(), java.nio.charset.StandardCharsets.UTF_8).withCloseable { reader ->
                    String line
                    while ((line = reader.readLine()) != null) {
                        logger.lifecycle(TrivyReportOutput.portableConsole(chosenFormat, line))
                    }
                }
            }
            if (exitCode != 0) {
                throw new GradleException('Trivy scan exited with code ' + exitCode)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    /**
     * Trivy writes INFO diagnostics to the process stderr stream. Passing the
     * stream straight through to Gradle paints these normal lines red in Eclipse.
     * Capture the diagnostics, decode UTF-8 and relay each line with its actual
     * severity. WARN/ERROR and unrecognized stderr are never hidden.
     *
     * This is called inside @TaskAction and does not access Project. The
     * process exit code remains authoritative, even when only INFO was logged.
     */
    private int executeTrivy(File executable, List<String> arguments, File directory) {
        ByteArrayOutputStream diagnosticBytes = new ByteArrayOutputStream()
        def result = execOperations.exec { spec ->
            spec.executable = executable.absolutePath
            spec.args(arguments)
            spec.workingDir = directory
            spec.errorOutput = diagnosticBytes
            spec.ignoreExitValue = true
        }
        TrivyReportOutput.fromUtf8(diagnosticBytes.toByteArray()).eachLine { line ->
            if (!line.trim()) return
            switch (TrivyReportOutput.diagnosticLevel(line)) {
                case 'INFO':
                    logger.lifecycle(line)
                    break
                case 'WARN':
                    logger.warn(line)
                    break
                default:
                    logger.error(line)
                    break
            }
        }
        return result.exitValue
    }

    private static void ensureParentDirectory(File file) {
        File parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new GradleException('Could not create Trivy output directory: ' + parent.absolutePath)
        }
    }
}
