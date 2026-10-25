#Requires -Version 7.2
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$gate = Join-Path $PSScriptRoot 'verify-shards.ps1'
$fixtureParent = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'target/shard-gate-tests'))
$fixtureRoot = Join-Path $fixtureParent ([Guid]::NewGuid().ToString())
$runId = 'shard-gate-fixture'
$caseIds = @('orders.local-job', 'orders.local-message', 'orders.sdk-context', 'orders.sdk-price')
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $planHash = [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes([string]::Join("`n", $caseIds)))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
$checked = 0

function Write-FixtureJson([string]$Path, $Value) {
    $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path -Encoding UTF8
}
function Write-FixtureRows([string]$Path, [object[]]$Rows) {
    @($Rows | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress }) | Set-Content -LiteralPath $Path -Encoding UTF8
}
function Read-FixtureRows([string]$Path) {
    return @(Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object { $_ | ConvertFrom-Json })
}
function New-Fixture([string]$Name) {
    $scenario = Join-Path $fixtureRoot $Name
    for ($index = 0; $index -lt 2; $index++) {
        $worker = "worker-$index"
        $directory = Join-Path $scenario $worker
        [void](New-Item -ItemType Directory -Path $directory -Force)
        $selected = @($caseIds[$index * 2], $caseIds[$index * 2 + 1])
        $plan = [ordered]@{ schemaVersion = 1; runId = $runId; workerId = $worker; environment = 'local'; team = 'orders'; shardIndex = $index; shardCount = 2; planHash = $planHash; plannedCaseIds = $caseIds; selectedCaseIds = $selected }
        Write-FixtureJson (Join-Path $directory 'context.plan.json') $plan
        $rows = @()
        for ($caseIndex = 0; $caseIndex -lt $selected.Count; $caseIndex++) {
            $rows += [pscustomobject]@{ type = 'test'; runId = $runId; workerId = $worker; environment = 'local'; team = 'orders'; phase = 'test'; logicalCaseId = $selected[$caseIndex]; caseId = "execution-$worker-$caseIndex"; status = 'PASSED'; hasError = $false }
        }
        $rows += [pscustomobject]@{ type = 'summary'; runId = $runId; passed = $selected.Count; failed = 0; skipped = 0 }
        Write-FixtureRows (Join-Path $directory 'results.jsonl') $rows
    }
    return $scenario
}
function Assert-FixtureRejected([string]$Name, [scriptblock]$Change, [string]$Reason) {
    $scenario = New-Fixture $Name
    & $Change $scenario
    try {
        $null = & $gate -ArtifactsDirectory $scenario -ExpectedRunId $runId
    } catch {
        if ($_.Exception.Message -notlike "*$Reason*") { throw "Unexpected rejection for ${Name}: $($_.Exception.Message)" }
        $script:checked++
        Write-Output "PASS reject: $Name"
        return
    }
    throw "Shard gate incorrectly accepted $Name"
}

