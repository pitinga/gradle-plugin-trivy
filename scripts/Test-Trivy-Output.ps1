#requires -Version 7.0
<#
.SYNOPSIS
    Test native Trivy table/JSON output in Gradle without changing the console,
    Windows code page, Eclipse preferences, or any global environment setting.
.EXAMPLE
    .\Test-Trivy-Output.ps1
.EXAMPLE
    .\Test-Trivy-Output.ps1 -GradleExecutable 'C:\Work\project\gradlew.bat'
.EXAMPLE
    .\Test-Trivy-Output.ps1 -ProjectPath 'C:\Work\spring-app'
.NOTES
    Existing projects must includeBuild this experimental plugin source.
    Scans run sequentially to avoid Trivy database lock contention.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'lab-output'),
    [string]$GradleExecutable = '',
    [switch]$Fast
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$projectDir = (Resolve-Path -LiteralPath $ProjectPath).Path
$gradleCmd = if ($GradleExecutable) {
    $GradleExecutable
} elseif (Test-Path (Join-Path $projectDir 'gradlew.bat')) {
    Join-Path $projectDir 'gradlew.bat'
} elseif (Test-Path (Join-Path $projectDir 'gradlew')) {
    Join-Path $projectDir 'gradlew'
} else {
    'gradle'
}
$pluginBuild = Get-Content (Join-Path $root 'build.gradle') -Raw
$versionMatch = [regex]::Match($pluginBuild, "(?m)^version\s*=\s*'([^']+)'")
if (-not $versionMatch.Success) { throw 'Cannot find Gradle plugin source version' }
$pluginVersion = $versionMatch.Groups[1].Value
$caseList = @(
    [pscustomobject]@{Name='00-table-default-console'; Format='table'; File=$false}
    [pscustomobject]@{Name='01-table-explicit-console'; Format='table'; File=$false}
    [pscustomobject]@{Name='02-table-native-file'; Format='table'; File=$true}
    [pscustomobject]@{Name='03-json-console'; Format='json'; File=$false}
    [pscustomobject]@{Name='04-json-native-file'; Format='json'; File=$true}
)
if ($Fast) { $caseList = @($caseList | Where-Object { $_.Name -in @('00-table-default-console','02-table-native-file','04-json-native-file') }) }

$runDir = Join-Path (Join-Path $projectDir 'build/trivy-output-lab') (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

Write-Host "Plugin: $pluginVersion  |  Project: $projectDir"
Write-Host "Gradle: $gradleCmd"
Write-Host "Results: $runDir"
Write-Host 'No terminal code-page or Eclipse settings are changed.'
Write-Host ''

$baselineIds = $null
$rows = @()
foreach ($case in $caseList) {
    $logFile = Join-Path $runDir ($case.Name + '.console.log')
    $extension = if ($case.Format -eq 'json') { 'json' } else { 'txt' }
    $reportFile = Join-Path $runDir ($case.Name + '.' + $extension)
    $params = @('-p', $projectDir, 'trivy', '--no-daemon', '--warning-mode', 'all')
    if ($case.Name -ne '00-table-default-console') {
        $params += '-PtrivyFormat=' + $case.Format
    }
    if ($case.File) {
        $params += '-PtrivyOutput=' + $reportFile
    }

    Write-Host "========== $($case.Name) =========="
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $exitCode = -1
    try {
        & $gradleCmd @params *> $logFile
        $exitCode = $LASTEXITCODE
    } catch {
        "PowerShell error: $($_.Exception.Message)" |
            Out-File -LiteralPath $logFile -Encoding utf8 -Append
    } finally {
        $timer.Stop()
    }

    $logText = if (Test-Path $logFile) { Get-Content $logFile -Raw -Encoding utf8 } else { '' }
    $saved = $case.File -and (Test-Path $reportFile)
    $reportText = if ($saved) { Get-Content $reportFile -Raw -Encoding utf8 } else { $logText }

    $validJson = $null
    if ($saved -and $case.Format -eq 'json') {
        try {
            $json = $reportText | ConvertFrom-Json
            $validJson = $null -ne $json -and $json.SchemaVersion -ge 1
        } catch { $validJson = $false }
    }

    $idSet = @([regex]::Matches($reportText, 'CVE-\d{4}-\d{4,}') |
        ForEach-Object { $_.Value } | Sort-Object -Unique)
    if ($null -eq $baselineIds -and $case.Name.StartsWith('00-')) { $baselineIds = $idSet }
    $same = if ($null -eq $baselineIds) { $null
    } elseif (@($baselineIds).Count -eq 0 -and @($idSet).Count -eq 0) { $true
    } elseif (@($baselineIds).Count -eq 0 -or @($idSet).Count -eq 0) { $false
    } else {
        @(Compare-Object -ReferenceObject @($baselineIds) -DifferenceObject @($idSet)).Count -eq 0
    }

    $nativeTableFile = $null
    if ($saved -and $case.Format -eq 'table') {
        # The on-disk output should retain Trivy's native Unicode borders.
        $nativeTableFile = $reportText.Contains('┌') -and $reportText.Contains('│')
    }

    $portableConsole = $null
    if ($IsWindows -and -not $case.File) {
        $start = $logText.IndexOf("Report format: $($case.Format) (console)")
        if ($start -ge 0) {
            $end = $logText.IndexOf('BUILD SUCCESSFUL', $start)
            $block = if ($end -ge 0) { $logText.Substring($start, $end - $start) } else { $logText.Substring($start) }
            $portableConsole = $block -cnotmatch '[^\x00-\x7F]'
        }
    }

    $versionShown = $logText.Contains("Gradle Trivy Plugin : $pluginVersion")
    $status = if ($exitCode -eq 0 -and $versionShown -and
        (-not $case.File -or $saved) -and
        ($same -ne $false) -and ($validJson -ne $false) -and
        ($nativeTableFile -ne $false) -and ($portableConsole -ne $false)) { 'OK' } else { 'CHECK' }

    $row = [pscustomobject]@{
        Case = $case.Name
        Format = $case.Format
        File = $case.File
        Status = $status
        CVEs = @($idSet).Count
        SameCVEs = $same
        ConsoleAscii = $portableConsole
        NativeUtf8File = $nativeTableFile
        JsonValid = $validJson
        Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
        ExitCode = $exitCode
        ConsoleLog = $logFile
        Report = if ($saved) { $reportFile } else { '' }
    }
    $rows += $row
    Write-Host ("{0} | CVEs={1} | Exit={2} | {3}s | Ascii={4}" -f
        $status, $row.CVEs, $exitCode, $row.Seconds, $portableConsole)
}

$csv = Join-Path $runDir 'summary.csv'
$rows | Export-Csv $csv -NoTypeInformation -Encoding utf8
$rows | Format-Table Case, Status, Format, File, CVEs, SameCVEs, ConsoleAscii, NativeUtf8File, JsonValid, Seconds -AutoSize
Write-Host "Summary: $csv"
if (@($rows | Where-Object Status -ne 'OK').Count -gt 0) {
    Write-Warning 'At least one output requires inspection; logs have been retained.'
    exit 1
}
