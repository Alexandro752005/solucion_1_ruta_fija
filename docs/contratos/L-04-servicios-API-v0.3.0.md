# L-04 — Servicio programado y ofertas, API 0.3.0

2026-10-19. Implementa Q-056/057/058 dentro de L-04, sin nueva decisión de negocio. [Contrato vigente](L-04.md). Solo pruebas locales; piloto bloqueado.

## Modelo y estados

`scheduled_service` conserva identidad, organización, programación vigente, ruta/tipo capturados, versión y referencia de la última oferta. Cada `assignment` nuevo es una oferta inmutable en sus condiciones, con `serviceId`. La historia incluye todas las ofertas, sus destinatarios, unidades, condiciones y marcas auténticas. No se vinculan retrospectivamente los legados: `serviceId` ausente significa servicio separado no acreditado.

| Transición de oferta | Estado del servicio | Efecto |
|---|---|---|
| Emisión nueva | PENDING_RESPONSE | Reserva provisional de ambos recursos; ninguna aceptación. |
| Aceptación propia | CONFIRMED | No inicia. |
| Inicio propio | IN_SERVICE | Ocupación física vigente. |
| Finalización | COMPLETED | No se reutiliza como una nueva prestación. |
| Rechazo, expiración, cancelación de oferta | PENDING_REASSIGNMENT | Conserva programación, historia, aceptación previa si existía y período de vínculo; no emite otra oferta automáticamente. |
| Acto expreso de cancelar servicio | CANCELLED | Motivo/fecha/recibo; no reutilizable ni reofertable. |

La sincronización ocurre mediante triggers en la misma transacción de oferta, incluidas expiraciones comprobadas por HTTP o barrido. Una consulta no muta: `status` indica persistencia y `effectiveStatus` indica PENDING_REASSIGNMENT al vencer la oferta según reloj servidor, aunque aún no haya barrido. Un comando normaliza el vencimiento bajo el lock de organización. El deadline anterior nunca se renueva.

V35 aditiva, sin edición de V34 ni backfill de aceptación/servicios históricos. FK compuestas mantienen organización, ruta/tipo y oferta actual; membresía de oferta inmutable. Índice único y lock de fila de servicio impiden dos ofertas vivas del mismo servicio incluso con conductores/unidades distintos. Continúan exclusiones de recursos/buffers V34.

## API ADMIN de su organización

Todas las rutas usan autenticación, rol, identidad y términos vigentes. CONDUCTOR consulta sus ofertas por la API móvil existente; no obtiene el expediente o todas las ofertas de otros destinatarios.

| Método/ruta `/api/v1` | Contrato |
|---|---|
| POST `/assignments` | Emite servicio y primera oferta atómicamente tras validar candidato, ruta/tipo y ventana. Idempotency-Key conserva el mismo resultado, no crea otro servicio. |
| GET `/assignments/creation-operations/{key}` | Consulta la emisión persistida sin recrear, renovar ni aceptar. 404 no autoriza replay automático. |
| GET `/scheduled-services?limit=100&offset=0` | Cabeceras propias, hasta 100 por página. |
| GET `/scheduled-services/{id}` | Cabecera y ofertas históricas propias. |
| POST `/scheduled-services/{id}/offers` | `{expectedVersion,operationKey,reason,offer}`. `offer` cumple AssignmentCreateRequest íntegro. Retira oferta vigente si procede y crea otra en el mismo servicio; nueva aceptación obligatoria. |
| POST `/scheduled-services/{id}/cancel` | `{expectedVersion,operationKey,reason}`. Cancela expresamente el servicio y retira su oferta pendiente/confirmada antes de iniciar. |
| GET `/scheduled-services/operations/{key}` | Recibo inmutable del ADMIN/organización: acción, servicio, oferta anterior/resultante, versión resultante y fecha servidor. |
| GET `/assignment-candidates?...&serviceId={id}` | Para sustitución, excluye solamente la reserva vigente de ese servicio propio. No libera recursos ni reserva candidatos; escritura revalida. |

`reason`: 1–160 caracteres, sin PII; clave: 8–100; versión >=0. Clave repetida exige mismo servicio/acción/payload; devuelve estado vigente y conserva recibo, no otra oferta. Conflicto de versión conserva formulario. No se sustituyen servicios iniciados/completados/cancelados. Un fallo posterior al retiro marca rollback de la transacción completa; no puede conservarse una cancelación a medias.

Cambiar conductor/unidad/ruta/horario exige la misma operación de reoferta y vuelve a validar incorporación, documento, declaración, período, mantenimiento y buffers. La anterior aceptación no se copia. El endpoint de sustitución de ruta L-03 delega en este coordinador y mantiene su recibo histórico. Una configuración antigua retirada/revisada no se convierte automáticamente en parámetros nuevos: el operador debe elegir configuración vigente mediante la reoferta completa si el atajo no puede reutilizarla.

Cancelar oferta futura no libera una ocupación física de otro servicio. Cancelar una oferta ya en prestación no es un atajo para liberar recursos: se rechaza en el nuevo modelo y se conserva la atención segura existente. No se añade seguimiento GPS ni declaración de llegada.

## Clientes y respuesta incierta

CRM distingue servicio/ofertas, muestra historia, reasigna mediante formulario/candidatos y cancela servicio con motivo y confirmación separada. Ofertas y comandos nuevos desactivan replay del interceptor de refresh. Pérdida de respuesta bloquea otro POST/cierre del formulario y conserva clave; recuperación por GET de emisión/recibo. Ni 404 ni aviso recibido prueban éxito. No se acepta desde CRM.

Flutter conserva `serviceId` separado del ID de oferta; aceptación anterior no habilita oferta nueva. Inicio requiere aceptación móvil auténtica además de reserva. Vistas ordinarias siguen sin PII. La consulta y comandos propios siguen sus validaciones servidor, sin cola offline habilitada por este cambio.

## Evidencia y límites

[Incremento3](../evidencia/L-04/incremento-3-servicios/README.md) identifica ejecuciones y preservación. Las regresiones Spring/MockMvc con PG son transacciones rollback; no son un navegador/TCP ni prueba humana. La carrera PG usa recursos técnicos inactivos sin cuentas y no acredita elegibilidad ni emisión HTTP. Pruebas CRM/Flutter aisladas usan dobles. El caso conectado separado usa Angular/Edge y repositorio Flutter por TCP/PG reales, sin dispositivo físico ni atribución humana. [Acta](../actas/L-04-cierre-funcional-local-2026-10-19.md): L-04 VERIFICADA, FUNCIONAL_LOCAL_PILOTO_BLOQUEADO; L-05 no iniciada.
