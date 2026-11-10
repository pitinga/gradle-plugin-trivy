# Gradle Trivy Plugin v0.1.2 — release approved

The 0.1.2 source code passed unit and plugin-validation tests on:

- Windows and Linux with Gradle 8.14.3, 9.1.0 and 9.9.0 (JDK 17);
- Windows and Linux with Gradle 9.9.0 (JDK 25);
- Real Trivy binary installation and vulnerability scanning on Windows/Linux;
- Default JAR rootfs mode, explicit directory and JAR targets, skipDirs flags and local self-cache exclusions;
- Gradle configuration cache.

The original v0.1.0 and v0.1.1 tags are immutable and remain unchanged.

This commit starts the **gated v0.1.2 release workflow**. Before tagging, it must
pass another plugin test/validation build AND Gradle Plugin Portal
`publishPlugins --validate-only` using GitHub encrypted repository secrets.
Only after successful validation will it create the annotated GitHub tag/release
and publish the same tag to the Gradle Plugin Portal.

Approval: user explicitly requested publication of the new version after successful tests.
