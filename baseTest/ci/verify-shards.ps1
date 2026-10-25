#Requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ArtifactsDirectory,
    [Parameter(Mandatory = $true)][string]$ExpectedRunId
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-GateProperty($Object, [string]$Name, [string]$Location) {
    if ($null -eq $Object -or $null -eq $Object.PSObject.Properties[$Name]) {
        throw "Shard gate: missing $Name in $Location"
    }
    return ,$Object.PSObject.Properties[$Name].Value
}

function Assert-GateToken($Value, [string]$Name) {
    if ($Value -isnot [string] -or $Value -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$') {
        throw "Shard gate: invalid $Name"
    }
}

function Assert-GateInteger($Value, [long]$Minimum, [long]$Maximum, [string]$Name) {
    if (($Value -isnot [int] -and $Value -isnot [long]) -or $Value -lt $Minimum -or $Value -gt $Maximum) {
        throw "Shard gate: invalid integral $Name"
    }
}

function Get-GateIds($Value, [string]$Name) {
    if ($Value -isnot [array] -or $Value.Count -eq 0) { throw "Shard gate: $Name must be a nonempty array" }
    $ids = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($id in $Value) {
        Assert-GateToken $id $Name
        if (!$ids.Add($id)) { throw "Shard gate: duplicate ID in $Name" }
    }
    return ,$ids
}

function Get-GatePlanHash($Ids) {
    [string[]]$sorted = @($Ids)
    [Array]::Sort($sorted, [System.StringComparer]::Ordinal)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes([string]::Join("`n", $sorted))
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

Assert-GateToken $ExpectedRunId 'ExpectedRunId'
$artifactRoot = Get-Item -LiteralPath $ArtifactsDirectory
if (!$artifactRoot.PSIsContainer) { throw 'Shard gate: ArtifactsDirectory must be a directory' }
$planFiles = @(Get-ChildItem -LiteralPath $artifactRoot.FullName -File -Recurse -Filter '*.plan.json')
if ($planFiles.Count -eq 0) { throw 'Shard gate: no plan manifests found' }

$plans = [System.Collections.Generic.Dictionary[int, object]]::new()
$workers = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
$selectedOwner = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
$reference = $null
$plannedIds = $null
foreach ($file in $planFiles) {
    $plan = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $schema = Get-GateProperty $plan 'schemaVersion' $file.Name
    Assert-GateInteger $schema 1 1 'schemaVersion'
    foreach ($field in @('runId', 'workerId', 'environment', 'team')) {
        Assert-GateToken (Get-GateProperty $plan $field $file.Name) $field
    }
    if ($plan.runId -cne $ExpectedRunId) { throw 'Shard gate: plan runId differs from ExpectedRunId' }
    $count = Get-GateProperty $plan 'shardCount' $file.Name
    $index = Get-GateProperty $plan 'shardIndex' $file.Name
    Assert-GateInteger $count 1 ([int]::MaxValue) 'shardCount'
    Assert-GateInteger $index 0 ($count - 1) 'shardIndex'
    $full = Get-GateIds (Get-GateProperty $plan 'plannedCaseIds' $file.Name) 'plannedCaseIds'
    $selected = Get-GateIds (Get-GateProperty $plan 'selectedCaseIds' $file.Name) 'selectedCaseIds'
    $claimedHash = Get-GateProperty $plan 'planHash' $file.Name
    if ($claimedHash -isnot [string] -or $claimedHash -cnotmatch '^[0-9a-f]{64}$' -or $claimedHash -cne (Get-GatePlanHash $full)) {
        throw 'Shard gate: planHash does not match recomputed plannedCaseIds'
    }
    if ($null -eq $reference) {
        $reference = $plan
        $plannedIds = $full
    } else {
        if ($plan.planHash -cne $reference.planHash) { throw 'Shard gate: plan drift between shards' }
        if ($plan.environment -cne $reference.environment -or $plan.team -cne $reference.team) {
            throw 'Shard gate: environment/team differs between shards'
        }
        if ($plan.shardCount -ne $reference.shardCount) { throw 'Shard gate: shardCount differs between shards' }
    }
    if ($plans.ContainsKey([int]$index)) { throw 'Shard gate: duplicate shard index' }
    if ($workers.ContainsKey($plan.workerId)) { throw 'Shard gate: duplicate workerId' }
    $entry = [pscustomobject]@{ Plan = $plan; Selected = $selected; Seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal); CompletedReports = 0; Passed = 0 }
    $plans.Add([int]$index, $entry)
    $workers.Add($plan.workerId, $entry)
    foreach ($id in $selected) {
        if (!$full.Contains($id)) { throw 'Shard gate: selected ID is outside plannedCaseIds' }
        if ($selectedOwner.ContainsKey($id)) { throw 'Shard gate: selected case overlap between shards' }
        $selectedOwner.Add($id, $plan.workerId)
    }
}
if ($plans.Count -ne $reference.shardCount) { throw 'Shard gate: missing shard manifests' }
for ($shardIndex = 0; $shardIndex -lt $reference.shardCount; $shardIndex++) {
    if (!$plans.ContainsKey($shardIndex)) { throw 'Shard gate: missing shard index' }
}
if ($selectedOwner.Count -ne $plannedIds.Count) { throw 'Shard gate: selected IDs do not cover the complete plan' }

