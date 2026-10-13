# P-00R — Plan de consolidación previo a la fase 3

Fecha de referencia: **2026-10-12, America/Lima**.  
Repositorio revisado: `D:\UTP\CICLO X UTP\INTEGRADOR I\APF1\solucion_1_ruta_fija`.  
Tipo de trabajo: diagnóstico estático y propuesta de conservación/integración.  
**Estado: preparación pendiente de ejecución; la fase 3 no queda iniciada ni aprobada.**

## 1. Diagnóstico y alcance

La copia local contiene una implementación que **no puede reproducirse con un clon de HEAD**: hay cambios rastreados sin commit y archivos fuente, migraciones, pruebas y automatización sin seguimiento. El riesgo principal es confirmar o trasladar solo una parte y dejar contratos incompletos entre PostgreSQL, backend, CRM y Flutter.

El mantenimiento de `main` local es aprovechable y debe integrarse después de resguardar y capturar el conjunto local. Sus deltas no modifican migraciones ni servicios de negocio. Aun así, hay solapamiento en el README y en el manifiesto npm, y la actualización de dependencias requiere una validación nueva.

También se comprobaron problemas concretos en lanzadores y scripts históricos. Se deben conservar para mantener trazabilidad, pero no constituyen hoy una receta de reproducción aprobada.

**Único archivo creado por P-00R:** `docs/fase3/plan-consolidacion_pre_fase_3.md`. No se modificaron código funcional, configuración, índice Git ni BD. No se crearon ramas, commits, worktrees adicionales, agentes ni herramientas. No se ejecutaron fetch, pull, integración, stash, reset, clean, preparación de archivos, servicios, migraciones, compilaciones ni pruebas.

Este plan no incorpora el plan, agentes o documentos futuros de fase 3. Los documentos antiguos llamados `fase-3-*` y las referencias F3.x pertenecen a hitos anteriores del repositorio; su existencia no demuestra que la nueva fase de consolidación esté aplicada.

## 2. Estado Git comprobado

| Elemento | Resultado de la revisión actual |
|---|---|
| Raíz del árbol Git | `D:/UTP/CICLO X UTP/INTEGRADOR I/APF1/solucion_1_ruta_fija`; coincide con la raíz esperada. |
| Rama activa | `etapa-2/f02-postgresql-local`. |
| HEAD completo | `087202418bafb220749c2b1558ce173e114892b5`. |
| Archivos rastreados modificados | **76**, todos con estado ` M`; sin cambios preparados en el índice. |
| Archivos sin seguimiento, expandidos | **63** con `status -uall`. |
| Entradas sin seguimiento, agrupadas por Git | **50** con `status -unormal`; incluyen carpetas. |
| Diferencia rastreada contra HEAD | **2.503 inserciones y 275 eliminaciones**, sin contar contenido nuevo sin seguimiento. |
| Worktrees | **Uno**: la raíz actual, en la rama y HEAD indicados. |
| `main` local | `36173545a5f48f2fad41a5482ec80764b98764b6`. |
| `origin/main` local | Mismo objeto `36173545a5f48f2fad41a5482ec80764b98764b6`; es una referencia almacenada, sin actualización por red. |
| Ancestro común HEAD/main | `8e118e065b60b8b37ed54231faa360d79a5aeae5`. |
| Divergencia desde el ancestro | **1 commit exclusivo de HEAD y 5 exclusivos de main**; no corresponde un avance directo de la rama actual a main. |
| Otra rama local | `baseline/fase-5-pre-etapa-2-20260918` en `c961ac9ba3c71360b95bd120b1b43f445577781b`; no equivale a un respaldo de los cambios actuales. |
| Instrucciones locales | No se encontró `AGENTS.md` aplicable en el repositorio ni en los directorios ascendentes revisados. |
| Índice al inicio, SHA-256 | `03c8d907ff28d62c7c4d42572463ec052f575011ba1d18f6b0e22d66c6d89027`. |

La revisión anterior consignaba 49 entradas sin seguimiento **antes de crear su propio informe**. Hoy ese informe, `docs/REVISION_GENERAL_2026-10-12.md`, es una entrada sin seguimiento y el recuento es 50. Las dos formas de contar no son intercambiables: 50 entradas agrupadas corresponden a 63 archivos.

Una nueva rama solo añade una referencia a un commit. Los cambios sin commit permanecen en el árbol de trabajo mientras Git lo permita; los archivos sin seguimiento tampoco pasan a formar parte de la rama. Un nuevo worktree creado desde HEAD recibiría únicamente el contenido confirmado en HEAD. **El resguardo y el checkpoint completo deben preceder al traslado a otro worktree.**

## 3. Inventario agrupado y selección propuesta

**Leyenda:** `M` = archivo rastreado modificado solo en el árbol de trabajo; `??` = archivo sin seguimiento. El inventario corresponde al estado inicial, antes de crear este plan. Las rutas son relativas a la raíz Git y se enumeran por archivo, no por carpeta.

| Clasificación principal | M | ?? | Total | Destino propuesto |
|---|---:|---:|---:|---|
| Código backend | 14 | 9 | 23 | Incluir tras revisión |
| Código CRM Angular | 4 | 0 | 4 | Incluir tras revisión |
| Código Flutter | 22 | 9 | 31 | Incluir tras revisión |
| Migraciones Flyway | 0 | 2 | 2 | Incluir tras revisión |
| Pruebas backend | 1 | 1 | 2 | Incluir tras revisión |
| Pruebas Flutter y soporte | 5 | 3 | 8 | Incluir tras revisión |
| Scripts PowerShell/Java y lanzadores BAT | 14 | 14 | 28 | Incluir tras revisión |
| Documentación, decisiones y evidencias | 7 | 16 | 23 | Incluir tras revisión |
| Configuración versionable y lockfile móvil | 9 | 0 | 9 | Incluir tras revisión |
| Generados: caché Angular en la raíz | 0 | 4 | 4 | Excluir del checkpoint |
| Generados: exportaciones y árbol de archivos | 0 | 5 | 5 | Excluir del checkpoint |
| **Total inicial** | **76** | **63** | **139** | **130 candidatos; 9 excluidos** |

La clasificación principal evita contar un archivo dos veces: los runners Java de scripts se agrupan como automatización, los `README.md` como documentación y `pubspec.lock` como configuración reproducible. Los archivos privados ignorados se describen aparte; no están dentro de los 139.

### 3.1. Código backend (23)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  backend/src/main/java/pe/rutafija/fleet/infrastructure/DriverRepository.java
M  backend/src/main/java/pe/rutafija/operation/api/MobileIncidentController.java
M  backend/src/main/java/pe/rutafija/operation/api/dto/IncidentResponse.java
M  backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileAvailabilityResponse.java
M  backend/src/main/java/pe/rutafija/operation/application/MobileAssignmentService.java
M  backend/src/main/java/pe/rutafija/operation/application/MobileCommandProcessor.java
M  backend/src/main/java/pe/rutafija/operation/application/MobileDriverService.java
M  backend/src/main/java/pe/rutafija/operation/application/MobileIncidentService.java
M  backend/src/main/java/pe/rutafija/operation/domain/Incident.java
M  backend/src/main/java/pe/rutafija/operation/domain/IncidentStatus.java
M  backend/src/main/java/pe/rutafija/operation/domain/MobileCommandReceipt.java
M  backend/src/main/java/pe/rutafija/operation/domain/MobileCommandType.java
M  backend/src/main/java/pe/rutafija/operation/infrastructure/IncidentRepository.java
M  backend/src/main/java/pe/rutafija/operation/infrastructure/OperationReportRepository.java
?? backend/src/main/java/pe/rutafija/operation/api/MobileOfflineSyncController.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileIncidentCloseRequest.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventRequest.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventResult.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventStatus.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventType.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventsRequest.java
?? backend/src/main/java/pe/rutafija/operation/api/dto/mobile/MobileSyncEventsResponse.java
?? backend/src/main/java/pe/rutafija/operation/application/MobileOfflineSyncService.java
```

### 3.2. Código CRM Angular (4)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  frontend/src/app/core/operations/operations.models.ts
M  frontend/src/app/features/incidents/incidents.page.html
M  frontend/src/app/features/incidents/incidents.page.ts
M  frontend/src/app/features/reports/reports.page.ts
```

