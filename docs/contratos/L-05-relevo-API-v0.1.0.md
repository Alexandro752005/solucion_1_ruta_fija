# L-05 — Relevo aceptado/asumido, API v0.1.0

2026-10-20. SRV-L05-02 ya aprobado. V41 y corrección aditiva V42 solo en `ruta_fija_test`; no cambian migraciones aplicadas ni habilitan piloto. [Contrato](L-05.md), [atención segura](L-05-interrupcion-API-v0.1.0.md).

## Operaciones

| Ruta bajo `/api/v1` | Contrato |
|---|---|
| GET `/scheduled-services/{id}/handovers` | ADMIN propio: preparación inmutable, ofertas entrantes, resolución, autoría real, punto codificado y pausa original. |
| GET `/scheduled-services/{id}/handover-candidates?from=...&to=...&offset=0` | ADMIN propio. Pares conductor/unidad vinculados, habilitados y disponibles para la ventana con márgenes. Paginación100 sin datos personales. Conserva ruta/tipo capturados del servicio. |
| POST `/scheduled-services/{id}/handovers` | ADMIN propio: `operationKey` UUID, `expectedVersion` del servicio, `driverId`, `vehicleId`, `scheduledAt`, `scheduledEndAt`, `reason`≤300, `pointReference` codificada≤100 y `safetyAndTransferConfirmed=true`. Solo servicio iniciado/interrumpido con participación saliente actual; un relevo pendiente/aceptado por servicio. |
| POST `/service-handovers/{id}/withdraw` | ADMIN propio, `operationKey`, `expectedVersion` del servicio y motivo. Retira exclusivamente la oferta no asumida; conserva el servicio saliente y la preparación. |
| GET `/mobile/assignments/{id}/handovers` | CONDUCTOR propio, incluso para consulta sin habilitación. Solo relevos donde participa; referencias por códigos, sin GPS detallado ni nombres/correos/documentos. |
| POST `/mobile/assignments/{id}/assume-handover` | CONDUCTOR entrante propio. `clientEventId`, `version` de su oferta, `occurredAt`, `gps` (`permissionGranted`, `locationServiceEnabled`, `foreground`) y `readyConfirmed=true`. Hora efectiva servidor; recibo durable e idempotente. |

La oferta se acepta por el endpoint normal `/mobile/assignments/{id}/accept`, con sus plazos y controles. Ni preparación ni aceptación inician. El inicio ordinario rechaza una oferta con `handoverId`: requiere **Asumir relevo**. No hay acción ADMIN para asumir, inicio por planificación ni cola offline. Respuestas `no-store`; POST de clientes sin replay automático por refresh.

## Tramos y recursos

El entrante debe ser otro CONDUCTOR habilitado con su unidad vigente distinta. CARD-01 y los períodos/márgenes/documentos impiden compartir una unidad en períodos superpuestos. La nueva oferta conserva ruta y tipo capturados; no modifica la planificación original del servicio ni adopta por inferencia versiones nuevas. Candidatos son una consulta; al aceptar y asumir se vuelven a comprobar las condiciones bajo el mismo bloqueo empresarial.

Asumir registra resolución con el usuario entrante y hora servidor. Si el saliente sigue ejecutando, su oferta pasa a `TRANSFERRED`, se cierra su tramo y se abre el nuevo exactamente en el mismo instante. No se completa ni se crea una declaración de ruta finalizada del saliente. Si ya fue interrumpido, conserva `INTERRUPTED` y su cierre anterior; la diferencia hasta el inicio entrante es la pausa original. Sus atenciones de seguridad **siguen pendientes** hasta revisión ADMIN separada.

Solo existe un tramo abierto por servicio, conductor y unidad. Se conservan participantes, unidades, origen de incidencias, planificación y `originalStartedAt` (mínimo de inicios originales), sin reinicio al relevar. Se eliminan snapshots de ambos participantes; cada punto nuevo corresponde al tramo propio abierto. Puntos anteriores conservan el máximo de siete días.

Una retirada explícita devuelve únicamente la reserva no iniciada conforme a las reglas ordinarias. Rechazo o vencimiento no cancelan la ejecución saliente; una preparación posterior debe ser expresa. Si el responsable completa válidamente antes de asumir el sustituto, se cancela el relevo no iniciado con causa `SERVICE_ENDED`, conservando la autoría CONDUCTOR y el efecto de servidor, sin atribuirlo a ADMIN. Cancelar continuidad interrumpida retira también su standby no asumido y conserva las atenciones.

Los compromisos de grupo incluyen continuidad interrumpida y cualquier atención de seguridad pendiente, incluso tras cancelar el servicio. No puede retirarse el grupo/recurso para ocultarlos. Una participación interrumpida histórica deja de bloquear por continuidad cuando esta termina; su atención pendiente sigue bloqueando por separado.

## Correcciones y alerta

Después de completar/cancelar y cerrar todos los tramos, ADMIN puede anexar una corrección con el conjunto completo, orden, intervalos sin solapamiento y antes/después por tramo. CONDUCTOR consulta/solicita solo lo propio. Originales, pausas originales, GPS y estado de interrupción/relevo no se reescriben; se conserva el efecto sobre corroboración.

Q-062: `startOverdue=true` se calcula por el servidor cuando un servicio confirmado supera su inicio previsto sin tramo original. CRM/Flutter muestran alerta y mantienen programación. ADMIN decide explícitamente esperar, reofertar/reprogramar o cancelar; una nueva oferta exige otra aceptación. No autoinicio, autocancelación ni rehabilitación de ofertas vencidas.

## Incertidumbre y límites

La vista devuelve `operationKey` de preparación y `resolutionKey` de resolución, con actor/hora y estado. Ante respuesta incierta se consulta la misma clave; no se fabrica éxito ni otra oferta. La inmutabilidad y los guardas de V41/V42 conservan la pertenencia y la resolución autorizada. El bloqueo empresarial serializa mutaciones; una prueba SQL de autorización no sustituye la carrera de negocio HTTP pendiente.

Las confirmaciones de seguridad y permiso son declaraciones controladas del flujo, no comprobación física del lugar, dispositivo, sensor o transferencia de pasajeros. Pruebas nativas/componentes no equivalen al recorrido TCP ni al piloto. La matriz identifica sus evidencias y límites.
