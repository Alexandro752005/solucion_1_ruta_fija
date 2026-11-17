@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "RF_EXIT_CODE=1"

rem F1.1B detiene solo PID registrados y verificados por Ruta Fija.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  goto :failed
)
if not "%~1"=="" (
  echo Uso: finalizar_ruta_fija.bat
  set "RF_EXIT_CODE=2"
  goto :failed
)

if exist ".runtime\ruta-fija-quick-demo.json" (
  echo [Ruta Fija] Cerrando primero la exposicion publica F5.2...
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaQuickDemo.ps1" -Action Stop
  if errorlevel 1 (
    echo [Ruta Fija] No se pudo verificar el cierre completo de la demo.
    goto :failed
  )
)

echo [Ruta Fija] Cerrando primero el puente movil administrado...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaMobileLocalBridge.ps1" -Action Stop -Execute
if errorlevel 1 (
  echo [Ruta Fija] No se pudo verificar el cierre del puente movil; no se detienen procesos ajenos.
  goto :failed
)

echo [Ruta Fija] Cerrando backend y CRM administrados...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Stop
if errorlevel 1 (
  echo [Ruta Fija] No se detuvo un proceso porque no pudo verificarse como propio.
  goto :failed
)

echo [Ruta Fija] Backend y CRM detenidos. PostgreSQL se conserva salvo lease propia y sin otros clientes...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action StopOwned -Execute
if errorlevel 1 (
  echo [Ruta Fija] El proyecto se detuvo; PostgreSQL se conserva. Revise el error anterior.
  goto :failed
)

echo [Ruta Fija] Cierre de procesos administrados completado; PostgreSQL segun lease y clientes.
endlocal & exit /b 0

:failed
echo [Ruta Fija] Cierre incompleto. Se conserva el detalle del error en esta ventana.
if not "%RF_LAUNCHER_NO_PAUSE%"=="1" pause
endlocal & exit /b %RF_EXIT_CODE%
