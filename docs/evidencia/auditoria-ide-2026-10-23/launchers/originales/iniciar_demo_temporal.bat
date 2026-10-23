@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F5.2: publica temporalmente CRM/API mediante Cloudflare Quick Tunnel.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

echo [Ruta Fija] Iniciando demo publica temporal...
%SystemRoot%System32WindowsPowerShell1.0powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scriptsInvoke-RutaFijaQuickDemo.ps1" -Action Start -StartupTimeoutSeconds 180
if errorlevel 1 (
  echo [Ruta Fija] F5.2 no pudo iniciar. No comparta ninguna URL incompleta.
  endlocal & exit /b 1
)

echo.
echo [Ruta Fija] La URL vigente tambien esta en:
echo   %~dp0.runtimeuta-fija-demo-url.txt
echo [Ruta Fija] Para cerrar toda la demo use finalizar_demo_temporal.bat
endlocal & exit /b 0
