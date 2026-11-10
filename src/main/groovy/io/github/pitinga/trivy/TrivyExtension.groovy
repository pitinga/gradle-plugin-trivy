package io.github.pitinga.trivy

/**
 * Groovy DSL configuration for the Trivy Gradle plugin.
 */
class TrivyExtension {
    /** Latest stable release for downloads/cache; an installed PATH binary takes precedence by default. */
    String version = 'latest'
    boolean autoInstall = true
    boolean preferInstalled = true
    boolean failOnVulnerability = false

    /** Scan the project directory with trivy fs . by default. */
    String scanMode = 'fs'
    /** Relative to the Gradle project directory (or an absolute path). */
    String target = '.'
    String scanners = 'vuln'
    String severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    boolean ignoreUnfixed = false
}
