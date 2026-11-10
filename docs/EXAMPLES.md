# Practical usage examples

[Back to README](../README.md) | [Configuration](CONFIGURATION.md) | [Troubleshooting](TROUBLESHOOTING.md)

**Version labels matter:** published `0.1.1` users can run the default JAR scan and configure existing extension fields. `target`, `skipDirs` and scan-specific `-P` overrides belong to **0.1.2+**, used through the composite-build instructions in the README.

## 1. Scan a Spring Boot application (published 0.1.1+)

Add the plugin to your **existing** Spring Boot build:

~~~groovy
plugins {
    id 'org.springframework.boot' version 'YOUR_EXISTING_SPRING_BOOT_VERSION'
    id 'io.github.pitinga.trivy' version '0.1.1'
}
~~~

~~~powershell
.\gradlew trivy
~~~

The plugin runs `bootJar`, installs/locates Trivy, and scans the resulting JAR with `rootfs`. If the Spring Boot plugin is already declared, add only the Trivy plugin line. The default scan does not fail the build when it finds CVEs.

## 2. Standard Java project (published 0.1.1+)

~~~groovy
plugins {
    id 'java'
    id 'io.github.pitinga.trivy' version '0.1.1'
}
~~~

~~~powershell
.\gradlew trivy
~~~

The plugin builds and scans `jar`.

## 3. Fail a pipeline when HIGH/CRITICAL vulnerabilities are found (published 0.1.1+)

~~~groovy
trivy {
    scanners = 'vuln'
    severity = 'HIGH,CRITICAL'
    ignoreUnfixed = true
    failOnVulnerability = true
}
~~~

~~~powershell
.\gradlew trivy --warning-mode all
~~~

A nonzero exit can also indicate a download/scan error. Inspect logs before interpreting an exit status.

## 4. Force the latest stable Trivy binary (published 0.1.1+)

~~~groovy
trivy {
    version = 'latest'
    preferInstalled = false
}
~~~

This setting bypasses the installed binary in PATH. The Gradle plugin version `0.1.1` is distinct from the Trivy CLI version.

## 5. Scan everything under the project with rootfs (development only)

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=.'
~~~

Here `bootJar` is explicit, because when `target` is set the plugin will **not** build an artifact automatically. This mode can find vulnerabilities in pre-existing generated JARs and other supported binaries. It automatically excludes the plugin-managed Trivy cache if present inside the scan directory.

## 6. Scan project metadata with fs (development only)

~~~powershell
.\gradlew trivy '-PtrivyScanMode=fs' '-PtrivyTarget=.'
~~~

In some Java projects this identifies zero language-specific files, even though the built JAR contains vulnerable dependencies. For packaged Java dependencies, prefer the default `rootfs` JAR scan.

## 7. Scan a specific JAR (development only)

~~~powershell
.\gradlew bootJar trivy '-PtrivyTarget=build/libs/app.jar'
~~~

Substitute your actual artifact filename. The plugin uses `rootfs` by default. For an absolute path, pass the full Windows or Linux path in the quoted `-P` argument.

## 8. Exclude caches or tooling (development only)

~~~groovy
trivy {
    target = '.'
    scanMode = 'rootfs'
    skipDirs = ['.gradle/trivy', 'build/tmp', '**/node_modules']
}
~~~

Or use PowerShell 7 to override the directories for one invocation:

~~~powershell
.\gradlew trivy '-PtrivyTarget=.' '-PtrivySkipDirs=.gradle/trivy,build/tmp,**/node_modules'
~~~

The CLI list **replaces** the DSL list. Excluding a directory changes the coverage; it does not remediate its vulnerabilities.

## 9. Eclipse / Buildship

1. Import/refresh the Gradle project in Eclipse.
2. Use the **Gradle Tasks** view to execute `trivy`.
3. For command arguments, open **Run → Run Configurations → Gradle Task → Arguments** and set **Program Arguments** (e.g., `--warning-mode all`).
4. The plugin uses a plain-text ASCII report to avoid corrupted box-drawing characters in the Eclipse Console.

Only on version 0.1.2+, `'-PtrivyTarget=.'` and other scan overrides may be passed through Program Arguments. Do **not** put Gradle options in **JVM Arguments**.

## 10. Azure DevOps pipeline

Example for a Linux agent where the project already applies the published plugin:

~~~yaml
pool:
  name: managed-agent-pool-linux

steps:
  - checkout: self
    displayName: 'Checkout'
    fetchDepth: 0

  - task: Bash@3
    displayName: 'Trivy | Scan Java artifact'
    inputs:
      targetType: inline
      script: |
        chmod +x ./gradlew
        ./gradlew trivy --no-daemon --warning-mode all
~~~

The agent must have a compatible Java runtime and network/database access for Trivy. For a security gate, configure `failOnVulnerability = true` in `build.gradle`. With version 0.1.2+, command-line scan overrides can also be supplied.

## 11. GitHub Actions

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
  - run: gradle trivy --no-daemon --warning-mode all
~~~

Both CI examples assume the repository has already configured the plugin. They do not publish or modify any plugin version.
