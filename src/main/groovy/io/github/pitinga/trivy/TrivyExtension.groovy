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

    /** Defaults match the original manual Gradle task. */
    String scanMode = 'rootfs'
    String scanners = 'vuln'
    String severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    boolean ignoreUnfixed = false
}
