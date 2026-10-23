# Matriz vigente L-05

**EN_CURSO — contrato0.5.0**, 2026-10-20. Solo validación funcional local; piloto bloqueado. [Contrato](../../contratos/L-05.md), [ejecución](../../ejecucion/L-05.md). Ningún criterio se verifica por existencia de código o pruebas históricas sin contraste.

| ID | Criterio obligatorio local | Estado | Evidencia / pendiente |
|---|---|---|---|
| CI-L05-01 | Inicio propio online, aceptación, habilitación, GPS requerido y tiempos servidor sin autoinicio | **VERIFICADO_LOCAL_API** | [L05.1](incremento-1-ejecucion/README.md): Spring/PG reales, diez casos ejecución y regresión de ofertas/rutas. OS declarado por cliente, no atestación física; TCP final enCI09. |
| CI-L05-02 | Tramos originales por conductor/unidad; aislamiento, versiones e idempotencia | PARCIAL | [Incremento5](incremento-5-relevo/README.md):17 relevo PG, originales, pausa, un tramo abierto, replay/versión/tenant e inmutabilidad; queda carrera de negocio HTTP real del caso L05-CON-01 autorizado. |
| CI-L05-03 | Ruta finalizada; terminal automática200m/120s/100m, ausencia y discrepancia diferenciadas | **VERIFICADO_LOCAL_API** | [GPS/cierre](incremento-2-gps/README.md): tres resultados, observación persistida/aviso sin coordenadas, lectura puntual Flutter. Navegador/TCP final enCI09, calibración L-08. |
| CI-L05-04 | Interrupción segura diferenciada y ocupación/atención coherentes | **VERIFICADO_LOCAL_API** | [Incremento4](incremento-4-interrupcion/README.md):16 atención PG, autoría real, recursos separados, inmutabilidad, negaciones/tenant, GPS detenido, mantenimiento/retorno y clientes con incertidumbre por key; TCP finalCI09, relevoCI06. |
| CI-L05-05 | Corrección ADMIN de tiempos finalizados auditada; originales y GPS conservados | **VERIFICADO_LOCAL_API** | [Incremento3](incremento-3-tiempos/README.md)15 tiempos, [incremento4](incremento-4-interrupcion/README.md) cancelado/interrumpido y [incremento5](incremento-5-relevo/README.md) dos tramos/solapamiento/consulta propia y originales intactos. Conexión finalCI09. |
| CI-L05-06 | Relevo preparado, ofrecido/aceptado y asumido por entrante; tramos atómicos | **VERIFICADO_LOCAL_API** | [Incremento5](incremento-5-relevo/README.md): preparación ADMIN/aceptación/acción propia, GPS/documentos, transición atómica, pausa, inicio/planificación y atención separada; carreraCI02 y TCP finalCI09. |
| CI-L05-07 | GPS solo en servicio/consentimiento/permiso/primer plano; intento30s y detención | **VERIFICADO_LOCAL_AUTOMATIZADO** | [GPS](incremento-2-gps/README.md): controlador con dispositivos ficticios, lifecycle/30s, pérdida de condiciones, descarte sin cola, lectura puntual, hora del sensor y precisión. Frontera API/PG; físico L-08 y TCP finalCI09. |
| CI-L05-08 | Resumen propio, acceso limitado por empresa/rol, puntos≤7d y purga incluso incidentales | **VERIFICADO_LOCAL_AUTOMATIZADO** | [20 GPS PG](incremento-2-gps/casos.json), [API0.2](../../contratos/L-05-GPS-API-v0.2.0.md): aislamiento/gates/reauth,48h/15min, sin prórroga, purga y referencias. CRM/Flutter componentes, no ensayo humano ni cumplimiento jurídico. |
| CI-L05-09 | Recorridos clientes conectados, pérdida de respuesta sin offline y regresiones pertinentes | PENDIENTE | [Caso L05-CON-01 autorizado](../../operacion/L-05-caso-conectado-autorizacion.md), máximo dos CONDUCTOR sintéticos; arnés/conexión por ejecutar y evidenciar, sin reabrir cuentas/casos. |
| CI-L05-10 | Retorno compatible, postflight/cupos, artefactos, inmutables y concordancia documental | PARCIAL | [Preservación V42](incremento-5-relevo/preservacion.json) y artefactos incrementales acreditados; faltan retorno conectado, postflight del nuevo caso y acta solo con matriz completa. |

## Límites de habilitación real

No se satisfacen por pruebas sintéticas: privacidad/identificación/finalidad/base y protección; ADMIN legítimo/aceptación propia; custodia independiente/ensayo del titular; infraestructura/HTTPS/base/copia/restauración; participantes/títulos/recursos; dispositivo/batería/calibración L-08. No reducir criterios locales mediante estos límites.

SRV-INT-L05-05 aprobado2026-10-20; implementación pendiente, no es evidencia de CI04. [Decisiones0.3.0](../../../specs/decisiones-L05-2026-10-19.json).

Corte incremental4: SRV-INT-L05-05 acreditado localmente según resultado y límites enlazados; la anotación anterior de implementación pendiente corresponde al corte3. V40 test, ledger1/3/2 y aceptaciones2 conservados. Relevo/multitramos/carreras/conectado/retorno/cierre pendientes; L-05 EN_CURSO.

Corte5 vigente: relevo/varios tramos y CI05/06 acreditados localmente con los límites enlazados. Las anotaciones anteriores de implementación pendiente son históricas. L05-CON-01 autorizado, aún no ejecutado; ledger al corte1/3/2. L-05 EN_CURSO.
