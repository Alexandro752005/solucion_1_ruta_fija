# Evidencia de Fase 1 — 2026-08-29

## Dictamen

Estado original: **APROBADA TÉCNICAMENTE PARA EL 25 % LOCAL**.

Actualización de continuidad: el propietario autorizó expresamente el inicio de
la Fase 2 el 2026-08-29. La evidencia de esa fase se conserva por separado en
[`evidencia-fase-2-2026-08-29.md`](evidencia-fase-2-2026-08-29.md).

La solución fue construida y ejecutada integralmente con Docker Desktop, sin
base de datos externa: PostgreSQL 16 corre en el mismo entorno local de VS Code.
El control de salida pendiente es operativo, no de implementación: asociar el
proyecto a un repositorio Git autorizado y ejecutar la CI sobre un commit. La
aceptación explícita del propietario para continuar ya fue recibida.

## Entorno observado

| Elemento | Resultado | Tipo de evidencia |
| --- | --- | --- |
| Sistema | Windows, PowerShell 5.1 / CLR 4 | Observado |
| Java | 21.0.10 | Observado |
| Node.js | 24.16.0 | Observado |
| npm | 11.13.0 mediante `npm.cmd` | Observado |
| Maven | 3.9.15 mediante Wrapper; checksum fijado | Observado y verificado |
| Spring Boot | 3.5.16 | Compilado y ejecutado |
| Angular | core 22.1.4; CLI/build 22.1.6 | Compilado y probado |
| TypeScript | 6.0.3 | Comprobado |
| Vitest | 4.1.11 | Ejecutado |
| Docker Desktop Engine | 29.7.2 | Observado en ejecución |
| Docker Compose | 5.1.4 | Ejecutado |
| WSL | 2.7.12.0 | Observado |
| PostgreSQL | 16.15, imagen `postgres:16-alpine` | Ejecutado |
| Commit | No disponible | El directorio aún no es un repositorio Git |

## Resultados de validación

| Comprobación | Resultado | Dictamen |
| --- | --- | --- |
| Perfil Docker `app` | `postgres`, `backend` y `frontend` saludables | Aprobado |
| PostgreSQL | 16.15 accesible sólo en `127.0.0.1:5432` | Aprobado |
| Flyway desde esquema vacío | versión `1`, descripción `identity organization audit`, éxito | Aprobado |
| Semillas de desarrollo | 2 organizaciones y 5 usuarios | Aprobado |
| Backend `verify` | 20 unitarias y 7 de integración; 0 fallos, 0 errores, 0 omitidas | Aprobado |
| Integración PostgreSQL | Testcontainers inició PostgreSQL 16.15 y aplicó Flyway | Aprobado |
| Flujo de autenticación HTTP | login, `/me`, refresh/rotación, reutilización, logout y errores | Aprobado |
| Aislamiento y RBAC | caso negativo de organización dentro de `AuthFlowIT` | Aprobado |
| API operativa | `/actuator/health` respondió `UP`; `/v3/api-docs` respondió 200 | Aprobado |
| Frontend servido por Nginx | `/health` y aplicación web respondieron 200; proxy `/api` validado | Aprobado |
| CORS autorizado | preflight devuelve origen, métodos, credenciales y correlation ID | Aprobado |
| CORS no autorizado | 403 JSON con `CROSS_ORIGIN_REQUEST_DENIED` | Aprobado |
| Auditoría persistida | eventos de autenticación presentes en PostgreSQL | Aprobado |
| Inmutabilidad de auditoría | trigger `trg_audit_event_append_only` registrado | Aprobado |
| Frontend lint/typecheck | Sin errores | Aprobado |
| Frontend pruebas | 8 archivos, 22 pruebas aprobadas | Aprobado |
| Frontend build | build de producción aprobado | Aprobado |
| Dependencias frontend | `npm audit --audit-level=high`: 0 vulnerabilidades | Aprobado |
| Secretos y alcance | 0 patrones de secretos y 0 implementaciones fuera de alcance | Aprobado localmente |
| CI remota | Workflow configurado, sin commit ni repositorio donde ejecutarlo | Pendiente operativo |

## Evidencia de ejecución

1. Docker Desktop estuvo disponible a través de WSL 2.7.12.0 y se levantó el
   perfil `app` con el archivo `.env` local ignorado. Ningún secreto se expone
   en este documento.
2. Los tres servicios quedaron saludables: frontend en `127.0.0.1:4200`, API
   en `127.0.0.1:8080` y PostgreSQL en `127.0.0.1:5432`.
3. PostgreSQL informó versión 16.15. Flyway registró la migración V1 y el seed
   de desarrollo dejó dos organizaciones y cinco usuarios, sin usar servicios
   externos ni una base administrada.
4. `mvnw verify` ejecutó 20 pruebas unitarias y 7 de integración. Testcontainers
   detectó el Docker local, creó PostgreSQL 16.15 efímero y aplicó Flyway antes
   de `AuthFlowIT`; el resultado fue verde sin pruebas omitidas.
5. El flujo HTTP contra el contenedor validó login 200, `/me` 200, refresh
   rotativo 200, reutilización de refresh 401, credenciales inválidas 401,
   origen no confiable 403, logout 204 y login a través del proxy Nginx 200.
6. La cookie de refresh se comprobó `HttpOnly` y `SameSite=Strict`. No se
   registran tokens, contraseñas ni cabeceras de autorización en la evidencia.
7. El preflight permitido desde `http://localhost:4200` devolvió los métodos
   permitidos, `Access-Control-Allow-Credentials: true` y el origen esperado.
   El preflight no confiable devolvió el contrato JSON normalizado con 403.
8. La consulta de auditoría observó los tipos `LOGIN_SUCCESS`, `LOGIN_FAILED`,
   `TOKEN_REFRESHED`, `REFRESH_REUSE_DETECTED` y `LOGOUT`, así como el trigger
   `trg_audit_event_append_only` sobre `audit_event`.

## Controles de seguridad incorporados

- JWT de acceso corto y refresh opaco almacenado como hash, rotativo y con
  revocación de familia ante reutilización.
- Cookie de refresh `HttpOnly` y `SameSite=Strict`; login, refresh y logout
  validan el origen del navegador.
- Filtro de validación de origen de API antes de Spring Security CORS, para que
  un origen no confiable reciba el contrato JSON y correlation ID normalizados.
- Límite de intentos de login por cuenta anonimizada y límite global, con 429 y
  `Retry-After`.
- Logout idempotente basado en cookie; no requiere conservar un Bearer válido.
- Auditoría transaccional de login correcto/fallido, refresh, reutilización y
  logout; tabla protegida mediante trigger append-only.
- Frontend con access token sólo en memoria, refresh coordinado entre pestañas
  y sin adjuntar Bearer a endpoints públicos.
- Runtime de frontend limitado al mismo origen, con timeout; Nginx conserva
  cabeceras de seguridad en todas las ubicaciones.

## Pendiente para cerrar formalmente la Fase 1

1. Autorizar la creación o vinculación de un repositorio Git para este
   directorio; actualmente `git rev-parse --is-inside-work-tree` confirma que
   no existe uno.
2. Ejecutar el workflow de CI contra un commit identificable y conservar su
   resultado.
3. Mantener la aceptación del propietario registrada para las transiciones de
   fase posteriores.
