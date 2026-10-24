# L-05 — Incremento5: relevo, varios tramos y continuidad

2026-10-20. **L-05 EN_CURSO**. Solo funcional local; piloto bloqueado y L-06 no iniciada. [API](../../../contratos/L-05-relevo-API-v0.1.0.md), [casos](casos.json), [resultado](resultado.json), [preservación](preservacion.json), [matriz](../matriz-L05.md).

## Comprobado

- Preparación ADMIN con motivo/punto codificado y comprobación expresa de detención/transferencia; sustituto con su propia unidad vinculada. Ruta/tipo capturados y planificación original conservados. Candidatos por organización/ventana y sin PII.
- Aceptación normal de la oferta entrante no inicia ni cambia la ejecución saliente. Solo el entrante aceptado, con permiso/consentimiento/primer plano y habilitación vigentes, confirma Asumir relevo. ADMIN no lo suple; el inicio ordinario rechaza esa oferta.
- Cambio atómico: saliente `TRANSFERRED` y tramo cerrado, entrante iniciado y nuevo tramo abierto, exactamente con la misma hora servidor. Un solo tramo abierto por servicio/conductor/unidad. `originalStartedAt` y planificación inicial no se reinician.
- Relevo de un tramo ya interrumpido conserva su cierre y la pausa original; no libera sus atenciones. Snapshots eliminados, puntos nuevos vinculados al nuevo tramo y puntos anteriores conservando su vencimiento.
- Rechazo/vencimiento/retirada de standby conserva ejecución saliente. Cancelar continuidad retira la reserva entrante y conserva atenciones. Completar válidamente antes del relevo termina la oferta no iniciada con causa de servidor y autoría CONDUCTOR real, sin inventar una intervención ADMIN.
- Corrección de dos tramos: conjunto completo, rechazo de solapamiento y originales inalterados. Consulta CONDUCTOR filtrada a su propio tramo. Guardas SQL rechazan transferencia sin resolución, edición de preparación/transferencia y borrado de resolución.
- Q-062: servidor informa inicio previsto superado manteniendo el servicio confirmado; CRM/Flutter avisan sin transición automática.
- Un grupo no puede retirarse mientras continúe la interrupción o la atención de seguridad, incluso tras cancelar continuidad. Se distinguen ambos motivos y se conserva historial.

## Ejecuciones y límites

**141 casos backend distintos**:17 relevo,16 atención,15 tiempos,20 GPS,10 ejecución,10 mantenimiento,14 ofertas,20 servicios,8 disponibilidad,1 retorno,9 grupos y1 carrera de autorización. Spring/filtros/controladores con PG real, fixtures rollback. No son carreras HTTP de negocio ni recorrido de navegador/app conectado. No limpieza global ni cuentas persistentes nuevas.

**69 Flutter**, **47 CRM** (41 asignaciones,6 grupos), analyze sin incidencias, typecheck, build CRM, JAR y APK debug correctos. Comprobación de componentes, renderer y sensores dobles; no ensayo humano, dispositivo, batería o calibración física. Artefactos sin instalar/desplegar.

V41/V42 aditivas solo test. La primera ejecución nativa encontró ambigüedad entre variable PL/pgSQL y alias de tabla; V42 conserva los guardas y corrige únicamente el alias, sin reescribir V41. Se conservan hashes y resúmenes saneados de errores iniciales de fixture/compilación y avisos de estilo. No se debilitaron validaciones.

Ledger1/3/2, dos aceptaciones y huellas de casos cerrados intactas. Tablas nuevas de tramos/GPS/tiempos/atención/relevo vacías después del rollback. Sellos anteriores, final3 y documento ADMIN publicado conservados.

## Pendiente

CI02/09: carreras de negocio con solicitudes HTTP concurrentes y recorrido conectado CRM/Flutter con backend/PG reales. CI10: retorno conectado con el mismo binario y operación deshabilitada, postflight/preservación y cierre documental final. Se propuso [un caso nuevo con máximo dos CONDUCTOR](../../../operacion/L-05-caso-conectado-propuesta.md); no se ejecuta ni consume cupos sin autorización concreta. Las cuentas de L-04 permanecen cerradas. Ninguno de estos pendientes se acredita por los totales anteriores.
