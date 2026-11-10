# Changelog

## 0.1.2-SNAPSHOT — Unreleased

- Default `trivy.version` to `latest`, resolving the latest stable official GitHub release when a download is needed.
- Preserve `preferInstalled = true`: use an existing Trivy executable from PATH first, without querying or downloading a release.
- Preserve the ability to pin `trivy.version` to a specific release and set `preferInstalled = false` to force cache/download resolution.
- Add a regression test for these defaults.

## 0.1.1 — 2026-11-06

Gradle 9.9.0 compatibility update.

### Changed
- Explicitly annotated `TrivyTaskBase`, `TrivyInstallTask` and `TrivyScanTask` with `@DisableCachingByDefault` to pass stricter Gradle 9.9 plugin validation while retaining live vulnerability scans.
- Updated CI compatibility testing for Gradle 8.14.3, 9.1.0, and 9.9.0 on Windows and Linux, including JDK 25 coverage for Gradle 9.9.0.
- Updated CI install/scan and Plugin Portal publication workflow runtime to Gradle 9.9.0.
- Did not rewrite the previously published v0.1.0 GitHub tag; v0.1.1 is the intended first Portal publication.

## 0.1.0 — 2026-11-06

First stable source release.

### Features
- `trivy`: builds the `bootJar` (or `jar`) and scans the resulting JAR with Trivy.
- `trivyInstall`: locates Trivy or installs a verified executable without scanning.
- Automatic discovery of an existing Trivy binary in `PATH`, with an option to force a selected version.
- Official GitHub Releases downloads with SHA-256 verification and a shared Gradle user-home cache.
- Automatic fallback to `<project>/.gradle/trivy` for permission errors in the shared cache.
- Text-only report with vulnerability IDs, severity, package, installed version, title, and fixed version; compatible with the Eclipse console on Windows.
- Configurable scanning mode, severity, unfixed-vulnerability handling, and fail-on-vulnerability behavior.

### Compatibility and validation
- Validated with Gradle 9.1.0, Windows and Linux GitHub runners; `--configuration-cache` and `--warning-mode all`.
- End-to-end automated installation and JAR scan tests on Windows and Linux.
- Locally validated on Windows / Eclipse using Gradle 9.1.0 and JDK 25.
- Publication metadata for Gradle Plugin Portal is included; Portal publication remains a separate operation.

### Limitations
- Only one JAR is scanned per task; no automatic container image scanning or multi-project aggregation.
- SHA-256 checks file integrity against GitHub-hosted checksums; it is not independent artifact signature verification.
- A cached executable is marked verified at install time; cached bytes are not rehashed on every use.
