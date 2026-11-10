# Gradle Trivy Plugin v0.1.3

## Highlights

- **Gradle Isolated Projects compatibility:** the plugin has been tested on Gradle 9.9 with Windows and Linux, JDK 17 and JDK 25.
- **Eliminates cross-project configuration access:** when the plugin runs in an app or library subproject, it now uses the immutable `project.rootDir` instead of accessing another project's layout.
- **Official compatibility metadata:** declares both `configurationCache = true` and `isolatedProjects = true`.
- **New Isolated Projects CI workflow:** builds a real multi-project Gradle consumer, checks default JAR task wiring, explicit targets, and isolated configuration cache reuse.
- **Real Trivy scans with Isolated Projects:** Windows/Linux smoke tests run the JAR scan using `--isolated-projects`, including a repeated invocation.

## Compatibility

- The normal `trivy` task continues to build and scan `bootJar` (or `jar`) with Trivy `rootfs` by default.
- The existing `target`, `skipDirs` and PowerShell `-P` overrides remain available without configuration changes.
- Verified in the normal eight-case CI matrix with Gradle 8.14.3 / 9.1.0 / 9.9.0, plus JDK 25 on Gradle 9.9.
- Isolated Projects is an incubating Gradle feature: CI exercises Gradle 9.9 specifically; support in later Gradle versions is not guaranteed without validation.

Previous GitHub tags and the published 0.1.2 version remain unchanged.