### 3.3. Código Flutter (31)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  mobile/lib/app/ruta_fija_app.dart
M  mobile/lib/app/ruta_fija_dependencies.dart
M  mobile/lib/app/ruta_fija_router.dart
M  mobile/lib/core/network/mobile_api_client.dart
M  mobile/lib/core/presentation/mobile_error_message.dart
M  mobile/lib/core/session/mobile_session_controller.dart
M  mobile/lib/features/assignments/data/mobile_assignment_command_store.dart
M  mobile/lib/features/assignments/data/mobile_assignment_repository.dart
M  mobile/lib/features/assignments/domain/mobile_assignment_models.dart
M  mobile/lib/features/assignments/presentation/assignment_detail_page.dart
M  mobile/lib/features/auth/presentation/mobile_login_page.dart
M  mobile/lib/features/bootstrap/presentation/conductor_navigation_shell.dart
M  mobile/lib/features/incidents/data/mobile_incident_repository.dart
M  mobile/lib/features/incidents/domain/mobile_incident_models.dart
M  mobile/lib/features/incidents/presentation/incident_detail_page.dart
M  mobile/lib/features/incidents/presentation/incidents_page.dart
M  mobile/lib/features/incidents/presentation/mobile_incident_presentation.dart
M  mobile/lib/features/profile/data/mobile_driver_repository.dart
M  mobile/lib/features/profile/domain/mobile_driver_models.dart
M  mobile/lib/features/profile/presentation/availability_page.dart
M  mobile/lib/features/profile/presentation/conductor_home_page.dart
M  mobile/lib/features/profile/presentation/profile_page.dart
?? mobile/lib/core/offline/mobile_offline.dart
?? mobile/lib/core/offline/mobile_offline_event.dart
?? mobile/lib/core/offline/mobile_offline_outbox.dart
?? mobile/lib/core/offline/mobile_offline_queue_store.dart
?? mobile/lib/core/offline/mobile_offline_sync_controller.dart
?? mobile/lib/features/location/data/geolocator_device_location_gateway.dart
?? mobile/lib/features/location/domain/mobile_foreground_location.dart
?? mobile/lib/features/location/presentation/foreground_location_page.dart
?? mobile/lib/features/sync/presentation/pending_sync_page.dart
```

### 3.4. Migraciones Flyway (2)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
?? backend/src/main/resources/db/migration/V11__driver_closed_mobile_incidents.sql
?? backend/src/main/resources/db/migration/V12__mobile_offline_availability_receipts.sql
```

### 3.5. Pruebas backend (2)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  backend/src/test/java/pe/rutafija/operation/api/MobileOperationFlowIT.java
?? backend/src/test/java/pe/rutafija/operation/api/MobileOfflineSyncIT.java
```

### 3.6. Pruebas Flutter y soporte (8)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  mobile/test/app/ruta_fija_app_test.dart
M  mobile/test/core/session/mobile_session_controller_test.dart
M  mobile/test/features/incidents/mobile_incident_repository_test.dart
M  mobile/test/features/profile/mobile_driver_repository_test.dart
M  mobile/test/support/mobile_fakes.dart
?? mobile/test/core/offline/mobile_offline_outbox_test.dart
?? mobile/test/core/offline/mobile_offline_sync_controller_test.dart
?? mobile/test/features/location/foreground_location_page_test.dart
```

### 3.7. Scripts PowerShell/Java y lanzadores BAT (28)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  finalizar_ruta_fija.bat
M  iniciar_ruta_fija.bat
M  scripts/Initialize-RutaFijaNativeConfig.ps1
M  scripts/Invoke-RutaFijaNativeRuntime.ps1
M  scripts/Invoke-RutaFijaNativeTest.ps1
M  scripts/Invoke-RutaFijaRealtimeProxySmoke.ps1
M  scripts/RunRutaFijaFlyway.java
M  scripts/Test-RutaFijaF33MobileOperations.ps1
M  scripts/Test-RutaFijaF34ContractReports.ps1
M  scripts/Test-RutaFijaF41FlutterFoundation.ps1
M  scripts/Test-RutaFijaF42MobileSessionProfileAvailability.ps1
M  scripts/Test-RutaFijaF43MobileAssignments.ps1
M  scripts/Test-RutaFijaF44MobileIncidentsAnnouncements.ps1
M  scripts/Test-RutaFijaNativePreflight.ps1
?? finalizar_demo_temporal.bat
?? iniciar_demo_temporal.bat
?? scripts/Install-RutaFijaCloudflared.ps1
?? scripts/Invoke-RutaFijaF44DriverIncidentClosureMigration.ps1
?? scripts/Invoke-RutaFijaM8V12Migration.ps1
?? scripts/Invoke-RutaFijaQuickDemo.ps1
?? scripts/RunRutaFijaF44Flyway.java
?? scripts/RunRutaFijaM8Flyway.java
?? scripts/Set-RutaFijaPostgresqlService.ps1
?? scripts/Test-RutaFijaF51IntegralRegression.ps1
?? scripts/Test-RutaFijaF52QuickDemo.ps1
?? scripts/Test-RutaFijaM7ForegroundLocation.ps1
?? scripts/Test-RutaFijaM8OfflineMinimum.ps1
?? scripts/Test-RutaFijaM9QualityEvidence.ps1
```

### 3.8. Documentación, decisiones y evidencias (23)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  README.md
M  backend/README.md
M  docs/Uso del Sistema.md
M  docs/contrato-api-f3-4-movil.md
M  docs/ejecucion-f4-4-incidencias-comunicados.md
M  frontend/README.md
M  mobile/README.md
?? docs/REVISION_GENERAL_2026-10-12.md
?? docs/ajuste-f4-4-cierre-incidencias-conductor.md
?? docs/decisiones/ADR-007-ubicacion-foreground-m7.md
?? docs/decisiones/ADR-008-offline-minimo-m8.md
?? docs/decisiones/ADR-009-calidad-evidencia-m9.md
?? docs/ejecucion-f5-1-regresion-integral.md
?? docs/ejecucion-f5-2-demo-temporal.md
?? docs/ejecucion-m7-ubicacion-foreground.md
?? docs/ejecucion-m8-offline-minimo.md
?? docs/ejecucion-m9-calidad-evidencia.md
?? docs/evidencia-f4-4-1-cierre-incidencia-conductor-2026-09-24.md
?? docs/evidencia-f5-1-regresion-integral-2026-09-30.md
?? docs/evidencia-f5-2-demo-temporal-2026-09-30.md
?? docs/evidencia-m7-ubicacion-foreground-2026-09-24.md
?? docs/evidencia-m8-offline-minimo-2026-09-26.md
?? docs/evidencia-m9-calidad-evidencia-2026-09-26.md
```

### 3.9. Configuración versionable y lockfile móvil (9)

**Incluir como conjunto revisado en el checkpoint propuesto.** La inclusión no certifica funcionamiento ni elimina los bloqueos descritos más adelante.

