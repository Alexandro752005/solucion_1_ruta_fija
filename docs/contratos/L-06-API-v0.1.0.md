# L-06 — Contrato API, v0.1.0

2026-10-20. Complementa el [contrato vigente](L-06.md), Q-066…074 e INC-L06-01/02 aprobadas. Las evidencias de ejecución se enlazan en la matriz; este documento no las sustituye.

## Incidencias

Catálogo: `AVERIA`, `ACCIDENTE`, `RETRASO`, `LICENCIA_VENCIDA`, `IMPOSIBILIDAD_SERVICIO`, `UBICACION_NO_CORROBORADA`, `RIESGO_SEGURIDAD`, `ACCESO_PARADA`, `FALLO_PLATAFORMA`, `OTRO`. Todo reporte necesita descripción. Reportes humanos ordinarios seleccionan `LOW/MEDIUM/HIGH/CRITICAL` expresamente; no se deduce gravedad del tipo. Registros previos y capturas técnicas del protocolo de interrupción que no tienen evaluación mantienen `UNASSESSED`: ADMIN debe evaluarlos antes del seguimiento o cierre estructurado. La ausencia de evaluación nunca impide comunicar una detención segura ni acredita seguridad.

`operationalEffect` procede de la existencia real de `service_attention`: `INTERRUPTION_RECORDED` o `NO_INTERRUPTION_RECORDED`. Este último no significa «se puede continuar». Se conserva el protocolo L-05: accidente con riesgo, avería insegura o imposibilidad real se comunican mediante la interrupción de la ejecución, con autoría, detención y atención por recurso. Retraso, discrepancia GPS o gravedad aislada no crean una interrupción automáticamente. Las interrupciones nuevas por imposibilidad usan su categoría específica; los originales anteriores no se reclasifican.

| Superficie | Operaciones |
|---|---|
| ADMIN | `POST /incidents`, `GET /incidents`, `GET /incidents/{id}` |
| ADMIN | `GET /incidents/responsible-candidates`, `GET /incidents/{id}/history`, `GET /incidents/operations/{operationKey}` |
| ADMIN | `POST /incidents/{id}/responsibility`, `/assessment`, `/closure`; `PATCH /incidents/{id}/follow-up` |
| CONDUCTOR | `POST /mobile/incidents`, consulta propia, historial propio, consulta de operación propia y `POST /mobile/incidents/{id}/information` |
| CONDUCTOR | Cierre propio existente `/mobile/incidents/{id}/close`, irreversible e idempotente; no elimina el original |

Prefijo común `/api/v1`. Mutaciones nuevas llevan `operationKey` UUID y `expectedVersion`; seguimiento compatible usa `version`. Reutilizar la misma clave/actor/contenido devuelve el recibo existente; cambiarlo produce conflicto. Historial conserva autor, fecha servidor, versión resultante y detalle de cada ronda. Rol/organización se obtienen de la sesión, nunca de IDs enviados como autoridad. Una respuesta incierta se consulta con su clave; CRM/Flutter no repiten automáticamente el POST ni descartan su formulario pendiente.

Responsable: ADMIN activo de la misma empresa. Al atender se necesita responsable activo y gravedad evaluada. La reasignación conserva ADMIN anterior/nuevo y motivo. Cierre: diagnóstico, acciones, resultado, decisión de si requiere evidencia, modo `REFERENCE/UNAVAILABLE_JUSTIFIED/NOT_REQUIRED` y referencia codificada o explicación. No archivos originales, PII o coordenadas en notas permanentes. El cierre conserva `resourcesReleased=false`: no libera atención, mantenimiento, disponibilidad o documentos.

`RESOLVED` y `CLOSED_BY_DRIVER` son terminales; tampoco el seguimiento heredado reabre. Recurrencia/cierre erróneo crea otro reporte con `previousIncidentId` y `relationReason=RECURRENCE/CLOSURE_ERROR`, misma organización y original cerrado. El vínculo es inmutable. La nueva incidencia tiene evaluación, responsable y cierre propios.

## Comunicados

