$ErrorActionPreference = 'Stop'
$reportTestWorkspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reportTestRoot = Join-Path $reportTestWorkspace ('target/report-gate-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path (Join-Path $reportTestRoot 'fixture/target/surefire-reports') -Force
Copy-Item -LiteralPath (Join-Path $reportTestWorkspace 'verify-framework.ps1') -Destination (Join-Path $reportTestRoot 'verify-framework.ps1')
$reportTestFile = Join-Path $reportTestRoot 'fixture/target/surefire-reports/TEST-fixture.xml'
$reportTestGate = Join-Path $reportTestRoot 'verify-framework.ps1'
function Assert-ReportRejected([string]$Label, [string]$Xml) {
    Set-Content -LiteralPath $reportTestFile -Value $Xml -Encoding utf8
    $reportWasRejected = $false
    try { & $reportTestGate -ReportOnly -Modules fixture | Out-Null } catch { $reportWasRejected = $true }
    if (-not $reportWasRejected) { throw "Report gate incorrectly accepted: $Label" }
    Write-Output "Rejected: $Label"
}
try {
    Set-Content -LiteralPath $reportTestFile -Value '<testsuite name="fixture" tests="1" skipped="0" failures="0" errors="0"><testcase name="ok"/></testsuite>' -Encoding utf8
    & $reportTestGate -ReportOnly -Modules fixture | Out-Null
    Assert-ReportRejected 'failure' '<testsuite tests="1" skipped="0" failures="1" errors="0"><testcase><failure/></testcase></testsuite>'
    Assert-ReportRejected 'all skipped' '<testsuite tests="1" skipped="1" failures="0" errors="0"><testcase><skipped/></testcase></testsuite>'
    Assert-ReportRejected 'partial skip' '<testsuite tests="2" skipped="1" failures="0" errors="0"><testcase/><testcase><skipped/></testcase></testsuite>'
    Assert-ReportRejected 'forged counts' '<testsuite tests="1" skipped="0" failures="0" errors="0"><testcase><failure/></testcase></testsuite>'
    Assert-ReportRejected 'no tests' '<testsuite tests="0" skipped="0" failures="0" errors="0"/>'
    Assert-ReportRejected 'DTD' '<!DOCTYPE testsuite [<!ENTITY bad SYSTEM "file:///nonexistent">]><testsuite tests="1" skipped="0" failures="0" errors="0"><testcase name="&bad;"/></testsuite>'
    Set-Content -LiteralPath $reportTestFile -Value '<testsuite tests="1" skipped="0" failures="0" errors="0"><testcase/></testsuite>' -Encoding utf8
    (Get-Item -LiteralPath $reportTestFile).LastWriteTimeUtc = [DateTime]::UtcNow.AddHours(-3)
    $reportWasRejected = $false
    try { & $reportTestGate -ReportOnly -Modules fixture | Out-Null } catch { $reportWasRejected = $true }
    if (-not $reportWasRejected) { throw 'Stale report accepted' }
    Remove-Item -LiteralPath $reportTestFile
    $reportWasRejected = $false
    try { & $reportTestGate -ReportOnly -Modules fixture | Out-Null } catch { $reportWasRejected = $true }
    if (-not $reportWasRejected) { throw 'Missing report accepted' }
    Write-Output 'Report gate: positive and 8 rejection scenarios passed'
} finally {
    $reportTestResolved = [System.IO.Path]::GetFullPath($reportTestRoot)
    $reportTestAllowedParent = [System.IO.Path]::GetFullPath((Join-Path $reportTestWorkspace 'target')) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $reportTestResolved.StartsWith($reportTestAllowedParent, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe report fixture cleanup path' }
    Remove-Item -LiteralPath $reportTestResolved -Recurse -Force
}
