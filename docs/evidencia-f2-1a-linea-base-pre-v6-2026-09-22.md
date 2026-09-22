# Evidencia F2.1A — línea base pre-V6

Fecha local: 2026-09-22, Lima.

## Backup y recuperación aprobados

| Elemento | Evidencia |
| --- | --- |
| Fuente | solucion_ruta_fija_1 |
| Destino nuevo | ruta_fija_recovery_20260922_f21a |
| Archivo | backups/f2-1a/solucion_ruta_fija_1_20260922T070433Z_f21a.dump |
| SHA-256 | de413296e4ce9bd41e2e37a478af8672dd1560a76062bda714808ef696542eaf |
| Resultado | F2_1A_BASELINE=PASS |

## Manifiesto protegido

| Dato | Resultado |
| --- | --- |
| PostgreSQL | 16.15, UTF8, UTC |
| Flyway | V1–V5 |
| Usuarios | 0 |
| Conteo por roles | SIN_USUARIOS |
| Refresh tokens | 0 |
| Filas group_coordinator | 0 |
| Eventos de auditoría | 0 |
| Restauración | Idéntica a la fuente |
| Docker operativo | 0 |

El script verificó que el constraint de roles continúa en estado previo a V6 y
que la fuente no cambió entre el manifiesto inicial y el final. La base de
recuperación y el dump se conservan; el directorio backups está ignorado por
Git.

## Caracterización aprobada

La verificación nativa aislada terminó con 21 pruebas unitarias y 13 pruebas de
integración aprobadas. FleetManagementIT, OperationFlowIT,
OperationStreamBroadcasterTest y AuthFlowIT dejan caracterizados los límites de
ADMINISTRADOR, COORDINADOR, tenant, WebSocket y tokens que F2 deberá unificar.

## Límite de esta fase

No se creó V6, no se modificó el enum UserRole, no se cambiaron permisos y no
se alteraron datos de desarrollo. El siguiente bloque autorizado será F2.1B
solamente después de aprobación explícita.

