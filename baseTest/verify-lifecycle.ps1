#Requires -Version 7.2
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string]$RunId = ('verification-' + [guid]::NewGuid().ToString('N')),
    [string]$MavenRepository
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'ci/verification-support.psm1') -Force
$verificationStarted = [DateTime]::UtcNow.ToString('o')
$verificationStage = 'preflight'
$verificationChecks = [System.Collections.Generic.List[object]]::new()
$verificationArtifacts = [System.Collections.Generic.List[object]]::new()
$verificationSource = [ordered]@{gitCommit = $null; gitDirty = $null; fingerprint = $null}
$verificationTools = [ordered]@{}
$verificationStatus = 'FAILED'
$verificationInitialDirectory = Join-Path $PSScriptRoot 'target'
$null = New-Item -ItemType Directory -Path $verificationInitialDirectory -Force
[ordered]@{schemaVersion = 1; type = 'testkit-verification-evidence'; runId = $RunId; status = 'RUNNING'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $verificationInitialDirectory 'verification-evidence.json') -Encoding UTF8
try {
    # This entry verifies the whole repository. Focused/local test runs use Maven directly.
    Assert-TestkitFullVerificationOptions -ArgumentSources @($env:MAVEN_ARGS, $env:MAVEN_OPTS, $env:JAVA_TOOL_OPTIONS, $env:JDK_JAVA_OPTIONS, $env:_JAVA_OPTIONS)
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.mvn/maven.config')) {
        $verificationMavenConfig = Get-Content -LiteralPath (Join-Path $PSScriptRoot '.mvn/maven.config') -Raw
        Assert-TestkitFullVerificationOptions -ArgumentSources @($verificationMavenConfig)
    }
    # TEST_ENV is explicit so local contract tests cannot accidentally inherit a company target.
    $verificationToolsOutput = (& mvn -version 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Maven preflight failed' }
    $verificationTools.maven = [regex]::Match($verificationToolsOutput, 'Apache Maven ([0-9.]+)').Groups[1].Value
    $verificationTools.java = [regex]::Match($verificationToolsOutput, 'Java version: ([^,\s]+)').Groups[1].Value
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $verificationGitCommit = (& git -C $PSScriptRoot rev-parse HEAD 2>$null)
        if ($LASTEXITCODE -eq 0) {
            $verificationSource.gitCommit = [string]$verificationGitCommit
            $verificationGitChanges = @(& git -C $PSScriptRoot status --porcelain -- .)
            $verificationSource.gitDirty = $verificationGitChanges.Count -gt 0
        }
    }
    $verificationSource.fingerprint = Get-TestkitSourceFingerprint -WorkspaceRoot $PSScriptRoot
    $verificationStage = 'build'
    $verificationMavenArguments = @('-B', '-ntp', '-f', (Join-Path $PSScriptRoot 'pom.xml'), '-Psupply-chain',
        '-Dtest.env=local', '-Dtestkit.level=all', '-Dtestkit.shard.index=0', '-Dtestkit.shard.count=1', '-Dtestkit.team=orders', '-Dtestkit.workerId=local',
        "-Dtestkit.runId=$RunId", '-Dtestkit.report.file=target/team-results.jsonl', '-Dtestkit.plan.directory=target/enterprise-plans',
        '-DskipTests=false', '-Dmaven.test.skip=false', '-DskipITs=false', '-Dspotless.skip=false', '-Dcyclonedx.skip=false')
    if ($MavenRepository) { $verificationMavenArguments += "-Dmaven.repo.local=$([System.IO.Path]::GetFullPath($MavenRepository))" }
    & mvn @verificationMavenArguments clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven lifecycle verification failed' }
    $verificationChecks.Add([ordered]@{name = 'build'; status = 'PASSED'})
    $verificationStage = 'report'
    & (Join-Path $PSScriptRoot 'verify-framework.ps1') -ReportOnly
    $verificationChecks.Add([ordered]@{name = 'report'; status = 'PASSED'})
    $verificationStage = 'plan'
    & (Join-Path $PSScriptRoot 'ci/verify-shards.ps1') -ArtifactsDirectory (Join-Path $PSScriptRoot 'enterprise-team-example/target') -ExpectedRunId $RunId
    $verificationChecks.Add([ordered]@{name = 'plan'; status = 'PASSED'})
    $verificationStage = 'gate-regressions'
    & (Join-Path $PSScriptRoot 'ci/test-report-gate.ps1')
    & (Join-Path $PSScriptRoot 'ci/test-shard-gate.ps1')
    & (Join-Path $PSScriptRoot 'ci/test-evidence-gate.ps1')
    & (Join-Path $PSScriptRoot 'ci/test-verification-options.ps1')
    $verificationChecks.Add([ordered]@{name = 'gate-regressions'; status = 'PASSED'})
    $verificationStage = 'evidence'
    if ((Get-TestkitSourceFingerprint -WorkspaceRoot $PSScriptRoot) -cne $verificationSource.fingerprint) {
        throw 'Build inputs changed during verification; run again with a stable workspace'
    }
    foreach ($verificationModule in @('base-test-toolkit', 'enterprise-adapters', 'java-api-lab', 'enterprise-team-example')) {
        foreach ($verificationFile in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "$verificationModule/target/surefire-reports") -Filter 'TEST-*.xml' -File) {
            $verificationArtifacts.Add((New-TestkitArtifactRecord -WorkspaceRoot $PSScriptRoot -Path $verificationFile.FullName -Kind 'test-report'))
        }
    }
    foreach ($verificationModule in @('base-test-toolkit', 'enterprise-adapters', 'java-api-lab')) {
        $verificationJars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "$verificationModule/target") -Filter "$verificationModule-*.jar" -File |
            Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' })
        if ($verificationJars.Count -ne 1) { throw "Expected one primary binary: $verificationModule" }
        $verificationArtifacts.Add((New-TestkitArtifactRecord -WorkspaceRoot $PSScriptRoot -Path $verificationJars[0].FullName -Kind 'binary'))
    }
    foreach ($verificationPlan in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'enterprise-team-example/target/enterprise-plans') -Filter '*.plan.json' -File) {
        $verificationArtifacts.Add((New-TestkitArtifactRecord -WorkspaceRoot $PSScriptRoot -Path $verificationPlan.FullName -Kind 'execution-plan'))
    }
    foreach ($verificationArtifactSpec in @(
        @{Path = 'enterprise-team-example/target/team-results.jsonl'; Kind = 'execution-report'},
        @{Path = 'target/verification-summary.json'; Kind = 'report-summary'},
        @{Path = 'target/testkit-sbom.json'; Kind = 'sbom'},
        @{Path = 'target/testkit-sbom.xml'; Kind = 'sbom'}
    )) {
        $verificationArtifacts.Add((New-TestkitArtifactRecord -WorkspaceRoot $PSScriptRoot -Path (Join-Path $PSScriptRoot $verificationArtifactSpec.Path) -Kind $verificationArtifactSpec.Kind))
    }
    $verificationSbom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'target/testkit-sbom.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($verificationSbom.bomFormat -cne 'CycloneDX' -or $verificationSbom.specVersion -cne '1.6' -or @($verificationSbom.components).Count -eq 0) {
        throw 'Missing or invalid aggregate SBOM'
    }
    $verificationStatus = 'PASSED'
} catch {
    $verificationChecks.Add([ordered]@{name = $verificationStage; status = 'FAILED'})
    throw
} finally {
    $verificationOutputDirectory = Join-Path $PSScriptRoot 'target'
    $null = New-Item -ItemType Directory -Path $verificationOutputDirectory -Force
    $verificationEvidencePath = Join-Path $verificationOutputDirectory 'verification-evidence.json'
    [ordered]@{
        schemaVersion = 1; type = 'testkit-verification-evidence'; runId = $RunId; status = $verificationStatus
        startedAt = $verificationStarted; completedAt = [DateTime]::UtcNow.ToString('o')
        source = $verificationSource; tools = $verificationTools
        checks = @($verificationChecks.ToArray()); artifacts = @($verificationArtifacts.ToArray())
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $verificationEvidencePath -Encoding UTF8
}
try {
    & (Join-Path $PSScriptRoot 'ci/verify-evidence.ps1') -WorkspaceRoot $PSScriptRoot -ExpectedRunId $RunId
} catch {
    $verificationRejectedEvidence = Get-Content -LiteralPath $verificationEvidencePath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
    $verificationRejectedEvidence.status = 'FAILED'
    $verificationRejectedEvidence.checks += @{name = 'evidence'; status = 'FAILED'}
    $verificationRejectedEvidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $verificationEvidencePath -Encoding UTF8
    throw
}
Write-Output "Lifecycle verification completed: $RunId"
