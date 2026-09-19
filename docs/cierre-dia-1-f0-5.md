# Cierre del Día 1 — F0.5 y Gate G0

Fecha: 2026-09-19
Estado F0.5: **aprobado técnicamente; pendiente de validación verde del propietario**

## Expediente de evidencia

| Fase | Evidencia |
|---|---|
| F0.1 | Línea base `c961ac9` y tag `baseline-fase5-pre-e2-20260918` |
| F0.2 | Perfil local, diagnóstico JDBC y PostgreSQL 16.15 en `127.0.0.1:5432` |
| F0.3 | Dump custom externo, SHA-256 `8F0B9DCEE89D685C747A18E7BC7E01BEC006309FC20CD945BE83F570AD9AC1C5` |
| F0.4 | Restauración aislada en `ruta_fija_recovery_20260919_f04` sin modificar desarrollo |
| F0.5 | Convención de ambientes, auditoría y plan de F1 sin Docker |

## Separación de ambientes

| Uso | Base | Situación |
|---|---|---|
| Desarrollo | `solucion_ruta_fija_1` | Existe y permanece como único destino local del perfil `local` |
| Pruebas | `ruta_fija_test` | Nombre reservado; su creación aislada es una tarea obligatoria de F1 |
| Recuperación | `ruta_fija_recovery_20260919_f04` | Existe, es UTF8 y se conserva como evidencia; no debe ser usada por la aplicación |

No hay una base de pruebas que pueda confundirse con desarrollo. La ausencia de `ruta_fija_test` es una observación declarada, no una sustitución silenciosa por la base de desarrollo.

## Roles previstos

El propietario actual `soporte` tiene privilegios administrativos y se usó solo para el preflight, respaldo y recuperación. F1 debe separar estos roles sin documentar sus claves:

- `rf_migrator`: Flyway, extensiones y DDL versionado.
- `rf_app`: conexión de la API y DML estrictamente necesario, sin DDL.
- `rf_test`: uso exclusivo de `ruta_fija_test`.
- `rf_restore`: operación limitada de respaldos y bases temporales de recuperación.

## Calidad comprobada sin contenedores

- Backend: `mvnw.cmd test` aprobó 21 pruebas.
- Frontend: typecheck/lint aprobó; Vitest aprobó 15 archivos y 37 pruebas; `ng build` aprobó.
- Archivo F0.3: checksum estable y `pg_restore --list` legible.
- Git: árbol de trabajo limpio tras los commits F0.2–F0.4. No se ejecutó Docker, Compose ni Testcontainers durante F0.

## Gate G0

**Dictamen: G0 CON OBSERVACIONES.**

La conexión nativa, el respaldo y la restauración aislada están aprobados. Sin embargo, el origen era una base inicial vacía: aún no se podían comparar Flyway V1–V5, `btree_gist`, constraints de V5, tablas de auditoría append-only ni conteos de negocio. Además, falta materializar `ruta_fija_test` y aplicar mínimo privilegio.

Esto no es una falla de recuperación; es el límite verificable de un snapshot vacío. El plan [F1 nativo sin Docker](plan-f1-postgresql-nativo-sin-docker.md) define la corrección controlada. No se declara G0 íntegramente cerrado hasta completar su restauración post-migración.
