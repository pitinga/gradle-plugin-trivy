# Gradle Trivy Plugin v0.1.3 — release approved

This release adds tested **Gradle Isolated Projects** compatibility while preserving the original Trivy `rootfs` JAR scan as default.

Validation completed before release:

- Eight existing Gradle checks passed on Windows and Linux with Gradle 8.14.3, 9.1.0, 9.9.0 (JDK 17), plus Gradle 9.9.0 (JDK 25).
- Four dedicated Gradle 9.9 Isolated Projects tests passed in a multi-project build (Windows/Linux, JDK 17/25), including configuration cache reuse.
- Real Trivy scans with `--isolated-projects` passed on Windows and Linux.

The versioned `v0.1.2` tag and Gradle Plugin Portal publication remain unchanged.

This commit starts a *gated* v0.1.3 release workflow. It must run `clean test validatePlugins publishToMavenLocal` and `publishPlugins --validate-only` using existing encrypted GitHub Repository Secrets before it creates the v0.1.3 GitHub tag/release and publishes that immutable tag to the Gradle Plugin Portal.

Authorization: the user explicitly requested publication after test success.
