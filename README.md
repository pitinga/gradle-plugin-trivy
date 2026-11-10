# Gradle Trivy Plugin

**Automated vulnerability scanning for Java and Spring Boot builds.** Build your application JAR, locate or securely install [Trivy](https://trivy.dev/), and display vulnerability findings directly in Gradle, Eclipse or CI/CD logs.

[**Gradle Plugin Portal**](https://plugins.gradle.org/plugin/io.github.pitinga.trivy) · [**Configuration reference**](docs/CONFIGURATION.md) · [**Usage examples**](docs/EXAMPLES.md) · [**Troubleshooting**](docs/TROUBLESHOOTING.md) · [**Changelog**](CHANGELOG.md) · [**GitHub Actions**](https://github.com/pitinga/gradle-plugin-trivy/actions)

> **Current published release:** `0.1.2` on the Gradle Plugin Portal. Version `0.1.1` remains available as an earlier release. Features such as `target` and `skipDirs` require **0.1.2 or later**, not 0.1.1.

## Quick start — version 0.1.2

Add the plugin to your existing **Groovy DSL** `build.gradle`:

~~~groovy
plugins {
    id 'io.github.pitinga.trivy' version '0.1.2'
}
~~~

This plugin works alongside your existing `org.springframework.boot` plugin or the standard `java` plugin. Run:

~~~powershell
.\gradlew trivy
~~~

**What happens by default?** The task runs `bootJar` when available (otherwise `jar`), then performs the equivalent of:

~~~text
trivy rootfs <path-to-generated-jar> --scanners vuln
~~~

The task automatically finds Trivy in `PATH` or installs a verified official release when missing. Findings appear in the Gradle console. By default, detected vulnerabilities **do not fail the build**; command/scan errors still fail.

For a CI security gate:

~~~groovy
trivy {
    severity = 'HIGH,CRITICAL'
    ignoreUnfixed = true
    failOnVulnerability = true
}
~~~

## What this plugin provides

| Capability | Behavior |
| --- | --- |
| Automatic JAR build and scan | `bootJar` preferred, otherwise `jar`; `rootfs` by default |
| Trivy executable discovery | Uses Trivy already installed in `PATH` unless disabled |
| Automatic installation | Official GitHub release download, SHA-256 validation |
| Cache reuse | Shared Gradle user home; fallback to project cache on permission errors |
| Cross-platform | Windows x64; Linux x64/ARM64; macOS x64/ARM64 installer support |
| Vulnerability reporting | ASCII-friendly CVE list showing severity, package, installed/fixed version and title |
| Flexible scan target | Explicit file or directory target — available in 0.1.2+ |
| Directory exclusions | `skipDirs`, with automatic self-cache exclusion — available in 0.1.2+ |
| Gradle configuration cache | Tested in CI; scan results re-run when the task executes |
| Gradle Isolated Projects | Automated Gradle 9.9 multi-project tests on Windows/Linux (JDK 17/25); support declaration prepared for next release |

## Available tasks

| Task | Description |
| --- | --- |
| `trivy` | Build and scan the application JAR by default. Installs Trivy if necessary |
| `trivyInstall` | Only find/install Trivy; do not build or scan the application |

Both tasks are available from Eclipse Buildship's **Gradle Tasks** view.

## Configuration at a glance

Options are configured inside `trivy { ... }` in your `build.gradle`.

| Property | Version 0.1.1 default | Version 0.1.2 default | Description |
| --- | --- | --- | --- |
| `version` | `'0.76.2'` | `'latest'` | **Trivy executable** version used when downloading |
| `autoInstall` | `true` | `true` | Download if missing |
| `preferInstalled` | `true` | `true` | Prefer the PATH executable, regardless of version |
| `scanMode` | `'rootfs'` | `'rootfs'` | `rootfs` or `fs` |
| `target` | Not supported | `''` | Blank: built JAR; `'.'`: project directory; or another path |
| `skipDirs` | Not supported | `[]` | Paths/globs excluded from a directory scan |
| `scanners` | `'vuln'` | `'vuln'` | Trivy scanner selector; report template is vulnerability-oriented |
| `severity` | `'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'` | Same | Severity filter |
| `ignoreUnfixed` | `false` | `false` | Ignore vulnerabilities lacking a fix |
| `failOnVulnerability` | `false` | `false` | Return a failing exit code for reportable findings |

**Important distinction:** `version = 'latest'` selects the Trivy **CLI version**, not the Gradle plugin version. With `preferInstalled = true`, a Trivy binary in `PATH` takes priority even if it is older. Set `preferInstalled = false` to honor a specified downloaded/cached version.

For the complete semantics, see [Configuration reference](docs/CONFIGURATION.md).

## Scan mode and target — avoid false negatives

Our Java tests found a significant coverage difference:

| Scan | What it scans | Java JAR vulnerability coverage |
| --- | --- | --- |
| `trivy rootfs <built JAR>` | Packaged application libraries | **Recommended default** |
| `trivy rootfs .` | Existing project files and built artifacts | Can include the packaged JAR and other binaries |
| `trivy fs .` | Recognized project/dependency metadata | **May miss Java dependencies** present in the built JAR |
| `trivy fs app.jar` | A JAR passed to filesystem mode | **Not recommended**; may detect zero language-specific files |

A `num=0` language-specific files message or an empty report does **not** establish that an application is secure.

In version 0.1.2+, `target = '.'` does **not** build a JAR automatically. To include up-to-date build outputs, explicitly run `bootJar` or `jar` first.

## Additional scan options — version 0.1.2+

The following examples require **0.1.2 or later**. They are not supported by version 0.1.1.

### Scan the whole project with rootfs

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.'
~~~

This inspects existing supported files in the project, including generated JARs. The plugin automatically excludes its own cached Trivy executable when the installation directory is within the scan target.

### Use fs to scan project metadata

~~~powershell
.\gradlew trivy '-PtrivyScanMode=fs' '-PtrivyTarget=.'
~~~

### Scan a specific existing JAR

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=build/libs/app.jar'
~~~

Replace `app.jar` with your actual artifact name.

### Skip selected directories

~~~groovy
trivy {
    scanMode = 'rootfs'
    target = '.'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

The equivalent one-off PowerShell command is:

~~~powershell
.\gradlew trivy '-PtrivyTarget=.' '-PtrivySkipDirs=.gradle/trivy,build/tmp,**/node_modules'
~~~

`-PtrivySkipDirs` **overrides** the list from `build.gradle`. Each entry becomes an independent `--skip-dirs` flag. For directory targets, the plugin also automatically excludes its own Trivy cache if it is located inside the target. Exclusions reduce scan coverage; only exclude paths deliberately.

See [Usage examples](docs/EXAMPLES.md) for Eclipse, Spring Boot, Java, GitHub Actions and Azure DevOps.

## Testing the plugin from source

Clone this repository next to your application checkout. In the consuming project's `settings.gradle`, before other settings:

~~~groovy
pluginManagement {
    includeBuild('../gradle-plugin-trivy')
}
~~~

Then add the plugin **without a version** to the existing `plugins` block:

~~~groovy
plugins {
    id 'io.github.pitinga.trivy'
}
~~~

Do not also declare the published version for this plugin. Refresh the Gradle project in Eclipse or rerun the Gradle wrapper. To return to the Portal release, remove the composite-build configuration and apply `version '0.1.2'` after it is available in the Portal.

## Trivy installation and cache

1. When `preferInstalled = true`, search `PATH` for Trivy and use it if found.
2. Otherwise resolve the requested Trivy version, look for an existing verified installation in `GRADLE_USER_HOME/trivy/VERSION/PLATFORM`, or download it from official GitHub Releases.
3. Verify the downloaded archive with its official SHA-256 checksum.
4. Only when the shared cache fails for permissions reasons, fall back to `<root-project>/.gradle/trivy/VERSION/PLATFORM`.

No system-wide Trivy installation or `PATH` mutation is performed. Trivy's vulnerability database cache is independent of its executable cache.

With `version = 'latest'`, resolving a download version requires a GitHub API lookup. With a pinned version, the exact selected cache can be reused. For errors or access problems, see [Troubleshooting](docs/TROUBLESHOOTING.md).

## Output, exit codes and security limitations

The report uses a simple ASCII text layout, suitable for Eclipse on Windows. It shows vulnerability IDs, severity, affected package, installed version, suggested fixed version and advisory title.

- `failOnVulnerability = false` (**default**) allows a successful Gradle build even when CVEs are found. Set it to `true` to gate builds.
- `scanners = 'vuln'` (**default**) does **not** run the Trivy secret scanner. The custom report template does not provide full secret-finding output. For secrets, use the Trivy CLI's own reporting separately.
- An empty report may mean the selected mode did not recognize the files, not that the project is safe.
- An explicit project-directory scan can report vulnerabilities in generated files or tools that are not dependencies of the application JAR.
- The plugin does **not** automatically scan container images, registries or all artifacts in a multi-project build.
- SHA-256 verification protects archive integrity against the supplied official checksum; it is not independent signature verification.

## Requirements and compatibility

| Environment | Status |
| --- | --- |
| Gradle 8.14.3, 9.1.0, 9.9.0 | Tested on Windows and Linux |
| JDK 17 | Tested |
| JDK 25 with Gradle 9.9.0 | Tested |
| Windows x64, Linux x64 | Tested in CI |
| Linux ARM64, macOS x64/ARM64 | Installer support; not in the CI matrix |
| Gradle 10 | Not validated |

Network access may be needed for Trivy binaries and vulnerability databases. Linux/macOS require `tar` for binary extraction. Windows ZIP extraction is handled by the plugin.

## Documentation and project links

- [Complete configuration reference](docs/CONFIGURATION.md) — all properties, defaults, version differences and CLI overrides
- [Step-by-step examples](docs/EXAMPLES.md) — Java, Spring Boot, Eclipse, Azure DevOps and GitHub Actions
- [Troubleshooting](docs/TROUBLESHOOTING.md) — Java scan modes, cached tools, PowerShell and security limitations
- [Changelog](CHANGELOG.md) — published and upcoming changes
- [Release and publishing guide](RELEASING.md) — publication workflow and credentials
- [Source code](https://github.com/pitinga/gradle-plugin-trivy) · [Issue tracker](https://github.com/pitinga/gradle-plugin-trivy/issues) · [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.pitinga.trivy)

## License

[MIT](LICENSE).
