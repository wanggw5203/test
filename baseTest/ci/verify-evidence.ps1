#Requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ExpectedRunId,
    [string]$WorkspaceRoot = (Join-Path $PSScriptRoot '..'),
    [string]$EvidencePath = 'target/verification-evidence.json'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'verification-support.psm1') -Force
$evidenceRoot = [System.IO.Path]::GetFullPath($WorkspaceRoot)
$evidenceFile = if ([System.IO.Path]::IsPathRooted($EvidencePath)) { $EvidencePath } else { Join-Path $evidenceRoot $EvidencePath }
$evidence = Get-Content -LiteralPath $evidenceFile -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
if ($evidence.schemaVersion -cne 1 -or $evidence.type -cne 'testkit-verification-evidence') { throw 'Unsupported verification evidence schema' }
if ($ExpectedRunId -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$' -or $evidence.runId -cne $ExpectedRunId) { throw 'Verification evidence run ID differs' }
if ($evidence.status -cne 'PASSED') { throw 'Verification evidence is incomplete or failed' }
if ($evidence.source.fingerprint -notmatch '^[a-f0-9]{64}$') { throw 'Invalid source fingerprint' }
if ((Get-TestkitSourceFingerprint -WorkspaceRoot $evidenceRoot) -cne $evidence.source.fingerprint) { throw 'Verification source inputs have changed' }
$evidenceChecks = @($evidence.checks)
foreach ($evidenceCheck in @('build', 'report', 'plan', 'gate-regressions')) {
    $evidenceMatches = @($evidenceChecks | Where-Object { $_.name -ceq $evidenceCheck })
    if ($evidenceMatches.Count -ne 1 -or $evidenceMatches[0].status -cne 'PASSED') { throw "Missing or unsuccessful verification check: $evidenceCheck" }
}
$evidencePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$evidenceKinds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$evidenceArtifacts = @($evidence.artifacts)
if ($evidenceArtifacts.Count -eq 0) { throw 'Verification evidence has no artifacts' }
foreach ($evidenceArtifact in $evidenceArtifacts) {
    $evidenceRelativePath = [string]$evidenceArtifact.path
    if ([string]::IsNullOrWhiteSpace($evidenceRelativePath) -or $evidenceRelativePath.Contains('\') -or
        [System.IO.Path]::IsPathRooted($evidenceRelativePath) -or $evidenceRelativePath.Contains(':') -or
        $evidenceRelativePath -match '(^|/)\.\.?(/|$)') { throw 'Unsafe verification artifact path' }
    if (-not $evidencePaths.Add($evidenceRelativePath)) { throw 'Duplicate verification artifact path' }
    if ($evidenceArtifact.sha256 -notmatch '^[a-f0-9]{64}$' -or $evidenceArtifact.sizeBytes -isnot [long] -and $evidenceArtifact.sizeBytes -isnot [int]) {
        throw 'Invalid verification artifact digest or size'
    }
    $evidenceAbsolutePath = [System.IO.Path]::GetFullPath((Join-Path $evidenceRoot $evidenceRelativePath))
    $evidenceAllowedPrefix = $evidenceRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $evidenceAbsolutePath.StartsWith($evidenceAllowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Verification artifact escapes workspace' }
    if (-not (Test-Path -LiteralPath $evidenceAbsolutePath -PathType Leaf)) { throw 'Missing verification artifact' }
    $evidenceItem = Get-Item -LiteralPath $evidenceAbsolutePath
    for ($evidenceAncestor = $evidenceItem; $null -ne $evidenceAncestor -and $evidenceAncestor.FullName -ne $evidenceRoot; $evidenceAncestor = $evidenceAncestor.Parent) {
        if (($evidenceAncestor.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Linked verification artifacts are unsupported' }
        # FileInfo has Directory instead of Parent.
        if ($evidenceAncestor -is [System.IO.FileInfo]) { $evidenceAncestor = $evidenceAncestor.Directory }
        if ($null -ne $evidenceAncestor -and ($evidenceAncestor.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Linked verification artifacts are unsupported' }
    }
    if ($evidenceItem.Length -ne $evidenceArtifact.sizeBytes -or
        (Get-FileHash -LiteralPath $evidenceAbsolutePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $evidenceArtifact.sha256) {
        throw 'Verification artifact has changed'
    }
    $null = $evidenceKinds.Add([string]$evidenceArtifact.kind)
}
foreach ($evidenceRequiredKind in @('test-report', 'execution-report', 'execution-plan', 'binary', 'sbom', 'report-summary')) {
    if (-not $evidenceKinds.Contains($evidenceRequiredKind)) { throw "Missing verification artifact kind: $evidenceRequiredKind" }
}
foreach ($evidenceModule in @('base-test-toolkit', 'enterprise-adapters', 'java-api-lab', 'enterprise-team-example')) {
    $evidenceModulePattern = '^' + [regex]::Escape($evidenceModule) + '/target/surefire-reports/TEST-[^/]+\.xml$'
    if (@($evidenceArtifacts | Where-Object { $_.kind -ceq 'test-report' -and $_.path -cmatch $evidenceModulePattern }).Count -eq 0) {
        throw "Missing module test report: $evidenceModule"
    }
}
foreach ($evidenceModule in @('base-test-toolkit', 'enterprise-adapters', 'java-api-lab')) {
    $evidenceBinaryPattern = '^' + [regex]::Escape($evidenceModule) + '/target/' + [regex]::Escape($evidenceModule) + '-[0-9][^/]*\.jar$'
    if (@($evidenceArtifacts | Where-Object { $_.kind -ceq 'binary' -and $_.path -cmatch $evidenceBinaryPattern }).Count -ne 1) {
        throw "Missing or ambiguous module binary: $evidenceModule"
    }
}
foreach ($evidenceRequiredFile in @(
    @{Path = 'target/verification-summary.json'; Kind = 'report-summary'},
    @{Path = 'target/testkit-sbom.json'; Kind = 'sbom'},
    @{Path = 'target/testkit-sbom.xml'; Kind = 'sbom'},
    @{Path = 'enterprise-team-example/target/team-results.jsonl'; Kind = 'execution-report'}
)) {
    if (@($evidenceArtifacts | Where-Object { $_.kind -ceq $evidenceRequiredFile.Kind -and $_.path -ceq $evidenceRequiredFile.Path }).Count -ne 1) {
        throw "Missing canonical verification artifact: $($evidenceRequiredFile.Path)"
    }
}
if (@($evidenceArtifacts | Where-Object { $_.kind -ceq 'execution-plan' -and $_.path -cmatch '^enterprise-team-example/target/enterprise-plans/[^/]+\.plan\.json$' }).Count -eq 0) {
    throw 'Missing enterprise execution plan'
}
Write-Output "Verification evidence accepted: $ExpectedRunId ($($evidenceArtifacts.Count) artifacts)"
