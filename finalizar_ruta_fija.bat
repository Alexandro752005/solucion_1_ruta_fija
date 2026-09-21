@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem F1.3 no inicia procesos nativos; este cierre es seguro e idempotente.
cd /d "%~dp0"
if errorlevel 1 (
  echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
  endlocal & exit /b 1
)

echo [Ruta Fija] F1.3 no inicio backend ni frontend.
echo [Ruta Fija] No hay procesos nativos que detener en esta fase.
echo [Ruta Fija] PostgreSQL 16 permanece como servicio local de Windows.
endlocal & exit /b 0
