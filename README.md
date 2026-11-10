# Gradle Trivy Plugin

A lightweight Gradle plugin that builds the JAR (including Spring Boot's bootJar), locates or installs Trivy, and scans the generated artifact. Designed for **Windows/Eclipse**, Linux and macOS.

**Published version:** `0.1.1` on the Gradle Plugin Portal. **Development branch:** `0.1.2-SNAPSHOT` (unreleased).

## Features

- Runs the existing bootJar task (preferred) or jar task before scanning.
- Uses Trivy already present on PATH, unless preferInstalled is disabled.
- If absent, resolves the **latest stable Trivy release by default**, downloads the official archive for the host OS/CPU and checks SHA-256 against the official checksums file.
- First tries the shared Gradle user home cache (normally ~/.gradle/trivy); on **permission errors** falls back to the root project's .gradle/trivy.
- Reuses a verified cached executable. Does not install globally or modify PATH.
- Uses the same plain-ASCII vulnerability report as the original Gradle task, avoiding corrupted Unicode table borders in the Eclipse console.
- Does not hide real Trivy errors. Vulnerability findings only fail the build when explicitly enabled.

## Requirements

- JDK 17+ and Gradle 8.14.3, 9.1.0 or 9.9.0 (CI-tested on Windows and Linux). Gradle 9.9.0 is also tested with JDK 25. Older Gradle versions are not part of the tested compatibility matrix.
- Windows x64, Linux x64/ARM64 or macOS x64/ARM64.
- Internet access to GitHub Releases for the first installation (unless Trivy is installed or cached).
- Access to Trivy vulnerability databases for scanning, subject to your network policy.
- tar on Linux/macOS for extracting the official archive. Windows ZIP extraction is built in.

## Quick start (source checkout)

To test changes from the unreleased development branch, use a composite build:

Clone this repository alongside the Gradle project.

Add to the consuming project's **settings.gradle**, before other settings:

~~~groovy
pluginManagement {
    includeBuild('../gradle-plugin-trivy')
}
~~~

Then in the consuming project's **build.gradle**:

~~~groovy
plugins {
    id 'org.springframework.boot' version 'YOUR_SPRING_BOOT_VERSION'
    id 'io.github.pitinga.trivy'
}
~~~

If Spring Boot is already applied, add only the Trivy plugin ID. It also works with the standard Gradle java plugin.

Run from Eclipse Gradle Tasks or from a terminal:

~~~powershell
.\gradlew trivy
~~~

To download or resolve Trivy without scanning:

~~~powershell
.\gradlew trivyInstall
~~~

## Use the published version

To use the published `0.1.1` version directly from the Gradle Plugin Portal without `includeBuild`:

~~~groovy
plugins {
    id 'io.github.pitinga.trivy' version '0.1.1'
}
~~~

**Note:** the new default `version = 'latest'` is currently on the `main` development branch and will not affect published `0.1.1` users until a subsequent plugin release.

## Optional configuration

~~~groovy
trivy {
    version = 'latest'               // latest stable Trivy release for downloads/cache (main branch)
    autoInstall = true               // download if missing
    preferInstalled = true           // prefer an installed PATH binary regardless of its version
    scanMode = 'rootfs'              // rootfs or fs
    scanners = 'vuln'
    severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    ignoreUnfixed = false
    failOnVulnerability = false
}
~~~

The default mode and vulnerability template preserve the original manual task. Scans are intentionally re-executed to pick up updated vulnerability information.

**Important:** when preferInstalled=true, a Trivy binary from PATH takes precedence even if its version differs from version. Set preferInstalled=false to force the specified cached/downloaded version.

The plugin scans the built JAR, not every dependency or artifact in a multi-project build. Apply it to each relevant project. Use the existing Trivy command directly for other types of scans.

## Installer cache policy

1. Discover an existing Trivy executable on PATH when preferInstalled=true.
2. Resolve version (latest stable by default on the development branch, querying GitHub Releases; you may specify a fixed version instead).
3. Check the verified executable at GRADLE_USER_HOME/trivy/VERSION/PLATFORM/.
4. If not present, download and verify the official archive, install and reuse it.
5. **Only** for shared-cache permission failures, fall back to PROJECT_ROOT/.gradle/trivy/VERSION/PLATFORM/.

Errors such as network failures, SHA-256 mismatches, missing archives or unsupported platforms are reported as failures, **not** silently treated as permissions problems.

The two caches are different from Trivy's vulnerability database cache, which Trivy manages separately. The project cache is ignored in .gitignore. A checksum marker records validation at installation time; this is not continuous re-attestation of previously cached binaries.

## Tasks

| Task | Purpose |
| --- | --- |
| trivyInstall | Locate or install Trivy, without building or scanning |
| trivy | Build bootJar / jar; install Trivy if necessary; scan the resulting JAR |

## Development

~~~powershell
gradle test validatePlugins
gradle publishToMavenLocal
~~~

The repository includes unit tests and a Gradle TestKit task-registration smoke test. GitHub Actions additionally runs real Trivy installations, database downloads, and JAR scans on Windows and Linux with Gradle 9.9.0. The plugin has also been validated in Eclipse on Windows with Gradle 9.1.0 and JDK 25. See [GitHub Actions](https://github.com/pitinga/gradle-plugin-trivy/actions).

## Security and publication

Official archives are downloaded from allowlisted HTTPS GitHub domains and checked against SHA-256 listed in the corresponding official checksum file. Downloads and checksums come from the same GitHub release; this verifies file integrity but is **not** an independent signature/attestation validation.

No binaries or vulnerability databases are committed to the repository. To prepare for publication, the project applies `com.gradle.plugin-publish` and declares the plugin ID `io.github.pitinga.trivy`. Follow [RELEASING.md](RELEASING.md) to validate the publication metadata and publish with your own Plugin Portal credentials. The first submission is subject to Portal approval.

## License

MIT.
