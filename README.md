# Ruta Fija — CRM Web Administrativo y Reportes

Monolito modular para administrar organizaciones, usuarios, grupos, conductores,
vehículos, operación y reportes. La Fase 4 completa la gestión administrativa,
la auditoría de consulta y los reportes exportables con Spring Boot, Angular y
PostgreSQL local.

> Alcance confirmado: este repositorio construye únicamente el **CRM web
> administrativo + reportes**. No incluye aplicación móvil. Durante esta fase
> tampoco integra FCM, SMTP, S3 ni ninguna base de datos externa.

## Tecnologías fijadas

| Componente | Versión / decisión |
| --- | --- |
| Java | 21 |
| Maven | 3.9.15 mediante Maven Wrapper |
| Spring Boot | 3.5.16 |
| Node.js | 24.16.x |
| Angular | 22.x, versión exacta en `frontend/package-lock.json` |
| Base de datos | PostgreSQL 16 en Docker |
| API | `/api/v1` |
| Backend | `http://localhost:8080` |
| CRM web | `http://localhost:4200` |

PostgreSQL del contenedor es PostgreSQL real, no un sustituto en memoria. H2 no
se usa como base principal ni como atajo para integración. Las pruebas de
persistencia deben ejecutarse contra PostgreSQL efímero con Testcontainers.

## Requisitos locales

- Docker Desktop con el motor iniciado, Docker Compose v2 y WSL 2.1.5 o
  posterior (`wsl --version`) en Windows.
- Visual Studio Code y la extensión Docker, recomendados para administrar los
  mismos contenedores desde el editor.
- Git.
- Java y Node solo son necesarios si se ejecutan backend o frontend fuera de
  contenedores.

La extensión de VS Code es la interfaz; el motor que crea la base de datos sigue
siendo Docker Desktop. No es necesario instalar PostgreSQL, pgAdmin ni otra
aplicación de base de datos.

Si Docker Desktop informa `WSL update required`, abra PowerShell como
Administrador y ejecute:

```powershell
wsl --update --web-download
```

Reinicie Windows si el instalador lo solicita, confirme `wsl --version` y vuelva
a iniciar Docker Desktop. Docker Desktop requiere WSL 2.1.5 o posterior.

Al abrir el proyecto, VS Code recomendará **Container Tools** y ofrece tareas
`Ruta Fija: ...` en `Terminal > Run Task` para iniciar PostgreSQL, construir la
solución, consultar su estado y detenerla sin borrar el volumen.

### Inicio y cierre rápido en Windows

Desde el Explorador de archivos o una consola de Windows, ejecute
`iniciar_ruta_fija.bat` para validar la configuración y levantar PostgreSQL, la
API y el CRM. Ejecute `finalizar_ruta_fija.bat` para detenerlos; este último no
usa `--volumes`, por lo que conserva los datos locales de PostgreSQL.

## Primera ejecución desde PowerShell

1. Cree la configuración local no versionada:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Genere un secreto JWT local de 32 bytes:

   ```powershell
   $jwtBytes = New-Object byte[] 32
   $jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
   [Convert]::ToBase64String($jwtBytes)
   ```

3. Edite `.env`: reemplace `POSTGRES_PASSWORD` por una contraseña local,
   `JWT_SECRET_BASE64` por el valor recién generado y `DEMO_USER_PASSWORD` por
   una contraseña exclusiva para los usuarios de demostración. Nunca confirme
   `.env` en Git.

4. Inicie solo PostgreSQL:

   ```powershell
   docker compose up -d postgres
   docker compose ps
   ```

5. Para construir y levantar toda la solución cuando los módulos estén listos:

   ```powershell
   docker compose --profile app up -d --build
   docker compose --profile app ps
   ```

6. Compruebe los puntos de entrada:

   - CRM: `http://localhost:4200`
   - API: `http://localhost:8080/api/v1`
   - Health: `http://localhost:8080/actuator/health`
   - OpenAPI: `http://localhost:8080/v3/api-docs`

Para observar el arranque:

```powershell
docker compose --profile app logs -f postgres backend frontend
```

Para detener conservando la base local:

```powershell
docker compose --profile app down
```

La siguiente operación elimina de forma irreversible los datos locales del
volumen y solo debe emplearse para ensayar una migración desde cero:

```powershell
docker compose --profile app down --volumes
```

## Ejecución y pruebas sin contenerizar la aplicación

Con PostgreSQL iniciado por Compose, se pueden ejecutar los módulos desde las
terminales integradas de VS Code:

```powershell
Set-Location backend
.\mvnw.cmd verify
```

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run test:ci
npm.cmd run build
```

Los valores de conexión y seguridad deben suministrarse mediante variables de
entorno o el perfil local documentado por cada módulo. No se deben codificar
credenciales en archivos fuente.

## Datos de demostración

Los datos semilla de las dos organizaciones existen exclusivamente bajo el
perfil `dev`. Sirven para demostrar RBAC y aislamiento multiempresa; no deben
activarse en `test` ni `prod`, ni copiarse a una futura instancia administrada.

## Flujo de calidad

La integración continua valida el modelo de Compose, construye las imágenes,
exige que las integraciones PostgreSQL no sean omitidas, ejecuta `mvnw verify`,
audita las dependencias npm con umbral alto y compila/prueba el frontend.
Dependabot revisa semanalmente Maven, npm y las acciones del pipeline. La
aceptación explícita del propietario autorizó la Fase 4. La auditoría final
deja constancia de los controles superados y de los límites que requieren una
futura preparación de producción. La ausencia de un repositorio Git sigue
siendo un control operativo pendiente, no un bloqueo para el entorno local
aprobado.

Documentación adicional:

- [Arquitectura de la Fase 1](docs/arquitectura-fase-1.md)
- [Decisión de PostgreSQL local](docs/decisiones/ADR-001-postgresql-docker-local.md)
- [Auditoría documental y riesgos](docs/auditoria-requisitos-fase-1.md)
- [Estado actual de la Fase 1](docs/fase-1-estado.md)
- [Evidencia parcial del 2026-08-29](docs/evidencia-fase-1-2026-08-29.md)
- [Estado y alcance de la Fase 2](docs/fase-2-estado.md)
- [Evidencia de la Fase 2](docs/evidencia-fase-2-2026-08-29.md)
- [Diseño y decisiones de la Fase 3](docs/fase-3-diseno.md)
- [Estado y alcance de la Fase 3](docs/fase-3-estado.md)
- [Evidencia de la Fase 3](docs/evidencia-fase-3-2026-08-29.md)
- [Estado de cierre de la Fase 4](docs/fase-4-estado.md)
- [Matriz de trazabilidad final](docs/matriz-trazabilidad-final.md)
- [Auditoría final de la Fase 4](docs/auditoria-final-fase-4.md)
- [Evidencia de la Fase 4](docs/evidencia-fase-4-2026-08-30.md)
- [Cumplimiento Java](REPORTE_CUMPLIMIENTO_JAVA.md)
- [Uso del Sistema](<docs/Uso del Sistema.md>)
