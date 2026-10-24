# L-03 — Evidencia final V32

**2026-10-19, America/Lima.** [Resultado estructurado](resultado.json), [matriz](../matriz-final-funcional-local-2026-10-19.md), [acta](../../../actas/L-03-cierre-funcional-local-2026-10-19.md). Alcance FUNCIONAL_LOCAL_PILOTO_BLOQUEADO, sin iniciar L-04.

## Servidor: implementación final

Desde `backend`, ejecución dirigida final con salida0 comprobada:

```powershell
mvn.cmd --offline -q '-Dtest=MaintenanceIntegrationTest,DeclaredAvailabilityIntegrationTest#declaration*,RouteAssignmentIntegrationTest#route*,GroupMembershipIntegrationTest,RouteCatalogIntegrationTest,TerminalCorroborationPolicyTest,L03MaintenanceSchedulingConcurrencyTest,L03GroupSnapshotConcurrencyTest,L02TemporalConcurrencyTest' test
```

Log saneado [l03-native-acceptance-final-v32.log](final-97/l03-native-acceptance-final-v32.log). Nueve XML de `final-97/`, **97 distintos, 0 fallos/errores/omitidos**:

| Suite | Casos | Alcance |
|---|---:|---|
| MaintenanceIntegrationTest |54|44 regresiones heredadas de elegibilidad más10 de mantenimiento/flota.|
| DeclaredAvailabilityIntegrationTest |8|Declaración, ocupación, tenant/rol, compromisos y login/logout.|
| RouteAssignmentIntegrationTest |9|Captura/cambio/aceptación, consulta propia, declaración final, idempotencia, rollback y caché.|
| GroupMembershipIntegrationTest |9|Membresías, recursos, permisos y retirada con compromisos.|
| RouteCatalogIntegrationTest |7|Orden mínimo, versiones inmutables, identidad de operación, tenant y retiro.|
| TerminalCorroborationPolicyTest |4|Geometría/edad/precisión/bordes y datos desconocidos.|
| L03MaintenanceSchedulingConcurrencyTest |2|Serialización programación/mantenimiento, dos conexiones PG.|
| L03GroupSnapshotConcurrencyTest |2|Retirada/snapshot, dos conexiones PG.|
| L02TemporalConcurrencyTest |2|Exclusión de períodos en ambos sentidos, dos conexiones PG.|

Los87 casos HTTP usan Spring/MockMvc y PostgreSQL reales; no son procesos HTTP TCP separados. Se hereda `L02PostgresIntegrationTest`, con rollback y gates activos; nunca `NativePostgresIntegrationTest` ni TRUNCATE. Las seis carreras usan recursos técnicos sin AppUser y retirada exacta. El logout comprueba revocación en PG y la petición siguiente en contexto de persistencia nuevo; el primer fallo de caché del fixture se conserva como antecedente.

Después se corrigió exclusivamente la precedencia del nivel de registro de los contextos de prueba, para evitar DEBUG de DTO/tokens generados. Comprobación dirigida exit0:

```powershell
mvn.cmd --offline -q '-Dtest=DeclaredAvailabilityIntegrationTest#declaration*,RouteCatalogIntegrationTest#createsAnOrderedRouteAndIdempotentRetryKeepsTheSameImmutableVersion' test
```

La reejecución de declaración restaura su XML completo de8; el catálogo final de7 proviene de la suite97, y la guarda separada ejecuta1 de esos siete. **No se suman reejecuciones al97**. [Log de guarda](guarda-registros/l03-declaration-final-and-log-guard.log), [XML catálogo de guarda](guarda-registros/TEST-pe.rutafija.routing.RouteCatalogIntegrationTest.xml).

Empaquetado **posterior a todas las correcciones de código principal**: `mvn.cmd --offline -q -DskipTests package`, exit0. [Log](l03-backend-package-final.log), [artefactos/huellas](../../../../artifacts/L-03-2026-10-19/manifest.json). El modo silencioso puede producir un log vacío por éxito.

