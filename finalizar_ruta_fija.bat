@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Detiene el CRM Ruta Fija local sin eliminar el volumen postgres_data.
rem No usa --volumes, por lo que la informacion de PostgreSQL se conserva.

cd /d "%~dp0"
if errorlevel 1 goto :workspace_error

echo.
echo [Ruta Fija] Deteniendo el entorno local y conservando los datos...

where docker >nul 2>&1
if errorlevel 1 goto :docker_missing

docker version --format "{{.Server.Version}}" >nul 2>&1
if errorlevel 1 goto :docker_unavailable

docker compose version >nul 2>&1
if errorlevel 1 goto :compose_missing

if not exist ".env" goto :env_missing

rem No se usan --volumes ni comandos que borren la base de datos local.
docker compose --profile app down
if errorlevel 1 goto :stop_failed

echo.
echo [Ruta Fija] Entorno detenido correctamente.
echo [Ruta Fija] El volumen postgres_data y los datos locales se conservaron.
set "EXIT_CODE=0"
goto :end

:workspace_error
echo [Ruta Fija] No fue posible acceder a la carpeta del proyecto.
set "EXIT_CODE=1"
goto :end

:docker_missing
echo [Ruta Fija] Docker no esta disponible en PATH. Instale o abra Docker Desktop.
set "EXIT_CODE=1"
goto :end

:docker_unavailable
echo [Ruta Fija] El motor de Docker no responde. Inicie Docker Desktop y vuelva a intentarlo.
set "EXIT_CODE=1"
goto :end

:compose_missing
echo [Ruta Fija] Docker Compose v2 no esta disponible. Actualice Docker Desktop.
set "EXIT_CODE=1"
goto :end

:env_missing
echo [Ruta Fija] Falta el archivo .env local; Compose lo necesita para identificar la configuracion.
set "EXIT_CODE=1"
goto :end

:stop_failed
echo [Ruta Fija] No se pudo detener completamente el entorno. Revise su estado con:
echo   docker compose --profile app ps
set "EXIT_CODE=1"

:end
endlocal & exit /b %EXIT_CODE%
