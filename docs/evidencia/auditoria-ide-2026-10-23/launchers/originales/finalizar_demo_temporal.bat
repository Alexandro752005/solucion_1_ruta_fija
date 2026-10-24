@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F5.2: corta primero la exposicion publica y luego libera CRM, API y PostgreSQL.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

%SystemRoot%System32WindowsPowerShell1.0powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scriptsInvoke-RutaFijaQuickDemo.ps1" -Action Stop
if errorlevel 1 (
  echo [Ruta Fija] El cierre de la demo requiere revision. No finalice procesos manualmente.
  endlocal & exit /b 1
)

echo [Ruta Fija] Demo cerrada: URL revocada y servicios locales detenidos.
endlocal & exit /b 0
