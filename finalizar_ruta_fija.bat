@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.1B detiene solo PID registrados y verificados por Ruta Fija.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

if exist ".runtime\ruta-fija-quick-demo.json" (
  echo [Ruta Fija] Cerrando primero la exposicion publica F5.2...
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaQuickDemo.ps1" -Action Stop
  if errorlevel 1 (
    echo [Ruta Fija] No se pudo verificar el cierre completo de la demo.
    endlocal & exit /b 1
  )
  echo [Ruta Fija] Demo publica y servicios locales detenidos.
  endlocal & exit /b 0
)

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Stop
if errorlevel 1 (
  echo [Ruta Fija] No se detuvo un proceso porque no pudo verificarse como propio.
  endlocal & exit /b 1
)

echo [Ruta Fija] Backend y CRM detenidos. PostgreSQL se conserva salvo lease propia y sin otros clientes...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action StopOwned -Execute
if errorlevel 1 (
  echo [Ruta Fija] El proyecto se detuvo, pero PostgreSQL requiere autorizacion de administrador.
  endlocal & exit /b 1
)

echo [Ruta Fija] Cierre de procesos administrados completado; PostgreSQL segun lease y clientes.
endlocal & exit /b 0
