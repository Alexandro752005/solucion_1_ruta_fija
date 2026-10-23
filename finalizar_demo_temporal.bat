@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
if errorlevel 1 (endlocal & exit /b 1)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaQuickDemo.ps1" -Action Stop
if errorlevel 1 (
 echo [Ruta Fija] No se verifico el cierre de la demo; revise el estado registrado.
 endlocal & exit /b 1
)
echo [Ruta Fija] Tunel y procesos administrados cerrados; PostgreSQL segun propiedad y clientes.
endlocal & exit /b 0
