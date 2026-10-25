param([int]$HttpPort = 18081, [int]$DubboPort = 20891, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$serviceRoot = $PSScriptRoot
$serviceJar = Join-Path $serviceRoot 'service/target/local-order-service.jar'
$stateDirectory = Join-Path $serviceRoot 'runtime'
$readyFile = Join-Path $stateDirectory 'ready.json'
if ((Test-Path -LiteralPath $readyFile)) {
    $savedState = Get-Content -Raw -LiteralPath $readyFile | ConvertFrom-Json
    $ownedProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($savedState.pid)" -ErrorAction SilentlyContinue
    if ($ownedProcess -and $ownedProcess.CommandLine -and $ownedProcess.CommandLine.IndexOf($serviceJar, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        $current = Invoke-RestMethod -Uri ($savedState.httpBaseUrl + 'health') -TimeoutSec 3
        if ($current.pid -eq $savedState.pid) { Write-Output "Already running: $($savedState.httpBaseUrl) ; $($savedState.dubboDirectUrl)"; return }
    }
}
if (-not $SkipBuild -or -not (Test-Path -LiteralPath $serviceJar)) {
    & mvn -B -ntp -f (Join-Path $serviceRoot 'service/pom.xml') package
    if ($LASTEXITCODE -ne 0) { throw 'Local service build failed' }
}
foreach ($listenPort in @($HttpPort, $DubboPort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $listenPort -ErrorAction SilentlyContinue) { throw "Port $listenPort is already in use; choose another port or stop the owning service" }
}
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
# Oracle javapath can be a launcher process with a different PID from the JVM.
# Prefer the actual JDK executable so startup ownership and stop checks are reliable.
$jdkPath = $env:JAVA_HOME
if (-not $jdkPath -or -not (Test-Path -LiteralPath (Join-Path $jdkPath 'bin/java.exe'))) {
    $javaHomeLine = & java -XshowSettings:properties -version 2>&1 | Select-String '^\s*java.home\s*='
    if (-not $javaHomeLine) { throw 'Cannot locate the JDK runtime; configure JAVA_HOME' }
    $jdkPath = $javaHomeLine.ToString().Split('=', 2)[1].Trim()
}
$javaPath = Join-Path $jdkPath 'bin/java.exe'
$argumentValues = @('-jar', ('"' + $serviceJar + '"'), "--http-port=$HttpPort", "--dubbo-port=$DubboPort", ('--state-dir="' + $stateDirectory + '"'))
$child = Start-Process -FilePath $javaPath -ArgumentList $argumentValues -WorkingDirectory $serviceRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $stateDirectory 'service.stdout.log') -RedirectStandardError (Join-Path $stateDirectory 'service.stderr.log')
try {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $child.Refresh()
        if ($child.HasExited) { throw 'Service exited during startup; read runtime/service.stderr.log' }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$HttpPort/health" -TimeoutSec 1
            if ($health.pid -eq $child.Id -and $health.status -eq 'UP') {
                if (-not (Test-Path -LiteralPath $readyFile)) { continue }
                $newState = Get-Content -Raw -LiteralPath $readyFile | ConvertFrom-Json
                if ($newState.pid -ne $child.Id) { continue }
                Write-Output "HTTP: http://127.0.0.1:$HttpPort/demo/orders"
                Write-Output "Dubbo: dubbo://[::1]:$DubboPort ; demo.golden.OrderService#create(java.util.Map)"
                Write-Output "PID: $($child.Id) ; stop using Stop-LocalService.ps1"
                return
            }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw 'Service did not become healthy; read runtime/service.stderr.log'
} catch {
    $child.Refresh()
    if (-not $child.HasExited) { Stop-Process -Id $child.Id }
    throw
}