```text
M  .gitignore
M  .vscode/tasks.json
M  backend/ruta-fija-native.env.example
M  frontend/angular.json
M  frontend/package.json
M  mobile/android/app/src/main/AndroidManifest.xml
M  mobile/android/gradle.properties
M  mobile/pubspec.lock
M  mobile/pubspec.yaml
```

### 3.10. Generados: caché Angular en la raíz (4)

**Excluir del checkpoint.** Conservar externamente solo cuando tenga valor histórico o requiera revisión privada; no borrar en P-00R.

```text
?? .angular/cache/22.1.6/ruta-fija-crm/.tsbuildinfo
?? .angular/cache/22.1.6/ruta-fija-crm/angular-compiler.db
?? .angular/cache/22.1.6/ruta-fija-crm/angular-compiler.db-lock
?? .angular/cache/22.1.6/ruta-fija-crm/vitest/da39a3ee5e6b4b0d3255bfef95601890afd80709/results.json
```

### 3.11. Generados: exportaciones y árbol de archivos (5)

**Excluir del checkpoint.** Conservar externamente solo cuando tenga valor histórico o requiera revisión privada; no borrar en P-00R.

```text
?? archivos de texto/codigo_backend.txt
?? archivos de texto/codigo_front.txt
?? archivos de texto/codigo_persistencia_postgresql.txt
?? archivos de texto/código_extra.txt
?? estructura.txt
```

### 3.12. Privados e ignorados: conservación separada

| Rutas o grupos comprobados por nombre/regla Git | Clasificación | Tratamiento propuesto |
|---|---|---|
| `.env`; `backend/.local/ruta-fija-native.env`; `backend/.local/ruta-fija-bootstrap.env` | Configuración privada, ignorada | Resguardo independiente con acceso restringido; excluir de Git. No se muestran valores. |
| `backups/` | Respaldos de BD y manifiestos potencialmente sensibles | Conservar en resguardo privado. Git y su bundle no preservan estos datos. No se ha probado ninguna restauración actual. |
| `.runtime/` | Logs y estado local de ejecución | Excluir del checkpoint; resguardar solo evidencia seleccionada y revisada. No trasladar PID ni estados de procesos como configuración reproducible. |
| `.tools/` | Herramientas locales, incluido el recurso de demo | Excluir binarios. Conservar el script de obtención y validación de la herramienta. La herramienta no se instaló ni ejecutó en esta revisión. |
| `.vscode/settings.json`; `mobile/android/local.properties`; `*.iml`; `mobile/.idea/` | Configuración del equipo/IDE y rutas de SDK | Excluir. Documentar requisitos mediante ejemplos sin rutas privadas ni credenciales reales. |
| `backend/target/`; caches Maven; `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`; `mobile/.dart_tool/`, `mobile/build/`, `mobile/android/.gradle/` | Compilados, dependencias descargadas, cachés e informes anteriores | Excluir. Regenerar posteriormente mediante la receta validada. Los informes anteriores son evidencia histórica. |
| `mobile/.flutter-plugins-dependencies`; Java generado y wrappers Gradle ignorados bajo `mobile/android/` | Artefactos Android/Flutter ignorados | No agregarlos indiscriminadamente. Verificar en una copia limpia que la receta documentada genera o aporta los elementos necesarios para compilar Android. |
| `.github/modernize/` | Salida local ignorada de herramientas | Excluir; revisar separadamente solo si contiene decisiones que deban documentarse. |

`git check-ignore` confirmó la exclusión de los archivos privados, runtime, tools y backups. **La caché `.angular/` de la raíz sigue sin estar ignorada**, a diferencia de `frontend/.angular/`: el cambio local de `.gitignore` añade `.tools/`, pero no resuelve esa caché raíz. Proponer después una regla precisa para ella y para exportaciones locales, sin ocultar fuentes nuevas.

Los cuatro archivos en `archivos de texto/` duplican código y contienen términos de autenticación; tres también referencian rutas a configuración privada. Es una señal para revisión confidencial, no una afirmación de que se haya comprobado una filtración de secretos. `estructura.txt` es un listado de unas 75.000 líneas, no una fuente funcional. Ninguno se necesita para compilar o reconstruir la implementación.

La plantilla `backend/ruta-fija-native.env.example` contiene marcadores instructivos en sus campos de contraseña/clave y es candidata a versionado. Se revisó la naturaleza de esos campos sin reproducir sus valores. Las otras plantillas existentes se conservan desde HEAD; deben seguir usando marcadores. La documentación F5.2 contiene referencias a túneles temporales: revisar su conveniencia y datos de demo antes de confirmar, sin trasladarlos como endpoints permanentes.

## 4. Conjuntos que deben conservarse juntos

El conjunto de reproducción es **HEAD `0872024` completo + los 130 archivos candidatos del inventario**, sujeto a revisión de contenido sensible. Este plan añade un candidato documental, para **131 candidatos después de P-00R**. No basta con copiar exclusivamente archivos modificados a una carpeta vacía: el código ya confirmado proporciona el resto de entidades, contratos, configuración, componentes y pruebas.

### 4.1. Cierre de incidencias por conductor: V11

Conservar en la misma captura funcional:

- `V11__driver_closed_mobile_incidents.sql`.
- `MobileIncidentCloseRequest.java`, `MobileIncidentController.java` y `MobileIncidentService.java`.
- `Incident.java`, `IncidentStatus.java`, `IncidentResponse.java`, `IncidentRepository.java` y `OperationReportRepository.java`.
- Modelos y pantallas de incidencias del CRM y Flutter enumerados en el inventario.
- El cambio de `MobileOperationFlowIT.java`, la prueba móvil de repositorio, el auditor F4.4, el aplicador/runner V11 y la documentación del ajuste.

**Dependencia comprobada:** el controlador rastreado importa el DTO nuevo en la línea 22; el servicio lo importa en la línea 10. La entidad incorpora `driver_closed_by` y `driver_closed_at`, y el enum/contratos incorporan `CLOSED_BY_DRIVER`. La migración aporta esas columnas, FK, restricciones e índice. El CRM y Flutter leen los campos de cierre.

Omitir el DTO deja referencias Java sin archivo fuente. Omitir V11 deja una entidad/contrato que exige elementos físicos ausentes si la BD solo llega a V10. Conservar SQL y código no demuestra que V11 esté aplicada en la BD actual.

### 4.2. Offline mínimo: V12 y contrato de sincronización

Conservar conjuntamente:

- `V12__mobile_offline_availability_receipts.sql`.
- `MobileOfflineSyncController.java`, `MobileOfflineSyncService.java` y los seis DTO/enum `MobileSyncEvent*` / `MobileSyncEvents*` enumerados.
- `MobileDriverService.java`, `MobileAvailabilityResponse.java`, `DriverRepository.java`, `MobileCommandType.java`, `MobileCommandReceipt.java`, `MobileCommandProcessor.java` y `MobileAssignmentService.java`.
- Los cinco archivos `mobile/lib/core/offline/`, `pending_sync_page.dart` y sus conexiones en dependencias, router, sesión, repositorios/modelos/pantallas de disponibilidad y asignaciones.
- `MobileOfflineSyncIT.java`, las dos pruebas offline Flutter, fakes, pruebas de sesión/perfil/app y los scripts de validación/migración M8.
- Los cambios del bootstrap Flyway y verificación nativa que ahora exigen V1–V12; ADR-008, ejecución y evidencia M8.

