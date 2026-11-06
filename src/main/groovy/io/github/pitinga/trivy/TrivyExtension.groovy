package io.github.pitinga.trivy

/**
 * Groovy DSL configuration for the Trivy Gradle plugin.
 */
class TrivyExtension {
    /** Version used for downloads; an installed PATH binary takes precedence by default. */
    String version = '0.76.2'
    boolean autoInstall = true
    boolean preferInstalled = true
    boolean failOnVulnerability = false

    /** Defaults match the original manual Gradle task. */
    String scanMode = 'rootfs'
    String scanners = 'vuln'
    String severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    boolean ignoreUnfixed = false
}
