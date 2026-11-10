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

    /** Keep the published behavior: scan the built bootJar/jar using rootfs. */
    String scanMode = 'rootfs'
    /** Empty: build and scan bootJar/jar. Set '.' or a file path for an explicit scan target. */
    String target = ''
    String scanners = 'vuln'
    String severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    boolean ignoreUnfixed = false
}
