#requires -Version 7.0
<#
.SYNOPSIS
  Runs a local Trivy output matrix and stores independent console logs and reports.
.EXAMPLE
  pwsh -File .\scripts\Test-Trivy-Output.ps1
.EXAMPLE
  pwsh -File .\scripts\Test-Trivy-Output.ps1 -ProjectPath 'C:\DBServer\eclipse-workspace\exemplo-spring-zs' -Fast
.NOTES
  An external -ProjectPath must apply THIS checkout of the plugin through
  pluginManagement { includeBuild('path/to/this/branch') } without a version.
  Tests run SEQUENTIALLY to avoid Trivy database/download lock contention.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'lab-output'),
    [string]$GradleExecutable = '',
    [switch]$Fast
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$projectDir = (Resolve-Path -LiteralPath $ProjectPath).Path

$gradleCmd = if ($GradleExecutable) {
    $GradleExecutable
} elseif (Test-Path -LiteralPath (Join-Path $projectDir 'gradlew.bat')) {
    Join-Path $projectDir 'gradlew.bat'
} elseif (Test-Path -LiteralPath (Join-Path $projectDir 'gradlew')) {
    Join-Path $projectDir 'gradlew'
} else {
    'gradle'
}

$pluginBuildScript = Get-Content -LiteralPath (Join-Path $repoRoot 'build.gradle') -Raw
$match = [regex]::Match($pluginBuildScript, "(?m)^version\s*=\s*'([^']+)'")
if (-not $match.Success) { throw 'Cannot determine plugin source version' }
$pluginVersion = $match.Groups[1].Value

$cases = @(
    [pscustomobject]@{ Name='00-custom-original-console'; Format='custom'; Strategy='direct'; SaveFile=$false }
    [pscustomobject]@{ Name='01-custom-native-file';     Format='custom'; Strategy='direct'; SaveFile=$true }
    [pscustomobject]@{ Name='02-table-native-console';  Format='table';  Strategy='direct'; SaveFile=$false }
    [pscustomobject]@{ Name='03-table-native-file';     Format='table';  Strategy='direct'; SaveFile=$true }
    [pscustomobject]@{ Name='04-table-capture-utf8';    Format='table';  Strategy='capture';SaveFile=$true }
    [pscustomobject]@{ Name='05-table-file-utf8';       Format='table';  Strategy='file';   SaveFile=$true }
    [pscustomobject]@{ Name='06-table-ascii-console';   Format='table';  Strategy='ascii';  SaveFile=$true }
    [pscustomobject]@{ Name='07-json-native-file';      Format='json';   Strategy='direct'; SaveFile=$true }
    [pscustomobject]@{ Name='08-json-capture-utf8';     Format='json';   Strategy='capture';SaveFile=$true }
    [pscustomobject]@{ Name='09-json-file-utf8';        Format='json';   Strategy='file';   SaveFile=$true }
)
if ($Fast) {
    $only = @('00-', '02-', '04-', '05-', '06-', '07-')
    $cases = @($cases | Where-Object {
        $name = $_.Name
        @($only | Where-Object { $name.StartsWith($_) }).Count -gt 0
    })
}

