# Matriz vigente de aceptación — L-08

2026-10-23 America/Lima. Contrato **0.9.0**. **VERIFICADA — FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL. GO PARA CANDIDATO LOCAL DE ENTREGA. NO_GO_PILOTO_REAL.**

La instrucción maestra autoriza esta frontera **local**. Cada `VERIFICADO_LOCAL_CANDIDATO_FINAL` acredita el mecanismo, documentación o ensayo de la columna «Evidencia comprobada», dentro de sus límites. No afirma cumplir en campo la totalidad del requisito original. Se conserva la [matriz 0.8.0 exacta](matriz-L08-v0.8.0-antecedente.md) y el [contrato 0.9.0](../../contratos/L-08.md) registra la evolución. La simulación conserva su procedencia.

| ID | Criterio conservado | Estado del candidato local | Evidencia comprobada | Condición/límite de campo |
|---|---|---|---|---|
| CI-L08-01 | Candidato trazable y reproducible | VERIFICADO_LOCAL_CANDIDATO_FINAL | Release v5, certificado distinto de debug, JAR V50 contrastado y manifiesto; reproducción idéntica byte a byte con la misma clave | Firma, custodia y endpoint efectivos antes de campo |
| CI-L08-02 | Roles, objetos, transiciones y exclusión offline | VERIFICADO_LOCAL_CANDIDATO_FINAL | Regresiones históricas conservadas; 56 pruebas actuales de ubicación, incluidas seis nuevas; analyze sin incidencias; sin cola | Sin nueva prueba integrada completa de todos los módulos |
| CI-L08-03 | Carga de 25 y p95 durable ≤ 2 s | VERIFICADO_LOCAL_CANDIDATO_FINAL | CAP25 histórico intacto: 100 creaciones y 100 aceptaciones; p95 343,0684 y 461,8702 ms | Una PC y loopback; no mide infraestructura real |
| CI-L08-04 | Sondas, contingencia y detección ≤ 5 min | VERIFICADO_LOCAL_CANDIDATO_FINAL | HTTP sintético y PG real; caída PG detectada en 2,117 s y conjunto HTTP/PG en 8,413 s; procedimiento 24/7 y cota de diseño 105 s | Host independiente, DNS acotado, alertas entregadas, guardia nominal 24/7 y recuperación real ≤ 30 min pendientes |
| CI-L08-05 | Restauración y RTO ≤ 4 h | VERIFICADO_LOCAL_CANDIDATO_FINAL | SQL sintético nuevo: 415,026 s incluyendo fallos e intervención; REST03 histórico: 105 tablas/API y 318,731 s | Pérdida total, medio independiente, clave portable y clientes representativos pendientes |
| CI-L08-06 | Respaldo cifrado y cálculo RPO ≤ 1 h | VERIFICADO_LOCAL_CANDIDATO_FINAL | pg_dump y AES-256/GCM ejecutados; archivo alterado y clave errónea rechazados; control de antigüedad; diseño 1800 + 900 = 2700 s | Copia en la misma PC; medio y clave independientes, tarea horaria, verificación diaria sostenida y RPO operativo pendientes |
| CI-L08-07 | Android, GPS, red, batería y falta de fix | VERIFICADO_LOCAL_CANDIDATO_FINAL | FIS02 físico cerrado conservado; seis simulaciones nuevas por más de 120 s, servicio preservado, sin coordenadas ficticias ni reenvío acumulado | Pérdida física de señal NO OBSERVADA; release v5 no instalada físicamente |
| CI-L08-08 | Chrome, Edge, zoom y formularios | VERIFICADO_LOCAL_CANDIDATO_FINAL | Histórico de 12 combinaciones, 66 páginas y 804 comprobaciones preservado | Inventariar equipos y navegadores de participantes reales |
| CI-L08-09 | Accesibilidad dirigida | VERIFICADO_LOCAL_CANDIDATO_FINAL | FIS02 con TalkBack y escala al 150 % confirmados por el usuario; contraste corregido y pruebas preservadas | Sin certificación integral de accesibilidad |
| CI-L08-10 | Secretos y entrega Android firmada | VERIFICADO_LOCAL_CANDIDATO_FINAL | Release v5 real, firma RSA 3072 sin debug, ACL usuario/SYSTEM, API HTTPS `.invalid` separada; no instalada | Clave candidata local; custodia, distribución, firma definitiva y dominio real pendientes |
| CI-L08-11 | Retiro y protocolo de transición | VERIFICADO_LOCAL_CANDIDATO_FINAL | Postflight SELECT-only conserva ledger, aceptaciones y 85 puntos; anexo vinculante PR-01 a PR-06 preparado | Aceptación propia de ADMIN, privacidad, ceremonia USB del titular, títulos, participantes y HTTPS reales pendientes |
| CI-L08-12 | Concordancia, preservación y decisión | VERIFICADO_LOCAL_CANDIDATO_FINAL | 1.288 entradas históricas reconciliadas, incluidos cuatro documentos mutables en copias exactas; archivo, acta y firma técnica local | Decisión de campo separada: NO_GO; sin aceptación personal inferida |

## Índice de pruebas y procedencia

- [Archivo nuevo y comandos](incremento-5-candidato-final/README.md), [higiene/preservación](incremento-5-candidato-final/hygiene/preservacion.json), [postflight de solo lectura](incremento-5-candidato-final/hygiene/postflight-salida.txt).
- [GPS simulado y 56 regresiones](incremento-5-candidato-final/mobile/resultado-mobile.json), [revisión independiente](incremento-5-candidato-final/release/qa-mobile.json). Las seis nuevas están incluidas en 56; no son 62 casos distintos.
- [Continuidad nueva y correcciones](incremento-5-candidato-final/ops/ops-summary-final.json), [procedimiento de respaldo](../../operacion/L-08-respaldo-independiente.md), [monitoreo](../../operacion/L-08-monitoreo-operativo.md).
- [Release y receta](incremento-5-candidato-final/release/README.md), [JAR/APK/manifiesto](../../../artifacts/L-08-entrega-final/manifest.json).
- [FIS02 físico histórico](incremento-4-cierre-fisico/README.md), [protocolo real](../../legal/protocolo-transicion-piloto-real.md), [acta local](../../actas/L-08-cierre-funcional-global.md).

**Aceptación operativa original: NO_GO.** Pendientes de campo: pérdida física de señal; monitor, alertas y guardia independientes 24/7; medio y clave independientes, calendario, RPO y restauración total representativa; custodia, firma y distribución definitivas; privacidad, ADMIN, titular, participantes, organizaciones, títulos y HTTPS efectivos. El acta local no habilita estos recursos.