**Dependencias comprobadas:** `MobileDriverService.java:17` importa `MobileSyncEventRequest` sin seguimiento. La firma de `MobileCommandProcessor.execute` incorpora un proveedor de estado de fallo y su llamada de asignaciones se actualiza: ambos lados deben mantenerse alineados. El controlador expone `POST /api/v1/mobile/sync/events`; Flutter usa `mobile/sync/events` desde el transporte nuevo.

V12 permite `AVAILABILITY_CHANGE` en el recibo y restringe `assignment_id`: nulo para disponibilidad y no nulo para comandos de asignación. V10 ya declara nullable esa FK; V12 no necesita eliminar una restricción NOT NULL.

El contrato admite disponibilidad, inicio y finalización, con un máximo de diez eventos por solicitud. Devuelve `APPLIED`, `ALREADY_APPLIED`, `CONFLICT` o `REJECTED`. La disponibilidad emplea el token de estado `updatedAt` y un bloqueo del conductor. La cola móvil está ligada a su propietario y se limpia al cerrar la sesión. Son propiedades observadas en código; su comportamiento concurrente, persistencia y aislamiento quedan pendientes de ejecución.

### 4.3. Ubicación foreground M7 y cliente móvil

Los tres archivos nuevos bajo `mobile/lib/features/location/` dependen de los cambios de inyección, navegación, perfil y pruebas. Deben viajar con:

- `mobile/pubspec.yaml` y `mobile/pubspec.lock` como pareja; ambos incluyen geolocator y el lock resuelve `14.0.3`.
- Permisos de ubicación de `AndroidManifest.xml`.
- `foreground_location_page_test.dart`, fakes, auditor M7 y ADR-007.
- El conjunto M8 cuando se conserve la inyección actual, pues las dependencias y el router conectan ambos módulos.

La implementación solicita una ubicación puntual en primer plano. No debe describirse como seguimiento GPS continuo. `gradle.properties` también cambia una opción de compilación Kotlin; conservarla para reconstruir el estado local y evaluar después su conveniencia y portabilidad.

### 4.4. Operación nativa, demo y evidencias

Las tareas nuevas de VS Code referencian scripts sin seguimiento. Los lanzadores habituales y de demo dependen de `Invoke-RutaFijaNativeRuntime.ps1`, `Invoke-RutaFijaQuickDemo.ps1` y el instalador/verificador de cloudflared. F5.1 referencia `Set-RutaFijaPostgresqlService.ps1`. No se deben confirmar las tareas o los lanzadores dejando fuera sus destinos.

Conservar las plantillas versionables, inicializador, proxy/puerto, scripts de pruebas y runners que esperan V1–V12. Las credenciales reales se entregan mediante configuración privada y no mediante el commit. Los scripts actuales fijan desarrollo a `solucion_ruta_fija_1` y pruebas a `ruta_fija_test`; no trasladar nombres de BD de planes anteriores sin comprobarlos.

Las evidencias F4.4/M7/M8/M9/F5 y la revisión anterior deben identificarse como registros fechados. Una palabra PASS en un documento o un informe de `target/` no certifica el árbol consolidado ni reemplaza la prueba desde una copia limpia.

### 4.5. Integridad de migraciones

V11 y V12 tienen bytes UTF-8 sin BOM y finales LF en esta copia. El resguardo debe conservarlos sin recodificación:

| Archivo | SHA-256 del archivo local |
|---|---|
| V11 | `d7003c519b27ada672aa587475cc8217d2d9c238d4b2185d416ac101ad9bf005` |
| V12 | `c0db4c94be12e132ab352351c0518c322c41c349c606ba9c85c200846a80344f` |

Estos hashes verifican bytes del archivo; **no son los checksums de Flyway**. La BD y su historial no se consultaron.

`core.autocrlf=true` está activo y `.gitattributes` fija LF explícitamente para V6–V8, sin reglas particulares para V11/V12. Registrar por separado la integridad del respaldo y la validación Flyway desde la revisión consolidada; no atribuir automáticamente un cambio de checksum Flyway al cambio de finales de línea. No reescribir ni renumerar migraciones ya aplicadas. Si hace falta ajustar atributos, proponerlo después como cambio revisable y comprobar los bytes resultantes.

## 5. Comparación con main local y propuesta de integración

### 5.1. Historia relevante

| Lado | Commit | Aporte observado |
|---|---|---|
| Rama actual | `0872024` | Incidencias y comunicados móviles F4.4; es la base confirmada de las extensiones locales. |
| main | `a690f13` | Estabilización de CI nativo y mantenimiento; reglas de colaboración, documentación, actualizaciones Angular/lock y CI Flutter. |
| main | `7e3a76e` | Maven Wrapper/distribución y JaCoCo. |
| main | `42d23a1` | GitHub Actions fijadas por SHA. |
| main | `17d03dc` | jsdom `30.1.1` y lock npm. |
| main | `3617354` | Registro documental de mantenimiento A1 y versiones mayores aplazadas. |

La comparación empleó el ancestro común para distinguir cambios de cada lado. Un diff directo HEAD→main presenta archivos F4.4 como eliminados porque main todavía no los contiene; **no demuestra que un commit de mantenimiento los haya borrado**. La integración debe conservar el commit exclusivo y sus archivos.

### 5.2. Mantenimiento candidato y riesgos de combinación

| Archivos de main | Diferencia | Tratamiento propuesto / verificación posterior |
|---|---|---|
| `backend/mvnw` | Mismo blob, modo Git `100644` en el índice actual frente a `100755` en main. CI lo invoca directamente en Linux. | Incorporar el modo ejecutable; comprobarlo en el árbol confirmado y en CI Linux. No alterar el índice en P-00R. |
| `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/mvnw.cmd` | Distribución Maven `3.9.15 → 3.10.0`, checksum de distribución y ajuste del lanzador CMD. | Integrar la familia de Wrapper completa, conservando su verificación; comprobar la distribución y compilación posteriormente. |
| `backend/pom.xml` | JaCoCo `0.8.13 → 0.8.15`. | Incorporar y verificar compilación/cobertura sin asumir equivalencia por ser mantenimiento. |
| `.github/workflows/ci.yml` | SHA de acciones, concurrencia, credenciales efímeras enmascaradas, permisos de objetos para pruebas, typecheck explícito y job Flutter. | Integrar y validar backend/CRM/Flutter desde el checkpoint completo; el workflow anterior solo tiene backend/CRM. |
| `.github/dependabot.yml`, `.github/CODEOWNERS`, `CONTRIBUTING.md`, `docs/mantenimiento-a1.md` | Gobierno de dependencias y colaboración. | Incorporar; comprobar que rutas/propietarios y reglas corresponden al repositorio real. |
| `frontend/package.json`, `frontend/package-lock.json` | Angular core `22.1.4 → 22.2.1`, CLI/build `22.1.6 → 22.2.1`, jsdom `27.1.0 → 30.1.1` y transitorias. | Integrar como pareja. Preservar el script local de arranque en 4300. jsdom cambia de versión mayor: exigir regresión, no asumir compatibilidad. |
| `README.md` | Reorganización de arranque, mantenimiento y entrega. | Conciliar manualmente los tres estados: ancestro, main y snapshot local; conservar instrucciones y enlaces F4.4/M7/M8/M9/F5 vigentes. |

Los manifiestos npm y lock actuales coinciden en sus dependencias raíz. El cambio local de `package.json` afecta al comando start, no a las versiones. La integración debe mantener esa coherencia: no aceptar las versiones de main dejando el lock anterior.

### 5.3. Diferencias funcionales y conflictos previstos

