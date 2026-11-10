# Gradle Trivy Plugin

A lightweight Gradle plugin that builds and scans the application **JAR with `trivy rootfs` by default**. Project-directory scans (`trivy fs .` or `trivy rootfs .`) and other file targets are available explicitly. Designed for **Windows/Eclipse**, Linux and macOS.

**Published version:** `0.1.1` on the Gradle Plugin Portal. **Development branch:** `0.1.2-SNAPSHOT` (unreleased).

## Features

- Preserves the published default: runs `bootJar` (or `jar`) and scans the generated archive with `trivy rootfs`.
- Supports opt-in scans of an existing directory or file using `trivy.target` / `-PtrivyTarget=...` and optional `trivy.scanMode` / `-PtrivyScanMode=...`.
- Supports directory exclusions through `trivy.skipDirs` (list) or `-PtrivySkipDirs=path1,path2`, forwarded as repeated `--skip-dirs` flags. Directory scans also automatically skip plugin-managed Trivy caches inside the scan target.
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

Run from Eclipse Gradle Tasks or from a terminal to build and scan the application JAR (default):

~~~powershell
.\gradlew trivy
~~~

To scan the entire project directory as a filesystem instead:

~~~powershell
.\gradlew trivy '-PtrivyScanMode=fs' '-PtrivyTarget=.'
~~~

To scan the entire project (including generated artifacts) with `rootfs`:

~~~powershell
.\gradlew trivy '-PtrivyTarget=.'
~~~

To scan a specific JAR (build it first when using an explicit target):

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=build/libs/app.jar'
~~~

Replace `app.jar` with the actual file name. For plain Java projects, use `jar` instead of `bootJar`. With no explicit target, the plugin builds automatically; with `target` specified, no `bootJar`/`jar` dependency is added.

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
    scanMode = 'rootfs'              // rootfs (default) or fs
    target = ''                      // blank (default): auto-build and scan bootJar/jar
                                     // '.': project directory; or an existing file path
    skipDirs = []                    // no extra exclusions for default JAR scan
                                     // e.g. ['.gradle/trivy', 'build/tmp', '**/node_modules']
    scanners = 'vuln'
    severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    ignoreUnfixed = false
    failOnVulnerability = false
}
~~~

The default remains `rootfs` on the packaged application JAR, preserving the CVE detection seen in the published plugin. Trivy `fs` on a compiled JAR may not detect its packaged Java dependencies. Use `fs` with the project directory, or `rootfs` for compiled JARs. The plain-ASCII vulnerability report is preserved from the original manual task. Scans re-execute to pick up updated vulnerability information.

Override the target directly from PowerShell without changing the build script:

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.\build\libs\app.jar'
~~~

On Windows PowerShell, quote the complete `-P` argument to keep the JAR path intact. `-PtrivyTarget` takes precedence over `trivy { target = '...' }`. `-PtrivySkipDirs` similarly overrides the configured `skipDirs` list. Paths are resolved relative to the Gradle project directory; absolute paths also work. You may optionally override the mode with `-PtrivyScanMode=rootfs`.

### Excluding directories with `skipDirs`

The original default remains **`trivy rootfs <built bootJar/jar>`** with no additional exclusions. To scan the project directory while excluding generated or tool files, configure:

~~~groovy
trivy {
    scanMode = 'rootfs'
    target = '.'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

Or pass a comma-separated list from **PowerShell 7** without editing `build.gradle`:

~~~powershell
.\gradlew trivy '-PtrivyTarget=.' '-PtrivySkipDirs=.gradle/trivy,build/tmp,**/node_modules'
~~~

Each non-empty entry becomes a distinct Trivy `--skip-dirs` argument; both relative paths and Trivy glob expressions are supported. The CLI list overrides the DSL list for that invocation. Empty or duplicate entries are ignored. You can also use `-PtrivyScanMode=fs` when you deliberately want filesystem scanning.

**Self-exclusion:** when scanning a directory, the plugin automatically omits any **plugin-managed** Trivy installation cache inside that directory (normally `./.gradle/trivy` for the fallback cache). This prevents the plugin from scanning its own executable. You can still explicitly list `.gradle/trivy` in `skipDirs`; it will not be added twice. The default single-JAR scan is unchanged.

Trivy's official [file/directory selection documentation](https://trivy.dev/docs/v0.76/guide/configuration/skipping/) describes the `--skip-dirs` option and glob matching.

**Important:** when preferInstalled=true, a Trivy binary from PATH takes precedence even if its version differs from version. Set preferInstalled=false to force the specified cached/downloaded version.

By default, the plugin builds and scans the JAR, including packaged libraries. Explicit project-directory scans may include build outputs and cached binaries, potentially reporting vulnerabilities unrelated to the application JAR; plugin-managed Trivy cache directories inside the target are automatically excluded. Such scans may also locate secrets with Trivy's secret scanner enabled, but the plugin's current ASCII report template is designed for vulnerability findings; use Trivy's default or JSON output for detailed secret findings. Apply the plugin to each relevant project in a multi-project build.

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
| trivy | Build bootJar/jar and scan it with rootfs by default; or scan an explicit file/directory with fs/rootfs |

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
