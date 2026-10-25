$ErrorActionPreference = 'Stop'
$readyFile = Join-Path $PSScriptRoot 'runtime/ready.json'
if (-not (Test-Path -LiteralPath $readyFile)) { Write-Output 'No service state file'; return }
$state = Get-Content -Raw -LiteralPath $readyFile | ConvertFrom-Json
$servicePid = [int]$state.pid
$ownedProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $servicePid" -ErrorAction SilentlyContinue
if (-not $ownedProcess) { Write-Output 'Service is already stopped'; return }
$expectedJar = Join-Path $PSScriptRoot 'service/target/local-order-service.jar'
if (-not $ownedProcess.CommandLine -or $ownedProcess.CommandLine.IndexOf($expectedJar, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw "Refusing to stop PID $servicePid because it is not this project's service process"
}
Stop-Process -Id $servicePid
Write-Output "Stopped local order service PID $servicePid (no project files removed)"
