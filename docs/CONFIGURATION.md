# Configuration reference — v0.1.4

[README](../README.md) · [Examples](EXAMPLES.md) · [Troubleshooting](TROUBLESHOOTING.md)

The plugin ID is **`io.github.pitinga.trivy`**. Version **`0.1.4`** uses Trivy's native `table` and `json` report formats. The old ASCII vulnerability template and the experimental `outputStrategy` property are **not supported**.

## Full configuration with defaults

All entries are optional. Copy only those you want to override.

~~~groovy
trivy {
    version = 'latest'                     // Trivy executable release, NOT this plugin version
    autoInstall = true
    preferInstalled = true

    scanMode = 'rootfs'                    // rootfs or fs
    target = ''                            // blank: build bootJar (or jar) and scan it
    skipDirs = []

    scanners = 'vuln'
    severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    ignoreUnfixed = false
    failOnVulnerability = false

    format = 'table'                       // table or json
    outputFile = ''                        // empty: console; else native UTF-8 report file
}
~~~

| Property | Type | Default | Meaning |
| --- | --- | --- | --- |
| `version` | String | `'latest'` | Release of the **Trivy CLI** to download/cache if needed |
| `autoInstall` | boolean | `true` | Install Trivy if not already available |
| `preferInstalled` | boolean | `true` | Use an installed `trivy` executable from PATH first, regardless of version |
| `scanMode` | String | `'rootfs'` | Supported: `rootfs` or `fs` |
| `target` | String | `''` | Empty: automatic JAR build; otherwise existing file or directory |
| `skipDirs` | List`<String>` | `[]` | Directory exclusion patterns forwarded as separate `--skip-dirs` arguments |
| `scanners` | String | `'vuln'` | Trivy scanner selector; default analyzes vulnerabilities |
| `severity` | String | `'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'` | Severity filter passed to Trivy |
| `ignoreUnfixed` | boolean | `false` | Pass `--ignore-unfixed` when enabled |
| `failOnVulnerability` | boolean | `false` | Ask Trivy for a failing exit code if findings are reported |
| `format` | String | `'table'` | Native Trivy `table` or `json` |
| `outputFile` | String | `''` | Empty: console; path: output written by Trivy using `--output` |

**`output` is not a DSL property.** Use `outputFile` in Groovy or `-PtrivyOutput=...` for a one-off CLI override. A relative output path is resolved from the **consuming project directory**. The plugin creates missing parent directories, replaces an existing report, and refuses to overwrite the scan target.

## CLI overrides: Gradle project properties

The plugin supports these five Gradle command-line arguments:

| Argument | Corresponding DSL property | Notes |
| --- | --- | --- |
| `-PtrivyScanMode=rootfs` / `fs` | `scanMode` | Overrides scan mode |
| `-PtrivyTarget=...` | `target` | Overrides scan target |
| `-PtrivySkipDirs=dir1,dir2` | `skipDirs` | Overrides the entire list |
| `-PtrivyFormat=table` / `json` | `format` | Overrides report format |
| `-PtrivyOutput=...` | `outputFile` | Overrides report file destination |

Example (PowerShell 7; quote the entire `-P` argument):

~~~powershell
.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'
~~~

Other DSL settings (such as `version`, `autoInstall`, `preferInstalled`, `scanners` and `severity`) are not exposed as these special `-P` overrides. Configure them in `trivy { ... }`.

## Native reporting and console encoding

### Table (default)

With `format = 'table'` and `outputFile = ''`, the CLI creates the **native Trivy table**. On Windows only, the plugin reads the UTF-8 table from a temporary report file and renders a **console-safe ASCII presentation** of the same table. Borders use `+`, `-` and `|`. This avoids the Unicode box-character corruption seen in some Gradle/Eclipse/PowerShell consoles, **without requiring terminal or Eclipse configuration**. The console may transliterate non-ASCII text or replace unsupported characters with `?`.

On Linux/macOS the native table is streamed without presentation changes.

### JSON

`format = 'json'` generates the standard Trivy JSON structure (including `SchemaVersion` and `Results`). In a Windows console, the plugin escapes non-ASCII characters as JSON Unicode escapes; the JSON values remain equivalent when parsed. The native file form is unchanged on every operating system.

### Files

Set `outputFile` to write an **unchanged native Trivy report** directly to disk in UTF-8:

~~~groovy
trivy {
    format = 'json'
    outputFile = 'build/reports/trivy.json'
}
~~~

The full report is written **only to the file**, as with Trivy CLI `--output`. Use `outputFile = ''` to show the full report on the console instead. A table file preserves Trivy's native Unicode box borders, even on Windows.

### Trivy INFO, WARN and ERROR

Trivy emits informational diagnostics on process stderr even for successful scans. The plugin relays `INFO` and `DEBUG` to regular Gradle lifecycle logging, so they do not appear red in Eclipse; `WARN` remains a warning; `ERROR`, `FATAL` and unclassified stderr remain errors. Scan exit codes are always respected.

## Modes, targets and automatic task dependencies

| Configuration | Trivy equivalent | Automatic JAR build? |
| --- | --- | --- |
| Default | `trivy rootfs <built-jar>` | **Yes**: `bootJar` preferred, otherwise `jar` |
| `target = '.'` | `trivy rootfs .` | No |
| `scanMode = 'fs'; target = '.'` | `trivy fs .` | No |
| `target = 'build/libs/application.jar'` | `trivy rootfs build/libs/application.jar` | No |

Explicit targets must exist before the task starts. To scan a current build plus the project directory, invoke `bootJar` explicitly:

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.'
~~~

Some Java projects yield `num=0` for `trivy fs .` even when their packaged JAR contains vulnerable dependencies. Prefer the default JAR `rootfs` scan for packaged Java software.

### Directory exclusions

~~~groovy
trivy {
    target = '.'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

Entries are forwarded to Trivy separately. When the target is a directory, the plugin also skips its **own managed Trivy installation cache** if physically located within that directory. No implicit exclusions are added to normal single-JAR scans. Directory exclusions can cause missed vulnerabilities; use sparingly.

## Trivy installation

- `trivyInstall` resolves or installs the executable without scanning.
- `trivy` finds Trivy, executes the selected scan and generates a report.
- With `preferInstalled = true`, the first suitable executable on PATH wins, even when `version = 'latest'` requests a different download version.
- With `preferInstalled = false`, the plugin uses the requested version from its verified cache or downloads an official release. `'latest'` is resolved when an installation is needed.
- Executables are cached under `GRADLE_USER_HOME/trivy`. On **permission errors**, installation falls back to `<root-project>/.gradle/trivy`.
- Official downloaded archives are checked against their release SHA-256 checksum; the Trivy vulnerability DB uses its own separate cache.

## Security behavior

With `failOnVulnerability = false`, CVEs can be reported while Gradle concludes `BUILD SUCCESSFUL`. Set `failOnVulnerability = true` to use the Trivy exit code for the configured scanner and severity; scan errors still fail regardless of this setting. Vulnerability detection depends on target, scanner, exclusions and database freshness.

The plugin automatically scans **one target per task invocation**. It does not automatically enumerate container images, registries or every project in a multi-project build.

## Compatibility

The project's CI matrix tests Windows and Linux with Gradle **8.14.3**, **9.1.0** and **9.9.0** (JDK 17); it also tests **JDK 25** with Gradle 9.9. Gradle **9.9 Isolated Projects** is validated for multi-project consumers and repeated configuration-cache reuse. Gradle 10 is not tested. macOS and Linux ARM64 installer code is available but not part of that CI matrix.

[See practical examples](EXAMPLES.md).
