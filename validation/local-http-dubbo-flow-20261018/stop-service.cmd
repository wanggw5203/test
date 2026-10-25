@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Stop-LocalService.ps1"
if errorlevel 1 (
  echo.
  echo Stop refused or failed. An IDEA-launched service must be stopped in IDEA.
)
echo.
pause
