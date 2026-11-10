# Troubleshooting and known limitations

[Back to README](../README.md) | [Configuration](CONFIGURATION.md) | [Examples](EXAMPLES.md)

## `trivy fs .` reports `num=0` but `rootfs` detects vulnerabilities

This can happen in Java projects. The user-validated example had vulnerable `com.rabbitmq:amqp-client` inside a compiled Spring Boot JAR. Direct `trivy fs .` identified no language-specific files, while `trivy rootfs .` detected the application JAR and its ten findings.

Different modes and targets have different coverage. **No findings** or `num=0` does *not* demonstrate that an application is secure. Use the default `.\gradlew trivy` to build and scan the packaged JAR with `rootfs`.

## `trivy fs build/libs/app.jar` seems clean

The plugin warns that `fs` on a compiled JAR may fail to inspect Java dependencies. Scan the JAR with `rootfs` instead. In the unreleased development branch, leave `scanMode = 'rootfs'` and either omit `target` entirely (automatic build) or specify the existing JAR's path.

## Failed because the configured target doesn't exist

With an explicit `target`, the development plugin does not automatically run `bootJar` or `jar`. Build the application first:

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=build/libs/YOUR_APPLICATION.jar'
~~~

Without a `target`, the plugin selects `bootJar` (or `jar`) and builds it automatically.

## Why was the plugin's own `trivy.exe` reported as vulnerable?

If the plugin installed Trivy into `<project>/.gradle/trivy`, a directory scan such as `trivy rootfs .` can scan that executable. The development plugin automatically skips its *own* cached installation when the cache is physically under the requested directory. You can also configure `skipDirs = ['.gradle/trivy']`.

With standalone Trivy:

~~~powershell
trivy rootfs . --scanners vuln --skip-dirs .gradle/trivy
~~~

This prevents self-analysis but does not remove or fix an actual vulnerability in the executable.

## Why doesn't the plugin show secrets?

The default is `scanners = 'vuln'` and its ASCII report is designed around vulnerability findings. For secrets, run Trivy directly with its normal output:

~~~powershell
trivy rootfs . --scanners secret
~~~

A private key found in your source tree should be treated as potentially exposed. If real, rotate/revoke it; excluding it from scans is not remediation. Protect logs that could contain secret fragments.

## Gradle says `BUILD SUCCESSFUL` although CVEs were found

The default `failOnVulnerability = false` uses Trivy exit code `0` for findings. To fail the build for selected severities:

~~~groovy
trivy {
    failOnVulnerability = true
    severity = 'HIGH,CRITICAL'
}
~~~

Actual command errors and download/checksum errors still fail the task.

## I configured `version = 'latest'` but the old Trivy binary runs

The default `preferInstalled = true` uses the first compatible Trivy executable in `PATH`, regardless of version. Force a requested cached/downloaded version:

~~~groovy
trivy {
    version = 'latest'
    preferInstalled = false
}
~~~

Note that the **published plugin version** is a separate setting in `plugins { id 'io.github.pitinga.trivy' version '0.1.1' }`.

## Shared cache is not writable

On permission-related failure under `GRADLE_USER_HOME/trivy`, the plugin falls back to `<root-project>/.gradle/trivy`. It then reuses that verified binary. This is expected. Trivy's vulnerability DB cache is separate.

## PowerShell reports `Task '.jar' not found`

Quote the complete command-line property, including the leading `-P`:

~~~powershell
.\gradlew trivy '-PtrivyTarget=build/libs/app.jar'
~~~

This property requires version 0.1.2+; do not use it against the published 0.1.1 version.

## Published plugin rejects `target` or `skipDirs`

The public Gradle Plugin Portal version is **0.1.1**, which does not contain these two settings. The GitHub `main` branch has **0.1.2** changes. To test them, use a composite build as shown in the README and remove the published version declaration while doing so. Use version 0.1.2 or later once available in the Portal.

## Eclipse console or Gradle deprecation warnings

Use `.\gradlew trivy --warning-mode all` or put `--warning-mode all` under **Run Configurations → Gradle Task → Arguments → Program Arguments**. The ASCII vulnerability template avoids Unicode drawing issues on the Windows Eclipse console.

## Known boundaries

- No automatic container-image or registry scanning.
- No full secret scanner output in the custom template.
- No automatic build/packaging when an explicit target is configured.
- In the development branch, a directory scan can include artifacts and binaries unrelated to the application; review findings by target.
- Windows and Linux CI validate Gradle 8.14.3, 9.1.0 and 9.9.0; macOS and Linux ARM64 installer support exists but is not in that matrix.
- Gradle 10 has not been validated.
- SHA-256 verification against official release files checks archive integrity; it is not independent code signing or attestation.
- Cached installations retain a verification marker; executable bytes are not rehashed on every invocation.
