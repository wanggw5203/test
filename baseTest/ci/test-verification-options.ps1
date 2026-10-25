#Requires -Version 7.2
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'verification-support.psm1') -Force
$optionScenarioCount = 0
foreach ($allowedOptions in @('', '-Xmx512m', '-Dmaven.repo.local=.m2/repository', '-Dfile.encoding=UTF-8')) {
    Assert-TestkitFullVerificationOptions -ArgumentSources @($allowedOptions)
    $optionScenarioCount++
}
# Validate strings only: never set process variables or start Maven with these options.
foreach ($rejectedOptions in @('-Dtestkit.level=smoke', '-Dtest=OneTest', '-DskipTests=true', '-Dmaven.test.skip=true',
    '-DskipITs=true', '-Dspotless.skip=true', '-Dcyclonedx.skip=true', '-pl enterprise-team-example',
    '--projects enterprise-team-example', '-rf enterprise-adapters', '-Pkafka-live', '-Dgroups=smoke')) {
    $optionWasRejected = $false
    try { Assert-TestkitFullVerificationOptions -ArgumentSources @($rejectedOptions) } catch { $optionWasRejected = $true }
    if (-not $optionWasRejected) { throw 'Verification option guard accepted a partial or bypassed run' }
    $optionScenarioCount++
}
Write-Output "Verification option scenarios verified: $optionScenarioCount"
