# Trivy output lab — separate experimental branch

> **Branch:** `experiment/output-formats-console-file`
>
> **This is NOT published.** The `main` branch, existing GitHub release tags, and Gradle Plugin Portal are unchanged. The original **custom ASCII console** report remains the default when no new option is set.

## Why this experiment exists

In Eclipse on Windows, the native Trivy `table` report previously showed corrupted box-drawing characters (`â”‚` instead of `│`). The plugin's original plain-ASCII template displayed the CVEs correctly, which is why it remains the default. This lab deliberately tests **different output paths** rather than assuming the problem can be solved by changing the Windows console code page. The native Trivy CLI supports `--format table`, `--format json`, and `--output <file>`.

## Parameters

The following three options can be set in the `trivy { ... }` extension or overridden individually with Gradle's `-P` arguments:

| Gradle DSL | PowerShell argument | Default | Allowed values |
| --- | --- | --- | --- |
| `format` | `-PtrivyFormat=...` | `custom` | `custom`, `table`, `json` |
| `outputFile` | `-PtrivyOutput=...` | empty | Relative to **Gradle project directory**, or absolute path |
| `outputStrategy` | `-PtrivyOutputStrategy=...` | `direct` | `direct`, `capture`, `file`, `ascii` |

`custom` is the same ASCII vulnerability template used in the released plugin, not an alias for the CLI's built-in table. All formats keep the existing mode, target, severity, skipDirs and failure behavior.

### What each strategy does

| Strategy | No outputFile | With outputFile | Why test it |
| --- | --- | --- | --- |
| **direct** | Trivy writes directly to Gradle's console | Trivy uses native `--output <file>`; result is **file only** | Baseline; exposes the previously corrupted Eclipse table output |
| **capture** | Capture raw stdout bytes, explicitly decode UTF-8, log text through Gradle | Same console display **plus exact original UTF-8 bytes** saved to file | Tests whether Gradle text logging solves the encoding issue |
| **file** | Trivy uses native `--output` to a temporary file, reads UTF-8, and logs text | Trivy writes directly to the chosen file and the plugin **also displays its UTF-8 text** | Tests whether decoding a report from disk solves the encoding issue |
| **ascii** | Captures UTF-8, displays simplified ASCII table borders | Saves untouched UTF-8 report to disk; displays ASCII borders in console | Guaranteed non-Unicode table borders, useful if Eclipse can't display UTF-8 consistently |

`ascii` simplifies border characters **only when `format = 'table'`**. It does not alter JSON or the bytes written to a file. The conversion can make some table borders less attractive, but should avoid `â”‚` and similar mojibake in the console.

An `outputFile` does not automatically imply the result is shown in the console when using `direct`: this is normal Trivy behavior. Choose `capture`, `file`, or `ascii` to get both file and console output.

## PowerShell 7 — try single commands

Keep the source from the **experimental branch** applied to your test project through a composite build (see below), then run:

~~~powershell
# 0. Default unchanged: built JAR + original ASCII report
.\gradlew trivy

# 1. Original Trivy table directly to console (known mojibake candidate)
.\gradlew trivy '-PtrivyFormat=table'

# 2. Table captured as UTF-8, displayed and saved
.\gradlew trivy '-PtrivyFormat=table' '-PtrivyOutputStrategy=capture' '-PtrivyOutput=build/reports/table-capture.txt'

# 3. Native Trivy writes table to file; Gradle reads file as UTF-8 and displays it
.\gradlew trivy '-PtrivyFormat=table' '-PtrivyOutputStrategy=file' '-PtrivyOutput=build/reports/table-file.txt'

# 4. Table with ASCII console borders and original UTF-8 file
.\gradlew trivy '-PtrivyFormat=table' '-PtrivyOutputStrategy=ascii' '-PtrivyOutput=build/reports/table-ascii.txt'

# 5. Proper JSON in a file — Trivy native --output
.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutput=build/reports/trivy.json'

# 6. JSON to both console and file
.\gradlew trivy '-PtrivyFormat=json' '-PtrivyOutputStrategy=capture' '-PtrivyOutput=build/reports/trivy-both.json'
~~~

Or use Groovy DSL:

~~~groovy
trivy {
    format = 'json'
    outputFile = 'build/reports/trivy.json'
    outputStrategy = 'direct'
}
~~~

