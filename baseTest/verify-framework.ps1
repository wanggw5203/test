[CmdletBinding()]
param(
    [switch]$ReportOnly,
    [ValidateRange(0,100)][int]$MaximumSkippedPercent = 0,
    [ValidateRange(1,1440)][int]$MaximumReportAgeMinutes = 120,
    [string[]]$Modules = @('base-test-toolkit','enterprise-adapters','java-api-lab','enterprise-team-example')
)
$ErrorActionPreference = 'Stop'
if (-not $ReportOnly) {
    & mvn -B -ntp -f "$PSScriptRoot/pom.xml" clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Framework verification failed' }
}
$frameworkRows = @()
if (@($Modules | Select-Object -Unique).Count -ne $Modules.Count) { throw 'Duplicate expected modules' }
foreach ($frameworkModule in $Modules) {
    if ($frameworkModule -notmatch '^[a-zA-Z0-9][a-zA-Z0-9-]*$') { throw 'Invalid module name' }
    $frameworkReportDirectory = Join-Path $PSScriptRoot "$frameworkModule/target/surefire-reports"
    if (-not (Test-Path -LiteralPath $frameworkReportDirectory -PathType Container)) { throw "Missing reports: $frameworkModule" }
    $frameworkReports = @(Get-ChildItem -LiteralPath $frameworkReportDirectory -Filter 'TEST-*.xml' -File)
    if ($frameworkReports.Count -eq 0) { throw "Missing XML reports: $frameworkModule" }
    $frameworkTests = 0; $frameworkSkipped = 0
    foreach ($frameworkReport in $frameworkReports) {
        if ($frameworkReport.LastWriteTimeUtc -lt [DateTime]::UtcNow.AddMinutes(-$MaximumReportAgeMinutes)) { throw "Stale report: $frameworkModule" }
        $frameworkReaderSettings = [System.Xml.XmlReaderSettings]::new()
        $frameworkReaderSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
        $frameworkReaderSettings.XmlResolver = $null
        $frameworkXmlReader = [System.Xml.XmlReader]::Create($frameworkReport.FullName, $frameworkReaderSettings)
        try {
            $frameworkXml = [System.Xml.XmlDocument]::new(); $frameworkXml.XmlResolver = $null
            $frameworkXml.Load($frameworkXmlReader)
        } finally { $frameworkXmlReader.Dispose() }
        $frameworkSuite = $frameworkXml.DocumentElement
        if ($frameworkSuite.LocalName -ne 'testsuite') { throw "Unexpected report format: $frameworkModule" }
        foreach ($frameworkAttribute in @('tests','skipped','failures','errors')) {
            if ($frameworkSuite.GetAttribute($frameworkAttribute) -notmatch '^\d+$') { throw "Invalid report counts: $frameworkModule" }
        }
        $frameworkCases = @($frameworkSuite.SelectNodes('./testcase'))
        if ($frameworkCases.Count -ne [int]$frameworkSuite.GetAttribute('tests')) { throw "Report test counts differ: $frameworkModule" }
        foreach ($frameworkNode in @('skipped','failure','error')) {
            $frameworkActualCount = @($frameworkCases | Where-Object { $null -ne $_.SelectSingleNode("./$frameworkNode") }).Count
            $frameworkCountAttribute = switch ($frameworkNode) { 'failure' {'failures'} 'error' {'errors'} default {'skipped'} }
            if ($frameworkActualCount -ne [int]$frameworkSuite.GetAttribute($frameworkCountAttribute)) { throw "Report result counts differ: $frameworkModule" }
        }
        $frameworkTests += [int]$frameworkSuite.GetAttribute('tests')
        $frameworkSkipped += [int]$frameworkSuite.GetAttribute('skipped')
        if ([int]$frameworkSuite.GetAttribute('failures') -gt 0 -or [int]$frameworkSuite.GetAttribute('errors') -gt 0) { throw "Failed tests: $frameworkModule" }
    }
    if ($frameworkTests -le $frameworkSkipped) { throw "No non-skipped tests executed: $frameworkModule" }
    if (100.0 * $frameworkSkipped / $frameworkTests -gt $MaximumSkippedPercent) { throw "Skip allowance exceeded: $frameworkModule" }
    $frameworkRows += [ordered]@{module=$frameworkModule;tests=$frameworkTests;skipped=$frameworkSkipped;executed=$frameworkTests-$frameworkSkipped}
    Write-Output "$frameworkModule : $frameworkTests tests verified ($frameworkSkipped skipped)"
}
if ($frameworkRows.Count -eq 0) { throw 'No modules selected for report gate' }
$frameworkSummaryDirectory = Join-Path $PSScriptRoot 'target'
$null = New-Item -ItemType Directory -Path $frameworkSummaryDirectory -Force
[ordered]@{schemaVersion=1;verifiedAt=[DateTime]::UtcNow.ToString('o');maximumSkippedPercent=$MaximumSkippedPercent;modules=$frameworkRows} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $frameworkSummaryDirectory 'verification-summary.json') -Encoding utf8
