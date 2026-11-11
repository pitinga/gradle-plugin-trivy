# Gradle Trivy Plugin v0.1.4

## Native reports and files

- **New default:** native Trivy `table` report, replacing the custom ASCII CVE template.
- **JSON:** choose `format = 'json'` or `-PtrivyFormat=json`.
- **Output files:** choose `outputFile = 'build/reports/trivy.json'` or `-PtrivyOutput=build/reports/trivy.json`; Trivy writes the complete **native UTF-8 report** directly to disk. By default the report goes to the console.
- **Windows/Eclipse:** the native table is converted **only for console presentation** to safe ASCII borders, avoiding corrupted Unicode box characters without requiring `chcp`, PowerShell encoding changes or Eclipse preferences. Files remain unchanged.
- **Cleaner logs:** Trivy `INFO`/`DEBUG` messages are forwarded to normal Gradle output; WARN/ERROR and failure exit codes remain visible.

## Runtime version banner

Both `trivy` and `trivyInstall` now show the exact **Gradle Trivy Plugin**, selected **Trivy CLI**, **Gradle** and **Java** versions. The plugin version is embedded at build time; the Trivy CLI version is queried from the resolved executable.

## Defaults retained

- Without `target`, automatically run `bootJar` (or `jar`) then `trivy rootfs` on the built JAR.
- `version = 'latest'`, `autoInstall = true`, `preferInstalled = true`.
- `scanMode = 'rootfs'`, `target = ''`, `skipDirs = []`.
- `scanners = 'vuln'`, `severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'`.
- `ignoreUnfixed = false`, `failOnVulnerability = false`.
- New report defaults: `format = 'table'`, `outputFile = ''`.

`outputStrategy` (an experimental branch-only option) and the former custom report are not included.

## Usage

~~~groovy
plugins {
    id 'io.github.pitinga.trivy' version '0.1.4'
}
~~~

~~~powershell
# Built JAR -> native Trivy table in console
.\gradlew trivy

# Built JAR -> native Trivy JSON file
.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'
~~~

## Validation

- User-validated on Eclipse/Buildship, Windows, Gradle 9.9.0, Java 25, with real Trivy scans and Isolated Projects configuration cache.
- Windows/Linux integration tests exercise native table/JSON output, UTF-8 output files, consistent findings, INFO diagnostics, and correct console-safe borders.
- Gradle compatibility CI validates Gradle 8.14.3 / 9.1.0 / 9.9.0, including JDK 25 on Gradle 9.9.0, plus dedicated Gradle 9.9 Isolated Projects tests.
- The plugin does not automatically scan container registries/images, and Gradle 10 is not validated.

Full current documentation: [README](README.md), [Configuration](docs/CONFIGURATION.md), [Examples](docs/EXAMPLES.md), [Troubleshooting](docs/TROUBLESHOOTING.md).

Prior release tags and published artifacts are preserved.