- **Solapamiento entre cambios confirmados desde el ancestro:** únicamente `README.md`.
- **Solapamiento entre mantenimiento de main y modificaciones rastreadas locales:** `README.md` y `frontend/package.json`.
- **Riesgos semánticos sin solapamiento de archivos:** documentación de main usa 4200 y los scripts/configuración locales usan 4300; CI Flutter exige conservar los nuevos módulos y lock; los validadores históricos de BD deben concordar con V12.
- **Extensiones funcionales locales:** cierre conductor V11, contrato offline V12 y recibos idempotentes, ubicación foreground, estado de sincronización/limpieza de sesión, accesibilidad y manejo de errores móvil, visualización de cierre en CRM/reportes y automatización de demo.
- **Mantenimiento exclusivo de main:** el delta desde el ancestro no introduce cambios en entidades, DTO, servicios de negocio ni migraciones SQL. Eso reduce el solapamiento, pero no acredita compatibilidad de ejecución.

No se ejecutó una integración ni una simulación de merge. Los conflictos anteriores son **previsiones estáticas**, no conflictos Git ya reproducidos.

**Ruta preferida:** después del resguardo y checkpoint completo, crear una rama de consolidación y un worktree separado desde el SHA del checkpoint. Integrar allí los cinco commits mediante una fusión de la referencia local fijada `36173545a5f48f2fad41a5482ec80764b98764b6`, preservando la historia de ambos lados. No sustituir archivos en bloque por la versión de main ni reconstruir solo algunos parches de CI/dependencias. Revisar los solapamientos manualmente y corregir los bloqueos de operación en cambios separados.

La raíz original debe permanecer disponible para comparación. Otro worktree no es un respaldo externo: comparte el repositorio Git. Antes de iniciar esa etapa, repetir estado, referencias y divergencia; si cambian, revisar el plan. Este paso no requiere fetch/pull.

## 6. Plan propuesto: orden, alcance, verificación y puertas

**Todo este apartado son acciones futuras. No se han ejecutado.** Un checkpoint de conservación puede registrar defectos conocidos como WIP; no debe etiquetarse como versión estable ni como fase aprobada.

### Paso 0 — Resguardar el estado sin depender de una rama nueva

1. Congelar temporalmente ediciones y tomar un manifiesto con raíz, HEAD, referencias, estado por archivo, tamaños, hashes y lista de excluidos.
2. Elegir un destino externo al repositorio y con acceso restringido. Comprobar espacio, rutas resueltas y ausencia de archivos omitidos. No considerar un directorio ignorado dentro de la misma raíz como la única copia.
3. Resguardar la historia y referencias mediante un bundle Git completo, y comprobar su lectura/validez. **El bundle solo cubre contenido confirmado.**
4. Copiar en bytes el árbol fuente rastreado y los 54 archivos nuevos candidatos, incluyendo V11/V12, DTO, servicios, pruebas, scripts y documentos. Añadir este plan. Registrar hashes. Un diff binario del trabajo rastreado puede servir como respaldo adicional, pero **no incluye archivos sin seguimiento**. En Windows, no recodificar esos bytes mediante Out-File o redirecciones de texto que cambien su encoding.
5. Guardar aparte la configuración privada y los respaldos existentes, con permisos restringidos. No copiar logs/PID como estado operativo restaurable. Las exportaciones de código con posible contenido privado solo deben ir a ese resguardo confidencial si se decide conservarlas.
6. Verificar en una carpeta separada que historia + fuentes + archivos nuevos permiten reconstruir la captura sin depender del árbol original.

**Incluye:** fuentes completas de HEAD, 130 candidatos iniciales y el plan; configuración/respaldos en almacenamiento privado separado.  
**Excluye del respaldo fuente reproducible:** cachés, dependencias descargadas, builds, binarios locales y exportaciones duplicadas.  
**Riesgo:** bundle o patch incompletos respecto del árbol local; copia que recodifique SQL; omisiones por rutas largas.  
**Puerta R0:** copia externa legible, manifiesto y hashes coincidentes, 54 fuentes/soportes nuevos presentes y configuración privada separada. No crear la rama de captura antes de satisfacer R0.

El listado de ignorados emitió una advertencia de ruta demasiado larga dentro de `mobile/build/`. Esa salida no acredita un inventario exhaustivo de artefactos ignorados. La copia de conservación debe tener un informe de omisiones; se pueden excluir expresamente los builds y caches, sin limpiar el original ni cambiar configuración global.

### Paso 1 — Checkpoint selectivo de conservación

1. Revisar los 130 candidatos y este plan; identificar credenciales, datos personales, endpoints efímeros y rutas del equipo. Sanear solo lo que corresponda en la etapa posterior autorizada, guardando el original en el resguardo privado.
2. Usar el inventario como lista explícita de inclusión. Después de R0, una eventual rama de captura puede partir de HEAD en el mismo árbol de trabajo, pero la protección real la aportan el resguardo y el commit completo.
3. Preparar exclusivamente las rutas aprobadas, mediante `git add --` con rutas concretas. No añadir la raíz ni directorios enteros susceptibles de incluir cachés/privados.
4. Revisar la lista y el diff del índice. Comprobar que hay V11/V12, los nueve Java nuevos, nueve Dart nuevos, pruebas y destinos de tareas/scripts. Confirmar que ninguna exclusión, archivo privado o binario entra.
5. Crear el checkpoint con una descripción que lo identifique como captura WIP, con defectos operativos pendientes. Comprobar el árbol del commit y sus diferencias respecto del manifiesto.

**Incluye propuesto:** 76 rastreados modificados + 54 nuevos útiles + este plan = **131 archivos candidatos**. Las rutas del inventario son la selección; si la revisión confidencial obliga a retirar un documento, registrar la exclusión y su copia privada.  
**Excluye:** los 9 generados del inventario y todos los privados/ignorados descritos. No usar adición forzada.  
**Verificaciones futuras:** estado del índice, diff preparado, control de whitespace, árbol del commit, imports/dependencias y ausencia de contenido sensible. No confundir esas revisiones con compilación.  
**Riesgo:** un checkpoint parcial guarda controladores sin DTO, entidades sin SQL, tareas sin scripts o router sin pantallas.  
**Puerta R1:** commit con el conjunto completo y manifiesto conciliado; los únicos residuos locales permitidos son exclusiones expresas. Debe quedar claro que el checkpoint no reproduce por sí mismo credenciales ni datos de PostgreSQL.

### Paso 2 — Integrar main local en una copia aislada

1. Crear la rama/worktree de consolidación **desde el checkpoint**, no desde `0872024` ni desde main sin la captura local.
2. Comprobar que las fuentes nuevas están en esa copia y que no se transportan secretos/cachés/PID desde la raíz original.
3. Integrar la referencia local de main fijada en este informe, conservando los cinco commits de mantenimiento y el commit exclusivo F4.4.
4. Conciliar README, package.json y lock. Mantener el puerto 4300 como configuración local de referencia, salvo una decisión explícita posterior que actualice todos sus consumidores.
5. Comprobar modo `100755` de mvnw, archivos de Wrapper, versiones de JaCoCo/dependencias y totalidad de CI Flutter. Revisar el diff final contra ambos padres y confirmar que no desaparece ningún módulo/SQL local.

**Verificación futura:** historia y ascendencia, integridad de archivos, referencias de tareas y configuración, coherencia de lockfiles y ausencia de archivos fuera de alcance.  
**Riesgo:** resolver README por sustitución completa, perder el start local o combinar versiones con un lock distinto.  
**Puerta R2:** historia integrada y revisión del árbol final sin referencias ausentes, conflictos pendientes ni pérdidas de los conjuntos funcionales. La compatibilidad de ejecución todavía queda pendiente.

### Paso 3 — Corregir la receta operativa y preparar validación

En cambios pequeños y revisables, después de la captura:

