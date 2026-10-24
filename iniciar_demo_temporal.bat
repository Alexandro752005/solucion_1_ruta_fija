@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if errorlevel 1 (endlocal & exit /b 1)
echo [Ruta Fija] Iniciando demo publica temporal bajo control local...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaQuickDemo.ps1" -Action Start -StartupTimeoutSeconds 180
if errorlevel 1 (
 echo [Ruta Fija] No se aprobo la demo. No comparta ninguna URL incompleta.
 endlocal & exit /b 1
)
echo [Ruta Fija] URL vigente: "%~dp0.runtime\ruta-fija-demo-url.txt"
echo [Ruta Fija] Cierre con finalizar_demo_temporal.bat
endlocal & exit /b 0
