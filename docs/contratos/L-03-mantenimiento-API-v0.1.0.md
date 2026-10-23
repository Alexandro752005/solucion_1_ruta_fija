# L-03 — Mantenimiento, API 0.1.0

Decisiones vigentes **MNT-01A/MNT-02A** aprobadas en [registro](../../specs/decisiones-L03-mantenimiento-2026-10-19.json). Implementación comprobada en el alcance funcional local; [matriz y límites](../evidencia/L-03/matriz-final-funcional-local-2026-10-19.md). No habilita piloto ni L-04.

## Estados y hechos separados

Una programación registra intervalo previsto inmutable y bloquea desde el primer día a medianoche Lima hasta retorno expreso. Su fecha final es informativa: no promete habilitación posterior. El preventivo debe resolver todos los compromisos que quedarían bloqueados, incluso posteriores a esa fecha.

El registro pasa OPEN → WORK_COMPLETED al acreditar trabajo terminado, con responsable/evidencia como códigos sin PII y hora/actor del servidor. **Registrar trabajo terminado no autoriza retorno.** ADMIN autoriza después el retorno de la unidad; así pueden terminarse varios trabajos sin que un trabajo abierto permita reactivar otro por inferencia. Si subsiste un trabajo abierto relevante, conducción en curso, inactividad o documentación desconocida/vencida, el servidor rechaza el retorno.

Al autorizar, los trabajos terminados pertinentes pasan RETURNED; el bloqueo sigue hasta la medianoche siguiente al día real del retorno. Se conserva el intervalo previsto, incluso ante final anticipado. Un mantenimiento futuro separado conserva su bloqueo futuro. No se libera el día del trabajo ni se activa Driver/cuenta/disponibilidad por devolver la unidad.

CANCELLED solo antes del primer día; conserva registro/motivo/actor. Una programación que empezó exige fin del trabajo y retorno, no cancelación retrospectiva. Reprogramar un plan futuro conserva el cancelado y crea otro mediante actos expresos; no edita sus fechas históricas.

## Endpoints ADMIN propios

Base `/api/v1/vehicles/{vehicleId}/maintenance`, gates de identidad/términos y GET no-store:

- GET: versión de control, registros, bloqueo mecánico actual e historial por IDs/estados/horas; no devuelve motivos/referencias libres ni datos personales.
- GET `/preview?startDate=YYYY-MM-DD`: referencias de todos los compromisos que impedirían confirmar desde ese inicio. Servidor vuelve a comprobar en la transacción de confirmación.
- POST: `{expectedVersion,operationKey,reason,kind:PREVENTIVE|URGENT,startDate?,endDate?}`. Preventivo exige ambas fechas inclusivas, inicio no pasado. Urgente usa el día real del servidor; no inventa una salida si se desconoce. Urgente aplica bloqueo y efectos sobre servicios sin borrarlos ni cerrarlos automáticamente.
- POST `/{id}/work-completed`: `{expectedVersion,operationKey,reason,responsibleReference,evidenceReference,workCompleted:true}`. Solo trabajo iniciado/abierto y confirmación expresa. Referencias de comprobación en código, no nombres, DNI, teléfono, correo, secretos o archivos.
- POST `/return`: `{expectedVersion,operationKey,reason}`. Requiere trabajo acreditado, revisión documental inequívoca y vigente para el día siguiente y ausencia de otros bloqueos aplicables. No supone que el conductor esté personalmente habilitado; su oferta sigue validando todos sus requisitos.
- POST `/{id}/cancel`: comando básico, solo programación no iniciada.

Claves idempotentes por vehículo/actor, payload hash y versión; repetición exacta no duplica.409 ante cambio/estado incompatibles,404 recurso ajeno y403 rol indebido. Consulta explícita tras resultado incierto; nunca reintentar automáticamente.

## Integridad y retorno seguro

Mutaciones de programación adquieren lock de organización antes de conductor/unidad/asignación para serializar preventivo/avería frente a reservas/inicios y evitar la carrera de confirmación. No es una cola offline. Historial de operación append-only; los metadatos documentales/revisiones previos permanecen intactos.

La devolución mecánica no sustituye revisión de títulos/documentos ni adopta revisiones contradictorias entre conductores. Sin contexto documental inequívoco, se mantiene WORK_COMPLETED con retorno pendiente. Las vistas ordinarias no reciben expedientes ni referencias libres de evidencia.

V31 se aplicó de forma aditiva exclusivamente en pruebas y añade declaración de disponibilidad separada y unicidad canónica de unidad. Ante colisiones existentes el preflight rechaza; no fusiona unidades ni reasigna empresas automáticamente. No se modifica una migración aplicada ni se afirma un retorno de producción.

## Comprobaciones ejecutadas

[MaintenanceIntegrationTest](../evidencia/L-03/aceptacion-V32/final-97/TEST-pe.rutafija.qualification.MaintenanceIntegrationTest.xml): diez casos nuevos de mantenimiento/flota y 44 regresiones heredadas, todos correctos con Spring/PG reales y rollback. Cubren días/bordes Lima, compromisos posteriores al fin estimado, urgencia y relevo, retorno temprano, trabajo pendiente, documentación del día de liberación, cancelación futura, idempotencia/tenant/rol, placa canónica y corrección material con compromisos.

[Dos carreras PostgreSQL](../evidencia/L-03/aceptacion-V32/final-97/TEST-pe.rutafija.support.L03MaintenanceSchedulingConcurrencyTest.xml) comprueban serialización entre programación y mantenimiento con conexiones independientes; no son dos clientes HTTP. El [navegador real](../evidencia/L-03/conectado-2e734706-f7ef-4947-b940-1f64fd13f86a/resultado.json) creó/canceló un plan preventivo; no acredita por sí solo los demás estados. Las escrituras ordinarias de flota adquieren el mismo bloqueo de programación y no alteran datos materiales de unidades con compromisos.
