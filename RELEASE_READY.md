# Gradle Trivy Plugin v0.1.4 — release approved

User authorization: publication of v0.1.4 was explicitly requested after successful local Eclipse tests.

The README and all current usage guides now describe **only v0.1.4**. Historical releases remain available in CHANGELOG.md and their immutable release tags.

Release highlights:
- Native Trivy `table` as the default and native `json`; remove the old custom vulnerability template and experimental outputStrategy.
- Optional native UTF-8 reports using `outputFile` and `-PtrivyOutput`; format override using `-PtrivyFormat`.
- Windows/Eclipse codepage-safe ASCII console rendering without terminal configuration; retain exact Trivy output bytes in files.
- Trivy INFO diagnostics use regular Gradle output; WARN/ERROR and scan failures retain correct severity.
- Banner identifying plugin, Trivy CLI, Gradle and Java versions; retain automatic JAR rootfs scanning and Gradle Isolated Projects compatibility.

Validation completed on the main branch:
- Gradle compatibility (Windows/Linux, Gradle 8.14.3, 9.1.0, 9.9.0, JDK 17/25): 8/8 successful — https://github.com/pitinga/gradle-plugin-trivy/actions/runs/43034617895
- Gradle 9.9 Isolated Projects multi-project validation: 4/4 successful — https://github.com/pitinga/gradle-plugin-trivy/actions/runs/43034618185
- Native table/JSON reports, UTF-8 files, correct diagnostics: 2/2 successful — https://github.com/pitinga/gradle-plugin-trivy/actions/runs/43034617815
- User confirmed actual Eclipse/Gradle 9.9/Java 25 scans and Configuration Cache reuse.

This marker triggers the **gated release workflow**: verify stable 0.1.4 and run automated tests and Plugin Portal `publishPlugins --validate-only` using encrypted credentials; create an annotated immutable v0.1.4 Git tag plus a GitHub Release; then publish **the tagged source** to the Gradle Plugin Portal. Do not overwrite earlier release tags.

Reference: RELEASE_NOTES_0.1.4.md.