## Clientes

Desde `frontend`: `npm.cmd run typecheck`; `npm.cmd run test:ci` limitado a `groups.page.spec.ts`, `routes.page.spec.ts`, `maintenance.page.spec.ts`, `assignment-route-panel.spec.ts`; `npm.cmd run build:development`. Todos correctos. **20 componentes**:5grupos+6rutas+5mantenimiento+4panel. [Tests](l03-crm-acceptance-final.log), [typecheck](l03-crm-typecheck-final.log), [build](l03-crm-build.log). Dobles de API, no equivalencia con navegador real.

Desde `mobile`: pruebas dirigidas de `mobile_driver_repository_test.dart`, `mobile_assignment_repository_test.dart`, `mobile_route_models_test.dart`, `assigned_route_widget_test.dart` y `ruta_fija_app_test.dart`: **28 PASS**. `flutter analyze --no-pub` sin incidencias, APK debug correcto. [Tests](l03-mobile-acceptance-final.log), [analyze](l03-mobile-analyze-clean.log), [APK](l03-mobile-build.log). Widget corregido para desplazarse hasta el botón, sin cambiar validaciones. Advertencia SDK XML no impidió el APK; no dispositivo físico.

## Evidencia conectada y límites

[Caso único](../conectado-2e734706-f7ef-4947-b940-1f64fd13f86a/resultado.json): **PASS_REAL_BROWSER_HTTP_PG_AND_FLUTTER_ROLE_REJECTION**. Angular en Edge headless→backendTCP→PG: ruta mínima creada/versionada, preview y preventivo futuro creado/cancelado. Repositorio Flutter de producción→TCP real: ADMIN rechazado403 en endpoint móvil. No demuestra objeto ajeno por sí solo ni consulta móvil positiva TCP. Esta última está cubierta por backend/PG y modelos/widgets separados. No se declara intervención humana nueva, participante real ni Android físico.

Este smoke se ejecutó antes del último endurecimiento de locks de flota/refresh de Driver; la suite97 final sí cubre ese delta. No se repitió el caso cerrado ni se reabrió el CONDUCTOR L-02 para fabricar un recorrido móvil adicional.

[Postflight selectivo final](postflight-selectivo-final.json): recursos/historial conservados, sesiones revocadas, marcador ausente y lock disponible. [Flyway final](post-suite-solo-lectura.json): V32 válido, ledger1/3/1, dos aceptaciones y hashes previos conservados. [Ocho inmutables](inmutables-preservados.json): final3, términos, CLI y actas/ensayos anteriores sin cambios.

## Historia de fallos y saneamiento

Incrementos anteriores retienen precondición histórica V28, lock propio del preflight V31 cancelado sin migración/repair, errores de fixture/expectativas, mapeo SQLCHAR/JPA corregido sin editarV32 y scroll de widget. No se cambian esas ejecuciones por éxitos retrospectivos. [Journal de saneamiento](../saneamiento-registros-generados.json): se retiraron exclusivamente valores/fracciones de credenciales generadas en logs/XML propios L-03; no campos de resultados. Una primera pasada se interrumpió por codificación cp1252 tras sanear algunos archivos, sin journal completo de sus hashes originales; se declara esa limitación. Las pasadas posteriores preparan todos los cambios y registran hashes antes de escribir. Manifiestos finales corresponden a los bytes saneados, no a una supuesta copia cruda.

Logs PowerShell pueden usar UTF16 BOM o cp1252; XML son UTF8. JSON finales se normalizaron después del aviso de Commons Logging, sin incluir secretos. La ausencia de patrones de JWT/DTO se comprobó sin imprimir valores; no equivale a una certificación forense universal.

[Fuentes por huella](../fuentes-cierre.sha256), [manifiesto final](../manifest-cierre.sha256) y [retorno seguro](../../../operacion/L-03-validacion-y-retorno.md). S/0, sin SMTP externo, Git/GitHub, offline, despliegue, claves definitivas, desarrollo/piloto o regeneración de final3.