try {
    $valid = New-Fixture 'valid'
    $result = & $gate -ArtifactsDirectory $valid -ExpectedRunId $runId
    if ($result.Shards -ne 2 -or $result.LogicalCases -ne 4 -or $result.PassedInvocations -ne 4) { throw 'Valid gate totals differ' }
    $checked++
    Write-Output 'PASS accept: valid'

    Assert-FixtureRejected 'missing-shard' { param($d) Remove-Item -LiteralPath (Join-Path $d 'worker-1/context.plan.json') } 'missing shard manifests'
    Assert-FixtureRejected 'missing-report' { param($d) Remove-Item -LiteralPath (Join-Path $d 'worker-1/results.jsonl') } 'missing completed JSONL'
    Assert-FixtureRejected 'cross-environment' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.environment = 'qa'; Write-FixtureJson $p $v } 'environment/team differs'
    Assert-FixtureRejected 'different-run' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.runId = 'other-run'; Write-FixtureJson $p $v } 'plan runId differs'
    Assert-FixtureRejected 'tampered-hash' { param($d) $p = Join-Path $d 'worker-0/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.planHash = 'a' * 64; Write-FixtureJson $p $v } 'planHash does not match'
    Assert-FixtureRejected 'plan-drift' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.plannedCaseIds += 'orders.z-extra'; $h = [System.Security.Cryptography.SHA256]::Create(); try { $v.planHash = [BitConverter]::ToString($h.ComputeHash([System.Text.Encoding]::UTF8.GetBytes([string]::Join("`n", $v.plannedCaseIds)))).Replace('-', '').ToLowerInvariant() } finally { $h.Dispose() }; Write-FixtureJson $p $v } 'plan drift'
    Assert-FixtureRejected 'duplicate-shard' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.shardIndex = 0; Write-FixtureJson $p $v } 'duplicate shard index'
    Assert-FixtureRejected 'duplicate-worker' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.workerId = 'worker-0'; Write-FixtureJson $p $v } 'duplicate workerId'
    Assert-FixtureRejected 'count-mismatch' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.shardCount = 3; Write-FixtureJson $p $v } 'shardCount differs'
    Assert-FixtureRejected 'selected-overlap' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.selectedCaseIds[0] = 'orders.local-job'; Write-FixtureJson $p $v } 'selected case overlap'
    Assert-FixtureRejected 'missing-coverage' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.selectedCaseIds = @($v.selectedCaseIds[0]); Write-FixtureJson $p $v } 'do not cover'
    Assert-FixtureRejected 'empty-shard' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.selectedCaseIds = @(); Write-FixtureJson $p $v } 'nonempty array'
    Assert-FixtureRejected 'duplicate-planned-id' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.plannedCaseIds += $v.plannedCaseIds[0]; Write-FixtureJson $p $v } 'duplicate ID'
    Assert-FixtureRejected 'selected-outside-plan' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.selectedCaseIds[0] = 'orders.unknown'; Write-FixtureJson $p $v } 'outside plannedCaseIds'
    Assert-FixtureRejected 'string-count' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.shardCount = '2'; Write-FixtureJson $p $v } 'integral shardCount'
    Assert-FixtureRejected 'wrong-schema' { param($d) $p = Join-Path $d 'worker-1/context.plan.json'; $v = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json; $v.schemaVersion = 2; Write-FixtureJson $p $v } 'integral schemaVersion'
    Assert-FixtureRejected 'failed-test' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].status = 'FAILED'; Write-FixtureRows $p $r } 'test status'
    Assert-FixtureRejected 'skipped-test' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].status = 'SKIPPED'; Write-FixtureRows $p $r } 'test status'
    Assert-FixtureRejected 'configuration-failure' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r += [pscustomobject]@{ type = 'test'; runId = $runId; phase = 'configuration'; status = 'FAILED' }; Write-FixtureRows $p $r } 'test status'
    Assert-FixtureRejected 'failed-summary' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[-1].failed = 1; Write-FixtureRows $p $r } 'failed/skipped summary'
    Assert-FixtureRejected 'skipped-summary' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[-1].skipped = 1; Write-FixtureRows $p $r } 'failed/skipped summary'
    Assert-FixtureRejected 'missing-summary' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; Write-FixtureRows $p @($r[0], $r[1]) } 'completion summary'
    Assert-FixtureRejected 'summary-count' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[-1].passed = 3; Write-FixtureRows $p $r } 'passed count differs'
    Assert-FixtureRejected 'missing-logical-case-event' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[-1].passed = 1; Write-FixtureRows $p @($r[0], $r[2]) } 'no PASSED test event'
    Assert-FixtureRejected 'wrong-report-environment' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].environment = 'qa'; Write-FixtureRows $p $r } 'report environment/team'
    Assert-FixtureRejected 'unknown-worker' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].workerId = 'stranger'; Write-FixtureRows $p $r } 'unknown workerId'
    Assert-FixtureRejected 'unselected-case' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].logicalCaseId = 'orders.local-job'; Write-FixtureRows $p $r } 'not selected'
    Assert-FixtureRejected 'duplicate-execution' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[-1].passed = 3; Write-FixtureRows $p @($r[0], $r[0], $r[1], $r[2]) } 'duplicate execution'
    Assert-FixtureRejected 'invalid-jsonl' { param($d) Add-Content -LiteralPath (Join-Path $d 'worker-1/results.jsonl') -Value '{broken' } 'invalid JSONL'
    Assert-FixtureRejected 'false-pass-error' { param($d) $p = Join-Path $d 'worker-1/results.jsonl'; $r = Read-FixtureRows $p; $r[0].hasError = $true; Write-FixtureRows $p $r } 'error marker'

    $foreign = New-Fixture 'foreign-run-event'
    Add-Content -LiteralPath (Join-Path $foreign 'worker-1/results.jsonl') -Value '{"type":"test","runId":"other-run","status":"FAILED"}'
    $null = & $gate -ArtifactsDirectory $foreign -ExpectedRunId $runId
    $checked++
    Write-Output 'PASS accept: foreign-run-event'
    Write-Output "Shard gate scenarios verified: $checked"
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $cleanupTarget = [System.IO.Path]::GetFullPath($fixtureRoot)
        $cleanupBoundary = $fixtureParent.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        if (!$cleanupTarget.StartsWith($cleanupBoundary, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing fixture cleanup outside the verified test directory'
        }
        Remove-Item -LiteralPath $cleanupTarget -Recurse -Force
    }
}
