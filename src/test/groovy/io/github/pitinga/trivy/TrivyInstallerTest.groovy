package io.github.pitinga.trivy

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TrivyInstallerTest {
    @Test
    void correctOfficialAssetNames() {
        assertEquals('trivy_0.76.2_windows-64bit.zip', TrivyInstaller.archiveName('0.76.2', 'windows-amd64'))
        assertEquals('trivy_0.76.2_Linux-64bit.tar.gz', TrivyInstaller.archiveName('0.76.2', 'linux-amd64'))
        assertEquals('trivy_0.76.2_Linux-ARM64.tar.gz', TrivyInstaller.archiveName('0.76.2', 'linux-arm64'))
        assertEquals('trivy_0.76.2_macOS-64bit.tar.gz', TrivyInstaller.archiveName('0.76.2', 'macos-amd64'))
        assertEquals('trivy_0.76.2_macOS-ARM64.tar.gz', TrivyInstaller.archiveName('0.76.2', 'macos-arm64'))
    }

    @Test
    void parsesExactOfficialChecksumEntry() {
        String hash = 'a' * 64
        String content = ('b' * 64) + '  trivy_other.zip\n' +
            hash + '  trivy_0.76.2_windows-64bit.zip\n'
        assertEquals(hash, TrivyInstaller.checksumFor(content, 'trivy_0.76.2_windows-64bit.zip'))
        assertThrows(RuntimeException) {
            TrivyInstaller.checksumFor(content, 'trivy_unknown.zip')
        }
    }

    @Test
    void rejectsUnknownPlatforms() {
        assertThrows(RuntimeException) {
            TrivyInstaller.archiveName('0.76.2', 'windows-arm64')
        }
    }
}
