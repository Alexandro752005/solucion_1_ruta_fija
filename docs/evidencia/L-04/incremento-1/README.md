# L-04 — Incremento1: frontera de aceptación y vencimiento

**2026-10-19, America/Lima. L-04 EN_CURSO.** [Resultado](resultado.json), [contrato](../../../contratos/L-04.md), [API](../../../contratos/L-04-ofertas-API-v0.1.0.md), [matriz](../matriz-L04.md). No es acta de cierre del lote.

## Servidor real de pruebas

Spring/filtros/JWT/gates y PostgreSQL `ruta_fija_test`/`rf_test`, fixtures con rollback, correo doble. Se reutiliza `QualificationIntegrationTest` y sus controles, sin la suite de limpieza global. Preflight V32, migración explícita V33 y postflight finales en los JSON adjuntos. Solo cambió `<` a `<=` en deadline/inicio del CHECK, manteniendo el resto de condiciones de V7. No desarrollo/piloto, repair, limpieza o modificación de SQL aplicado.

Desde `backend`, ejecuciones con exit0 comprobado:

```powershell
mvn.cmd --offline -q '-Dtest=OfferAcceptanceIntegrationTest#offer*,MaintenanceIntegrationTest,RouteAssignmentIntegrationTest#route*,OfferResponseWindowTest' test
```

En `final/`: **79 PASS,0fallos/errores/omitidos**:12ofertas+54mantenimiento/elegibilidad+9rutas/asignación+4política temporal. Después se añadieron dos negativos específicos y se ejecutaron solo esos métodos:

```powershell
mvn.cmd --offline -q '-Dtest=OfferAcceptanceIntegrationTest#offerConductorCannotEmitAndAdminCannotCallTheConductorAcceptanceRoute+offerForeignDriverOrVehicleAreHiddenAndCannotCreateATenantCrossingHold' test
```

En `permisos-adicionales/`:2 PASS. La unión por clase/método da **81 distintos**, sin duplicados. No se dice que un único comando haya ejecutado81. Los 14 casos de ofertas cubren omisión móvil, bloqueo de ADMIN_DIRECT/reserva/inicio, rol/tenant, plazo600s/inicio exacto, aceptación antes/en límite, idempotencia sin renovación, nueva reserva que expira la anterior sin job y conflicto secuencial con primera reserva pendiente. No equivalen a carreras con dos clientes ni a un modelo servicio/oferta ya desacoplado.

Las dos pruebas heredadas que admitían reserva/inicio ADMIN se adaptaron a los rechazos de Q-052, manteniendo el control documental en las transiciones móviles. Un fixture de placa antes ADMIN_DIRECT ahora usa oferta móvil; su identidad/snapshot siguen verificados. No se eliminaron suites ni sus archivos históricos.

Empaquetado `mvn.cmd --offline -q -DskipTests package`, exit0, JAR solo en target; no artefacto de cierre/despliegue L-04. La adición posterior de dos métodos de prueba no cambia fuentes principales empaquetadas.

## CRM

`npm.cmd run typecheck`, `npm.cmd run build:development` y:

```powershell
npm.cmd run test:ci -- --include=src/app/features/assignments/assignments.page.spec.ts --include=src/app/features/routes/assignment-route-panel.spec.ts --include=src/app/core/operations/operations-api.service.spec.ts
```

Todos exit0, **11 PASS**: componente de oferta4, panel de ruta4, API3. Usa dobles HTTP; no navegador real ni backend conectado. Comprueba modo móvil/sin deadline cliente, formulario conservado ante409 y ausencia de acciones ADMIN. No nueva ejecución Flutter/dispositivo/SMTP en este incremento.

## Preservación y pendientes

Ledger **1/3/1**, aceptaciones **2**, hashes de fixtures humanos/L-02 y ocho archivos inmutables conservados. [Postflight](l04-post-permissions.json), [huellas](inmutables-preservados.json), [manifiesto del incremento](manifest.sha256). Ninguna cuenta comprometida nueva, ensayo cerrado reabierto o cupo recuperado. No marcadores activos; no autoriza limpieza global.

`historico-inicial/` conserva dos expectativas409 que recibieron400 y el typecheck inicial sin responseMode en TS. Se corrigieron código/contrato; no se reemplazan esos resultados por éxitos. `antes-expiracion-en-alta/` conserva el corte76 PASS anterior a extender las comprobaciones. [Registro de saneamiento](saneamiento-registros.json): únicamente patrones de credenciales de registros generados propios, sin imprimir valores ni modificar resultados.

El primer lanzamiento del postflight posterior a permisos construyó el classpath desde la raíz en vez de `backend` y no llegó a conectar a PostgreSQL. Se conserva su fallo de arranque; se corrigió el directorio de ejecución y la lectura final pasó, sin modificar configuración privada ni datos.

Pendientes obligatorios L-04: márgenes por tipo y candidatos, carreras de reservas con márgenes, separación servicio/oferta/reasignación, cambios de conductor/unidad/horario con aceptación nueva, incertidumbre/integración CRM/Flutter y recorrido conectado/retorno/cierre. CI01/02 no bastan para cerrar el lote. Piloto bloqueado, L-05 no iniciada.
