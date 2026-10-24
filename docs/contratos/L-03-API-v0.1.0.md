# API L-03 — Incremento grupos 0.1.0

Estado **IMPLEMENTADO, VERIFICADO HTTP/POSTGRESQL Y COMPONENTES; L-03 CERRADO FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**. [Matriz final](../evidencia/L-03/matriz-final-funcional-local-2026-10-19.md). Base `/api/v1`, ADMIN autenticado del tenant y gates de identidad/términos vigentes. GET `no-store`; objetos ajenos404, rol indebido403, sesión inválida401. Nunca se recibe un orgId elegible del cliente.

## Grupos y membresías

- GET `/groups/{groupId}/resources`: `{groupId,version,active,memberships,commitments,history,observedAt}`. Solo UUID/referencias de recursos, tipo, estado, origen/hora y actor por ID; sin nombres/correos/DNI/licencias, documentos o motivos libres en la consulta.
- `memberships`: `{resourceId,kind:DRIVER|VEHICLE,active,source,recordedAt}`. Varias membresías por recurso dentro de la organización; una sola pareja persistente grupo/recurso. `LEGACY_PRIMARY` significa relación heredada observada en migración, no fecha histórica de alta acreditada. `PRIMARY_COMPATIBILITY` conserva incorporación/cambio explícito del grupo de referencia anterior; no reactiva por un cambio ajeno a ese campo. El grupo de referencia no limita las demás membresías.
- `commitments`: `{assignmentId,status,attentionKind?,scheduledAt,scheduledEndAt,groupReferenceSource}`. Incluye oferta/reserva, servicio en curso y atención abierta pendiente de reasignación/relevo. `CREATION_SNAPSHOT`, `LEGACY_CURRENT_REFERENCE` y `CURRENT_MEMBERSHIP` distinguen captura confirmada al crear, referencia heredada observada y relación actual; no se reconstruye el grupo histórico desconocido.
- POST `/groups/{groupId}/memberships`: `{expectedVersion,operationKey,reason,resourceKind,resourceId,active}`. Versión≥0, key8…100, motivo1…160 sin datos personales, tipo DRIVER/VEHICLE y objeto propio. Serializado por grupo. Repetición exacta de clave/actor/cuerpo no duplica; distinto cuerpo o versión obsoleta409. Historial/auditoría conservados. Ningún cambio crea vínculo de unidad, activa Driver/cuenta, cambia rol ni evita una validación global de horario.
- PATCH `/groups/{groupId}` conserva nombre/description/active y añade `expectedVersion` obligatorio; GET/lista devuelve `version`. Retirada lógica sin borrar membresías/servicios, previsualizada desde resources y comprobada de nuevo en transacción. Compromisos impiden retirar grupo o sus recursos. El bloqueo antiguo por el simple booleano Driver.active no sustituye esta regla.
- GET `/groups/{groupId}/drivers` y filtro `groupId` de `/drivers` consultan todas las membresías activas, no solo `Driver.group_id`. Ese campo permanece como referencia compatible, sin conceder permisos.

## Integridad y comportamiento cliente

V29 es aditiva y se aplicó exclusivamente en pruebas tras preflight, preservando L-01/L-02 y ledger. Restricciones de tenant en membresías/actor; claves únicas por pareja y por operación. Captura de grupos de asignación nueva toma locks compartidos compatibles con el lock exclusivo de retirada, y conserva referencias para el historial. Una asignación nueva exige al menos una membresía de conductor en grupo activo; agrupar vehículos no la sustituye.

CRM carga recursos propios y usa solo sus códigos. Motivo obligatorio; una respuesta incierta bloquea otra mutación hasta consulta explícita. Mutaciones sin refresh/reenvío automático. Retirada requiere consultar referencias; el resultado previo no sustituye la comprobación final del servidor. Membresías no se confunden con permisos o con los períodos exclusivos de L-02.

La elegibilidad cuenta al menos una membresía en grupo activo de la misma empresa; el resto de gates/documentos/períodos sigue intacto. No se permite que retirar el grupo de referencia invalide la pertenencia a otro grupo activo ni que el grupo activo eluda documentos/unidad.

La matriz registra nueve pruebas nuevas HTTP/PG, cinco de componentes CRM y dos carreras de locks nuevas, además de las regresiones L-02. El test técnico de locks no equivale a dos solicitudes HTTP concurrentes ni a incorporación personal; no se declara navegador real por componente. Mantenimiento/rutas/disponibilidad completaron sus contratos de incremento dentro de L-03; no se inicia L-04.
