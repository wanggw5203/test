@echo off
setlocal
cd /d %~dp0
call mvn clean verify || exit /b 1
if not exist ..\dist mkdir ..\dist
for %%F in (target\base-test-toolkit-*.jar) do (
  copy /y "%%F" "..\dist\%%~nxF" || exit /b 1
  powershell -NoProfile -Command "(Get-FileHash -LiteralPath '..\dist\%%~nxF' -Algorithm SHA256).Hash | Set-Content -LiteralPath '..\dist\%%~nxF.sha256'" || exit /b 1
  echo Built %cd%\..\dist\%%~nxF
)
