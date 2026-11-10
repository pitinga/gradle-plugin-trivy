package io.github.pitinga.trivy

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class TrivyReportOutputTest {
    @TempDir Path tempDir

    @Test
    void supportsOriginalTemplateTableAndJsonWithoutChangingDefaultTemplate() {
        String template = '{{ range . }}{{ .Target }}{{ end }}'
        assertEquals(['--format', 'template', '--template', template],
            TrivyReportOutput.formatArguments('custom', template))
        assertEquals(['--format', 'table'],
            TrivyReportOutput.formatArguments('table', template))
        assertEquals(['--format', 'json'],
            TrivyReportOutput.formatArguments('json', template))
    }

    @Test
    void supportsFourIndependentPresentationStrategies() {
        assertEquals(['direct', 'capture', 'file', 'ascii'], TrivyReportOutput.STRATEGIES)
        assertEquals('file', TrivyReportOutput.checkedStrategy(' FILE '))
        assertEquals('table', TrivyReportOutput.checkedFormat('TABLE'))
        assertThrows(GradleException) { TrivyReportOutput.checkedFormat('raw') }
        assertThrows(GradleException) { TrivyReportOutput.checkedStrategy('magic') }
    }

    @Test
    void asciiRenderingChangesOnlyPresentationAndNotSavedUtf8Bytes() {
        String unicode = '┌──┬────┐\n│ ID │ CVE │\n├──┼────┤\n└──┴────┘\n'
        byte[] raw = unicode.getBytes('UTF-8')
        File file = tempDir.resolve('report-table.txt').toFile()
        TrivyReportOutput.saveExactBytes(file, raw)

        assertArrayEquals(raw, Files.readAllBytes(file.toPath()))
        String ascii = TrivyReportOutput.toAsciiTable(TrivyReportOutput.fromUtf8(raw))
        assertTrue(ascii.contains('+--+----+'))
        assertTrue(ascii.contains('| ID | CVE |'))
        assertFalse(ascii.contains('┌'))
        assertFalse(ascii.contains('│'))
    }

    @Test
    void outputPathsAreRelativeToProjectOrAbsolute() {
        File root = tempDir.toFile()
        assertNull(TrivyReportOutput.destination(root, ''))
        assertEquals(new File(root, 'build/reports/report.json'),
            TrivyReportOutput.destination(root, 'build/reports/report.json'))
        assertEquals(root, TrivyReportOutput.destination(root, root.absolutePath))
        assertThrows(GradleException) {
            TrivyReportOutput.destination(root, 'plugin=evil')
        }
    }

    @Test
    void storesArbitraryJsonExactlyWithoutEncodingConversion() {
        byte[] expected = '{"schema":"CVE-2026-63337","note":"ação"}'.getBytes('UTF-8')
        File report = tempDir.resolve('nested/scan.json').toFile()
        TrivyReportOutput.saveExactBytes(report, expected)
        assertArrayEquals(expected, Files.readAllBytes(report.toPath()))
    }
}
