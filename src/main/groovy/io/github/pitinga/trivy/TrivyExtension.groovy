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
    /** Optional Trivy --skip-dirs patterns. Empty by default for built-JAR scans. */
    List<String> skipDirs = []
    String scanners = 'vuln'
    String severity = 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL'
    boolean ignoreUnfixed = false

    /** Lab: original ASCII template (custom), native Trivy table, or JSON. */
    String format = 'custom'
    /** Lab: optional destination relative to the Gradle project, or an absolute path. */
    String outputFile = ''
    /** Lab: direct, capture (UTF-8), file (Trivy --output + UTF-8 read), or ascii. */
    String outputStrategy = 'direct'
}
