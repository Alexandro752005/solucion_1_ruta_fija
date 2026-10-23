@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if errorlevel 1 (endlocal & exit /b 1)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeTest.ps1" -Action Verify
set "RF_VERIFY_EXIT=%ERRORLEVEL%"
endlocal & exit /b %RF_VERIFY_EXIT%
