# SRV-INT-L05-05 — Registro de interrupción y liberación segura

Versión0.1.0, 2026-10-20 America/Lima. **PROPUESTA PENDIENTE**; no modifica por sí sola el contrato. Complementa Q-067 y Q-065/SRV-L05-02; no reabre el relevo aceptado/asumido ya aprobado ni introduce comienzo/cumplimiento por ADMIN.

## Precisión operativa por confirmar

Q-067 define causas (accidente con riesgo, avería insegura o imposibilidad real), prioridad de detención segura/comunicación y coordinación ADMIN. No concreta el acto registrado de interrupción ni el cierre del bloqueo de atención de cada recurso. La propuesta conserva esas causas; retraso/GPS no corroborado no son causas automáticas.

1. CONDUCTOR responsable puede comunicar y registrar la interrupción propia online. Se registra si ya pudo detenerse de forma segura; declarar riesgo no se bloquea por no poder confirmar todavía la detención. ADMIN de la misma organización también puede registrar una interrupción **comunicada y comprobada**, con motivo, referencia de comprobación y confirmación de detención segura. El autor real queda identificado: no suplanta un acto del conductor.
2. El tramo se cierra como `INTERRUPTED`, con hora servidor y una incidencia asociada. El servicio conserva lo realizado e inicio original, queda pendiente de decisión expresa y no se cuenta como cumplimiento correcto. Ningún registro implica sincronización offline o confirmación retroactiva; una discrepancia temporal se tramita por la corrección autorizada.
3. Se detiene GPS. Conductor y unidad permanecen bloqueados para nuevos servicios por atención pendiente; cerrar el tramo o cancelar el servicio no libera esos bloqueos automáticamente.
4. ADMIN verifica y resuelve separadamente la atención del conductor y de la unidad. Registra motivo/referencia/hora y confirmación de seguridad/transferencia de pasajeros cuando corresponda. Puede liberar la atención del conductor y mantener retirada la unidad. No se fabrican declaraciones de disponibilidad.
5. Liberar atención no equivale a habilitar operación. Vuelven a comprobarse cuenta/grupo, documentos, vínculo vigente, compromisos y estado de cada recurso. Una unidad con mantenimiento sigue MNT-01A/MNT-02A y su retorno expreso; no se eluden esos bloqueos ni se habilita un documento desconocido.
6. ADMIN puede coordinar el relevo aprobado o cancelar expresamente la continuidad después de una interrupción registrada. Relevo exige oferta y aceptación del entrante, unidad propia válida y su acto online Asumir relevo; no permite compartir una unidad en períodos superpuestos. La pausa y la participación interrumpida se conservan.

La aprobación de esta precisión no crea cuentas persistentes, no reabre casos cerrados, no habilita piloto ni L-06. Backend/CRM/Flutter, pruebas negativas y carreras seguirán siendo obligatorios. Hasta confirmar esta precisión se continúa con los trabajos independientes.
