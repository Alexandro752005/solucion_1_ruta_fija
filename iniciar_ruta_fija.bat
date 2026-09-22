@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.1B: inicia API Spring Boot y CRM Angular nativos con controles de PID y health.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
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

%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Invoke-RutaFijaNativeRuntime.ps1" -Action Start
if errorlevel 1 (
  echo [Ruta Fija] F1.1B no aprobo el inicio nativo. Revise .runtime\backend.stderr.log o .runtime\frontend.stderr.log.
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] CRM disponible en http://localhost:4200
echo [Ruta Fija] API disponible en http://127.0.0.1:8080
endlocal & exit /b 0
