@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Inicia el CRM Ruta Fija local con Docker Compose.
rem No muestra el contenido de .env ni modifica los volumenes de datos.

cd /d "%~dp0"
if errorlevel 1 goto :workspace_error

echo.
echo [Ruta Fija] Verificando Docker Desktop y la configuracion local...

where docker >nul 2>&1
if errorlevel 1 goto :docker_missing

docker version --format "{{.Server.Version}}" >nul 2>&1
if errorlevel 1 goto :docker_unavailable

docker compose version >nul 2>&1
if errorlevel 1 goto :compose_missing

if not exist ".env" goto :env_missing

rem Valida la configuracion sin imprimir valores de variables sensibles.
docker compose --profile app config --quiet
if errorlevel 1 goto :config_invalid

echo [Ruta Fija] Construyendo e iniciando PostgreSQL, API y CRM...
docker compose --profile app up --detach --build --wait --wait-timeout 360
if errorlevel 1 goto :start_failed

echo.
echo [Ruta Fija] Entorno disponible.
echo   CRM:    http://localhost:4200
echo   API:    http://localhost:8080/api/v1
echo   Health: http://localhost:8080/actuator/health
echo.
docker compose --profile app ps
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
echo [Ruta Fija] Falta el archivo .env local. Creelo desde .env.example antes de iniciar.
set "EXIT_CODE=1"
goto :end

:config_invalid
echo [Ruta Fija] La configuracion de Compose no es valida. Revise .env sin compartir sus secretos.
set "EXIT_CODE=1"
goto :end

:start_failed
echo [Ruta Fija] El entorno no alcanzo un estado saludable. Revise los logs con:
echo   docker compose --profile app logs --tail 200 postgres backend frontend
set "EXIT_CODE=1"

:end
endlocal & exit /b %EXIT_CODE%
