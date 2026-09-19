# Evidencia de la Fase 5 — 2026-08-30

## Línea base

- Rama: `main`.
- Commit inicial: `7422a75`.
- Alcance: rediseño estructural de frontend sin cambios de backend.

## Verificaciones ejecutadas

| Verificación | Resultado |
| --- | --- |
| `npm.cmd run typecheck` | Aprobada. |
| `npm.cmd run test:ci` | 15 archivos, 37 pruebas aprobadas. |
| `npm.cmd run build` | Aprobada, sin advertencias. |
| `npm.cmd audit --omit=dev --audit-level=high` | 0 vulnerabilidades reportadas. |
| Navegador Chromium | Acceso montado, hoja global cargada y 0 bloqueos CSP. |
| `.\mvnw.cmd verify` | BUILD SUCCESS. |
| Pruebas unitarias backend | 21 aprobadas, 0 fallos. |
| Pruebas de integración | 13 aprobadas, 0 fallos, 0 omitidas. |
| PostgreSQL de integración | PostgreSQL 16.15 mediante Testcontainers. |
| Migraciones | 5 migraciones Flyway validadas y aplicadas. |
| `git diff --check` | Sin errores de espacios. |
| Backend/Compose modificados | Ninguno. |

## Pruebas añadidas

- Matriz de rutas por rol.
- Topbar ADMINISTRADOR sin sidebar.
- Topbar SUPER_ADMIN limitada a Resumen y Organizaciones.
- Ciclo de vida del modal y bloqueo de desplazamiento.
- Cierre del modal mediante Escape.

## Verificación Docker

- `docker compose --profile app up -d --build` finalizó correctamente con la
  imagen de frontend de Fase 5.
- `postgres`, `backend` y `frontend` se verificaron en estado `healthy`.
- `GET http://localhost:8080/actuator/health` respondió HTTP 200 y estado `UP`.
- `GET http://localhost:4200/login` respondió HTTP 200.
- El HTML servido no contiene el manejador `onload` bloqueado por la CSP y carga
  la hoja de estilos de producción como stylesheet normal.

## Trazabilidad documental

- Decisiones y adaptaciones: `docs/fase-5-diseno.md`.
- Estado final: `docs/fase-5-estado.md`.
- Auditoría final: `docs/auditoria-final-fase-5.md`.
- Operación desde otro equipo: `docs/Uso del Sistema.md`.
