#Requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'verification-support.psm1') -Force
$evidenceTestWorkspace = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidenceTestRoot = Join-Path $evidenceTestWorkspace ('target/evidence-gate-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $evidenceTestRoot -Force
$evidenceTestManifest = Join-Path $evidenceTestRoot 'evidence.json'
$evidenceTestRunId = 'evidence-regression'
$evidenceTestCount = 0
$evidenceTestArtifacts = @()
$evidenceTestSpecs = @(
    @{Path = 'base-test-toolkit/target/surefire-reports/TEST-fixture.xml'; Kind = 'test-report'},
    @{Path = 'enterprise-adapters/target/surefire-reports/TEST-fixture.xml'; Kind = 'test-report'},
    @{Path = 'java-api-lab/target/surefire-reports/TEST-fixture.xml'; Kind = 'test-report'},
    @{Path = 'enterprise-team-example/target/surefire-reports/TEST-fixture.xml'; Kind = 'test-report'},
    @{Path = 'base-test-toolkit/target/base-test-toolkit-1.1.0-SNAPSHOT.jar'; Kind = 'binary'},
    @{Path = 'enterprise-adapters/target/enterprise-adapters-1.1.0-SNAPSHOT.jar'; Kind = 'binary'},
    @{Path = 'java-api-lab/target/java-api-lab-1.1.0-SNAPSHOT.jar'; Kind = 'binary'},
    @{Path = 'target/verification-summary.json'; Kind = 'report-summary'},
    @{Path = 'target/testkit-sbom.json'; Kind = 'sbom'},
    @{Path = 'target/testkit-sbom.xml'; Kind = 'sbom'},
    @{Path = 'enterprise-team-example/target/team-results.jsonl'; Kind = 'execution-report'},
    @{Path = 'enterprise-team-example/target/enterprise-plans/worker.plan.json'; Kind = 'execution-plan'}
)
foreach ($evidenceTestSpec in $evidenceTestSpecs) {
    $evidenceTestPath = Join-Path $evidenceTestRoot $evidenceTestSpec.Path
    $null = New-Item -ItemType Directory -Path (Split-Path -Parent $evidenceTestPath) -Force
    Set-Content -LiteralPath $evidenceTestPath -Value 'verified fixture content' -Encoding UTF8
    $evidenceTestArtifacts += New-TestkitArtifactRecord -WorkspaceRoot $evidenceTestRoot -Path $evidenceTestPath -Kind $evidenceTestSpec.Kind
}
$evidenceTestInput = Join-Path $evidenceTestRoot 'pom.xml'
Set-Content -LiteralPath $evidenceTestInput -Value '<project/>' -Encoding UTF8
$evidenceTestTemplate = [ordered]@{
    schemaVersion = 1; type = 'testkit-verification-evidence'; runId = $evidenceTestRunId; status = 'PASSED'
    source = @{fingerprint = (Get-TestkitSourceFingerprint -WorkspaceRoot $evidenceTestRoot)}
    checks = @('build', 'report', 'plan', 'gate-regressions') | ForEach-Object { @{name = $_; status = 'PASSED'} }
    artifacts = $evidenceTestArtifacts
} | ConvertTo-Json -Depth 8

function Assert-EvidenceRejected {
    param([string]$Label, [scriptblock]$Change)
    $evidenceCase = $evidenceTestTemplate | ConvertFrom-Json -AsHashtable
    & $Change $evidenceCase
    $evidenceCase | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $evidenceTestManifest -Encoding UTF8
    $evidenceRejected = $false
    try {
        & (Join-Path $PSScriptRoot 'verify-evidence.ps1') -WorkspaceRoot $evidenceTestRoot -EvidencePath $evidenceTestManifest -ExpectedRunId $evidenceTestRunId | Out-Null
    } catch { $evidenceRejected = $true }
    if (-not $evidenceRejected) { throw "Evidence gate incorrectly accepted: $Label" }
    $script:evidenceTestCount++
    Write-Output "Evidence gate rejected: $Label"
}

try {
    Set-Content -LiteralPath $evidenceTestManifest -Value $evidenceTestTemplate -Encoding UTF8
    & (Join-Path $PSScriptRoot 'verify-evidence.ps1') -WorkspaceRoot $evidenceTestRoot -EvidencePath $evidenceTestManifest -ExpectedRunId $evidenceTestRunId
    $evidenceTestCount++
    Assert-EvidenceRejected 'failed-run' { param($doc) $doc.status = 'FAILED' }
    Assert-EvidenceRejected 'running-run' { param($doc) $doc.status = 'RUNNING' }
    Assert-EvidenceRejected 'different-run' { param($doc) $doc.runId = 'another-run' }
    Assert-EvidenceRejected 'unsupported-schema' { param($doc) $doc.schemaVersion = 2 }
    Assert-EvidenceRejected 'missing-stage' { param($doc) $doc.checks = @($doc.checks | Where-Object { $_.name -ne 'build' }) }
    Assert-EvidenceRejected 'duplicate-stage' { param($doc) $doc.checks += $doc.checks[0] }
    Assert-EvidenceRejected 'failed-stage' { param($doc) $doc.checks[0].status = 'FAILED' }
    Assert-EvidenceRejected 'invalid-source-digest' { param($doc) $doc.source.fingerprint = 'invalid' }
    Assert-EvidenceRejected 'empty-artifacts' { param($doc) $doc.artifacts = @() }
    Assert-EvidenceRejected 'duplicate-artifact' { param($doc) $doc.artifacts += $doc.artifacts[0] }
    Assert-EvidenceRejected 'missing-artifact-kind' { param($doc) $doc.artifacts = @($doc.artifacts | Where-Object { $_.kind -ne 'sbom' }) }
    Assert-EvidenceRejected 'missing-module-report' { param($doc) $doc.artifacts = @($doc.artifacts | Where-Object { $_.path -ne 'java-api-lab/target/surefire-reports/TEST-fixture.xml' }) }
    Assert-EvidenceRejected 'missing-module-binary' { param($doc) $doc.artifacts = @($doc.artifacts | Where-Object { $_.path -ne 'enterprise-adapters/target/enterprise-adapters-1.1.0-SNAPSHOT.jar' }) }
    Assert-EvidenceRejected 'missing-xml-sbom' { param($doc) $doc.artifacts = @($doc.artifacts | Where-Object { $_.path -ne 'target/testkit-sbom.xml' }) }
    Assert-EvidenceRejected 'invalid-artifact-digest' { param($doc) $doc.artifacts[0].sha256 = 'invalid' }
    Assert-EvidenceRejected 'changed-artifact-digest' { param($doc) $doc.artifacts[0].sha256 = ('b' * 64) }
    Assert-EvidenceRejected 'changed-artifact-size' { param($doc) $doc.artifacts[0].sizeBytes++ }
    Assert-EvidenceRejected 'string-artifact-size' { param($doc) $doc.artifacts[0].sizeBytes = '26' }
    Assert-EvidenceRejected 'missing-artifact' { param($doc) $doc.artifacts[0].path = 'missing.fixture' }
    Assert-EvidenceRejected 'parent-traversal' { param($doc) $doc.artifacts[0].path = '../outside.fixture' }
    Assert-EvidenceRejected 'absolute-path' { param($doc) $doc.artifacts[0].path = [System.IO.Path]::GetFullPath($evidenceTestManifest) }
    Assert-EvidenceRejected 'alternate-stream' { param($doc) $doc.artifacts[0].path = 'test-report.fixture:extra' }
    Set-Content -LiteralPath $evidenceTestInput -Value '<changed-project/>' -Encoding UTF8
    Assert-EvidenceRejected 'modified-source-after-verification' { param($doc) }
    Write-Output "Evidence gate scenarios verified: $evidenceTestCount"
} finally {
    $evidenceCleanupRoot = [System.IO.Path]::GetFullPath($evidenceTestRoot)
    $evidenceAllowedRoot = [System.IO.Path]::GetFullPath((Join-Path $evidenceTestWorkspace 'target')) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $evidenceCleanupRoot.StartsWith($evidenceAllowedRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe evidence fixture cleanup path' }
    Remove-Item -LiteralPath $evidenceCleanupRoot -Recurse -Force
}
