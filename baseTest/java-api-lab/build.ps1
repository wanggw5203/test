$ErrorActionPreference = 'Stop'
& mvn -B -ntp -f "$PSScriptRoot/../pom.xml" -pl java-api-lab -am clean package
if ($LASTEXITCODE -ne 0) { throw 'API lab build failed' }
