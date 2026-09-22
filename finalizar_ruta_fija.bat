@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.1B detiene solo PID registrados y verificados por Ruta Fija.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Stop
if errorlevel 1 (
  echo [Ruta Fija] No se detuvo un proceso porque no pudo verificarse como propio.
  endlocal & exit /b 1
)

echo [Ruta Fija] PostgreSQL 16 permanece como servicio local de Windows.
endlocal & exit /b 0
