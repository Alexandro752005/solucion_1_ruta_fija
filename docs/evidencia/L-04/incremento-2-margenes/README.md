# L-04 — Incremento2: márgenes por tipo y candidatos

**2026-10-19, America/Lima. PASS del incremento; L-04 EN_CURSO.** [Resultado](resultado.json), [matriz](../matriz-L04.md), [API0.2.0](../../../contratos/L-04-margenes-API-v0.2.0.md). No acta de cierre del lote, piloto ni L-05.

## Ejecución y entorno

Spring/filtros/JWT/gates y PostgreSQL **ruta_fija_test / rf_test** reales, fixtures HTTP con rollback, correo doble. V34 aplicada explícitamente con rf_migrator, partiendo de V33 validada, sin repair ni edición de SQL aplicado. [Preflight](before-v33.json), [migración](migrate-v34.json), [postflight](post-suite-v34.json).

Desde `backend`, exit0 comprobado:

```powershell
mvn.cmd --offline -q '-Dtest=ServiceMarginIntegrationTest#margin*,OfferAcceptanceIntegrationTest#offer*,RouteAssignmentIntegrationTest#route*,MaintenanceIntegrationTest,OfferResponseWindowTest' test
```

`final-regresion/`: cinco XML con **99 PASS,0fallos/errores/omitidos**:18márgenes+14ofertas+9rutas/asignación+54mantenimiento/elegibilidad+4política temporal. Los18 comprueban ausencia de defaults, versiones/captura, retiro, idempotencia/versión, inmutabilidad DB, solapamiento/borde, candidatos sin reservar/PII, vigencia documental/período del buffer, cambio posterior a la consulta, reservas futuras compatibles, legado desconocido, roles/gates, configuración realmente ajena y mantenimiento cruzando medianoche Lima.

Ejecución adicional exit0:

```powershell
mvn.cmd --offline -q '-Dtest=ServiceMarginIntegrationTest#marginActualForeignConfigurationCannotBeReadSelectedOrEnumerated,L04BufferedReservationConcurrencyTest' test
```

El negativo de configuración ajena quedó incluido después entre los18 finales; **no se suma otra vez**. Los dos casos nuevos `L04BufferedReservationConcurrencyTest` sí se suman: **101 distintos**. Excluyen conflicto de márgenes sobre conductor/unidad con dos conexiones PG y comprueban frontera exacta; no son dos clientes HTTP ni una incorporación documental. La configuración de esos fixtures usa un hash técnico de relleno y un modo de fila legado: prueba restricciones PG, no una configuración normal por ADMIN. La configuración normal sí pasa por API en los18.

## Clientes y compilación

Desde `frontend`: typecheck, build de desarrollo y:

```powershell
npm.cmd run test:ci -- --include=src/app/features/assignments/assignments.page.spec.ts --include=src/app/features/assignments/service-types.page.spec.ts --include=src/app/features/routes/assignment-route-panel.spec.ts --include=src/app/core/operations/operations-api.service.spec.ts
```

**20 PASS** en cuatro archivos:7oferta/candidatos+6configuración+4panel+3API. Formularios sin números prefijados, configuración explícita/revisiones/retiro, no auth replay de mutaciones de configuración, incertidumbre consultada por operación, selección invalidada por cambio de horario y respuestas tardías, fin de paginación aunque el servidor omita `nextOffset` nulo. Dobles HTTP; no navegador real.

Maven offline `-DskipTests package`, exit0, JAR en target con fuentes principales finales. No artefacto de cierre/despliegue L-04. Sin nueva suite Flutter, ensayo humano, SMTP, dispositivo ni recorrido TCP en este incremento. La consulta móvil y el recorrido completo siguen sus criterios pendientes; no se sustituyen por componentes.

## Retiro técnico y conservación

Casos PG **7617c84d-4c4c-4b6f-9082-85de54eb9345** (unidad) y **8aa821e6-fbbb-4e2f-8b1a-53ab5ff73c30** (conductor), resultados originales en `concurrencia/`. Retirada por UUID/forma/tenant comprobados de asignaciones, snapshots y recursos técnicos sin cuenta. Los dos tipos técnicos quedan **inactivos**, con revisión y recibo de retiro conservados. No se eliminan revisiones inmutables ni se desactivan triggers para limpiar. [Comprobación posterior de solo lectura](retiro-carreras.json).

Ledger **1/3/1**, aceptaciones **2** y hashes humanos/L-02 preservados, cero cuentas comprometidas nuevas y cero SMTP externos. Marcadores ausentes/lock libre; no autoriza limpieza global ni recupera cupos. [Ocho inmutables](inmutables-preservados.json), incluidos final3/términos/actas/ensayos anteriores, comprobados por lectura de huellas. Evidencia archivada del incremento1 conservada; sus fuentes/documentos mutables evolucionan legítimamente, sin reescribir su manifiesto histórico.

## Historia, límites y siguiente dependencia

`historico-inicial/` conserva el primer rechazo por faltar un campo del fixture TS y la regresión que detectó que RESERVADO sin compromiso real no debía aceptarse. Se corrigió el código para admitir otra reserva solo con un compromiso auténtico compatible; la prueba adversa de caché se mantuvo. `final/` conserva el corte95 anterior al endurecimiento final de legado/inicio y pruebas adicionales. No se reemplazan resultados previos por éxitos.

[Saneamiento](saneamiento-registros.json) limitado a registros generados propios de este incremento; no se imprimen secretos ni se modifican resultados/evidencia cerrada. [Fuentes](fuentes.sha256), [manifiesto del corte](manifest.sha256).

CI01…04 acreditados localmente. Restan CI05…10: completar separación servicio/oferta y reasignación, cancelación expresa del servicio, cambios completos de recursos/horario con nueva aceptación, incertidumbre/integración CRM/Flutter, recorrido conectado y retorno/archivo final. Los números sintéticos no fijan valores operativos ni acreditan suficiencia legal, participantes reales, custodia o infraestructura del piloto. **Piloto bloqueado, L-05 no iniciada.**