1. Reparar rutas y caracteres de control de ambos BAT de demo. Comprobar su contenido y después su invocación controlada, cuando esté autorizada la ejecución de demo.
2. Definir una receta actual V1–V12 para una BD vacía y una ruta de actualización por estado de origen. El aplicador histórico V11 no funciona sobre el árbol actual V1–V12; ajustarlo o identificarlo como histórico con una receta aislada correcta. No retirar V12 del checkpoint para eludir el control.
3. Alinear los guardas de parada con 8080/4300 y los consumidores de configuración/documentación. Confirmar que las tareas invocan la receta vigente.
4. Resolver la dependencia histórica de recuperación en F1.3/G2; diferenciar auditoría actual y evidencia de una copia temporal. No recrear esa BD por suposición.
5. Completar instrucciones para crear configuración privada desde plantillas, aislar pruebas y regenerar artefactos Android ignorados. Documentar los requisitos realmente disponibles sin instalar agentes o herramientas como parte de P-00R.

**Riesgo:** desactivar controles para obtener PASS, ejecutar un runner fijado a desarrollo sobre una restauración o validar una receta histórica como si fuera actual.  
**Puerta R3:** instrucciones/scripts coherentes con árbol V1–V12, puertos actuales y nombres protegidos de BD; defectos estáticos corregidos o expresamente retirados de la receta soportada. Historial SQL aplicado preservado.

### Paso 4 — Validar la revisión consolidada y proteger PostgreSQL

Las verificaciones deben ejecutarse sobre el **SHA final integrado**, en una copia limpia y sin usar fuentes, cachés o credenciales heredadas inadvertidamente del árbol original.

| Verificación futura | Condición para aprobar |
|---|---|
| Reproducción desde Git | Están todos los archivos y recursos; rutas de tareas/imports resueltas. Requisitos y pasos documentados; sin depender de archivos privados del PC salvo configuración suministrada explícitamente. |
| Instalación de dependencias declaradas | `npm ci` usa manifiesto/lock conciliados; Flutter respeta su lock. Comprobar prerequisitos instalados antes de ejecutar; no alterarlos como parte de esta revisión. |
| Backend | Compilación y suites unitarias/integración contra PostgreSQL 16 de pruebas protegido; ningún target de desarrollo/producción. Verificar recibos, replay, conflictos y aislamiento conductor/tenant. |
| Migraciones | Comprobar historial/checksums V1–V12 en pruebas y en una restauración aislada; probar escenario vacío y actualización por origen cuando corresponda. No modificar SQL aplicado ni baselinar para ocultar diferencias. |
| CRM | Typecheck, pruebas y build; campos/estados de cierre de incidencias y reportes conservados; proxy/API/WebSocket acordes al puerto definido. |
| Flutter/Android | Analyze, pruebas y build desde copia limpia; ubicación/permiso foreground, cola persistente, reconciliación, limpieza al cerrar sesión y presentación de errores/accesibilidad. |
| CI Linux | Wrapper ejecutable y distribución/checksum válidos; jobs backend, frontend y móvil completos. No usar únicamente éxito local en Windows como evidencia de CI. |
| Datos y recuperación | Antes de cualquier operación posterior sobre desarrollo, respaldo actual PostgreSQL; restauración aislada verificable, comparación de estructura/datos y aceptación. Git no sustituye ese respaldo. |
| Operación/demostración | Recorrido humano ADMIN–CONDUCTOR–CRM y demo solo cuando corresponda al alcance autorizado; cierre comprobado. No sustituirlo por comprobaciones de patrones en los scripts. |

La BD de desarrollo nombrada en scripts es `solucion_ruta_fija_1`; la BD de pruebas es `ruta_fija_test`. Una restauración de ensayo debe tener destino aislado y controles adecuados. No ejecutar runners con URL de desarrollo fijada sobre esa restauración ni sobre datos reales para obtener evidencia.

Las suites, auditorías y arranques existentes tienen efectos. Por ejemplo, F5.1 puede controlar el servicio PostgreSQL y F1.3 realiza sondas SQL transaccionales. Revisar cada script antes de usarlo; su prefijo Test no significa que sea una lectura sin efectos.

**Riesgo:** validar otro SHA, usar datos reales desde una prueba, confundir evidencia antigua con una regresión nueva o una prueba de API con un recorrido humano completo.  
**Puerta R4:** resultados nuevos vinculados al SHA consolidado, entornos/destinos identificados y limitaciones documentadas; respaldo/restauración aceptados antes de cambios de datos reales.

### Paso 5 — Habilitar el inicio de fase 3

1. Registrar el SHA consolidado aprobado, evidencia de validación y receta de reproducción.
2. Mantener la captura WIP y el resguardo externo hasta que la revisión integrada y la recuperación estén aceptadas.
3. Incorporar después los documentos/agentes de fase 3 previstos por el usuario mediante su propio alcance de trabajo.
4. No mover main, publicar, abrir exposición pública ni retirar copias de seguridad como consecuencia automática de este plan.

**Puerta R5:** R0–R4 satisfechas, bloqueos operativos resueltos y decisión explícita de iniciar la fase siguiente. P-00R termina con la entrega del documento.

## 7. Bloqueos concretos y condiciones para resolverlos

| ID | Evidencia actual | Qué bloquea | Condición de avance propuesta |
|---|---|---|---|
| B01 | V11/V12 y Java/Dart esenciales están `??`; imports de fuentes rastreadas los usan. | Reproducción desde HEAD y cualquier traslado/commit parcial. | R0 y R1: resguardo y checkpoint completo verificado. |
| B02 | No se realizó un resguardo nuevo ni se comprobó reconstrucción/restauración en P-00R. | Manipulación posterior del árbol y cambios sobre datos reales. | Resguardo fuente verificable antes de integrar; respaldo y restauración BD antes de operaciones sobre desarrollo. |
| B03 | mvnw `100644` en índice actual, `100755` en main; el workflow lo ejecuta directamente. | Reproducción de CI Linux de esta rama. | Integrar el modo y validar CI en el SHA final. |
| B04 | README cambia en ambos historiales; package.json cambia localmente y en main; main aporta un lock distinto. | Integración aprobada y receta/dependencias coherentes. | Conciliación revisada y regresión de Angular/jsdom, sin resolver archivos completos por sustitución. |
| B05 | Ambos BAT de demo tienen **U+000B**, faltan separadores de las rutas de PowerShell/script y el BAT de inicio contiene además un CR aislado en la ruta de salida. | Uso fiable de los lanzadores de demo. No impide conservarlos como WIP. | Reparación posterior y validación controlada. No ejecutarlos con su contenido actual. |
| B06 | `Invoke-RutaFijaF44DriverIncidentClosureMigration.ps1:153–175` exige exactamente V1–V11; el runner exige solo V11 pendiente, pero el árbol contiene V12. | Aplicación de V11 con esa receta histórica sobre el árbol actual. | Receta por versión de origen con guardas válidas; conservar V11/V12 y comprobar BD antes de elegirla. |
| B07 | Guardas de parada de V11/V12 (`:60–65` y `:64–68`) revisan 8080/4200, mientras runtime/package/CORS locales usan 4300. | Garantía completa de parada del CRM antes de migraciones futuras. | Alinear guardas/configuración/documentación y verificar todos los procesos/puertos del flujo soportado. |
| B08 | F1.3 fija `ruta_fija_recovery_20260919_f04` y la consulta; G2 invoca ese auditor. Su existencia actual no se consultó. | Aprobación del control histórico completo si falta esa copia. | Resolver ciclo de vida y propósito del control sin inferir el catálogo actual ni exigir una BD temporal permanente. |
| B09 | No se consultó PostgreSQL ni su historial Flyway y no se ejecutaron suites/builds actuales. | Afirmar V11/V12 aplicadas, integración compatible o fase aprobada. | Evidencia nueva R4; mantener como históricas las evidencias existentes. |
| B10 | Caché raíz visible como `??`, exportaciones duplicadas y aviso de ruta larga en build ignorado. | Adición indiscriminada y resguardo genérico sin informe de omisiones. | Lista explícita, exclusiones y comprobación de copia; reglas de ignore precisas posteriormente. |