$baseOutput = Join-Path $projectDir 'build/trivy-output-lab'
$resultsDir = Join-Path $baseOutput (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

Write-Host "Plugin source : $repoRoot"
Write-Host "Plugin version: $pluginVersion"
Write-Host "Gradle project: $projectDir"
Write-Host "Gradle command: $gradleCmd"
Write-Host "Results folder: $resultsDir"
Write-Host "Experiments   : $($cases.Count)"
Write-Host 'Each experiment invokes the actual Trivy scan (no parallel database access).'
Write-Host ''

$results = @()
$baselineCveIds = $null
foreach ($case in $cases) {
    $name = $case.Name
    $logFile = Join-Path $resultsDir ($name + '.console.log')
    $extension = if ($case.Format -eq 'json') { 'json' } else { 'txt' }
    $reportFile = Join-Path $resultsDir ($name + '.' + $extension)

    $argsList = @(
        '-p', $projectDir,
        'trivy',
        ('-PtrivyFormat=' + $case.Format),
        ('-PtrivyOutputStrategy=' + $case.Strategy),
        '--no-daemon', '--warning-mode', 'all'
    )
    if ($case.SaveFile) {
        $argsList += '-PtrivyOutput=' + $reportFile
    }

    Write-Host "========== $name =========="
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $exitCode = -1
    try {
        & $gradleCmd @argsList *> $logFile
        $exitCode = $LASTEXITCODE
    } catch {
        "PowerShell execution error: $($_.Exception.Message)" |
            Out-File -LiteralPath $logFile -Encoding utf8 -Append
    } finally {
        $watch.Stop()
    }

    $logText = if (Test-Path -LiteralPath $logFile) {
        Get-Content -LiteralPath $logFile -Raw -Encoding utf8
    } else { '' }

    $saved = $case.SaveFile -and (Test-Path -LiteralPath $reportFile)
    $jsonValid = $null
    if ($saved -and $case.Format -eq 'json') {
        try {
            $json = Get-Content -LiteralPath $reportFile -Raw -Encoding utf8 | ConvertFrom-Json
            $jsonValid = $null -ne $json -and $json.PSObject.Properties.Name -contains 'SchemaVersion'
        } catch {
            $jsonValid = $false
        }
    }

    # Compare unique vulnerability IDs across all representations, not just exit codes.
    $reportText = if ($saved) { Get-Content -LiteralPath $reportFile -Raw -Encoding utf8 } else { $logText }
    if ($case.Format -eq 'json' -and $saved -and $jsonValid) {
        $reportJson = $reportText | ConvertFrom-Json
        $cveIds = @($reportJson.Results | ForEach-Object { $_.Vulnerabilities } |
            Where-Object { $null -ne $_ } | ForEach-Object { $_.VulnerabilityID } |
            Where-Object { $_ -match '^CVE-\d{4}-\d{4,}$' } | Sort-Object -Unique)
    } else {
        $cveIds = @([regex]::Matches($reportText, 'CVE-\d{4}-\d{4,}') |
            ForEach-Object { $_.Value } | Sort-Object -Unique)
    }
    if ($null -eq $baselineCveIds -and $case.Name.StartsWith('00-')) {
        $baselineCveIds = $cveIds
    }
    $sameCves = if ($null -eq $baselineCveIds) {
        $null
    } elseif (@($baselineCveIds).Count -eq 0 -and @($cveIds).Count -eq 0) {
        $true
    } elseif (@($baselineCveIds).Count -eq 0 -or @($cveIds).Count -eq 0) {
        $false
    } else {
        @(Compare-Object -ReferenceObject @($baselineCveIds) -DifferenceObject @($cveIds)).Count -eq 0
    }

    $versionShown = $logText.Contains("Gradle Trivy Plugin : $pluginVersion")
    $status = if ($exitCode -eq 0 -and (-not $case.SaveFile -or $saved) -and $versionShown -and ($jsonValid -ne $false) -and ($sameCves -ne $false)) {
        'OK'
    } else {
        'CHECK'
    }
    $result = [pscustomobject]@{
        Case = $name
        Format = $case.Format
        Strategy = $case.Strategy
        File = $case.SaveFile
        ExitCode = $exitCode
        Status = $status
        PluginVersionSeen = $versionShown
        JsonValid = $jsonValid
        CVEs = @($cveIds).Count
        SameCVEsAsOriginal = $sameCves
        Seconds = [math]::Round($watch.Elapsed.TotalSeconds, 1)
        ConsoleLog = $logFile
        Report = if ($saved) { $reportFile } else { '' }
    }
    $results += $result
    Write-Host ("{0} | Exit={1} | {2}s | Saved={3} | PluginVersion={4}" -f
        $status, $exitCode, $result.Seconds, $saved, $versionShown)
}

$summaryFile = Join-Path $resultsDir 'summary.csv'
$results | Export-Csv -LiteralPath $summaryFile -NoTypeInformation -Encoding utf8
$results | Format-Table Case, Format, Strategy, File, Status, CVEs, SameCVEsAsOriginal, Seconds -AutoSize
Write-Host "Saved comparison: $summaryFile"
Write-Host "Inspect .console.log for Eclipse/Windows table rendering and .txt/.json for original UTF-8."
if (@($results | Where-Object Status -ne 'OK').Count -gt 0) {
    Write-Warning 'One or more cases require inspection. Original console output is kept separately.'
    exit 1
}
