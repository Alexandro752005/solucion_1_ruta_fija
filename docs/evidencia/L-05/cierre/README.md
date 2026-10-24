# L-05 — Archivo de cierre funcional local

2026-10-20. **PASS — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Matriz completa](../matriz-L05.md), [acta](../../../actas/L-05-cierre-funcional-local-2026-10-20.md), [resultado](resultado.json), [manifest de cierre](../manifest-cierre.sha256).

## Evidencia conservada

- [79 regresiones finales](casos-regresion-final.json):15 ofertas,10 ejecución,16 atención,17 relevo,20 GPS y1 retorno. Una nueva prueba de sesión restringida; unión actual142 casos backend distintos con el incremento5, sin sumar repeticiones. XML saneados, sin propiedades/salidas de configuración.
- [Cinco incrementos anteriores](l05-final-immutable-check.json) y sus evidencias selladas: inicio, GPS/retención/acceso, correcciones, interrupción/atención y relevo/multitramos. Se reutilizan los resultados válidos en sus fronteras; no se presentan como ejecuciones nuevas.
- [Recorrido conectado contrastado](recorrido-conectado-contrastado.json): único RUN844b3bf6-f2fa-4666-bd0e-b1f7a1188696. Cinco etapas CRM/Edge y nueve Flutter con componentes/repositorios de producción, backend y PostgreSQL reales; sensor/permiso y correo simulados. No intervención humana ni dispositivo físico.
- [Historial de diagnóstico](historial-diagnostico.json): cinco procesos continuaron el mismo caso, con exactamente dos nuevas cuentas. Las observaciones fallidas siguen conservadas. La primera excepción del cliente posterior al200 de interrupción no se capturó con precisión; no se inventa su causa ni se reemitió esa interrupción. Las siguientes comprobaciones y estados persistidos acreditan el recorrido final.
- [Postflight independiente](l05-connected-postflight-final.json), [antes de regresión](l05-final-before-regression.json) y [después](l05-final-postflight-v42.json): datos persistentes idénticos durante las regresiones con rollback, Flyway válido, ledger1/3/4 y dos aceptaciones. El campo histórico `caseRunId` del preflight referencia el caso humano L-01 preservado; el RUN conectado L-05 es el identificado arriba.
- [Artefactos](artefactos-verificados.json): JAR actual, CRM development y APK debug previo cuya fuente móvil de producción sigue idéntica. No instalación ni despliegue.
-16 pruebas CRM dirigidas/typecheck/build y16 Flutter dirigidas/analyze correctos; logs adjuntos. [Comandos y límites](comandos-y-limites.json), [fuentes actuales](fuentes.sha256), [concordancia](concordancia.json).

## Controles y límites

El relevo conserva dos originales con cierre/inicio exactamente en el mismo instante. Preparación y asunción con respuesta incierta consultaron la clave confirmada sin segundo POST. La carrera HTTP terminó ASSUME200/WITHDRAW409 RESOURCE_VERSION_CONFLICT, con una resolución. El retorno comprobó OPERATIONAL_FEATURE_DISABLED específicamente y cierre propio seguro con el mismo esquema/clases; no es retorno a un binario antiguo ni restauración.

Retiro selectivo completado: dos cuentas/conductores y sus recursos deshabilitados, períodos y sesiones revocados, catálogo retirado; cuatro servicios terminales, sin tramos abiertos ni reservas vivas. **TEST_CLEANUP_RELEASED=true**, marcador/secreto privado ausentes y lock libre. Historial, ledger y aceptaciones conservados; las filas anteriores coinciden por hash. La liberación no autoriza limpiezas globales ni reutilización de cupos.

Cinco sellos incrementales,161 archivos cerrados L-04 y12 inmutables verificados, incluido final3 sin regeneración. Solo ruta_fija_test/rf_test; no migraciones de desarrollo/piloto, Git/GitHub, SMTP, cuentas reales o claves definitivas. Privacidad, personas/títulos, custodia, infraestructura y dispositivo L-08 siguen bloqueando piloto. **L-06 no iniciada/no autorizada.**
