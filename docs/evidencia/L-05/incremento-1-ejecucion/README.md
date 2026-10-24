# L05.1 — Inicio, tramos originales y frontera GPS

**PASS del incremento; L-05 EN_CURSO**, 2026-10-19 America/Lima. [Resultado](resultado.json), [casos y XML saneados](casos.json), [preservación](preservacion.json), [API0.1.0](../../../contratos/L-05-ejecucion-API-v0.1.0.md), [matriz](../matriz-L05.md).

## Acreditado

- Inicio exclusivamente propio/aceptado, consentimiento persistido y declaración explícita de permiso, ubicación habilitada y primer plano. Ninguna coordenada previa al inicio. Horas reales del servidor; replay idéntico no crea otro tramo y contexto distinto entra en conflicto.
- V36 crea tramos por servicio/oferta/conductor/unidad, con índices para un tramo abierto por cada recurso y originales inmutables. Dos pruebas SQL negativas con savepoints conservan el fixture: no edición de inicio, reapertura ni eliminación. Esas pruebas no son una carrera de dos transacciones.
- Ruta finalizada cierra el tramo propio y retira snapshot después de la corroboración. ADMIN ya no puede declarar cumplimiento, tampoco en alias heredado; endpoints/datos se conservan. Revocar consentimiento no impide un cierre seguro y no inventa GPS.
- Captura bloqueada en DISPONIBLE, segundo plano y antes/futuro del tramo. Snapshot ligado a su ejecución; cierre no usa otro servicio. Consentimiento/retiro/punto usan el mismo lock y recargan conductor. Auditoría y WebSocket no copian coordenadas.
- Flutter obtiene prueba OS antes del inicio sin obtener un punto; consentimiento explícito separado sin captura, denegación sin HTTP/cola, y transición a segundo plano impide enviar el punto. CRM no ofrece completar por el conductor.

## Ejecuciones

**57 casos backend distintos:**10 ServiceExecution,20 ScheduledService,14 OfferAcceptance,9 RouteAssignment y4 TerminalCorroborationPolicy. Son filtros/servicios Spring con PostgreSQL nativo y rollback, salvo cuatro de geometría pura. Se revisaron las fixtures positivas de inicio para otorgar consentimiento **sintético** por su API normal y aportar el contexto OS; no se debilitaron controles ni se borraron pruebas históricas. La regresión de documentos/aceptación/márgenes se conserva y no se contabilizan reejecuciones como casos nuevos.

**18 Flutter distintos** (10 repositorio,5 pantalla ubicación,3 gate OS), analyze final sin incidencias; CRM11/typecheck/build de desarrollo y backend package correctos. [JAR incremental](../../../../artifacts/L-05-incremento-1-2026-10-19/manifest.json). No APK nuevo, navegador/TCP conectado nuevo, intervención humana o dispositivo físico.

Desde backend, comandos principales:

```powershell
mvn.cmd --offline -q -DskipTests test-compile
# L05DatabasePreflight --migrate-test-v36, con classpath local y configuración privada existente
mvn.cmd --offline -q '-Dtest=ServiceExecutionIntegrationTest#execution*,ScheduledServiceIntegrationTest#service*,RouteAssignmentIntegrationTest#route*,OfferAcceptanceIntegrationTest#offer*' test
mvn.cmd --offline -q '-Dtest=ServiceExecutionIntegrationTest#execution*,TerminalCorroborationPolicyTest' test
mvn.cmd --offline -q -DskipTests package
# L05DatabasePreflight --validate-test
```

Flutter: pruebas de los tres archivos location gate/pantalla/assignment repository, y repetición solo de repositorio tras ajustar sintaxis del mapa para el analizador. `flutter analyze --no-pub` final: sin incidencias. CRM: `npm.cmd run test:ci -- --include='src/app/features/assignments/assignments.page.spec.ts'`, typecheck y build:development.

Primeras ejecuciones fallidas conservadas: el rechazo ADMIN devolvía400 en vez de409 (corregido); widgets requerían simular lifecycle/resumed y esperar el snackbar que cubría el botón, sin suprimir assertions; el analizador señaló una recomendación de sintaxis, corregida sin desactivar lints. XML archivados sin properties ni streams de configuración; no se conservan secretos.

## Preservación y límites

Solo `ruta_fija_test`, runtime rf_test y migrador explícito rf_migrator. V35→V36 con Flyway normal, sin repair/clean/override. Postflight válido; ledger **1/3/2**, aceptaciones2, mismos hashes de cuentas/servicios/ofertas/historia y cero tramos persistidos tras rollback. Cero cuentas persistentes nuevas y sin reapertura de casos cerrados. Los161 archivos sellados L-04 siguen idénticos; cuatro archivos de planificación evolucionan legítimamente y conservan respaldo anterior. Doce referencias inmutables, incluidos final3/términos/CLI/actas y ensayos, verificadas sin regeneración.

**Pendiente:** interrupción/correcciones/relevo, cierre terminal con observación/aviso y punto puntual, historial/purga/acceso≤7d, captura30s, integración/recorrido conectado, concurrencia y retorno final. El permiso es declaración del cliente, no atestación hardware. Calibración/batería/dispositivo L-08 y requisitos reales del piloto continúan pendientes; no se habilita piloto ni L-06. Máximo siete días también para puntos incidentales.
