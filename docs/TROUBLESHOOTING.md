# Troubleshooting — Gradle Trivy Plugin 0.1.4

[README](../README.md) · [Configuration](CONFIGURATION.md) · [Examples](EXAMPLES.md)

## Trivy table characters are corrupt in Windows, Eclipse, or PowerShell

Version **0.1.4** uses Trivy's **native table** format. Without a report file, Windows console output is automatically adapted to readable ASCII borders (`+`, `-`, `|`) to avoid incompatible legacy code pages. **No `chcp`, PowerShell output encoding, Eclipse preference or environment change is required.**

A native UTF-8 report file preserves Trivy's Unicode borders:

~~~powershell
.\gradlew trivy '-PtrivyOutput=build/reports/trivy-table.txt'
~~~

If you see Unicode/mojibake in the console, confirm the plugin version banner reads `Gradle Trivy Plugin : 0.1.4` and that you are not inadvertently loading an older Portal release or stale source checkout.

## INFO appears in red in Eclipse

Trivy itself sends normal `INFO` diagnostics to process STDERR. The plugin routes `INFO` and `DEBUG` through normal Gradle lifecycle messages; `WARN`, `ERROR` and unknown STDERR are still diagnostic. A nonzero Trivy exit code remains a failure. If you see red INFO lines, verify the 0.1.4 version banner.

## `output = '...'` fails as an unknown property

The configuration name is **`outputFile`**, not `output`:

~~~groovy
trivy {
    format = 'json'
    outputFile = 'build/reports/trivy.json'
}
~~~

One-off Gradle override: `-PtrivyOutput=build/reports/trivy.json`. The report path is relative to the consuming project directory unless absolute.

## Why did the full report disappear from the console after setting outputFile?

This is deliberate: Trivy CLI `--output` writes the report to the file. Gradle still shows task/version/scanner diagnostics. Remove `outputFile` or leave it blank to show the report on the console instead.

## Is there a custom format, outputStrategy or template option?

No. Version 0.1.4 supports Trivy's **native** `table` and `json` formats only. Set `format = 'table'` (default) or `format = 'json'`. The previous custom template and experimental `outputStrategy` options are not part of the current plugin.

## Build is successful even though HIGH CVEs were found

`failOnVulnerability = false` is the default. A vulnerability report does **not** fail the build unless the setting is enabled. For a security gate:

~~~groovy
trivy {
    severity = 'HIGH,CRITICAL'
    failOnVulnerability = true
}
~~~

Other Trivy CLI/runtime failures still fail the task.

## `trivy fs .` reports `num=0` but the JAR scan finds CVEs

`fs` on the source project can miss Java libraries present inside a compiled JAR. Use the recommended `rootfs` JAR scan with:

~~~powershell
.\gradlew trivy
~~~

A scan with zero findings does not establish that software is safe; scope and format matter.

## `trivy fs build/libs/application.jar` seems clean

For Java libraries packed inside a JAR, use `rootfs` (default). `fs` is not necessarily suited to compiled JAR dependency inspection.

## The requested scan target doesn't exist

An explicit `target` does **not** trigger compilation or packaging:

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=build/libs/YOUR_APPLICATION.jar'
~~~

Without an explicit target, the plugin automatically depends on `bootJar` or `jar`.

## Trivy detects its own cached executable

When scanning a directory, the plugin automatically excludes its own managed Trivy binary cache if physically located under that directory. You may additionally configure `skipDirs = ['.gradle/trivy']`. For the default single-JAR scan, no cache directory exclusion is needed.

## `version = 'latest'` still uses an older installed Trivy

The default `preferInstalled = true` gives priority to the CLI on PATH. To force the requested latest stable Trivy download/cache, use:

~~~groovy
trivy {
    version = 'latest'
    preferInstalled = false
}
~~~

The Gradle **plugin** version in `plugins { ... }` is separate from this Trivy **CLI** version.

## Shared Trivy cache isn't writable

A permission-related failure writing `GRADLE_USER_HOME/trivy` triggers a fallback to the root project's `.gradle/trivy`. This is expected. The Trivy vulnerability DB cache is separate.

## PowerShell reports `Task '.jar' not found`

Quote the entire `-P` argument, including its prefix:

~~~powershell
.\gradlew trivy '-PtrivyTarget=build/libs/application.jar'
~~~

## Isolated Projects fails before `:trivy` executes

A configuration error in another Gradle plugin or your consuming `build.gradle` can prevent the task graph from being created. Gradle 9.9 can report `Project ':' cannot access task dependencies directly` for code that inspects another task's dependencies while configuring outgoing publication artifacts. Correct the consumer build script or test the plugin in the standalone lab. Successful plugin execution with `--isolated-projects` should store a configuration cache entry, then reuse it on the next run.

## Secret scans

The default scanner is `vuln`. With the native Trivy formats, additional Trivy scanners can be configured using `scanners`, but their coverage depends on the selected Trivy version, scan mode and target. Review sensitive outputs carefully: secret-finding reports can contain sensitive information. Do not treat exclusions or missing results as remediation.

## Requirements and limits

- Java/Gradle must be compatible with each other. The CI matrix covers Gradle 8.14.3, 9.1.0 and 9.9.0 (JDK 17), and Gradle 9.9.0 with JDK 25, on Windows/Linux.
- Windows x64 and Linux x64 scanning is exercised in CI; other listed installer architectures are not in that matrix.
- Gradle 10 is untested.
- The plugin does not automatically aggregate all projects or scan container registries/images.
- SHA-256 verification against official release checksums verifies archive integrity, not independent cryptographic signing.
- Verified cached executable files are not re-hashed on every task execution.
