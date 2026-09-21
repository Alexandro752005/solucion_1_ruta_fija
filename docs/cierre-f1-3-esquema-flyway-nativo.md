# Cierre técnico F1.3 - esquema Flyway nativo

Fecha: 2026-09-21

Estado: **aprobado técnicamente; pendiente de confirmación verde del propietario**

## Cambio aplicado

Flyway ejecutó V1-V5 en solucion_ruta_fija_1 usando rf_migrator y sin iniciar
Spring Boot. El resultado contiene las doce tablas de negocio, el historial
Flyway y la extensión btree_gist para las restricciones de solape.

El perfil local mantiene datasource con rf_app y Flyway con rf_migrator.
Hibernate sigue configurado con ddl-auto=validate. Las semillas siguen
desactivadas.

## Permisos resultantes

| Cuenta | Desarrollo | DDL | DML |
| --- | --- | --- | --- |
| rf_migrator | Propietario | Sí, mediante migraciones controladas | Sí, como propietario. |
| rf_app | Conexión exclusiva | No | Completo sobre 11 tablas; SELECT/INSERT en audit_event. |
| rf_test | Sin acceso a desarrollo | No | Sin DML de desarrollo. |

El historial flyway_schema_history no es legible ni modificable por rf_app.
La función prevent_audit_event_mutation no conserva EXECUTE público ni para la
aplicación.

## Evidencia verificada

- F1_3_FLYWAY=PASS: cinco migraciones aplicadas.
- F1_3_DML_GRANTS=PASS: permisos selectivos otorgados.
- F1_3_SCHEMA_AUDIT=PASS: estructura, permisos y reglas de negocio validadas.
- V4 rechazó DESCONECTADO y V5 rechazó ambos tipos de solape en transacciones
  revertidas.
- La auditoría de base de datos bloqueó UPDATE y DELETE sobre audit_event.
- rf_app aprobó INSERT/SELECT/UPDATE/DELETE transaccional y fue rechazado al
  intentar CREATE TABLE.
- Desarrollo terminó sin datos de negocio de prueba; ruta_fija_test y la base
  de recuperación permanecen vacías e intactas.
- No se ejecutaron Docker, Compose, Testcontainers, Spring Boot, API ni CRM.

## Próxima puerta

F1.1B puede comenzar solo tras la señal verde del propietario. Su alcance es
arranque nativo real, health checks, procesos controlados por PID e inicio y
cierre seguro de API y CRM.
