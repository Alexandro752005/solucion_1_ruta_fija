# Auditoría final de la Fase 4

## Dictamen

**APROBADO CON OBSERVACIONES** para el alcance autorizado: CRM web
administrativo + reportes con PostgreSQL 16 local en Docker. No constituye una
certificación de producción ni afirma que se hayan entregado componentes móviles
o integraciones externas excluidas por el usuario.

## Alcance y método

Se revisaron el SRS, arquitectura, modelo/diccionario de datos, especificación
API y guías de construcción, auditoría, evaluación y automatización aportadas.
También se contrastó el prompt rector con el código fuente, migraciones,
configuración Docker, automatización Windows y pruebas ejecutadas. Cuando los
documentos solicitaban funciones de móvil, geolocalización o servicios externos,
prevalece la restricción posterior del usuario: CRM web local sin simulación de
aceptación de conductor.

## Hallazgos y resolución

| Id | Riesgo identificado | Resolución aplicada | Comprobación | Estado |
| --- | --- | --- | --- | --- |
| AUD-F4-01 | Cancelar una asignación no reservada podía liberar la reserva de otra. | La liberación se ejecuta únicamente si la propia asignación tenía `reservedAt`. | Caso `cancellingAnUnreservedAssignmentDoesNotReleaseAnotherReservation`. | Resuelto |
| AUD-F4-02 | La verificación de horario de aplicación no cerraba una carrera concurrente. | V5 agrega exclusiones PostgreSQL GiST por conductor y vehículo para `SCHEDULED`/`EN_SERVICIO`. | Inserción solapada directa rechazada por integración. | Resuelto |
| AUD-F4-03 | Un JWT con rol anterior podía conservar privilegios tras un cambio de rol. | La autenticación comprueba usuario activo, organización y rol vigente en base; los refresh tokens se revocan al cambiar rol o desactivar. | Caso `staleRoleTokenIsRejectedAfterAnAdministratorChangesTheUserRole`. | Resuelto |
| AUD-F4-04 | Se podía desactivar el usuario de un conductor con servicio activo. | La desactivación se bloquea si el conductor ligado tiene asignación `SCHEDULED` o `EN_SERVICIO`. | Caso `activeScheduledAssignmentBlocksDeactivationOfItsLinkedDriverUser`. | Resuelto |
| AUD-F4-05 | WebSocket podía entregar eventos de grupo a coordinadores no asignados. | Sesiones asociadas al usuario actual y filtro por organización/grupo visible antes de enviar. | `OperationStreamBroadcasterTest`. | Resuelto |
| AUD-F4-06 | Un CSV podía interpretar fórmulas al abrirlo en una hoja de cálculo. | Las celdas que inician con `=`, `+`, `-` o `@` tras espacios se neutralizan. | `reports.page.spec.ts`. | Resuelto |
| AUD-F4-07 | El arranque local podía expirar en equipos Docker lentos. | El iniciador espera hasta 360 s y la salud del backend permite 210 s de arranque; el segundo arranque por `.bat` terminó saludable. | `iniciar_ruta_fija.bat` ejecutado con éxito. | Resuelto |

## Controles aprobados

- Persistencia real: Flyway validó cinco migraciones y el contenedor local llegó
  a V5 sin pérdida del volumen PostgreSQL.
- Aislamiento: las rutas de operación, reporte y auditoría derivan organización
  de la identidad autenticada y devuelven 404 ante recursos ajenos.
- Roles: coordinador no obtiene consultas administrativas de auditoría; los
  reportes son de `ADMINISTRADOR`.
- Integridad: optimismo por versión, idempotencia de creación, validación de
  transición y exclusión de agenda a nivel de base.
- Privacidad: no se persisten ni publican coordenadas; el filtro de metadatos de
  auditoría excluye secretos, tokens, cookies y campos de ubicación.
- Reportes: no hay datos de interfaz inventados; PDF y XLSX se generan en el
  backend a partir de consultas persistidas, con control de rango y tamaño.
- Operación local: los scripts no exponen `.env`; el script de cierre no usa
  `--volumes`, por lo que la información local se conserva.

## Observaciones para una fase posterior

| Observación | Motivo | Acción antes de producción |
| --- | --- | --- |
| No existe cliente móvil ni GPS | Excluido por alcance; no se deben fingir aceptación, ubicación o acuse de lectura. | Diseñar app móvil, consentimiento, trazabilidad y pruebas end-to-end reales. |
| No hay servicios externos | FCM, SMTP, S3, mapas y PostgreSQL administrado no fueron autorizados. | Seleccionar proveedores, secretos, reintentos, costos y pruebas de fallo. |
| Recuperación operativa | El volumen local persiste, pero no se ejecutó una política de backup/restauración ni RPO/RTO. | Definir respaldo cifrado, restauración ensayada y retención. |
| Producción y escalamiento | Docker local no demuestra TLS, observabilidad centralizada, alta disponibilidad ni WebSocket horizontal. | Preparar despliegue, proxy TLS, monitoreo, alertas y bus de eventos compartido. |
| CI remoto | Existe workflow, pero no hay repositorio Git remoto disponible para ejecutar el pipeline. | Crear repositorio, proteger ramas y exigir la puerta CI. |

La evidencia ejecutada y los comandos reproducibles se registran en
[evidencia-fase-4-2026-08-30.md](evidencia-fase-4-2026-08-30.md).