$reportFiles = @(Get-ChildItem -LiteralPath $artifactRoot.FullName -File -Recurse -Filter '*.jsonl')
if ($reportFiles.Count -eq 0) { throw 'Shard gate: no JSONL reports found' }
$executions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in $reportFiles) {
    $fileWorkers = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $summaries = [System.Collections.Generic.List[object]]::new()
    $filePassed = 0
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $location = "$($file.Name):$lineNumber"
        try { $row = $line | ConvertFrom-Json }
        catch { throw "Shard gate: invalid JSONL in $location" }
        $type = Get-GateProperty $row 'type' $location
        $runId = Get-GateProperty $row 'runId' $location
        if ($runId -cne $ExpectedRunId) { continue }
        if ($type -ceq 'summary') {
            foreach ($field in @('passed', 'failed', 'skipped')) {
                Assert-GateInteger (Get-GateProperty $row $field $location) 0 ([int]::MaxValue) "summary.$field"
            }
            if ($row.failed -gt 0 -or $row.skipped -gt 0) { throw 'Shard gate: failed/skipped summary for the expected run' }
            $summaries.Add($row)
            continue
        }
        if ($type -ceq 'invocation') { continue }
        if ($type -cne 'test') { throw 'Shard gate: unsupported event type for the expected run' }
        $status = Get-GateProperty $row 'status' $location
        if ($status -cne 'PASSED') { throw 'Shard gate: failed/skipped or unknown test status for the expected run' }
        $phase = Get-GateProperty $row 'phase' $location
        if ($phase -cne 'test') { throw 'Shard gate: unexpected configuration event for the expected run' }
        foreach ($field in @('workerId', 'environment', 'team', 'logicalCaseId', 'caseId')) {
            Assert-GateToken (Get-GateProperty $row $field $location) $field
        }
        if (!$workers.ContainsKey($row.workerId)) { throw 'Shard gate: report contains an unknown workerId' }
        if ($row.environment -cne $reference.environment -or $row.team -cne $reference.team) {
            throw 'Shard gate: report environment/team differs from the plan'
        }
        $entry = $workers[$row.workerId]
        if (!$entry.Selected.Contains($row.logicalCaseId)) { throw 'Shard gate: report contains a case not selected for its worker' }
        if ($null -ne $row.PSObject.Properties['hasError'] -and ($row.hasError -isnot [bool] -or $row.hasError)) {
            throw 'Shard gate: PASSED test contains an error marker'
        }
        if (!$executions.Add("$($row.workerId)|$($row.caseId)")) { throw 'Shard gate: duplicate execution caseId in reports' }
        [void]$entry.Seen.Add($row.logicalCaseId)
        $entry.Passed++
        $filePassed++
        [void]$fileWorkers.Add($row.workerId)
    }
    if ($fileWorkers.Count -gt 1) { throw 'Shard gate: one JSONL contains multiple workers; archive independent reports' }
    if ($fileWorkers.Count -eq 1) {
        if ($summaries.Count -ne 1) { throw 'Shard gate: worker report requires exactly one completion summary' }
        if ($summaries[0].passed -ne $filePassed) { throw 'Shard gate: summary passed count differs from test events' }
        $workerId = @($fileWorkers)[0]
        $workers[$workerId].CompletedReports++
    } elseif ($summaries.Count -gt 0) {
        throw 'Shard gate: summary has no matching worker test events in its file'
    }
}
foreach ($workerId in $workers.Keys) {
    $entry = $workers[$workerId]
    if ($entry.CompletedReports -eq 0) { throw 'Shard gate: missing completed JSONL report for a worker' }
    foreach ($id in $entry.Selected) {
        if (!$entry.Seen.Contains($id)) { throw 'Shard gate: selected logical case has no PASSED test event' }
    }
}
[pscustomobject]@{
    RunId = $ExpectedRunId
    Environment = $reference.environment
    Team = $reference.team
    PlanHash = $reference.planHash
    Shards = $plans.Count
    LogicalCases = $plannedIds.Count
    PassedInvocations = $executions.Count
}
