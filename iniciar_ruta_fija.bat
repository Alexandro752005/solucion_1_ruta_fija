@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.1B: inicia API Spring Boot y CRM Angular nativos con controles de PID y health.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

if exist ".runtime\ruta-fija-quick-demo.json" (
  echo [Ruta Fija] Existe una demo temporal activa.
  echo Use finalizar_demo_temporal.bat antes del arranque local.
  endlocal & exit /b 1
)

if not exist "backend\.local\ruta-fija-native.env" (
  echo [Ruta Fija] Falta la configuracion local de secretos.
  echo Ejecute en PowerShell:
  echo   .\scripts\Initialize-RutaFijaNativeConfig.ps1
  endlocal & exit /b 1
)

if not exist "backend\.local\ruta-fija-bootstrap.env" (
  echo [Ruta Fija] Falta el bootstrap privado de roles F1.2.
  echo Ejecute en PowerShell:
  echo   .\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
  endlocal & exit /b 1
)

set "RF_RUNTIME_MODE="
if /I "%~1"=="--smoke-read-only" set "RF_RUNTIME_MODE=-SmokeReadOnly"
if not "%~1"=="" if /I not "%~1"=="--smoke-read-only" (endlocal & exit /b 2)
if not "%~2"=="" if /I not "%~2"=="--test-database" (endlocal & exit /b 2)
if /I "%~2"=="--test-database" if /I not "%~1"=="--smoke-read-only" (endlocal & exit /b 2)
if /I "%~2"=="--test-database" set "RF_RUNTIME_MODE=-SmokeReadOnly -SmokeTestDatabase"
if not "%~3"=="" (endlocal & exit /b 2)
echo [Ruta Fija] Preparando PostgreSQL con propiedad controlada...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action Start -Execute
if errorlevel 1 (
  echo [Ruta Fija] No se pudo activar PostgreSQL. Acepte la solicitud de administrador.
  endlocal & exit /b 1
)

echo [Ruta Fija] Iniciando backend y CRM en modo de bajo consumo...
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Start -StartupTimeoutSeconds 180 %RF_RUNTIME_MODE%
if errorlevel 1 (
  echo [Ruta Fija] F1.1B no aprobo el inicio nativo. Revise .runtime\backend.stderr.log o .runtime\frontend.stderr.log.
  echo [Ruta Fija] Revirtiendo PostgreSQL porque el proyecto no inicio...
  "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaPostgresqlLease.ps1" -Action StopOwned -Execute
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] CRM disponible en http://127.0.0.1:4300
echo [Ruta Fija] API disponible en http://127.0.0.1:8080
echo [Ruta Fija] Para liberar los recursos use finalizar_ruta_fija.bat
endlocal & exit /b 0
