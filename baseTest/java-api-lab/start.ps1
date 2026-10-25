param([int]$Port = 8080, [int]$TokenTtlSeconds = 1800)
$ErrorActionPreference = 'Stop'
$labJars = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'target') -Filter 'java-api-lab-*.jar' -File -ErrorAction SilentlyContinue)
if ($labJars.Count -ne 1) { throw 'Run build.ps1 first; expected one current API lab JAR' }
$labJar = $labJars[0].FullName
& java "-Dlab.port=$Port" "-Dlab.tokenTtlSeconds=$TokenTtlSeconds" -jar $labJar
if ($LASTEXITCODE -ne 0) { throw 'API lab exited with an error' }
