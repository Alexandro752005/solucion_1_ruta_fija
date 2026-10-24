# REC-CI07-02 — Implementación y comprobaciones; intervención humana pendiente

2026-10-17, America/Lima. **L-01 EN_CURSO. CI07 PENDING; ninguna reparación real del fixture ejecutada por el agente.**

## Autorización y cambios

El operador confirmó PRINTF, PRINTLN y valor ficticio visibles en B y aprobó expresamente REC-CI07-02 v0.1.0. [Registro inmutable](../../operacion/L-01-REC-CI07-02-autorizacion-2026-10-17.json), SHA EF4E1B5B5C03C448F77B4F2F9B744BC5E710DF75A36902D2FCE91B97EBE9CFC4. La propuesta SHA B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2 se conserva como antecedente aprobado, sin reescribir su contenido.

Implementación solo en fuentes de test:

- `ManualL01RecoveryFixtureRepair`: guardas exactas de base/rol/V26/caso/instalación/pública/ID01/términos/ledger/auditoría, autorización/hash y confirmación de salida. Genera un sustituto tras autenticar y reautenticar; exige repetirlo oculto antes de codificarlo y escribir. Vacía buffers y devuelve únicamente metadatos públicos.
- `ManualL01HumanTrialServer`: comando `reparar-codigo`, dos autenticaciones normales con captura privada de sesiones, revisión de `/auth/me`, impresión una sola vez y confirmación oculta. Transacción con bloqueo de cuenta/credencial, revalidación, actualización única y auditoría atómica. Ningún endpoint o cambio del cliente/protocolo normal.
- Reanudación explícita del mismo caso y segmento nuevo: guarda las afirmaciones anteriores como historia, conserva aceptación/gate pendiente y distingue las sesiones nuevas efectivamente observadas. Recupera la referencia pública de la base; no recupera secretos de evidencias ni inventa tokens históricos. La auditoría impide otra reparación incluso si fallara la escritura posterior del estado público.
- `ManualL01HumanTrial --inspect-case` informa también las guardas de reparación mediante SELECT, sin iniciar Spring o generar un código.

La recuperación posterior sigue exigiendo firma, código, OTP simulado, contraseña nueva, consumo/rotación, pública conservada, sesiones rechazadas, login y declaraciones humanas. La reparación no produce PASS ni acredita la entrega original.

## Pruebas ejecutadas y límites

| Comprobación | Resultado | Entorno y límite |
|---|---|---|
| Pruebas dirigidas finales | **35/35 PASS**:21 de arnés y14 de reparación | Unitarias con dobles; no DB/Spring/SMTP ni aceptación humana |
| Guardas negativas | PASS | Cada guarda falsa/ausente, autenticación/sesión fallida, cancelación/entrada vacía/diferente y estado cambiado antes de guardar |
| Uso único/concurrencia | PASS | Store de prueba sincronizado: una única confirmación concurrente puede guardar; no representa dos reparaciones reales en PostgreSQL |
| Transacción y auditoría | PASS | Doble transaccional: fallo de auditoría revierte la actualización; revalidación después de bloquear y metadatos sin secretos |
| Preflight PostgreSQL | **PASS**,2026-10-17T15:54:38.700388700Z | ruta_fija_test/rf_test/V26, bloqueos de filas y advisory de cupos reales; UPDATE/INSERT solo mediante EXPLAIN **sin ANALYZE**; cero DML de aplicación, cero commit o generación de código |
| Estado preservado | PASS | Ledger1/2/0, cupos0/2/20, una aceptación y credencial, marcador intactos; lock temporal del preflight liberado, servidor sigue detenido |
| final3 | PASS, huellas vigentes | ZIP3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B y manifiestoE78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD; sin regenerar |

[Log final dirigido](REC-CI07-02-2026-10-17/rec-ci07-02-directed-unit-final.log). [Preflight final](REC-CI07-02-2026-10-17/preflight-postgresql-final.json), SHA **8893D08A7F7714375BDFF0860D6F77923D344F798F350F100D7014AAA05E4CF5**; [fuente final](REC-CI07-02-2026-10-17/ManualRecoveryRepairPreflight-final.java), [salida original](REC-CI07-02-2026-10-17/preflight-postgresql-final-original.log) y [manifiesto](REC-CI07-02-2026-10-17/manifest.sha256).

Los intentos intermedios se conservaron: compilación inicial y correcciones de dobles de prueba; no se suman sus conteos al final. El primer preflight rechazó un bloqueo de tabla del ledger con SQLSTATE42501, coherente con su protección. Se sustituyó por los tres advisory locks de creación de V22/V23, conservando privilegios. Se guardó esa observación y los cortes intermedios; el campo intermedio `rowAndTableLocksTested` quedó sustituido por `rowAndHistoricalQuotaAdvisoryLocksTested` en el corte final. No se afirma que el preflight final bloquease la tabla del ledger ni que ejecutara UPDATE/INSERT.

**Pendiente humano y límite técnico:** no se probó todavía el commit real de esta reparación, su entrega local del sustituto o la recuperación manual/HTTP posterior del caso. Los tests y EXPLAIN no equivalen a esos resultados. El operador realizará contraseñas, repetición del sustituto, firma/OTP y login; el agente no los introduce.

## Continuación preparada

[Guía operativa](../../operacion/L-01-REC-CI07-02-ejecucion.md): reanudar personalmente el mismo caso con `-Serve -ResumeRunId`, mantener A abierta y comunicar RUN_ID/login cargado. Esperar la comprobación de lock/marcador antes de `reparar-codigo`. No ejecutar otro `enroll`, recrear una cuenta o iniciar un caso distinto.

CI03 gate pendiente/REC-CI03-01 no autorizado; SC03/SC07 condicionales sin aplicar. CI01 permanece VERIFICADO, sin repetir SMTP; términos20FFD952… intactos, TEST_CLEANUP_RELEASED=false, piloto bloqueado/L-02 sin iniciar. Sin cambios de producción, migraciones, despliegue o Git/GitHub.
