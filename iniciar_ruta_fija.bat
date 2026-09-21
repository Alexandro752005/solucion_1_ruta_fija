@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.1A: valida el entorno nativo sin iniciar Spring Boot, Flyway, API ni CRM.
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

%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Test-RutaFijaNativePreflight.ps1"
if errorlevel 1 (
  echo [Ruta Fija] El preflight nativo no fue aprobado. No se inicio ningun proceso.
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] F1.1A aprobado: configuracion nativa y preflight verificados.
echo [Ruta Fija] API y CRM permanecen detenidos hasta F1.1B, posterior a F1.2 y F1.3.
endlocal & exit /b 0
