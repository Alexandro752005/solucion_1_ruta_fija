# L-05 — Interrupción y atención segura, API v0.1.0

2026-10-20. Implementación de SRV-INT-L05-05 aprobada; corte incremental, no cierre del lote. [Contrato](L-05.md). Solo `ruta_fija_test`; V40 aditiva conserva migraciones anteriores. No habilita piloto, no es ensayo físico ni humano.

## Operaciones

| Método / ruta bajo `/api/v1` | Actor y contrato |
|---|---|
| POST `/mobile/assignments/{id}/interrupt` | CONDUCTOR propio autenticado. `clientEventId` UUID, `version`, `occurredAt`, `reason`, `detail`≤300, `safeStopConfirmed` booleano explícito. Puede comunicar el riesgo antes de confirmar detención; hora efectiva servidor. Recibo durable e idempotente. |
| POST `/assignments/{id}/interrupt` | ADMIN de la organización. `operationKey` UUID, `expectedVersion`, `reason`, `detail`, `safeStopConfirmed=true`, `verificationReference` codificada. Comunicación comprobada; autoría ADMIN real, sin suplantación. |
| GET `/scheduled-services/{id}/attention` | ADMIN de la misma organización; historial de interrupción y revisiones separadas. |
| GET `/mobile/assignments/{id}/attention` | CONDUCTOR propio, incluso sin habilitación para nuevos servicios. Solo su participación. |
| POST `/service-attention/{id}/resolve` | ADMIN propio. `operationKey`, `expectedVersion` del historial, `releaseDriver`, `vehicleDecision` (`NO_CHANGE`, `KEEP_WITHDRAWN`, `READY`), `safetyAndTransferConfirmed=true`, `reason`, `verificationReference`. Al menos una decisión expresa. |

Motivos enumerados: `ACCIDENT_RISK`, `UNSAFE_BREAKDOWN`, `IMPOSSIBLE_SERVICE`. Retraso, discrepancia GPS o pérdida de permiso no producen interrupción automática. Notas y referencias no deben contener PII ni coordenadas: hay rechazo básico de patrones, sin afirmar detección exhaustiva de texto libre. Respuestas `Cache-Control: no-store`.

## Consistencia y seguridad

La interrupción requiere servicio iniciado y tramo propio abierto, versión vigente y aislamiento empresarial. Se cierra ese tramo `INTERRUPTED` con hora/actor reales, sin `completed_at` ni declaración de ruta finalizada. Incidencia vinculada al tramo exacto; las notificaciones no duplican coordenadas. Se elimina el snapshot actual; puntos originales conservan exclusivamente su vencimiento de siete días. No se admiten nuevos puntos del tramo cerrado.

Conductor y unidad pasan a `ATENCION`; no se habilitan nuevas ofertas/servicios ni cambios ordinarios que eludan el bloqueo. La pérdida de habilitación operativa no impide informar el riesgo, consultar la propia participación ni comunicar una incidencia posterior referida a su tramo. ADMIN no puede completar un servicio en nombre del conductor.

Las revisiones son anexos inmutables, con actor, hora servidor y claves. `KEEP_WITHDRAWN` desactiva la unidad **y mantiene pendiente su atención**; no equivale a `READY`. `READY` exige revisión documental y retorno de mantenimiento cuando corresponda y **no activa una unidad inactiva**. La revisión del conductor comprueba su habilitación y recupera solo la declaración de disponibilidad previa, sin inventar otra ni reactivar una baja.

En una unidad retirada por seguridad, la comprobación explícita de retorno de mantenimiento puede registrarse conservando inactividad y atención. Sigue bloqueado el día del retorno conforme a MNT-02A; después se exige la revisión `READY` y, si procede, activación ordinaria separada. No basta la salida estimada ni la liberación del otro recurso.

La cancelación expresa de continuidad interrumpida conserva el tramo y las atenciones pendientes. Solo después de finalización/cancelación y con todos los tramos cerrados se admiten correcciones temporales separadas conforme al contrato de tiempos: originales, GPS y resultado de interrupción no se reescriben.

## Clientes y respuestas inciertas

CRM exige confirmaciones inicialmente desmarcadas, separa conductor/unidad y muestra autores mediante códigos. Flutter solo registra la interrupción propia y consulta atención. Ni el cliente ni un timeout generan un cierre correcto o un reenvío automático. Ante incertidumbre se consulta por la misma clave y se mantiene bloqueada otra mutación hasta contrastarla. No hay cola offline.

El relevo posterior pertenece al incremento de relevo aprobado SRV-L05-02; este corte no lo acredita. La evidencia ejecutada se enlazará desde la matriz al sellar el incremento.
