@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.3: valida esquema PostgreSQL nativo sin iniciar Spring Boot, API ni CRM.
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

%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Test-RutaFijaMigratedSchemaF13.ps1"
if errorlevel 1 (
  echo [Ruta Fija] La auditoria del esquema F1.3 no fue aprobada. No se inicio ningun proceso.
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] F1.3 aprobado: esquema Flyway y permisos nativos verificados.
echo [Ruta Fija] API y CRM permanecen detenidos hasta F1.1B.
endlocal & exit /b 0
