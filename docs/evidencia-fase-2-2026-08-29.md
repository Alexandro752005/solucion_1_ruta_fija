# Evidencia de Fase 2 — 2026-08-29

## Dictamen

**APROBADA LOCALMENTE PARA EL 50 % DEL MVP.** La gestión administrativa se
ejecuta sobre PostgreSQL 16 dentro del mismo entorno Docker usado por VS Code.
No se conectó ninguna base de datos externa.

## Validaciones ejecutadas

| Comprobación | Resultado |
| --- | --- |
| Flyway local | `1|identity organization audit|t` y `2|fleet administration|t` |
| Docker Compose | PostgreSQL, backend y frontend en estado `healthy` |
| Health backend | `GET /actuator/health` respondió 200 |
| Health frontend | `GET /health` respondió 200 |
| Lecturas administrativas autenticadas | `/users`, `/groups`, `/drivers` y `/vehicles` respondieron 200 para administrador del tenant |
| Migración desde cero en integración | Testcontainers PostgreSQL aplicó V1 y V2 |
| Backend | 20 pruebas unitarias y 9 de integración; 0 fallos, 0 errores, 0 omitidas |
| Frontend | typecheck y build aprobados; 9 archivos Vitest y 24 pruebas aprobadas |

La lectura local autenticada encontró dos usuarios semilla del tenant y ningún
grupo, conductor ni vehículo todavía. Es el estado inicial correcto: los
recursos de Fase 2 se crean desde el CRM, no se simulan para mostrar métricas.

## Casos significativos de integración

`FleetManagementIT` valida, entre otros controles:

1. alta de grupo, asignación de coordinador, usuario conductor, conductor,
   vehículo y vínculo conductor–vehículo;
2. visibilidad limitada del coordinador y prohibición de escrituras para ese
   rol;
3. aislamiento frente a recursos de otra organización;
4. auditoría de eventos administrativos;
5. rechazo de documento duplicado, estado manual `EN_SERVICIO` y desactivación
   de un grupo que aún contiene conductores activos.

`AuthFlowIT` conserva la validación de login, refresh, logout, CORS,
aislamiento y auditoría de identidad.

## Artefactos principales

- Migración: `backend/src/main/resources/db/migration/V2__fleet_administration.sql`.
- API: controladores de organizaciones, usuarios, grupos, conductores y
  vehículos bajo `/api/v1`.
- UI: pantallas Angular en `frontend/src/app/features/` y cliente HTTP en
  `frontend/src/app/core/management/management-api.service.ts`.
- Pruebas: `FleetManagementIT` y
  `frontend/src/app/core/management/management-api.service.spec.ts`.

## Alcance no validado porque no pertenece a esta fase

No se declaró ni se validó operación en ruta, mapas, ubicación, móvil,
asignaciones, incidencias, anuncios, WebSocket, métricas operativas o reportes.
La ausencia de estas funciones es intencional y evita presentar datos ficticios.
