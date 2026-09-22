# F2.1A — Caracterización funcional antes de V6

## Propósito

F2.1A identifica qué permisos de ADMINISTRADOR y COORDINADOR deben converger
en ADMIN sin mezclar todavía la migración de datos, el cambio de enum ni la
adaptación de Angular.

La base local de desarrollo tiene APP_SEED_ENABLED=false y no contiene cuentas
demo. Por ello, la caracterización usa las pruebas de integración aisladas en
ruta_fija_test: crean datos temporales, los eliminan al finalizar y nunca usan
solucion_ruta_fija_1.

## Línea base ejecutada

| Control | Resultado |
| --- | --- |
| PostgreSQL de desarrollo | 16.15, UTC, UTF8, Flyway V1–V5 |
| Usuarios en desarrollo | 0; no se inventaron datos para esta fase |
| Pruebas unitarias backend | 21 aprobadas |
| Pruebas de integración PostgreSQL | 13 aprobadas |
| Base de pruebas después de ejecutar | Limpia, Flyway V5 |
| Runtime CRM/API durante backup | Detenido |

## Matriz de comportamiento actual y objetivo

| Comportamiento actual caracterizado | Evidencia automatizada | Resultado exigido después de F2 |
| --- | --- | --- |
| ADMINISTRADOR crea grupos, usuarios, conductores, vehículos y vínculos. | FleetManagementIT | ADMIN podrá ejecutar estas operaciones dentro de su tenant. |
| COORDINADOR solo ve grupos y conductores a los que está vinculado. | FleetManagementIT | ADMIN verá todos los recursos de su tenant; nunca recursos de otro tenant. |
| COORDINADOR opera asignaciones, incidencias, comunicados y stream solo para sus grupos. | OperationFlowIT y OperationStreamBroadcasterTest | ADMIN operará todo el tenant; group_coordinator no decidirá acceso ni difusión. |
| Reportes y auditoría son exclusivos de ADMINISTRADOR. | OperationFlowIT | ADMIN conservará reportes y auditoría, con aislamiento de tenant. |
| ADMINISTRADOR de otra organización recibe recurso no encontrado. | FleetManagementIT y OperationFlowIT | La protección entre tenants se mantiene sin cambios. |
| Un token cuyo rol ya no coincide con la cuenta se rechaza. | OperationFlowIT y AuthFlowIT | Un JWT antiguo con ADMINISTRADOR o COORDINADOR será inválido tras V6. |
| La asignación de coordinadores de grupo admite escrituras. | FleetManagementIT y OperationFlowIT | La operación quedará retirada o bloqueada; las filas históricas se conservarán temporalmente. |

## Reglas que no cambian

- SUPER_ADMIN sigue siendo global y no hereda operación de un tenant.
- CONDUCTOR no obtiene acceso al CRM por herencia accidental.
- La auditoría append-only no se reescribe: los nombres de rol antiguos que
  existan en hechos históricos permanecen como evidencia.
- La base de datos no se ha modificado a V6 durante F2.1A.

## Preparación para F2.1B

F2.1B podrá crear y ensayar V6 solo contra una copia restaurada. La validación
deberá conservar el total de cuentas, convertir ADMINISTRADOR y COORDINADOR a
ADMIN, revocar sus refresh tokens y comprobar que no exista una autorización
activa basada en group_coordinator.

