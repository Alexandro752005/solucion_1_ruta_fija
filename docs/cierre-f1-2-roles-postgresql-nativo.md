# Cierre técnico F1.2 - roles y aislamiento PostgreSQL nativo

Fecha: 2026-09-21
Estado: **aprobado técnicamente; pendiente de confirmación verde del propietario**

## Alcance ejecutado

F1.2 creó y validó, exclusivamente en PostgreSQL 16 local:

| Recurso | Resultado |
| --- | --- |
| Desarrollo | solucion_ruta_fija_1, UTF8, UTC, propietario rf_migrator. |
| Pruebas | ruta_fija_test, UTF8, UTC, propietario rf_migrator. |
| Recuperación | ruta_fija_recovery_20260919_f04, preservada y fuera de alcance. |
| Migración | rf_migrator, cuenta sin superusuario/CREATEDB/CREATEROLE. |
| Aplicación | rf_app, conexión exclusiva a desarrollo y sin CREATE sobre public. |
| Pruebas | rf_test, conexión exclusiva a pruebas y sin CREATE sobre public. |

PUBLIC perdió CONNECT/TEMPORARY sobre ambas bases y USAGE/CREATE sobre public.
Las credenciales se generan localmente en backend/.local/, que Git ignora; no
se registró ningún secreto en el repositorio.

## Separación Flyway/aplicación

El perfil local usa variables distintas:

| Función | Cuenta | Permiso en F1.2 |
| --- | --- | --- |
| Flyway | rf_migrator | DDL controlado en desarrollo y pruebas. |
| API | rf_app | Conexión y uso de esquema en desarrollo, sin DDL. |
| Pruebas futuras | rf_test | Conexión y uso de esquema en pruebas, sin DDL. |

Las tablas aún no existen. Por eso el DML de aplicación y pruebas se otorgará
selectivamente en F1.3 tras aplicar las migraciones, evitando que
flyway_schema_history se vuelva escribible para la API.

## Evidencia verificada

- Bootstrap reproducible y reanudable: F1_2_BOOTSTRAP=PASS.
- Auditoría de roles y aislamiento: F1_2_ROLE_ISOLATION=PASS.
- rf_app y rf_test fueron rechazados al intentar entrar al entorno ajeno.
- Ambos fueron rechazados al intentar crear una tabla; no quedaron tablas probe.
- Desarrollo y pruebas continúan con cero tablas de negocio y sin
  flyway_schema_history.
- Preflight JDBC: PostgreSQL 16.15, UTF8, sesión UTC, rf_app sin permiso
  CREATE.
- iniciar_ruta_fija.bat y finalizar_ruta_fija.bat finalizaron con código 0;
  no dejaron listeners en 8080 ni 4200.
- Los cinco scripts PowerShell nuevos/modificados y tasks.json fueron
  validados sintácticamente.
- backend/mvnw.cmd test: 21 pruebas, 0 fallos, 0 errores.
- No se ejecutaron Docker, Compose, Testcontainers, Flyway, Spring Boot,
  backend ni frontend.

## Límites conservados

- V1-V5, btree_gist, constraints de exclusión y auditoría append-only todavía
  no se aplican; pertenecen a F1.3.
- El arranque real de API/CRM permanece bloqueado hasta F1.1B.
- La sustitución de pruebas Testcontainers se mantiene para F1.4.

## Próxima puerta

Solo tras la confirmación verde del propietario puede iniciarse F1.3, que
aplicará Flyway de forma controlada sobre desarrollo vacío y verificará el
esquema resultante antes de conceder DML selectivo.