`POST /announcements`: ADMIN, título/contenido sin PII/coordenadas, audiencia `ORGANIZATION/GROUP`, grupo de la misma empresa si aplica, `requireReadAck` expreso y `operationKey` UUID. Publicación inmutable. Clave de operación y huella impiden duplicación en reintentos del mismo contenido. `GET /announcements/operations/{key}` consulta la publicación del actor.

Al publicar se capturan usuarios CONDUCTOR activos de la empresa, usando todas sus pertenencias activas al grupo elegido. La habilitación operativa, disponibilidad y lectura se mantienen independientes. Alta posterior en grupo/empresa no inventa una recepción anterior. La migración solo reconoce como destinatarios legados los recibos que ya existían y coinciden en empresa/cuenta/conductor; no reconstruye historia desde la pertenencia de hoy.

`GET /mobile/announcements` y `/{id}` requieren consulta propia de una cuenta aprobada vigente. Registran primera consulta/recepción, **no Leído**. La salida del grupo conserva su destinatario original; no recibe mensajes nuevos de ese grupo. Salir de la empresa termina el acceso. `POST /mobile/announcements/{id}/read` requiere acción expresa; conserva la primera fecha servidor al repetirse. No modifica ofertas ni servicios.

`GET /announcements/{id}/attention` muestra destinatarios codificados, consultas, lecturas, pendientes y retiradas. Pendiente = destinatario vigente sin lectura cuando se exigió; ADMIN recibe aviso y puede consultarlo sin que ese aviso se confunda con el estado actual del pendiente.

`POST /announcements/{id}/recipients/{userId}/withdraw`: ADMIN de la empresa, versión del destinatario, clave UUID y motivo de seguridad. Se retira el acceso de ese destinatario; original, recibos y motivo se conservan. La retirada no puede deshacerse ni reconstruye el pasado. Un resultado incierto se contrasta por clave/autor antes de otra acción.

## Bandeja y canal externo

`GET /notices` (bandeja propia ADMIN), `GET /notices?failures=true` (no enviados/fallos de la empresa), `GET /mobile/notices` (propia CONDUCTOR), `POST /[mobile/]notices/{id}/read` (lectura propia idempotente). Paginación máxima100. Consultar no confirma lectura; leer un aviso no confirma Leído de un comunicado ni responde a una oferta. Retirada de seguridad oculta también las referencias del comunicado en la bandeja propia.

Los eventos operativos se insertan dentro de la transacción de negocio, por destinatario real: ADMIN de la empresa y CONDUCTOR afectados. Una reversión transaccional elimina también el aviso no confirmado. No se copian cuerpos libres, secretos, PII o coordenadas; telemetría GPS no se archiva en esta bandeja. No hay cola offline ni reenvío automático.

El límite de proveedor está separado. `NOT_REQUIRED` significa bandeja sin entrega externa requerida; `NOT_CONFIGURED` significa que no hubo envío externo; `FAILED` acredita fallo de comprobación del canal. El DTO distingue `PROVIDER_ACCEPTED` de entrega al dispositivo/lectura, pero el adaptador local actual **no envía ni produce esa aceptación**. Incluso un adaptador declarado disponible conserva el bloqueo `DISPATCH_NOT_AUTHORIZED` hasta preparar y acreditar proveedor/privacidad/dispositivo. El doble puede fallar al consultar disponibilidad: ese fallo queda visible y la transacción mantiene el aviso.

El [contraste S/0](../operacion/L-06-contraste-push-S0.md) identifica una vía documental, sin acreditar configuración, cuota efectiva de un proyecto, registro de equipo o recepción real. No se ha integrado SDK, creado proyecto/credencial ni enviado push/correo operativo. Esas dependencias se conservan expresamente antes de habilitar la entrega externa y el piloto; no se presentan como funcionamiento real de FCM.

## Persistencia y retorno

V43 agrega catálogo, historial y protección terminal. V44 agrega destinatarios y avisos; ambas aplicadas exclusivamente en `ruta_fija_test` mediante preflight explícito. SQL aplicado inmutable. Retorno: mismas clases/esquema, `app.qualification.operations-enabled=false`, consulta propia limitada conservada y nueva operación rechazada. No se ejecuta un binario viejo ni se borran datos para simular reversión.
