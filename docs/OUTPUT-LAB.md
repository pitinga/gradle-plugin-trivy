# Native Trivy report validation — v0.1.4

[README](../README.md) · [Configuration](CONFIGURATION.md) · [Examples](EXAMPLES.md)

This is an integration test guide for **Gradle Trivy Plugin 0.1.4**. It uses the plugin source checkout with a disposable Java project under `lab-output/`, and does not require any published dependency or IDE configuration.

## Goals

- Default `table` and explicit `table` behave identically.
- Native `json` is produced correctly in console/file.
- On Windows consoles with legacy code pages, table borders display as portable ASCII while **Trivy's on-disk output stays native UTF-8**, with Unicode corners and full titles.
- Trivy `INFO` diagnostics appear on STDOUT rather than red STDERR. WARN/ERROR messages and nonzero exit codes remain visible.
- Switching formats or file output does not alter detected CVE identifiers.
- No `chcp`, PowerShell output encoding or Eclipse preference changes are needed.

## Quick manual test (PowerShell 7)

From the repository's `scripts` directory:

~~~powershell
gradle -p ..\lab-output trivy
gradle -p ..\lab-output trivy '-PtrivyFormat=table'
gradle -p ..\lab-output trivy '-PtrivyOutput=build/reports/trivy-table.txt'
gradle -p ..\lab-output trivy '-PtrivyFormat=json'
gradle -p ..\lab-output trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'
~~~

The laboratory JAR deliberately contains outdated Java dependencies **for testing only**. Do not introduce them into production applications.

## Automatic evidence collection

Run from the repository root:

~~~powershell
pwsh -File .\scripts\Test-Trivy-Output.ps1
~~~

Or use an external Gradle wrapper:

~~~powershell
pwsh -File .\scripts\Test-Trivy-Output.ps1 -GradleExecutable 'C:\Work\my-project\gradlew.bat'
~~~

Five scenarios run sequentially to avoid concurrent Trivy DB-cache access. Each produces an individual console log, optional native table/JSON report, and `summary.csv` in:

~~~text
lab-output/build/trivy-output-lab/<timestamp>/
~~~

The summary checks plugin-version detection, matching CVE IDs, JSON syntax, Windows-only ASCII console rendering, and UTF-8 table borders in the saved report.

## Eclipse validation

Run the `trivy` task from Buildship on a Java/Spring Boot consumer. The plugin source can be resolved through:

~~~groovy
pluginManagement {
    includeBuild('C:/DBServer/eclipse-workspace/gradle-plugin-trivy')
}
~~~

Use `id 'io.github.pitinga.trivy'` without a version when testing from the source checkout. Observe native Trivy table content, readable ASCII borders, no red INFO diagnostics, and the version banner. For a repeatable `--isolated-projects` test on Gradle 9.9, run twice and confirm configuration-cache reuse and repeated vulnerability scanning.

The same checks run in [GitHub Actions](../.github/workflows/output-lab.yml) on Windows and Linux. Actual vulnerability counts can change as Trivy's database updates; the expected invariant is **consistent identifiers across formats during the same test series**.
