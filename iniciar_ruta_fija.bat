@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "RF_EXIT_CODE=1"

rem F1.1B: inicia API Spring Boot y CRM Angular nativos con controles de PID y health.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  goto :failed
)

if exist ".runtime\ruta-fija-quick-demo.json" (
  echo [Ruta Fija] Existe una demo temporal activa.
  echo Use finalizar_demo_temporal.bat antes del arranque local.
  goto :failed
)

if not exist "backend\.local\ruta-fija-native.env" (
  echo [Ruta Fija] Falta la configuracion local de secretos.
  echo Ejecute en PowerShell:
  echo   .\scripts\Initialize-RutaFijaNativeConfig.ps1
  goto :failed
)

if not exist "backend\.local\ruta-fija-bootstrap.env" (
  echo [Ruta Fija] Falta el bootstrap privado de roles F1.2.
  echo Ejecute en PowerShell:
  echo   .\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
  goto :failed
)

set "RF_RUNTIME_MODE="
if /I "%~1"=="--smoke-read-only" set "RF_RUNTIME_MODE=-SmokeReadOnly"
if not "%~1"=="" if /I not "%~1"=="--smoke-read-only" goto :usage
if not "%~2"=="" if /I not "%~2"=="--test-database" goto :usage
if /I "%~2"=="--test-database" if /I not "%~1"=="--smoke-read-only" goto :usage
if /I "%~2"=="--test-database" set "RF_RUNTIME_MODE=-SmokeReadOnly -SmokeTestDatabase"
if not "%~3"=="" goto :usage
echo [Ruta Fija] Preparando PostgreSQL con propiedad controlada...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action Start -Execute
if errorlevel 1 (
  echo [Ruta Fija] PostgreSQL no pudo prepararse. Revise el error anterior.
  goto :failed
)

echo [Ruta Fija] Iniciando backend y CRM en modo de bajo consumo...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Start -StartupTimeoutSeconds 180 %RF_RUNTIME_MODE%
if errorlevel 1 (
  echo [Ruta Fija] Inicio incompleto. Revise el error anterior y .runtime\backend-build.log,
  echo .runtime\backend.stdout.log, .runtime\backend.stderr.log o .runtime\frontend.stderr.log.
  echo [Ruta Fija] Revirtiendo PostgreSQL porque el proyecto no inicio...
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action StopOwned -Execute
  goto :failed
)

if not defined RF_RUNTIME_MODE (
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Sync-RutaFijaMobilePeer.ps1" -Execute
  if errorlevel 1 (
    echo [Ruta Fija] API y CRM activos; no pudo prepararse el movil. Revise el error anterior.
    goto :failed
  )
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaMobileLocalBridge.ps1" -Action Start -Execute
  if errorlevel 1 (
    echo [Ruta Fija] API y CRM activos; el puente movil no aprobo. Revise .runtime\mobile-bridge.stderr.log.
    goto :failed
  )
)

echo.
echo [Ruta Fija] CRM disponible en http://127.0.0.1:4300
echo [Ruta Fija] API disponible en http://127.0.0.1:8080
echo [Ruta Fija] El puente conecta la app instalada; este BAT no instala ni sustituye APK.
echo [Ruta Fija] Para liberar los recursos use finalizar_ruta_fija.bat
endlocal & exit /b 0

:usage
echo Uso: iniciar_ruta_fija.bat [--smoke-read-only [--test-database]]
set "RF_EXIT_CODE=2"
:failed
echo [Ruta Fija] Inicio incompleto. Se conserva el detalle del error en esta ventana.
if not "%RF_LAUNCHER_NO_PAUSE%"=="1" pause
endlocal & exit /b %RF_EXIT_CODE%
