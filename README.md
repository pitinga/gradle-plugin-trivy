# Gradle Trivy Plugin

Scan Java and Spring Boot builds with the official [Trivy](https://trivy.dev/) CLI, directly from Gradle, Eclipse/Buildship and CI/CD. Automatically build a JAR, find or install a verified Trivy executable, and generate **native Trivy table or JSON reports**.

**Current version: 0.1.4** · **Plugin ID: `io.github.pitinga.trivy`**

[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.pitinga.trivy) · [Configuration](docs/CONFIGURATION.md) · [Examples](docs/EXAMPLES.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Changelog](CHANGELOG.md)

## Quick start

Add the plugin to the existing `plugins` block of your **Groovy DSL** `build.gradle`:

~~~groovy
plugins {
    id 'java' // or your existing Spring Boot plugin
    id 'io.github.pitinga.trivy' version '0.1.4'
}
~~~

Run from the project directory:

~~~powershell
.\gradlew trivy
~~~

**No additional configuration is required.** When `target` is blank, the task builds `bootJar` (if present), otherwise `jar`, and scans the resulting artifact using `trivy rootfs`. The default output format is Trivy's **native `table`**, not a custom vulnerability template.

The plugin selects a Trivy executable from `PATH` by default, or installs an official release with SHA-256 verification if needed. The Trivy executable version is independent of the Gradle plugin version.

## Tasks

| Task | Purpose |
| --- | --- |
| `trivy` | Build the default JAR when needed, scan it and produce a report |
| `trivyInstall` | Find or install Trivy without scanning |

Both tasks print a runtime banner showing the **Gradle Trivy Plugin**, selected **Trivy CLI**, **Gradle** and **Java** versions. For example:

~~~text
==================================================
Gradle Trivy Plugin : 0.1.4
Trivy CLI           : 0.76.2
Gradle              : 9.9.0
Java                : 25
==================================================
~~~

These are illustrative runtime versions. The plugin version is read from its packaged metadata; the CLI version is queried from the **actual selected executable**. If `preferInstalled = true`, the executable in `PATH` takes precedence even when its version differs from `trivy.version`.

## All configuration options and defaults

Place optional settings in `build.gradle`:

~~~groovy
trivy {
    version = 'latest'                 // Trivy CLI release to download, not plugin version
    autoInstall = true
    preferInstalled = true
    scanMode = 'rootfs'                // rootfs or fs
    target = ''                        // blank = automatically build and scan bootJar/jar
    skipDirs = []                      // only relevant to supported directory scans
    scanners = 'vuln'
    severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    ignoreUnfixed = false
    failOnVulnerability = false
    format = 'table'                   // native Trivy table or json
    outputFile = ''                    // empty = console; path = native UTF-8 file
}
~~~

| Option | Default | Meaning |
| --- | --- | --- |
| `version` | `'latest'` | Trivy **CLI** version used when installing/downloading |
| `autoInstall` | `true` | Download Trivy if it cannot otherwise be resolved |
| `preferInstalled` | `true` | Prefer installed Trivy from `PATH` over selected download version |
| `scanMode` | `'rootfs'` | Trivy `rootfs` or `fs` |
| `target` | `''` | Automatically built JAR; alternatively an existing file/directory |
| `skipDirs` | `[]` | Repeated `--skip-dirs` patterns; skips matching directories |
| `scanners` | `'vuln'` | Trivy scanners, such as `vuln` |
| `severity` | `'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'` | Severity filter |
| `ignoreUnfixed` | `false` | Pass `--ignore-unfixed` when enabled |
| `failOnVulnerability` | `false` | Fail the build when Trivy reports findings (according to the scan settings) |
| `format` | `'table'` | **`table` or `json`**, both native Trivy formats |
| `outputFile` | `''` | Empty: console; otherwise Trivy `--output` with a project-relative or absolute path |

### Native reports and output files

The Trivy CLI generates all reports. **No custom report template or `outputStrategy` option exists in 0.1.4.**

| Desired output | PowerShell 7 command |
| --- | --- |
| Table in console (default) | `.\gradlew trivy` |
| Explicit native table | `.\gradlew trivy '-PtrivyFormat=table'` |
| JSON in console | `.\gradlew trivy '-PtrivyFormat=json'` |
| Native UTF-8 table in file | `.\gradlew trivy '-PtrivyOutput=build/reports/trivy.txt'` |
| Native UTF-8 JSON in file | `.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'` |

When `outputFile` is configured, Trivy writes the complete report **to the file instead of the console**, following its native `--output` behavior. Paths are relative to the consuming Gradle project directory unless absolute. Reports are overwritten on repeated scans.

On Windows, legacy code pages can corrupt the Unicode box characters in Trivy's native table. **No terminal, PowerShell, code-page or Eclipse settings are required:** the plugin lets Trivy render its table, then automatically converts only the **console presentation** to readable ASCII borders (`+`, `-`, `|`). Accented or non-ASCII characters may be simplified in the Windows console. **The report saved to disk remains the exact native UTF-8 output**, including Unicode table borders and full original descriptions. On Linux/macOS, console output is passed through natively.

Likewise, Trivy's normal `INFO` diagnostics are shown as normal Gradle messages instead of red STDERR lines in Eclipse. `WARN` and `ERROR` remain diagnostic messages; nonzero Trivy exit codes are not hidden.

### Gradle `-P` overrides

The following are **per-execution** overrides; they do not require edits to `build.gradle`:

| Property | Affects |
| --- | --- |
| `-PtrivyScanMode=rootfs` or `fs` | `scanMode` |
| `-PtrivyTarget=...` | `target` |
| `-PtrivySkipDirs=dir1,dir2` | `skipDirs` (replaces entire DSL list) |
| `-PtrivyFormat=table` or `json` | `format` |
| `-PtrivyOutput=...` | `outputFile` |

**In PowerShell 7, quote each complete `-P` argument** as in the examples. Other DSL properties, such as `preferInstalled` and `version`, are configured in `trivy { ... }`.

## Scanning an application vs. a project directory

| Scan | Builds JAR automatically? | Notes |
| --- | --- | --- |
| `.\gradlew trivy` | **Yes** | Recommended for Java/Spring Boot: `rootfs` on built JAR |
| `.\gradlew bootJar trivy '-PtrivyTarget=.'` | **No** (but `bootJar` is explicitly invoked) | `rootfs .` on existing project files, including built JARs |
| `.\gradlew trivy '-PtrivyScanMode=fs' '-PtrivyTarget=.'` | **No** | `fs .` scans recognized files/metadata; may miss dependencies inside packaged JARs |
| `.\gradlew trivy '-PtrivyTarget=build/libs/app.jar'` | **No** | Scan a specific **existing** JAR with `rootfs` |

An explicit `target` does **not** build the target for you. In a directory scan, the plugin excludes its own managed Trivy installation cache if it is physically inside the target directory. User `skipDirs` patterns can also exclude files; choose them carefully to avoid false negatives.

## Eclipse / Buildship

Import or refresh the consuming Gradle project, then run the **`trivy`** task using **Gradle Tasks** or **Run Configurations → Gradle Task**. Pass `-PtrivyFormat=json`, `-PtrivyOutput=...` or `--isolated-projects` in **Program Arguments**, not JVM Arguments. You do **not** need `chcp 65001` or any Eclipse encoding change.

The plugin supports **Configuration Cache** and Gradle **Isolated Projects** (incubating). Trivy scans still execute when the configuration cache is reused.

## CI / security gate

By default, a detected vulnerability is **reported but does not fail the build**. To fail a build for findings that match your security policy:

~~~groovy
trivy {
    severity = 'HIGH,CRITICAL'
    ignoreUnfixed = true
    failOnVulnerability = true
    format = 'json'
    outputFile = 'build/reports/trivy.json'
}
~~~

Then run `.\gradlew trivy` on your CI agent. Actual CLI errors, checksum mismatches and scan failures also fail the task. A scan with no findings is not proof of security: supported formats, targets, scanners and the current vulnerability database determine coverage.

For Azure DevOps, GitHub Actions, and practical configuration, see [Examples](docs/EXAMPLES.md).

## Trivy installation and caching

1. Check `PATH` first when `preferInstalled = true`.
2. Otherwise resolve the requested Trivy CLI version (`'latest'` selects the latest stable GitHub release for download), then reuse a verified cache or install an official release with SHA-256 validation.
3. Use `GRADLE_USER_HOME/trivy` normally; fall back to `<root-project>/.gradle/trivy` on permission-related cache errors only.

There is no global Trivy installation or modification of `PATH`. Trivy's vulnerability database cache is separate from the executable cache.

## Compatibility and limitations

| Environment | Validation |
| --- | --- |
| Gradle 8.14.3, 9.1.0 and 9.9.0 / JDK 17 | Windows and Linux CI matrix |
| Gradle 9.9.0 / JDK 25 | Windows and Linux CI matrix |
| Gradle 9.9.0 / Isolated Projects | Windows and Linux; independent multi-project Gradle consumer |
| Windows x64 / Linux x64 | Real Trivy scans and report tests |
| Linux ARM64 / macOS x64 / macOS ARM64 | Installer implementation available; not included in CI matrix |
| Gradle 10 | Not validated |

`trivy` scans one selected target per task execution. Container image/registry scan and multi-project aggregation are not provided automatically. SHA-256 validation checks downloaded archives against official checksums; it is not an independent artifact signature.

## Development: use the source checkout instead of the published plugin

In the consuming project's `settings.gradle`:

~~~groovy
pluginManagement {
    includeBuild('../gradle-plugin-trivy')
}
~~~

In the consuming project's `build.gradle`, declare `id 'io.github.pitinga.trivy'` **without a version**. This is for local development only; normal consumers should use the published `0.1.4` version above.

## Links

[Complete configuration](docs/CONFIGURATION.md) · [Usage examples](docs/EXAMPLES.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [Release instructions](RELEASING.md) · [Changelog and release history](CHANGELOG.md) · [MIT license](LICENSE)