## 8. Comprobaciones ejecutadas y acciones pendientes

### Ejecutadas en P-00R

- Confirmación de raíz, rama, HEAD, referencias locales y worktrees.
- Inventario Git con entradas agrupadas y archivos expandidos; comprobación de ausencia de cambios preparados.
- Revisión de diferencias rastreadas, ancestro común, divergencia y cambios independientes de main/HEAD.
- Lectura de SQL V11/V12, DTO, servicios, referencias Java/Dart, configuración versionable, tareas, scripts y documentación relevante.
- Comprobación de modos Git de mvnw, versiones declaradas y pares manifiesto/lock; revisión estática de rutas y bytes de los lanzadores de demo.
- Identificación de privados mediante nombres/reglas Git; comprobación de marcadores en plantillas sin mostrar valores. No se considera una auditoría exhaustiva de secretos.
- Registro SHA-256 del índice y de los 139 archivos inventariados, más cinco archivos versionados de mantenimiento usados como control.
- Creación de este documento y verificación final de que los archivos anteriores, índice, HEAD, referencias y worktree mantienen el estado observado.

### Propuestas, sin ejecutar

Resguardo externo/bundle, copia verificable, revisión confidencial, checkpoint selectivo, ramas y worktree de consolidación, integración de main, reparación de scripts/documentación, compilaciones, suites, consultas/migraciones/restauraciones de BD, CI y demo. Su ejecución corresponde a una etapa posterior.

**Control final de alcance:** los 76 rastreados modificados originales permanecen sin cambios adicionales; los 63 archivos inicialmente sin seguimiento permanecen y se añade solo este plan (**64 archivos sin seguimiento expandidos; 51 entradas agrupadas**). El hash del índice, HEAD, referencias locales y lista de worktrees permanecen iguales. Los hashes de los 144 archivos de control coinciden con los iniciales. Estas comprobaciones acreditan la conservación del alcance de P-00R, no la corrección funcional.

**Entrega de P-00R:** diagnóstico, inventario completo, conjuntos de conservación, integración propuesta y puertas concretas. La tarea finaliza aquí, sin ejecutar el plan.


## 9. Ejecución de conservación e higiene — 2026-10-13

Las secciones 1–8 conservan el diagnóstico y la propuesta originales del 12 de octubre. Esta sección registra la ejecución posterior autorizada: **resguardo y checkpoint completados, higiene aplicada; integración de main y validación funcional pendientes**. La conservación sigue identificada como WIP.

### 9.1. Precondiciones y diferencias desde el informe

La raíz Git, HEAD inicial `087202418bafb220749c2b1558ce173e114892b5`, rama inicial `etapa-2/f02-postgresql-local`, un worktree y referencias main/origin/main `36173545a5f48f2fad41a5482ec80764b98764b6` coincidían con el cierre de P-00R. El índice estaba vacío de cambios preparados y mantenía su hash original.

Se comprobaron **76 modificaciones y 64 archivos sin seguimiento**, incluyendo este plan, antes de la higiene: 131 rutas útiles y nueve generados excluidos. No hubo altas/bajas respecto del inventario esperado. Los archivos de control de la revisión previa conservaron sus hashes, salvo las dos correcciones editoriales del propio plan realizadas al cerrar P-00R. No se repitió la auditoría arquitectónica ni se actualizó ninguna referencia por red.

Se revisaron los candidatos contra siete valores privados existentes y formas de tokens/claves, sin encontrar coincidencias en la selección ni mostrar valores. Esta comprobación dirigida no equivale a una auditoría completa de secretos.

### 9.2. Resguardo externo verificado

**Destino:** `D:\UTP\CICLO X UTP\INTEGRADOR I\APF1\resguardos_ruta_fija\2026-10-13_pre-higiene_5063a165`.

| Componente | Ubicación dentro del resguardo | Resultado |
|---|---|---|
| Historia Git previa | `historia/ruta-fija.bundle` | Bundle completo de referencias locales; verificado y clonado sin checkout. El clon pasa fsck y conserva todos los objetos de las referencias originales, incluidos tag y árbol internos. |
| Árbol fuente completo | `fuentes/` | **618 archivos**: 563 rastreados y 55 nuevos útiles. Copia en bytes y hashes verificados. |
| Configuración privada/local | `privado/configuracion/` | Cinco archivos locales, separados de las fuentes; también se preservó configuración Git local. Sin valores en el informe. |
| Respaldos ya existentes | `privado/respaldos/` | **20 archivos**, 717.863 bytes, sin omisiones. Es conservación de archivos existentes, no un nuevo dump ni una restauración de PostgreSQL. |
| Exportaciones originales | `privado/exportaciones-originales/` | Cinco copias verificadas antes de mover los originales al archivo externo. |
| Control de captura | `control/` | Índice original, inventario, selección explícita, patch binario rastreado, verificador y resultados. |

El manifiesto inicial cubre **655 archivos**; todas las copias coincidieron por tamaño y SHA-256, sin omisiones. Se reconstruyeron adicionalmente 618 fuentes en `reconstruccion-verificada/`, desde el resguardo, sin restaurar secretos ni ejecutar aplicaciones. La selección y el índice originales se preservaron antes de crear la rama.

El directorio externo tiene ACL protegida, con acceso para el usuario actual, SYSTEM y Administradores. Los archivos privados y las exportaciones permanecen separados de las fuentes y no entraron en Git.

Archivos para verificar el resguardo:

- `manifiesto-resguardo.json` y `manifiesto-resguardo.sha256`.
- SHA-256 del manifiesto inicial: `cb28e6d998af7e5a844b09b57d99cc31a8c31f4d17451cd90da66e7c022342e5`.
- `control/verificacion-resguardo.json`, `control/verificacion-reconstruccion.json` y `control/conservar_repo.py`.
- Repetición de la comprobación de las copias: ejecutar el verificador existente con acción `verify` y `--backup` apuntando al destino anterior. No usar `--compare-origin` después de la higiene: el índice, documentación y exclusiones cambiaron de forma autorizada y las exportaciones se movieron.

V11 y V12 conservan los hashes publicados en 4.5, tanto en el resguardo como en la copia de trabajo y los blobs del checkpoint. El manifest registra los bytes originales; no se confundieron con checksums Flyway.

**Límite concreto:** se conservó también `backups/f1-7/solucion_ruta_fija_1_20260922T034141Z_f17.dump`, cuyo tamaño original es cero. Su copia es exacta, pero ese archivo no aporta un respaldo recuperable. No se consultó ni alteró la BD.

Las cachés, dependencias descargadas, compilados, logs, estados de procesos y binarios locales se excluyeron deliberadamente del respaldo fuente; permanecen en el repositorio local. No hubo limpieza masiva.

### 9.3. Rama y commits

**Rama activa de conservación:** `conservacion/pre-fase3-2026-10-13`, creada después de verificar el resguardo y desde el HEAD inicial. No se cambió la punta de la rama original ni de main/origin/main.

