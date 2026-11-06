# Gradle Trivy Plugin v0.1.1

Compatibility update for Gradle **9.9.0**, released 6 November 2026.

## Changes

- Added explicit non-cacheable task annotations required by Gradle 9.9 plugin validation for the Trivy installation and scan tasks.
- Maintains the automatic Trivy installer, SHA-256 verification, shared cache and permission-based fallback, `bootJar`/`jar` scanning, and ASCII console report.
- Added Gradle 9.9.0 to GitHub Actions tests on Windows and Linux with JDK 17 and JDK 25; earlier Gradle versions 8.14.3 and 9.1.0 are also tested.
- Updated live install and JAR scan integration tests and Plugin Portal publication workflow to use Gradle 9.9.0.

The original GitHub release tag **v0.1.0** remains unchanged. Plugin Portal publishing is a separate, manual GitHub Actions operation and requires the repository's protected publisher credentials.
