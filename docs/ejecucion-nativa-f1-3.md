# F1.3 - primer esquema nativo con Flyway

> Nota histórica: este documento conserva la evidencia del primer hito V1-V5.
> En el árbol actual, una instalación nueva debe seguir [Uso del
> Sistema](<Uso del Sistema.md>) y el bootstrap aplica V1-V10. No ejecute el
> auditor histórico F1.3 como verificación del estado actual.

F1.3 aplica el esquema versionado de Ruta Fija directamente a PostgreSQL 16
local, sin iniciar Spring Boot, API, CRM ni Docker. La única base alterada es
solucion_ruta_fija_1.

## Resultado de la subfase

| Elemento | Estado |
| --- | --- |
| Flyway | V1 a V5 aplicadas correctamente en desarrollo. |
| Esquema | 12 tablas de negocio y flyway_schema_history. |
| Integridad | btree_gist, auditoría append-only y dos constraints de exclusión. |
| Aplicación | rf_app tiene DML selectivo, pero no DDL. |
| Historial Flyway | Propiedad de rf_migrator y no accesible desde rf_app. |
| Pruebas | ruta_fija_test sigue vacía y aislada. |
| Recuperación | ruta_fija_recovery_20260919_f04 no fue modificada. |

## Ejecución controlada desde una base vacía

Después de aprobar F1.2, el orden obligatorio es:

    .\scripts\Invoke-RutaFijaFlywayF13.ps1
    .\scripts\Grant-RutaFijaApplicationPrivilegesF13.ps1
    .\scripts\Test-RutaFijaMigratedSchemaF13.ps1

El primer script exige desarrollo vacío, V1-V5 pendientes, perfiles locales,
semillas desactivadas y API/CRM apagados. Permite como máximo una conexión
pgAdmin inactiva y sin transacción; rechaza sesiones activas o transaccionales.

El segundo script concede a rf_app SELECT/INSERT/UPDATE/DELETE sobre las once
tablas operativas y SELECT/INSERT sobre audit_event. No concede DDL, acceso
modificable a flyway_schema_history ni ejecución directa de la función de
auditoría.

El tercer comando ejecuta la auditoría completa F1.3. Una salida aprobada
incluye F1_3_SCHEMA_AUDIT=PASS.

## Controles comprobados

- PostgreSQL 16.15, UTF8 y UTC.
- Historial Flyway con V1-V5 exitosas y exactas.
- Propiedad del esquema por rf_migrator.
- Hibernate conserva ddl-auto=validate.
- btree_gist está instalado.
- El trigger audit_event append-only rechaza UPDATE y DELETE.
- V4 rechaza el estado DESCONECTADO para conductores.
- V5 rechaza solapes de conductor y vehículo de forma independiente.
- rf_app ejecuta DML real dentro de transacciones revertidas y no puede crear
  tablas.
- Las pruebas de verificación no dejan datos de negocio.

## Límite de F1.3

Esta subfase no arrancó el backend ni el frontend. Tras la aprobación verde de
F1.3, F1.1B incorporó el arranque nativo, health checks y gestión de PID. Vea
[F1.1B — Arranque nativo controlado](ejecucion-nativa-f1-1b.md).
