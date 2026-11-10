package io.github.pitinga.trivy

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.Normalizer

/**
 * The reports themselves always come from Trivy (--format table/json).
 * Console-safe rendering is a presentation-only operation and never changes
 * a report file or the Trivy vulnerability result set.
 */
class TrivyReportOutput {
    static final List<String> FORMATS = ['table', 'json'].asImmutable()

    static String checkedFormat(String format) {
        String value = (format ?: '').trim().toLowerCase(Locale.ROOT)
        if (!FORMATS.contains(value)) {
            throw new GradleException('trivy.format must be table or json; received: ' + format)
        }
        return value
    }

    static List<String> formatArguments(String format) {
        return ['--format', checkedFormat(format)]
    }

    static File destination(File projectDirectory, String requested) {
        String path = (requested ?: '').trim()
        if (!path) return null
        // A Trivy output plugin is not a local filename.
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
     * Gradle's Windows client can send UTF-8 bytes to a CP850 console, corrupting
     * Unicode box drawing even when a Gradle plugin already decoded UTF-8.
     * We use Trivy's actual table and modify *only its console presentation*.
     * No template is involved. A report written to disk remains native UTF-8.
     *
     * All output code points are rendered as ASCII, including titles: this
     * makes presentation reliable for CP437, CP850, Windows Terminal, and IDEs.
     * Unrepresentable letters become '?' on the console, never in report files.
     */
    static String portableTable(String value) {
        if (value == null) return ''
        StringBuilder borderMapped = new StringBuilder(value.length())
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset)
            offset += Character.charCount(cp)
            if (cp >= 0x2500 && cp <= 0x257F) {
                if (cp in [0x2500, 0x2501, 0x2504, 0x2505, 0x2508, 0x2509, 0x250C, 0x250D, 0x2550]) {
                    borderMapped.append('-')
                } else if (cp in [0x2502, 0x2503, 0x2506, 0x2507, 0x250A, 0x250B, 0x250E, 0x250F, 0x2551]) {
                    borderMapped.append('|')
                } else {
                    borderMapped.append('+')
                }
            } else if (cp == 0x2192) {
                borderMapped.append('->')
            } else if (cp == 0x2022) {
                borderMapped.append('*')
            } else if (cp in [0x2013, 0x2014, 0x2212]) {
                borderMapped.append('-')
            } else if (cp == 0x00A0) {
                borderMapped.append(' ')
            } else {
                borderMapped.appendCodePoint(cp)
            }
        }

        String decomposed = Normalizer.normalize(borderMapped.toString(), Normalizer.Form.NFKD)
        StringBuilder ascii = new StringBuilder(decomposed.length())
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i)
            int kind = Character.getType(c)
            if (kind == Character.NON_SPACING_MARK || kind == Character.COMBINING_SPACING_MARK) continue
            ascii.append(c <= 0x7F ? c : '?')
        }
        return ascii.toString()
    }

    /**
     * A JSON console is also safe on legacy Windows code pages. Replacing raw
     * non-ASCII characters with their JSON \uXXXX escapes retains JSON values.
     * The on-disk JSON file is always written natively by Trivy.
     */
    static String portableJson(String value) {
        if (value == null) return ''
        StringBuilder result = new StringBuilder(value.length())
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i)
            if (c <= 0x7F) {
                result.append(c)
            } else {
                result.append('\\u').append(Integer.toHexString((int) c).padLeft(4, '0'))
            }
        }
        return result.toString()
    }

    static String portableConsole(String format, String text) {
        switch (checkedFormat(format)) {
            case 'table': return portableTable(text)
            case 'json': return portableJson(text)
            default: throw new IllegalStateException('Unexpected Trivy format')
        }
    }
}
