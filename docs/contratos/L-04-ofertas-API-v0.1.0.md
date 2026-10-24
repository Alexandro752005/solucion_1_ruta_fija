# L-04 — Frontera de oferta y aceptación, API0.1.0

Incremento de L-04 **EN_CURSO**, no cierre del lote. [Contrato](L-04.md), [matriz](../evidencia/L-04/matriz-L04.md).

## Emisión

`POST /api/v1/assignments`, ADMIN autenticado de su organización y gates vigentes. Mantiene ruta/versiones, período, revisión documental, mantenimiento y declaración de disponibilidad L-02/L-03.

- `responseMode` omitido o `MOBILE_CONFIRMATION`: oferta `PENDING_RESPONSE`, sin aceptación/inicio fabricados. `ADMIN_DIRECT` explícito409; registros anteriores permanecen consultables sin reescribirlos.
- `responseDeadlineAt` es un campo heredado de entrada; no decide el plazo. Se calcula siempre `min(emisión_servidor+10min,inicio_previsto)` y se devuelve el resultado persistido. Si inicio no es futuro, no se crea una oferta viva.
- En el instante de vencimiento no se acepta. V33 permite que deadline sea exactamente igual al inicio; no agrega un microsegundo ficticio ni modifica las demás condiciones de ciclo de vida.
- Repetición con la misma clave idempotente y cuerpo devuelve el mismo registro/plazo. No reinicia diez minutos. Cuerpo distinto409. Una oferta expirada no revive por repetir su alta.
- Antes de otra reserva se normalizan ofertas vencidas del mismo tenant bajo el bloqueo de organización, con auditoría. Una nueva oferta válida puede usar el intervalo liberado sin esperar al job. La exclusión de reservas vigentes continúa activa.

## Respuesta y transición

`POST /mobile/assignments/{id}/accept|reject` conserva controles de cuenta CONDUCTOR propia, versión, plazo, elegibilidad y recibo idempotente. Aceptar confirma pero no inicia. Aceptar en el borde o después produce expiración persistida/rechazo, sin eliminar vínculo ni aceptación ajena.

Los alias web `POST /assignments/{id}/reserve|start` se mantienen identificables, pero responden409 al actor ADMIN propio; no pueden reservar/aceptar/iniciar en nombre del conductor. Objeto ajeno404 y rol indebido403 conservan sus guards. El móvil tampoco inicia un registro ADMIN_DIRECT heredado sin aceptación auténtica.

El barrido de vencimiento usa una transacción por organización y el mismo orden de bloqueo que la emisión: organización antes de filas. El control al aceptar y crear reservas no depende de recibir avisos ni del barrido. No se declara notificación entregada por publicar un evento.

## Clientes y pendientes que esta API no cierra

CRM emite MOBILE_CONFIRMATION, no elige vencimiento, muestra oferta pendiente y conserva el formulario tras conflicto. No ofrece botones ADMIN de reserva/inicio ni edición material heredada; el cambio explícito de ruta L-03 sigue separado. Pruebas de componentes no equivalen a navegador real.

L-04 todavía debe completar márgenes por tipo/candidatos, separación servicio/oferta/reasignación, sustituciones de recursos/horario y reconciliación de respuestas inciertas en el recorrido completo, carreras y evidencia conectada final. El bloqueo visual no sustituye los controles server-side. No se declara este delta como acreditación de todos esos criterios.

Solo pruebas `ruta_fija_test`, correo simulado, S/0, sin offline/colas, despliegue, Git/GitHub o final3 nuevo. Piloto bloqueado; L-05 sin iniciar.
