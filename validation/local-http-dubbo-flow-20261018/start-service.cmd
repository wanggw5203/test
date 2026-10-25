@echo off
setlocal
cd /d "%~dp0"
echo Starting local HTTP and Dubbo service...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-LocalService.ps1" %*
if errorlevel 1 (
  echo.
  echo Startup failed. If IDEA is running LocalOrderApplication, stop it first.
  echo Check runtime\service.stderr.log for service errors.
) else (
  echo.
  echo Health: http://127.0.0.1:18081/health
  echo The service runs in the background. Use stop-service.cmd to stop it.
)
echo.
pause
