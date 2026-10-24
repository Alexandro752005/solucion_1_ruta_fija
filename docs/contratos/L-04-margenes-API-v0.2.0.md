# L-04 — Tipos de servicio, márgenes y candidatos, API0.2.0

**2026-10-19, America/Lima. Incremento L04.2 verificado localmente; L-04 EN_CURSO.** Complementa la [API de aceptación0.1.0](L-04-ofertas-API-v0.1.0.md) y el [contrato](L-04.md). [Evidencia](../evidencia/L-04/incremento-2-margenes/README.md). No habilita piloto ni L-05.

## Configuración expresa y conservación

ADMIN del tenant configura referencias de tipo de servicio y minutos anteriores/posteriores. No se deduce una modalidad de transporte del nombre ni se crean tipos o márgenes universales. Ambos valores son enteros no negativos expresamente indicados; los ceros de los fixtures son datos sintéticos de prueba, no valores aprobados para un piloto ni declaración de suficiencia legal. La interfaz comienza sin valores numéricos.

| Endpoint ADMIN | Contrato |
|---|---|
| GET `/api/v1/service-types?limit=100&offset=0` | Cabeceras propias incluidas retiradas; limit1…100, páginas explícitas. |
| GET `/service-types/{id}` | Cabecera/versiones inmutables con actor/tiempo persistidos y hash de contenido. La vista devuelve nombre, márgenes, revisión, hash y fecha; no PII. |
| POST `/service-types` | `{name,beforeMinutes,afterMinutes,operationKey,reason}`; crea cabecera/revisión1 atómicamente. |
| POST `/service-types/{id}/versions` | Mismos campos y `expectedVersion`; nueva revisión, sin editar capturas anteriores. |
| POST `/service-types/{id}/status` | `{expectedVersion,active,operationKey,reason}`; retirada solo impide nuevas ofertas. |
| GET `/service-types/operations/{key}` | Recurso asociado a la operación del mismo actor/tenant, en su estado actual. Relectura tras incertidumbre, sin nueva mutación automática ni falso éxito ante404. |

Gates de identidad/términos, rol ADMIN, nombre/motivo sin PII, control de versión y clave idempotente por actor/tenant. Repetición exacta no crea otra revisión; cuerpo distinto409, objeto/versión ajenos404, rol indebido403. GET no-store. Mutaciones de configuración y emisión comparten bloqueo de organización; no se selecciona silenciosamente una revisión obsoleta para una nueva oferta.

## Captura y ventana reservada

POST `/assignments` exige ahora `serviceTypeVersionId` vigente propio además de `routeVersionId`. Persiste el ID de revisión y ambos márgenes junto a la oferta. La respuesta añade `serviceTypeVersionId`, `marginBeforeMinutes`, `marginAfterMinutes`, `marginReferenceStatus`.

La ventana semiabierta reservada para **conductor y unidad** es `[inicio−margen_anterior, fin+margen_posterior)`. Ambos márgenes se suman al comparar servicios consecutivos: si el anterior exige10min posteriores y el siguiente5min anteriores, se necesitan15min de separación. El borde exacto se permite. Reserva/aceptación/inicio recomprueban compatibilidad, período, documentos y mantenimiento del intervalo con márgenes. Una ocupación en curso no se declara libre porque pasó su previsión.

Una configuración revisada o retirada no reescribe la captura conocida de una oferta anterior. La aceptación de esa oferta usa su revisión inmutable y revalida sus recursos actuales. Cambiar sus condiciones materiales requiere otra oferta, nunca editar el snapshot. La declaración del conductor sigue separada de la ocupación calculada; más de una reserva futura compatible puede confirmarse. Cancelar una no libera el estado calculado si queda otra confirmada.

V34 añade ventanas reservadas y exclusiones PostgreSQL para ambos recursos, conserva las demás restricciones y valida tenant/revisión/márgenes. Las filas históricas mantienen configuración nula y `LEGACY_MARGIN_UNRECORDED`: sus nuevos límites nominales se derivan del horario anterior sin inventar una configuración aprobada. No se permite acreditarles márgenes retrospectivamente. Una oferta histórica sin captura no se acepta/inicia; un compromiso activo desconocido impide reutilizar esos recursos hasta resolución expresa. No se presupone margen cero.

## Consulta de candidatos

GET `/api/v1/assignment-candidates?serviceTypeVersionId=…&routeVersionId=…&scheduledAt=…&scheduledEndAt=…&limit=100&offset=0` devuelve `{serviceTypeVersionId,reservationStartsAt,reservationEndsAt,items:[{driverId,vehicleId,periodId}],nextOffset?,observedAt}`. No nombres, correos, DNI ni expediente. `nextOffset` puede omitirse al terminar; el cliente no repite página0 por esa ausencia.

Filtra por tenant, cuenta/identidad/grupo, declaración, período revisado, documentación/vigencia, unidad activa, mantenimiento y ocupaciones/reservas con márgenes. El límite pagina pares potenciales, no promete que una página vacía signifique ausencia total si hay `nextOffset`. CRM permite consultar la siguiente página. La consulta no reserva; se repiten los controles al escribir. Si cambian tipo/ruta/horario se descarta la selección y respuestas tardías, y debe consultarse otra vez.

Mantenimiento preventivo también detecta compromisos cuyo margen posterior alcanza el día bloqueado, aunque la conducción prevista termine antes de medianoche Lima. A la inversa, una consulta/oferta con margen que alcanza un mantenimiento se rechaza.

## Evidencia y límites

99 casos Spring/PG y unidad en regresión final, más dos carreras PostgreSQL con conexiones independientes: **101 distintos**. CRM20, typecheck/build y JAR correctos. Las carreras usan recursos técnicos sin AppUser, no dos navegadores ni aprobación documental. Retiran sus recursos exactos y conservan dos tipos técnicos inactivos con revisiones/recibos inmutables. Ledger1/3/1 y aceptaciones2 preservados.

Sin nuevo recorrido TCP/navegador/Flutter/dispositivo en este incremento. Faltan separación servicio/oferta, reasignación/cancelación de servicio, cambios completos de recursos/horario, incertidumbre e integración final. No se acredita L-04 por este incremento ni por los conteos.
