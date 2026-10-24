# L-07 — Precisiones de proceso propuestas, v0.1.0

2026-10-21. **PROPUESTA; requiere respuesta explícita solo a los dos detalles siguientes.** No modifica las reglas aprobadas Q080/081/083/086, RET-02, T04/T05/T06 ni los bloqueos del piloto. [Contrato](L-07.md).

## REP-L07-01 — Cohorte del indicador y corte

El modelo conserva el servicio canónico `scheduled_service` y las ofertas históricas `assignment`; el encabezado refleja la última programación al reofertar. Contar ofertas duplica el servicio y usar solo el encabezado puede trasladarlo fuera del denominador original.

Se propone:

1. Un servicio cuenta una vez en el periodo de su **primera programación persistida** (fecha `scheduled_at` de su primera oferta, ordenada por `created_at,id`), dentro de días completos en la zona de la organización. Reprogramar no lo saca de ese denominador; se muestra la programación vigente por separado.
2. En el numerador de esa misma cohorte entran solo los servicios con cierre válido de ejecución iniciada, registrado por servidor **hasta el corte de consulta/emisión**. El fin real/corregido se conserva y muestra, incluso si cae fuera del periodo original; no se afirma que ocurrió dentro del periodo por pertenecer a la cohorte. Cancelados, interrumpidos, en curso y pendientes permanecen separados en el denominador.
3. Denominador cero muestra «Sin servicios programados», sin100% ficticio. Legados sin servicio canónico/oferta original comprobable se informan como cobertura incompleta; no se inventa un backfill ni se convierten en conformes.
4. La vista vigente refleja los datos actuales. Cada emisión guarda corte, versión/regla, filtros/zona, filas/totales y referencias a correcciones/cambios. Un cierre tardío o corrección genera otra emisión identificada; nunca modifica el snapshot anterior. Exportar varios formatos de una emisión usa ese mismo snapshot.

No se vuelve a preguntar qué KPI se desea:ya es completados/programados. Se concreta únicamente la pertenencia del periodo frente a reprogramación y cierre posterior.

## AUD-L07-02 — Cómputo, excepción acotada y copias

Los **seis meses calendario** de auditoría operativa ya están elegidos. Se propone concretar:

1. Vencimiento a la misma hora local del evento **seis meses después en America/Lima**, ajustando al último día del mes cuando no exista ese día. En el instante de vencimiento deja de estar disponible por la consulta ordinaria. Registrar política/vencimiento sin reescribir el evento original; adopción de política para legados marcada como tal, sin afirmar qué política se ejecutó en el pasado.
2. Excepción solo para eventos concretos de un caso abierto, con justificación codificada, responsable, referencia, aprobación y vencimiento explícitos; **máximo30 días adicionales desde el vencimiento ordinario**, sin renovación automática o retención de toda la organización. Acceso restringido. Si se necesita otra conservación, una nueva decisión deberá identificar finalidad y plazo; no se implementa conservación indefinida.
3. ADMIN identifica/solicita lo de su empresa; responsable técnico ejecuta selección previa y retiro transaccional restringido/auditado. Se verifican aislamiento, vencimiento exacto, concurrencia, idempotencia y ausencia de secretos. No se reactivan cuentas ni se habilita al SUPER_ADMIN una consulta operativa general.
4. Las copias no extienden plazos:inventario con categorías/vencimientos, retirar o sustituir al caducar; antes de restaurar, reaplicar retiros/vencimientos antes de permitir acceso. En L07 se prueba el mecanismo/política con sintéticos; medios reales, cifrado/restauración/RTO/RPO siguen sin acreditarse y requieren L08. No se modifica final3 ni se eliminan los archivos sintéticos sellados de pruebas como efecto de una purga del piloto.
5. Se mantienen independientes GPS7d, expediente/cuentas/HMAC30d tras fin, términos90d y recuperación30d por caso. No aplicar seis meses a todos esos datos ni ejecutar una purga real sobre desarrollo/piloto.

La propuesta no autoriza cuentas persistentes nuevas, SMTP/push externo, despliegue, otro lote o cambios del protocolo de recuperación. Continúan auditoría/permisos, redacción, resúmenes y preferencias que no dependan de estas respuestas.
