# L-03 — Propuesta concreta del proceso de mantenimiento

Versión **0.1.0**, 2026-10-19, America/Lima. **DECISIONES APROBADAS: MNT-01A y MNT-02A.** El titular seleccionó ambas opciones en respuesta directa a la consulta agrupada. Se conserva el texto de alternativas como antecedente; B no fue elegida. La aprobación no acredita implementación o pruebas. Q-036…039 permanecen confirmadas. Fuente: VT-V3-MAINTENANCE-DAYS.

## Base ya aprobada

Días completos Lima, motivo/unidad/intervalo previsto, preventivo sin confirmar hasta resolver todos los solapamientos; urgente bloquea de inmediato y conserva servicios afectados para reasignación/relevo seguro. ADMIN debe confirmar retorno real con responsable/fecha/evidencia y documentos/otros bloqueos comprobados; nunca autoactivar al llegar la hora prevista.

## MNT-01 — Reservas posteriores a la salida prevista aún no confirmada

Dos implementaciones respetan los gates al iniciar, pero ofrecen distinta capacidad antes de la salida real:

- **MNT-01A (recomendada, conservadora):** mientras un mantenimiento esté abierto, impedir nuevas reservas cuya conducción alcance cualquier instante posterior al inicio del mantenimiento, incluso posterior a su salida estimada. El fin previsto es informativo y sirve para planificar/revisar conflictos, sin prometer un retorno. Solo el retorno expreso permite reservar otra vez, según MNT-02 y los demás bloqueos.
- **MNT-01B:** permitir reservas posteriores al último día previsto mientras aún no se haya producido un retraso; al vencer la previsión sin retorno, bloquear e invalidar las futuras afectadas conservándolas para reasignación. Volver a comprobar al aceptar/iniciar. Puede aumentar reprogramaciones de compromisos ya aceptados.

**Elegida expresamente MNT-01A.** Tampoco cancela silenciosamente compromisos existentes fuera del intervalo preventivo propuesto: si quedarían bloqueados, debe mostrarlos y resolverlos antes de confirmar ese mantenimiento.

## MNT-02 — Retorno anticipado y día del retorno

- **MNT-02A (recomendada):** ADMIN puede finalizar antes de la fecha prevista con motivo y comprobación. El día real del retorno sigue bloqueado completo en Lima; días posteriores se liberan si pasan documentos y otros bloqueos. Se conserva la previsión original y el instante real, sin reescribir historia.
- **MNT-02B:** ni un retorno anticipado libera los restantes días originalmente programados; puede acreditarse fin del trabajo, pero se bloquea hasta terminar el intervalo previsto completo. Un cambio del plan requeriría otra operación motivada y auditada.

Ambas conservan el bloqueo de todo el día; no se propone habilitar conducción dentro del día de mantenimiento. Avería en servicio conserva el curso para actuación segura, sin fin automático.

**Elegida expresamente MNT-02A.** Liberación desde el comienzo del día siguiente al retorno real, sometida a documentos y otros bloqueos; la previsión original permanece inmutable.

Los límites inclusivos multifecha se derivan de Q-036: inicio a medianoche Lima del primer día y fin exclusivo a medianoche del día siguiente al último. No falta una decisión sobre zona horaria o sobre quién confirma el retorno.

Se continúa con grupos, catálogo/versionado de rutas, privacidad/aislamiento y las partes técnicas independientes. Estas opciones no amplían autorizaciones de base, correo, presupuesto o lotes.