All relative file paths are resolved from the consumer project's directory. In PowerShell, quote the **entire** `-P` argument.

## Run the whole test matrix

Clone or download this experimental branch, then from the repository root:

~~~powershell
# All 10 variants: console logs + saved reports + summary.csv
pwsh -File .\scripts\Test-Trivy-Output.ps1

# Six representative variants, faster:
pwsh -File .\scripts\Test-Trivy-Output.ps1 -Fast
~~~

This uses the included `lab-output` Gradle consumer with a deliberately vulnerable historical RabbitMQ Java dependency packaged as a nested JAR. It is a disposable **test fixture** and must never be copied into production dependencies.

The script runs one scan at a time to avoid Trivy database-cache lock contention, but **all comparisons are generated in one command**. Each experiment saves a separate `*.console.log` and an optional `*.json` or `*.txt` file to:

~~~text
lab-output/build/trivy-output-lab/<timestamp>/
  00-custom-original-console.console.log
  02-table-native-console.console.log
  04-table-capture-utf8.console.log
  04-table-capture-utf8.txt
  05-table-file-utf8.console.log
  05-table-file-utf8.txt
  06-table-ascii-console.console.log
  06-table-ascii-console.txt
  07-json-native-file.json
  summary.csv
~~~

You can also run this script against your **existing Spring Boot test project** (after you connect it to the experimental plugin source):

~~~powershell
pwsh -File .\scripts\Test-Trivy-Output.ps1 -ProjectPath 'C:\DBServer\eclipse-workspace\exemplo-spring-zs'
~~~

This does not rewrite any source file in your Spring Boot project. It writes only Gradle build outputs and report evidence under that project's `build/trivy-output-lab`. The project itself must already use this experimental branch of the plugin.

### Connect an Eclipse/Gradle consumer to this experimental branch

1. Download this branch as ZIP from GitHub's branch page (**Code → Download ZIP**), or clone it:
   ~~~powershell
   git clone --branch experiment/output-formats-console-file --single-branch https://github.com/pitinga/gradle-plugin-trivy.git
   ~~~
2. In the consumer project's existing `settings.gradle`, add inside its **existing** `pluginManagement { ... }` (or create it at the top if absent):
   ~~~groovy
   pluginManagement {
       includeBuild('C:/DBServer/eclipse-workspace/gradle-plugin-trivy')
   }
   ~~~
   Adjust this path to the experimental checkout.
3. In the consumer's `plugins` block, use `id 'io.github.pitinga.trivy'` **without any Portal version**. Keep its other plugins.
4. Refresh the Gradle project in Eclipse. Test the commands above, or place `-PtrivyFormat=table -PtrivyOutputStrategy=capture` in your Gradle Run Configuration's **Program Arguments**.

If the console reports `Gradle Trivy Plugin : 0.1.4-SNAPSHOT`, it has loaded the experiment. If it shows the previously published version or doesn't accept `-PtrivyFormat`, it is not using the branch.

## What counts as success?

- The old **custom/direct** result must continue detecting the same CVEs as the original plugin.
- Each JSON report must parse as JSON and include Trivy's `SchemaVersion` and `Results` where applicable.
- Native table reports saved to file should contain correct Unicode characters when opened with a UTF-8-aware editor, even if direct Eclipse console output is corrupted.
- Compare the **same CVE IDs** across custom, table and JSON outputs. Display differences must not change the underlying security findings.
- Try all three table display strategies inside Eclipse. Record whether `capture`, `file` or `ascii` is legible.

`--format table` may render Unicode correctly in Windows Terminal while Eclipse displays mojibake; this distinction is exactly what we're testing. The experiments do not change the Trivy vulnerability database or how the scan target is selected.

## Operational notes

- Large JSON or table reports in **capture** mode are kept in memory before being printed; use `direct` with an output file for very large scans.
- Files preserve original bytes in `direct`, `capture`, `file`, and `ascii`. The ASCII transformation affects the **console only**.
- The plugin does not overwrite the scanned target as a report file.
- Gradle execution still fails when Trivy fails; `failOnVulnerability` still controls exit code on findings.
- No output data is uploaded, published, or committed automatically by the PowerShell script. The automated CI workflow uploads short-lived test artifacts only from the lab's synthetic fixture.

**This is an experiment.** Keep the changes on this branch until we have your real Windows/Eclipse results. If it fails, deleting the branch leaves `main` and all public releases untouched.
