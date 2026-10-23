# L-02 — Matriz de ejecución y aceptación

Fecha: 2026-10-18, America/Lima. **EN_CURSO**. Inicio autorizado por `OK VERDE`. [Contrato0.1.0](../../contratos/L-02.md). No se inicia L-03 ni se habilita piloto.

| ID | Alcance / reglas | Estado y evidencia necesaria |
|---|---|---|
| CI-L02-01 | Base de acceso/expediente de L-01 reutilizada; PB-02.1…3/PB-01.5 | Cierre L-01 y artefactos contrastados como antecedentes. [Preflight](preflight-archivo-L01-2026-10-18.json):49/50 hashes iguales; backlog mutable distinto, preservado y respaldado, sin atribuir causa ni restaurar sobre la edición. No repetir OTP/recuperación humanos. Regresión afectada pendiente cuando se verifique el incremento. |
| CI-L02-02 | Estado posaprobación por cuenta/Driver/grupo/documentación; PB-02.4 | Primer incremento de consulta backend→CRM. Pendientes comprobación conectada200,404 ajeno,403 roles, no PII, cuenta sin solicitud/ambiguos y expiraciones. No confundir consulta con habilitación. |
| CI-L02-03 | CARD-01 confirmada; Q-029; una unidad vigente por conductor y exclusividad inversa | Decisión recibida. Protección heredada y estado de conflicto iniciales; pendientes periodos/historial, concurrencia real y restricción persistente revisada. No reparar vínculos existentes automáticamente. |
| CI-L02-04 | Vigencia personal/renovación; Q-019/022 | Pendientes renovación posterior a aprobación, gates antes de reservar/aceptar/iniciar y tratamiento de pérdida de vigencia durante conducción. Aprobación histórica conservada. |
| CI-L02-05 | Suspensión operativa y efectos; Q-022/023 | Pendientes motivo/evidencia/autor, ofertas canceladas, asignaciones futuras invalidadas/ocupación liberada y servicio pendiente de reasignación; curso como incidencia/relevo, consulta/subsanación conservadas. |
| CI-L02-06 | Corrección material; Q-024 e ID-01 | Pendiente procedimiento restringido que preserve cuenta/Driver/historial y no sustituya persona ni sobrescriba HMAC. Registro inmutable existente se conserva. |
| CI-L02-07 | Unidad propia/empresarial y revisión separada; Q-030/031/032 | Pendiente contrato/flujo conectado de solicitud y revisión de unidad/empresa/servicio; M1/títulos/unknown y sin copias. No presupone títulos reales. |
| CI-L02-08 | Vínculos temporales y sustitución; Q-033/034/035, POL-LINK-DATES/END, Q-104 | Pendientes inicio/fin, inclusión día Lima, fin obligatorio empresarial, renovación/desvinculación/historial y respuesta perdida. Cierre servicio no rompe vínculo. Sin reinterpretar datos heredados como vigencias aprobadas. |
| CI-L02-09 | Continuidad CRM/Android y autorización server-side | Pendiente resolver transición provisionado→consulta/subsanación→habilitación sin dependencia circular ni sesión operativa indebida. Sin duplicación de cuenta/persona/rol/empresa; una sola persona no se representa como dos participantes reales. |
| CI-L02-10 | Regresión dirigida, aislamiento, transacciones, migración/retorno y evidencia | Pendiente; no ejecutar limpieza global ni migraciones de desarrollo/piloto. Cada comando realmente ejecutado se registra; compilación no es prueba de funcionamiento. Conservar final3 y contadores. |

Los pendientes reales del piloto del acta L-01 permanecen separados; este inicio no los satisface ni los elimina. Esta matriz no autoriza iniciar otros lotes por su dependencia técnica.


## Corte del primer incremento

[Informe L02.0](incremento-L02.0-2026-10-18.md): implementacion backend/CRM y compilaciones Java/TypeScript/Angular correctas. **CI-L02-02/03 implementados parcialmente, no VERIFICADOS**: no hay aun ejecucion conectada/concurrencia ni modelo temporal completo. Fixture de privacidad existente adaptado, sin tests nuevos/ejecutados. Estado de lote EN_CURSO.