| Registro | Identificación | Alcance |
|---|---|---|
| Checkpoint WIP | `e0b4ee5ee0947be90b2b67a8aba2ba810be0ac6a` — `WIP: preservar implementacion local antes de fase 3` | **131 rutas**: 76 modificaciones y 55 altas; V11/V12, DTO, servicios, clientes, pruebas, scripts y documentos como conjunto. Padre `087202418bafb220749c2b1558ce173e114892b5`. |
| Higiene versionable | Commit que incorpora esta sección — `chore: higiene controlada y documentacion de Ruta Fija` | Solo `.gitignore`, `README.md`, `docs/README.md` y este plan. Separado del WIP. |

El SHA exacto del commit de higiene se registra al finalizar en `control/cierre-higiene.json` del resguardo y en la entrega de la tarea. La propia sección se identifica por el commit que la contiene para evitar una autorreferencia de hash. Puede localizarse con el log del plan.

La preparación utilizó las rutas concretas de `control/rutas-checkpoint.nul`, sin adición de la raíz ni de carpetas completas. La revisión del índice confirmó exclusión de privados/generados y blobs V11/V12 idénticos. `diff --cached --check` del WIP señaló 14 líneas del plan original con dos espacios intencionales de salto Markdown; se preservaron y registraron como excepción de formato. No se presentó ese control como aprobado sin observaciones ni como validación funcional.

### 9.4. Exclusiones aplicadas

Se añadieron únicamente estas reglas ancladas al `.gitignore` existente:

```gitignore
/.angular/
/archivos de texto/codigo_backend.txt
/archivos de texto/codigo_front.txt
/archivos de texto/codigo_persistencia_postgresql.txt
/archivos de texto/código_extra.txt
/estructura.txt
```

Las exportaciones se excluyen por sus nombres concretos: no se ignora de forma general todo TXT ni toda la carpeta. Se conservaron las excepciones y reglas anteriores, incluidas plantillas, tareas y Wrapper Maven.

En `.vscode/settings.json` se fusionaron **21 patrones por mapa** de `files.exclude` y `search.exclude`, para dependencias, builds, cachés, runtime/logs, herramientas locales y las cinco exportaciones conocidas. Se conservaron todas las claves/valores previos, incluidas exclusiones de búsqueda, watchers y preferencias de Java, TypeScript, tareas y Git. No se forzó su versionado; permanece ignorado.

Los controles de reglas sobre las fuentes capturadas y una matriz de recursos versionables confirman que las nuevas exclusiones no ocultan fuentes, SQL, pruebas, lockfiles, wrappers ni plantillas necesarias. Los artefactos Gradle que ya estaban ignorados antes de esta tarea siguen sujetos a la verificación de Android desde copia limpia; no se ocultaron mediante reglas nuevas.

### 9.5. Exportaciones archivadas sin borrado definitivo

Se revisaron referencias en el árbol fuente completo. Las cinco exportaciones solo aparecían en este plan; no había consumidores operativos en README, tareas, CI ni scripts. Los originales se movieron con `Move-Item -LiteralPath` después de verificar la copia externa; se comprobaron origen/destino resueltos dentro del alcance y hash posterior. No se reemplazó ningún destino.

Cada destino de la tabla es relativo a `privado/archivados/` del resguardo externo. La copia adicional previa sigue en `privado/exportaciones-originales/`.

| Origen dentro del repositorio | Destino relativo de archivo | SHA-256 verificado |
|---|---|---|
| `archivos de texto/codigo_backend.txt` | `archivos de texto/codigo_backend.txt` | `b8b271c92b867ab28bf0daec3f6a88ebdbce820c1010ff59449eec58ee9ea8e5` |
| `archivos de texto/codigo_front.txt` | `archivos de texto/codigo_front.txt` | `eb6d0b8e6f0026df3ed0f64b4bfea77632b2a87428e59295d96712a8d312f322` |
| `archivos de texto/codigo_persistencia_postgresql.txt` | `archivos de texto/codigo_persistencia_postgresql.txt` | `1734f4706e5732e5287c54f30024332535c7eb85d1eb2c51379ef0b3e2fc8151` |
| `archivos de texto/código_extra.txt` | `archivos de texto/código_extra.txt` | `44549bdb070f5c7191040c3ad84353fd3b1881bb4598ed46a40e01cfc6e90c05` |
| `estructura.txt` | `estructura.txt` | `ff2ae49d17ea5c9d8c86a55be5c5308b7275345eed13733e68174f4694cf1f51` |

El registro `control/archivado-exportaciones.json` conserva rutas absolutas, tamaños, hashes, método de traslado y copia adicional. No se borró definitivamente ningún archivo.

### 9.6. Documentación, referencias y retiro

Se creó [docs/README.md](../README.md) como índice breve de documentación vigente para trabajar, evidencia histórica y pendientes, y se añadió un enlace desde el README raíz. No se movió ni eliminó ningún documento o script funcional/referenciado.

Antes de la higiene se comprobaron **66 enlaces locales de README**, **39 destinos de tareas**, **77 destinos literales de scripts** y cinco rutas usadas por CI, sin destinos ausentes. Los enlaces añadidos se resolvieron tras la edición. Son comprobaciones de existencia: no evalúan rutas dinámicas, instalaciones ni comportamiento en ejecución.

Los candidatos a revisión para retiro son el aplicador/runner histórico V11 y el contrato de recuperación F1.3/G2. Se mantienen en sus ubicaciones porque tienen consumidores; cualquier retiro requiere antes sustituir o actualizar esos consumidores. Los documentos de fases antiguas se clasifican como históricos, sin eliminarlos por nombre, fecha o prefijo.

### 9.7. Comprobaciones de cierre y límites

- Fuentes funcionales, scripts, pruebas, configuración funcional, lockfiles y migraciones mantienen sus hashes de captura. Las únicas ediciones autorizadas sobre archivos capturados son `.gitignore`, el README raíz y la adición de esta sección al plan; el índice documental es nuevo.
- Los bytes originales del diagnóstico del plan permanecen como prefijo, sin reescritura de las secciones 1–8.
- V11/V12 mantienen los bytes de 4.5 y están confirmadas en Git. Esto no declara su aplicación en PostgreSQL.
- Las exclusiones Git y de VS Code se comprobaron sobre las fuentes y recursos necesarios; `settings.json` permanece ignorado y conserva preferencias anteriores.
- Las cinco exportaciones están archivadas con hashes y copia adicional; las cachés y dependencias locales se mantienen.
- El commit de higiene se revisó con una selección limitada a cuatro rutas y control de whitespace de sus cambios.
- La rama original, main y origin/main conservan sus puntas iniciales. No se publicaron cambios ni se crearon worktrees asociados adicionales.

**No ejecutado:** integración de main, reparación de lógica o scripts operativos, compilaciones, suites, servicios, consultas/migraciones/restauraciones de BD, demo, instalación de agentes/herramientas o publicación.

**Pendiente para integrar main:** partir del WIP y la higiene ya confirmados, preparar un worktree de consolidación y conciliar README, package.json/lock, Wrapper/CI y mantenimiento de la referencia local. Sigue pendiente incorporar los cinco commits de main.

**Pendiente para validar desde copia limpia:** resolver BAT dañados, receta V11/V12, guardas 4200/4300 y auditor de recuperación; comprobar requisitos/artefactos Android; ejecutar builds/suites/CI y recorrido humano vinculados al SHA integrado, más respaldo actual y restauración PostgreSQL aislada. La higiene completada no aprueba R2–R5 ni el inicio funcional de fase 3.

El cierre verificable, los dos SHA y los resultados detallados están en `control/cierre-higiene.json` del resguardo. R0/R1 y la higiene están completados; las condiciones de integración y validación permanecen abiertas.

