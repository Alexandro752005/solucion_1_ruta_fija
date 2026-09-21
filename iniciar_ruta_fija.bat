@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.2: valida roles PostgreSQL nativos sin iniciar Spring Boot, Flyway, API ni CRM.
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

%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Test-RutaFijaRoleIsolation.ps1"
if errorlevel 1 (
  echo [Ruta Fija] La auditoria de aislamiento F1.2 no fue aprobada. No se inicio ningun proceso.
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] F1.2 aprobado: roles y bases nativas aisladas.
echo [Ruta Fija] API y CRM permanecen detenidos hasta F1.1B, posterior a F1.3.
endlocal & exit /b 0
