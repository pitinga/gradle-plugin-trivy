# Usage examples — Gradle Trivy Plugin 0.1.4

[README](../README.md) · [Configuration](CONFIGURATION.md) · [Troubleshooting](TROUBLESHOOTING.md)

All examples use **`io.github.pitinga.trivy` version `0.1.4`** and the native `table` or `json` report formats. Add the plugin to the existing project; do not replace the application's other plugin declarations.

## Spring Boot: build and scan the executable JAR

~~~groovy
plugins {
    id 'org.springframework.boot' version 'YOUR_SPRING_BOOT_VERSION'
    id 'io.github.pitinga.trivy' version '0.1.4'
}
~~~

~~~powershell
.\gradlew trivy
~~~

With no explicit target, the plugin depends on `bootJar` and scans that JAR using `rootfs`. The native table is displayed in the Gradle/Eclipse console, with a Windows-compatible presentation when needed. CVEs do not fail the build by default.

## Plain Java: scan a regular JAR

~~~groovy
plugins {
    id 'java'
    id 'io.github.pitinga.trivy' version '0.1.4'
}
~~~

~~~powershell
.\gradlew trivy
~~~

The plugin depends on `jar` and scans the built artifact with `rootfs`.

## Native JSON saved to a report file

Groovy DSL:

~~~groovy
trivy {
    format = 'json'
    outputFile = 'build/reports/trivy.json'
}
~~~

Or a one-off PowerShell execution:

~~~powershell
.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'
~~~

The file contains Trivy's **native UTF-8 JSON**, with package paths and vulnerability details. When a report file is set, Trivy sends the complete report to that file rather than duplicating it in the console.

## Native table saved to a file

~~~powershell
.\gradlew trivy '-PtrivyFormat=table' '-PtrivyOutput=build/reports/trivy-table.txt'
~~~

The saved file preserves Trivy's original Unicode table borders and full descriptions, even on Windows. For the normal console presentation, omit `-PtrivyOutput`.

## Customize Trivy CLI selection

~~~groovy
trivy {
    version = 'latest'
    preferInstalled = false
    autoInstall = true
}
~~~

This bypasses Trivy from PATH, selecting a cached or downloaded official release. The plugin version in the `plugins` block remains `0.1.4`.

## Security gate: fail on HIGH or CRITICAL CVEs with an available fix

~~~groovy
trivy {
    scanners = 'vuln'
    severity = 'HIGH,CRITICAL'
    ignoreUnfixed = true
    failOnVulnerability = true
    format = 'json'
    outputFile = 'build/reports/trivy.json'
}
~~~

~~~powershell
.\gradlew trivy --warning-mode all
~~~

When Trivy reports matching vulnerabilities, the task fails. Actual command failures also fail. Evaluate the security context before deciding which severities and exclusions to use.

## Scan the entire project directory

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.'
~~~

With an explicit `target = '.'`, the plugin does **not** depend on `bootJar` automatically. This command builds it explicitly before scanning existing files. For a plain Java project, invoke `jar` instead of `bootJar`.

The plugin skips its own managed Trivy binary cache when that cache lives inside the directory being scanned.

## Scan dependency metadata with fs

~~~powershell
.\gradlew trivy '-PtrivyScanMode=fs' '-PtrivyTarget=.'
~~~

`fs .` can miss compiled Java JAR dependencies that `rootfs` detects in the packaged JAR. Do not assume an empty result is proof of safety.

## Scan an existing JAR by path

~~~powershell
.\gradlew trivy '-PtrivyTarget=build/libs/application.jar'
~~~

The JAR must already exist, and `rootfs` remains the default. If it needs rebuilding, run `bootJar` or `jar` first.

## Exclude selected directories

~~~groovy
trivy {
    target = '.'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

Or, for one execution:

~~~powershell
.\gradlew trivy '-PtrivyTarget=.' '-PtrivySkipDirs=.gradle/trivy,build/tmp,**/node_modules'
~~~

The `-PtrivySkipDirs` argument replaces the entire DSL list. Exclusions reduce coverage.

## Eclipse / Buildship

1. Refresh the Gradle project.
2. Open **Run → Run Configurations → Gradle Task** (or the Gradle Tasks view) and choose task `trivy`.
3. Place any scan/report overrides in **Program Arguments**; for example:
   ~~~text
   --isolated-projects -PtrivyFormat=json -PtrivyOutput=build/reports/trivy.json
   ~~~
4. Run. The console shows the plugin/Trivy/Gradle/Java version banner and Trivy INFO messages as normal output, without making informational lines red. When no `outputFile` is provided, Windows terminals display the native Trivy table using safe ASCII borders — **no chcp, PowerShell encoding change, or Eclipse configuration is needed**.

On Gradle 9.9, repeat `trivy --isolated-projects` to verify `Reusing configuration cache`. The scan must execute again even if Gradle reuses configuration.

## Azure DevOps (Linux)

~~~yaml
pool:
  name: managed-agent-pool-linux

steps:
  - checkout: self
    displayName: Checkout
    fetchDepth: 0

  - task: Bash@3
    displayName: 'Trivy | Native JSON security report'
    inputs:
      targetType: inline
      script: |
        chmod +x ./gradlew
        ./gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json' --no-daemon --warning-mode all

  - task: PublishBuildArtifacts@1
    displayName: Publish Trivy JSON
    condition: always()
    inputs:
      PathtoPublish: '$(Build.SourcesDirectory)/build/reports/trivy.json'
      ArtifactName: trivy-report
      publishLocation: Container
~~~

The project must already apply plugin version `0.1.4`. Configure `failOnVulnerability` in the DSL if CI should fail on findings. Artifact publishing requires an actual generated report.

## GitHub Actions

~~~yaml
steps:
  - uses: actions/checkout@v7
  - uses: actions/setup-java@v6
    with:
      distribution: temurin
      java-version: '17'
  - uses: gradle/actions/setup-gradle@v6
    with:
      gradle-version: '9.9.0'
      cache-provider: basic
  - run: ./gradlew trivy --no-daemon --warning-mode all
~~~

## Source checkout for development

To test a local fork/branch rather than the Portal plugin, add to the **consumer's** `settings.gradle`:

~~~groovy
pluginManagement {
    includeBuild('C:/DBServer/eclipse-workspace/gradle-plugin-trivy')
}
~~~

Use `id 'io.github.pitinga.trivy'` **without a version** in the consumer's `plugins` block, then refresh Gradle. Remove `includeBuild` and restore `version '0.1.4'` to use the published release.

[All settings and defaults](CONFIGURATION.md).
