# Evidencia de Fase 4 — 2026-08-30

## Verificación de código

| Comprobación ejecutada | Resultado observado |
| --- | --- |
| `backend\mvnw.cmd clean verify` | Correcto: recompilación limpia con 21 pruebas unitarias y 13 pruebas de integración, sin fallos ni errores. Testcontainers levantó PostgreSQL 16 y Flyway validó/aplicó V1–V5. |
| `OperationFlowIT` | Correcto: verifica creación `SCHEDULED`, idempotencia, conflicto de agenda, exclusión PostgreSQL ante solapamiento, reserva, inicio, cierre, cancelación segura, incidencias, reportes, PDF/XLSX, auditoría, ticket WebSocket, rol obsoleto y aislamiento tenant. |
| `OperationStreamBroadcasterTest` | Correcto: un coordinador recibe únicamente eventos de su grupo; otro coordinador no recibe el evento. |
| `frontend\npm.cmd run typecheck` | Correcto. |
| `frontend\npm.cmd run test:ci` | Correcto: 12 archivos y 30 pruebas aprobadas. |
| `frontend\npm.cmd run build` | Correcto localmente y también dentro de la imagen Docker de producción. |
| `frontend\npm.cmd audit --audit-level=high` | Correcto: `found 0 vulnerabilities`. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\medir-cumplimiento-java.ps1` | Correcto: 8 713 líneas Java, 3 967 TypeScript, 0 Dart; participación Java de 68,71 %. |

## Verificación Docker real

1. Se ejecutó `iniciar_ruta_fija.bat` desde la raíz del proyecto.
2. Compose validó la configuración, construyó las imágenes de backend y
   frontend y mantuvo el contenedor PostgreSQL existente con sus datos.
3. Flyway registró en los logs: `Successfully applied 1 migration ... now at
   version v5`; corresponde a la exclusión de solapamientos de asignación.
4. El primer arranque reveló que este Docker Desktop local necesitó 188,672 s
   para inicializar el backend. Se aumentó el tiempo de espera del iniciador a
   360 s y el período de inicio del healthcheck a 210 s. La repetición del
   iniciador concluyó con los tres servicios saludables.

Estado final confirmado por Compose:

| Servicio | Estado |
| --- | --- |
| `postgres` | `healthy` |
| `backend` | `healthy` en `127.0.0.1:8080` |
| `frontend` | `healthy` en `127.0.0.1:4200` |

Las consultas HTTP locales de solo lectura confirmaron:

| Comprobación | Resultado |
| --- | --- |
| `GET /actuator/health` | `UP` |
| `GET /actuator/info` | `app.phase = 4` |
| `GET http://localhost:4200/` | HTTP `200` |
| OpenAPI | Incluye `/api/v1/audit-events`, `/api/v1/reports/assignments/export` y `/api/v1/reports/incidents/export`. |

## Cierre

La evidencia es local y reproducible. El script `finalizar_ruta_fija.bat` está
disponible para detener los servicios sin usar `--volumes`; no se ejecutó como
parte de esta verificación para mantener el CRM disponible al finalizar.
