# Corrección del arnés de preparación de servicio

2026-10-22, America/Lima. Mismo RUN `a999ef52-d87f-498d-9251-1581cbb72562`. **L-08 EN_CURSO; recorrido físico PENDING; piloto bloqueado.**

## Fallo conservado

El primer `preparar-servicio` creó normalmente la ruta y el tipo mediante HTTP200, pero envió nombres de origen y destino distintos de los extremos de la versión capturada. El backend respondió HTTP400, `ASSIGNMENT_INVALID_TRANSITION`, conforme a su regla de igualdad exacta.

[Diagnóstico de solo lectura](../fallo-preparacion-servicio-diagnostico.json): cero asignaciones con la clave exacta del caso y cero servicios con sus versiones de ruta/tipo. Ledger1/3/7 y dos aceptaciones conservados. El usuario ejecutó personalmente `pausar`; posteriormente se comprobaron ausencia de proceso/puerto y lock libre. El marcador permanece y `TEST_CLEANUP_RELEASED=false`; las suites con fixtures siguen suspendidas.

## Corrección limitada al arnés

- Consulta la ruta existente por el API normal y selecciona exactamente la versión capturada, activa y vigente; toma sus nombres inicial y final.
- Conserva cuenta, ruta, tipo y clave de operación. Busca primero un eventual commit por esa clave, antes de exigir disponibilidad o reconstruir una solicitud; recupera únicamente IDs coincidentes con los recursos del caso.
- Persiste una marca de envío sin confirmar y la huella del payload antes del POST. Ante pérdida de respuesta, bloquea otra emisión hasta reconciliar el resultado. No usa el HTTP400 antiguo para autorizar repetir un envío posterior incierto.
- Conserva el horario aún futuro. Solo puede renovar un horario vencido cuando hay rechazo400 previo identificado y ausencia exacta de asignación/servicio; deja el horario anterior y la razón como antecedente. Un resultado incierto bloquea la preparación.
- No modifica reglas productivas, ruta/tipo persistidos, cuotas, aceptaciones o estados mediante SQL. No reenvía automáticamente el POST ni envía correo.

## Pruebas y límites

[Resultado y huellas](resultado-verificado.json). Selección Maven offline `-Dtest=L08ConnectedServicePreparationTest test`: **14 casos puros, cero fallos, errores o saltos**. Los12 iniciales se archivaron antes de añadir las dos guardas; son14 casos distintos, no26. Comprueban extremos, selección de versión, estados inválidos, estabilidad del payload, horario y bloqueo de envío incierto.

La comprobación posterior de solo lectura mantiene cero oferta/servicio, ledger1/3/7 y dos aceptaciones. No se ejecutaron fixtures de PostgreSQL. La siguiente emisión corregida contra backend/PostgreSQL y la aceptación/inicio/GPS/cierre personales **todavía no se acreditan**.

Fuentes anteriores en `antes/`; fuentes corregidas, huellas de clases y logs seleccionados en esta carpeta. La compilación de este soporte de pruebas no cambia el JAR/APK archivados ni implica otra entrega.

## Continuación

En PowerShell interactiva, desde la raíz, reanudar el mismo caso con `-Serve -ResumeRunId 'a999ef52-d87f-498d-9251-1581cbb72562'`. Comprobar nuevamente protección y USB antes de una invocación manual de `preparar-servicio`. Ante otro fallo conservar el caso y diagnosticar; no crear otra cuenta/oferta ni liberar el marcador.


## Contraste posterior de emisión

**Corte físico2026-10-22T21:20Z:** mismo caso reanudado2, oferta `6ad87efd-c9a5-4d06-943c-72c863f76772` emitida por HTTP201 y comprobada en PostgreSQL como PENDING_RESPONSE; servicio `961b62f8-1d30-469f-a88d-5db10884e663`. Corrección del arnés comprobada ahora también en emisión real de pruebas. Aceptación/inicio/GPS/cierre personales pendientes en este corte; consentimientofalse. Ledger1/3/7,aceptaciones2,marcador/lock activos y TEST_CLEANUP_RELEASED=false. Sin otra cuenta,envío externo o cambio de regla productiva. L-08 EN_CURSO; piloto bloqueado.

[Observación de solo lectura](../oferta-observacion-9a138f03-5d3e-474d-af56-3dc7278146fb.json). La frase anterior sobre emisión pendiente corresponde al corte previo; no se modifica su resultado archivado.
