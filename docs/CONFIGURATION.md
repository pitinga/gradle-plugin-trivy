# Configuration reference

[Back to README](../README.md) | [Examples](EXAMPLES.md) | [Troubleshooting](TROUBLESHOOTING.md)

This guide distinguishes plugin **0.1.1** from the **0.1.2** release. Applying version 0.1.1 from the Gradle Plugin Portal does not provide features introduced in 0.1.2.

## Groovy DSL configuration

The following block describes **version 0.1.2**:

~~~groovy
trivy {
    version = 'latest'            // Trivy CLI version, not Gradle plugin version
    autoInstall = true
    preferInstalled = true
    scanMode = 'rootfs'
    target = ''                    // blank = build and scan bootJar/jar
    skipDirs = []                 // optional directory exclusions
    scanners = 'vuln'
    severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    ignoreUnfixed = false
    failOnVulnerability = false
}
~~~

| Option | Type | Published 0.1.1 default | 0.1.2 default | Purpose |
| --- | --- | --- | --- | --- |
| `version` | String | `0.76.2` | `latest` | Trivy CLI release for downloads |
| `autoInstall` | boolean | `true` | `true` | Install Trivy if absent |
| `preferInstalled` | boolean | `true` | `true` | Prefer binary in PATH, even if its version differs |
| `scanMode` | String | `rootfs` | `rootfs` | `rootfs` or `fs` |
| `target` | String | Unavailable | `''` | Blank: built JAR; `'.'`: project; or other file/directory |
| `skipDirs` | list of strings | Unavailable | `[]` | Repeated Trivy `--skip-dirs` flags |
| `scanners` | String | `vuln` | `vuln` | Trivy scanners; see report limitation below |
| `severity` | String | `UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL` | Same | Findings filter |
| `ignoreUnfixed` | boolean | `false` | `false` | Add `--ignore-unfixed` |
| `failOnVulnerability` | boolean | `false` | `false` | Trivy exit code `1` if findings are reported |

In **published 0.1.1**, do not configure `target` or `skipDirs`: these properties are not available. You *may* explicitly configure `version = 'latest'` in 0.1.1, but it is not the default there.

## Modes and targets (0.1.2+)

| Configuration | Equivalent Trivy command | Builds a JAR? | Notes |
| --- | --- | --- | --- |
| Nothing specified | `trivy rootfs <bootJar-or-jar>` | **Yes** | Default; good for detecting packaged Java CVEs |
| `target = '.'` | `trivy rootfs .` | No | Scans existing project files, including generated JARs |
| `scanMode = 'fs'; target = '.'` | `trivy fs .` | No | Looks for supported project metadata; may not find compiled Java dependencies |
| `target = 'build/libs/app.jar'` | `trivy rootfs build/libs/app.jar` | No | File must already exist |
| `scanMode = 'fs'; target = 'build/libs/app.jar'` | `trivy fs build/libs/app.jar` | No | Not recommended for Java CVEs inside JARs |

With an explicit `target`, the plugin deliberately does **not** execute `bootJar` or `jar` automatically. Build the JAR first if needed. A scan showing `num=0` language-specific files is **not evidence that the application has no vulnerabilities**.

## Gradle -P command-line overrides

These three properties are supported on **0.1.2+**:

| Gradle property | What it overrides |
| --- | --- |
| `-PtrivyTarget=...` | `target` (file/directory) |
| `-PtrivyScanMode=...` | `scanMode` |
| `-PtrivySkipDirs=dir1,dir2` | Entire `skipDirs` list (comma-separated) |

PowerShell 7 example:

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.' '-PtrivySkipDirs=.gradle/trivy,build/tmp'
~~~

Quote the **whole** `-P` argument in PowerShell, especially with paths, punctuation and globs. Other extension properties, including `version`, are *not* automatically mapped to `-P` by the current plugin; set those inside the `trivy { ... }` block.

## Directory exclusions

~~~groovy
trivy {
    scanMode = 'rootfs'
    target = '.'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

Each entry is forwarded to Trivy as a separate `--skip-dirs` argument. Relative paths and Trivy glob patterns can be used. Empty list entries are ignored, and duplicates are consolidated.

In a **directory scan**, version 0.1.2+ automatically excludes **its own Trivy installation cache** if that cache is physically inside the target directory. For example, when fallback installation placed Trivy under `./.gradle/trivy`, a scan of `.` skips that directory without requiring manual configuration. A normal single-JAR scan has **no automatic exclusions**. Excluding files can hide vulnerabilities: do not exclude `build/libs` when you intend to scan the packaged JAR.

## Vulnerability output and scanners

The plugin defaults to `scanners = 'vuln'` and uses a plain-ASCII vulnerability report (severity, CVE, package, installed and fixed versions, title) to avoid Windows/Eclipse Unicode rendering problems.

**Limitation:** the custom template displays vulnerability data only. Setting the Trivy scanner to `secret` is **not a supported way to obtain a complete secret report** from this plugin. Use Trivy CLI's normal reporting for secrets:

~~~powershell
trivy rootfs . --scanners secret
~~~

A successful build with `failOnVulnerability = false` does not mean the result is clean. Even with `failOnVulnerability = true`, the result only reflects what the chosen scanner, mode, target and exclusions could detect.

## Installation, cache and tasks

- `trivyInstall`: resolve an existing Trivy executable or install a verified official one; does not scan.
- `trivy`: build and scan JAR by default; or scan the requested target; executes each invocation to reflect changing vulnerability data.

`preferInstalled = true` selects Trivy in PATH first. Otherwise the plugin checks `GRADLE_USER_HOME/trivy/VERSION/PLATFORM` and installs from official GitHub Releases if needed. It falls back to the root project's `.gradle/trivy` only after a **permission-related** shared-cache error. Downloads are checked against official release SHA-256 checksums. The vulnerability database is cached separately by Trivy.

## Compatibility

CI tests Gradle 8.14.3, 9.1.0 and 9.9.0 on Windows and Linux with JDK 17, plus Gradle 9.9.0 with JDK 25. The installer also supports Linux ARM64 and macOS x64/ARM64, but these platforms are not in the CI matrix. Gradle 10 is not validated.

Continue with [examples](EXAMPLES.md) or [troubleshooting](TROUBLESHOOTING.md).
