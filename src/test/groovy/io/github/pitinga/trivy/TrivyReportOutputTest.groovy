package io.github.pitinga.trivy

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import groovy.json.JsonSlurper

import java.nio.file.Files
import java.nio.file.Path
import static org.junit.jupiter.api.Assertions.*

class TrivyReportOutputTest {
    @TempDir Path tempDir

    @Test
    void defaultsToNativeTrivyFormatsOnly() {
        assertEquals(['table', 'json'], TrivyReportOutput.FORMATS)
        assertEquals(['--format', 'table'], TrivyReportOutput.formatArguments('TABLE'))
        assertEquals(['--format', 'json'], TrivyReportOutput.formatArguments('json'))
        assertThrows(GradleException) { TrivyReportOutput.formatArguments('custom') }
        assertThrows(GradleException) { TrivyReportOutput.formatArguments('template') }
    }

    @Test
    void adaptsNativeTableWithoutChangingCveIdsOrNativeFiles() {
        String unicode = '┌─────┬──────────┐\n│ Ação │ CVE-2026-63337 │\n├─────┼──────────┤\n└─────┴──────────┘\n'
        byte[] original = unicode.getBytes('UTF-8')
        File report = tempDir.resolve('native-table.txt').toFile()
        TrivyReportOutput.saveExactBytes(report, original)
        assertArrayEquals(original, Files.readAllBytes(report.toPath()))

        String shown = TrivyReportOutput.portableConsole('table', unicode)
        assertTrue(shown.contains('CVE-2026-63337'))
        assertTrue(shown.contains('Acao'))
        assertTrue(shown.contains('+-----+----------+'))
        assertTrue(shown.contains('| Acao |'))
        assertTrue(shown.chars().allMatch { it < 128 }, 'Console must use only ASCII')
        assertFalse(shown.contains('┌'))
        assertTrue(Files.readString(report.toPath()).contains('┌'))
    }

    @Test
    void jsonConsoleEscapesUnicodeWithoutChangingJsonSemantics() {
        String original = '{"name":"ação","city":"東京","CVE":"CVE-2026-63337","emoji":"😃"}'
        String shown = TrivyReportOutput.portableConsole('json', original)
        assertTrue(shown.chars().allMatch { it < 128 }, 'JSON console must use only ASCII')
        assertEquals(new JsonSlurper().parseText(original), new JsonSlurper().parseText(shown))
        assertTrue(shown.contains('CVE-2026-63337'))
        assertTrue(shown.contains('\\u'))
    }

    @Test
    void fileDestinationsDoNotRewriteOrDamageNativeBytes() {
        File root = tempDir.toFile()
        assertNull(TrivyReportOutput.destination(root, ''))
        assertEquals(new File(root, 'build/reports/result.json'),
            TrivyReportOutput.destination(root, 'build/reports/result.json'))
        assertEquals(root, TrivyReportOutput.destination(root, root.absolutePath))
        assertThrows(GradleException) {
            TrivyReportOutput.destination(root, 'plugin=unexpected')
        }
        File file = tempDir.resolve('nested/trivy.json').toFile()
        byte[] bytes = '{"note":"ação","box":"┌"}'.getBytes('UTF-8')
        TrivyReportOutput.saveExactBytes(file, bytes)
        assertArrayEquals(bytes, Files.readAllBytes(file.toPath()))
    }
}
