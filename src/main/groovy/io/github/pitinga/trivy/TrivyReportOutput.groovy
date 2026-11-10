package io.github.pitinga.trivy

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Small, deterministic output helpers used by the experimental reporting modes.
 * This class never accesses Gradle Project, so it is safe at task execution time.
 */
class TrivyReportOutput {
    static final List<String> FORMATS = ['custom', 'table', 'json'].asImmutable()
    static final List<String> STRATEGIES = ['direct', 'capture', 'file', 'ascii'].asImmutable()

    static String checkedFormat(String format) {
        String value = (format ?: '').trim().toLowerCase(Locale.ROOT)
        if (!FORMATS.contains(value)) {
            throw new GradleException('trivy.format must be custom, table or json; received: ' + format)
        }
        return value
    }

    static String checkedStrategy(String strategy) {
        String value = (strategy ?: '').trim().toLowerCase(Locale.ROOT)
        if (!STRATEGIES.contains(value)) {
            throw new GradleException('trivy.outputStrategy must be direct, capture, file or ascii; received: ' + strategy)
        }
        return value
    }

    static List<String> formatArguments(String format, String originalTemplate) {
        switch (checkedFormat(format)) {
            case 'custom':
                return ['--format', 'template', '--template', originalTemplate]
            case 'table':
                return ['--format', 'table']
            case 'json':
                return ['--format', 'json']
            default:
                throw new IllegalStateException('Unreachable output format')
        }
    }

    static File destination(File projectDirectory, String requested) {
        String path = (requested ?: '').trim()
        if (!path) return null
        // "plugin=..." would invoke a Trivy output plugin rather than writing a report.
        if (path.startsWith('plugin=')) {
            throw new GradleException('trivy.outputFile must be a local filename, not a Trivy output plugin')
        }
        File result = new File(path)
        return result.isAbsolute() ? result : new File(projectDirectory, path)
    }

    static String fromUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8)
    }

    static void saveExactBytes(File destination, byte[] bytes) {
        File parent = destination.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new GradleException('Cannot create Trivy report directory: ' + parent.absolutePath)
        }
        Files.write(destination.toPath(), bytes)
    }

    /**
     * A deliberately lossy console-only option to isolate Windows/Eclipse UTF-8
     * problems. The original report bytes are never altered when saved to disk.
     */
    static String toAsciiTable(String value) {
        if (value == null) return ''
        return value
            .replaceAll('[│┃║]', '|')
            .replaceAll('[─━═]', '-')
            .replaceAll('[┌┬┐├┼┤└┴┘╭╮╯╰┏┳┓┣╋┫┗┻┛╔╦╗╠╬╣╚╩╝]', '+')
            .replace('→', '->')
            .replace('•', '*')
    }
}
