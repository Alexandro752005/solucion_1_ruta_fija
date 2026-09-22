@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeTest.ps1" -Action Verify
exit /b %ERRORLEVEL%
