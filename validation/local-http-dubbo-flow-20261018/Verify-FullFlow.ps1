param([switch]$Fresh, [switch]$ConfigOnly)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Start-LocalService.ps1') -SkipBuild
$pluginDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../tools/ai-test-flow-idea-plugin'))
$previousRoot = $env:AI_TEST_FLOW_RPC_VALIDATION_ROOT
$previousOutput = $env:AI_TEST_FLOW_RPC_OUTPUT_SUBDIR
$previousConfigOnly = $env:AI_TEST_FLOW_RPC_CONFIG_ONLY
try {
    $env:AI_TEST_FLOW_RPC_VALIDATION_ROOT = $PSScriptRoot
    if ($Fresh) {
        $env:AI_TEST_FLOW_RPC_OUTPUT_SUBDIR = 'rechecks/manual-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
        Write-Output ('Fresh projects: ' + (Join-Path $PSScriptRoot $env:AI_TEST_FLOW_RPC_OUTPUT_SUBDIR))
    }
    if ($ConfigOnly) { $env:AI_TEST_FLOW_RPC_CONFIG_ONLY = 'true' }
    & (Join-Path $pluginDirectory 'gradlew.bat') -p $pluginDirectory test --tests io.github.aitestflow.service.LocalHttpDubboFullFlowValidationTest --rerun-tasks --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw 'Full flow failed; inspect protocol-specific quality-assets/evidence run directories' }
} finally {
    $env:AI_TEST_FLOW_RPC_VALIDATION_ROOT = $previousRoot
    $env:AI_TEST_FLOW_RPC_OUTPUT_SUBDIR = $previousOutput
    $env:AI_TEST_FLOW_RPC_CONFIG_ONLY = $previousConfigOnly
}
Write-Output 'Verified: generation + HTTP + Dubbo + fault detection + recovery. Default golden AI feedback limitations remain documented.'
