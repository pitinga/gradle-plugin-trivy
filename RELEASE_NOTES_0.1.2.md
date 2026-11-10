# Gradle Trivy Plugin v0.1.2

## Highlights

- **Preserved original default scan:** build `bootJar` (or `jar`) and scan the resulting JAR using Trivy `rootfs`. No configuration changes are required for existing consumers.
- **Latest Trivy CLI release by default when a download is necessary:** if no executable in PATH and no compatible verified cache exists, resolve the latest stable Trivy release from the official GitHub API. `preferInstalled = true` still prioritizes PATH.
- **Flexible scan targets:** `trivy { target = '.' }` or `-PtrivyTarget=.` scans the project directory; a specific JAR path scans that existing file. Setting an explicit target does not automatically build a JAR.
- **Flexible directory exclusions:** `trivy { skipDirs = ['.gradle/trivy', 'build/tmp'] }` or `-PtrivySkipDirs=.gradle/trivy,build/tmp` forwards separate Trivy `--skip-dirs` arguments.
- **Self-scan prevention:** directory scans exclude plugin-managed Trivy installation cache directories when contained inside the target, avoiding reports about the scanner executable itself.
- **Optional filesystem mode:** `-PtrivyScanMode=fs -PtrivyTarget=.`; use the default rootfs JAR scan for packaged Java dependency CVEs.
- **Improved documentation:** complete README, configuration matrix, practical examples for Eclipse and CI, troubleshooting, and explicit version availability notes.

## Validation

- Unit and Gradle plugin validation tests across Windows/Linux, Gradle 8.14.3 / 9.1.0 / 9.9.0 with JDK 17 and Gradle 9.9.0 with JDK 25.
- Real Trivy installer, built-JAR vulnerability scans, alternate filesystem/rootfs targets, skipDirs and configuration-cache integration on Windows and Linux.

## Compatibility and safeguards

- Java/Gradle consumers that rely on the original `.\gradlew trivy` behavior are unchanged.
- The `version` option selects a **Trivy executable**, not this plugin's version. `preferInstalled = false` bypasses PATH when a selected cached/downloaded version is desired.
- An empty `fs` report does not prove Java dependencies are safe. The vulnerability-focused ASCII template does not display full secret-scanner findings.
- The original v0.1.0 and v0.1.1 GitHub tags remain unchanged.
