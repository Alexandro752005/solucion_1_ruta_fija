# Seguimiento local de lotes

`tareas.json` es la copia operativa local derivada de `ruta_fija_kit/docs/seguimiento/tareas.json`. El registro original del kit permanece sin cambios y la copia normativa v3.1 vive en `specs/`.

## Reglas de actualización

- Mantener intactos los IDs `L-*`, `MOD-*`, `BR-*`, `AC-*`, `OPEN-*` y `VT-*`.
- Actualizar estado solo con evidencia actual y enlazar el artefacto local correspondiente.
- `BLOQUEADA` requiere identificar decisión, recurso o prueba faltante y quién debe resolverlo; no se convierte en `VERIFICADA` por una prueba de compilación.
- No incorporar al tablero local pasos de control de versiones. La trazabilidad se guarda mediante fecha, ruta de evidencia, comando real, resultado y huellas SHA-256 cuando correspondan.
- No inventar decisiones de negocio ni usar documentos históricos para sobrescribir v3.1.

## Estado a 2026-10-14

- L-00: VERIFICADA. Baseline backend/CRM/Flutter, PostgreSQL aislado, trazabilidad, snapshot final3 y decisión provisional Gmail SMTP S/0 con excepción de dominio documentados. No se envió correo; configuración/credenciales y entrega controlada quedan en L-01. La validación física queda en L-08.
- L-01: EN_CURSO. Revalidación: backend `mvnw verify` 36 unitarias + 42 integración, CRM typecheck/43 pruebas/build, Flutter analyze/43 pruebas; todos pasaron. `ruta_fija_test` en V14 y `final3` conserva su SHA-256. La edición genérica ya bloquea cambios de correo de CONDUCTOR; ADMIN no puede crear/gestionar cuentas ADMIN. SMTP no se reintentó: el nombre de usuario local aún no coincide con la lista permitida. Pendientes: envío/recepción/consumo OTP real, alta/reemplazo positivo ADMIN, ficha/aprobación/implementación de términos, invitaciones, recuperación CONDUCTOR, custodia y ensayo real SUPER_ADMIN. No iniciar L-02.
- L-02 a L-08: POR_AUDITAR. No se inicia ni se pasa a otro lote hasta solicitar y recibir del usuario una nueva señal literal `OK VERDE`.
- La revisión y el estado se documentan en [L-00](../ejecucion/L-00.md), el [contrato L-01](../contratos/L-01.md), [ejecución L-01](../ejecucion/L-01.md), [evidencia técnica L-01](../evidencia/L-01/estado-tecnico.md), la [decisión Gmail](../evidencia/L-00/decision-Gmail-2026-10-14.md), el [borrador de términos ADMIN](../legal/terminos-admin-v0.2.0-borrador.md), la [decisión de recuperación SUPER_ADMIN](../evidencia/L-01/decision-recuperacion-superadmin-2026-10-14.md) y su [protocolo backend](../evidencia/L-01/recuperacion-superadmin-protocolo.md).
## Ciclo de trabajo local

1. Leer el contrato del lote, las reglas citadas y la dependencia anterior.
2. Asignar archivos exclusivos por perfil; conservar un solo escritor para contratos y migraciones compartidas.
3. Antes del cambio funcional, registrar el estado local y el plan de vuelta.
4. Ejecutar las comprobaciones aprobadas del lote y guardar su salida sin secretos.
5. Actualizar mapa, backlog, memoria actual y tablero; respaldar la memoria en cada hito acordado.
