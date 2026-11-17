# Dashboard ADMIN: controles y actualización, fase 4

Fecha: **26/10/2026**, America/Lima. Autorización: «OK VERDE» después del [cierre de fase 3](dashboard-admin-fase-3-datos-reales-2026-10-26.md). Resultado: **CONTROLES_Y_ACTUALIZACIÓN_VERIFICADOS_LOCALMENTE**. Esta entrega completa la fase 4 del [plan de evolución](../decisiones/2026-10-26-evaluacion-dashboard-admin.md); la fase 5 de QA integral y la fase 6 de entrega final permanecen pendientes.

El resultado está activo en **http://127.0.0.1:4300/dashboard**, perfil ADMIN. Se mantiene el contrato [DA-01](../contratos/DA-01-dashboard-admin-v0.1.0.md), incluidos los alcances de exportación, privacidad, versiones de ruta y cortes independientes.

## Comportamiento disponible

**Exportar resumen** abre un diálogo de reportes de servicios del día seleccionado y la zona confirmada por el servidor. Consulta el período y las emisiones existentes sin emitir automáticamente. El usuario puede seleccionar una versión confirmada y descargar **CSV, Excel o PDF** de ese mismo contenido persistido. La interfaz muestra referencia, versión, corte y SHA-256 canónico; aclara que los bytes de los tres formatos son distintos y que el reporte no incluye flota, conductores, incidencias, avisos ni esquema de ruta.

**Emitir reporte** es una acción expresa. Si se pierde la respuesta, queda visible la referencia pendiente y el botón **Consultar o recuperar con la misma operación**. Cerrar/reabrir el diálogo o cambiar la fecha del Dashboard conserva esa operación y su período dentro de la misma pantalla. No se genera otra clave automáticamente. Cambiar de sesión o empresa elimina datos y operaciones en memoria. La referencia no se persiste en localStorage/sessionStorage; antes de abandonar o recargar completamente la pantalla, resolver una operación pendiente y conservar su referencia visible.

El panel de rutas añade selección por página de hasta 100 registros. Conserva la elección explícita si sigue activa y vigente al refrescar el catálogo. Una selección consulta un solo detalle; no se descargan automáticamente todos los recorridos. Las páginas sin rutas activas se identifican como tales, sin afirmar ausencia global. **Ver ruta** abre un visor con zoom de 1× a 4×, desplazamiento acotado, restablecimiento y lista textual de paradas. El zoom se mantiene ante un refresco de la misma geometría y se restablece al cambiar de revisión o trazado. Escape cierra el diálogo y devuelve el foco a su control de apertura.

El canal operativo distingue **desconectado, conectando, conectado y reconectando**. Solo confirma conexión cuando abre realmente el WebSocket. Conserva las firmas existentes de `connect()`/`disconnect()` y descarta tickets o callbacks de un ciclo ya cerrado.

La pantalla utiliza un suscriptor al canal y un temporizador de **60 segundos** mientras la pestaña permanece visible. Agrupa eventos en **300 ms**, conserva las invalidaciones de rutas aunque el último evento del grupo sea de otro tipo y consolida una ronda al volver a la pestaña. Las consultas automáticas mantienen como máximo una petición en vuelo y una invalidación pendiente por fuente. El sondeo no recarga el catálogo estático ni la auditoría oculta. Al salir se cancelan suscripciones y temporizadores; una reconexión solicita la actualización necesaria para cubrir cambios perdidos.

## Uso desde ADMIN

1. Entrar en **Resumen**. «Sesión activa» indica autenticación; el estado del canal y los cortes de las tarjetas se muestran por separado.
2. Para descargar, pulsar **Exportar resumen**, elegir una versión confirmada y pulsar **CSV**, **Excel** o **PDF**. Las versiones locales de prueba ya aparecen en el listado.
3. Pulsar **Emitir reporte** únicamente cuando se necesite un corte nuevo. Si el resultado queda pendiente, usar la recuperación con la misma referencia.
4. Para consultar otro día, cambiar el selector del Dashboard. Solo se recalculan las cohortes de servicios e incidencias; los cinco KPI actuales conservan su significado.
5. Cuando existan rutas registradas, elegir una en el panel, pulsar **Ver ruta** y utilizar los controles del visor. **Ver rutas** abre el módulo existente para gestionar sus registros.

No se inventaron rutas para llenar el gráfico. El catálogo local sigue vacío; los controles de geometría se verificaron con fixtures explícitos en la revisión aislada de loopback **4312**.

## Cambios y alcance técnico

- `DashboardReportFacade` y `DashboardReportExportComponent`: consulta, selección, emisión expresa, recuperación y descargas utilizando `ServiceReportsApiService` existente. La operación vive en el ámbito de la pantalla y sobrevive al cierre del diálogo.
- `DashboardRoutePickerComponent` y `DashboardRouteViewerComponent`: catálogo paginado y esquema local, sin proveedor cartográfico ni seguimiento de vehículos.
- `DashboardLiveUpdates` y `DashboardConnectionComponent`: visibilidad, sondeo, eventos y estado de conexión.
- `AdminDashboardFacade`: consultas agrupadas por fuente, cancelación de períodos anteriores y conservación de la selección de ruta.
- `OperationRealtimeService`: estado público de solo lectura, cancelación del ticket pendiente y protección frente a callbacks anteriores. La suite completa incluye sus consumidores existentes.
- La revisión sintética reutiliza el visor nuevo; conserva aislamiento de API y WebSocket.

No hubo cambios en Java, SQL, permisos, autenticación, guardas, shell, Android, launchers, términos, dependencias ni presupuestos de compilación. Los hashes de backend, configuración de compilación, contratos y shell coinciden con el respaldo previo. Se guardaron **856 archivos** antes de esta fase en `backend/.local/accesos/dashboard-admin-fase-4-20261026/source-before/`, excluido del repositorio.

## Comandos y resultados

Los comandos npm se ejecutaron desde `frontend/`; las restantes comprobaciones, desde la raíz. Los registros públicos y el resumen se encuentran en [validation.json](../evidencia/dashboard-admin-fase-4-2026-10-26/validation.json).

| Comando o ensayo | Resultado comprobado |
| --- | --- |
| `npm.cmd run typecheck` | Código 0, aplicación y pruebas |
| `npm.cmd run test:ci` | **52 archivos / 335 pruebas aprobadas**, 0 fallos; 31 casos nuevos respecto a fase 3 |
| `npm.cmd run build` | Código 0, sin advertencias |
| `npm.cmd run build -- --configuration=dashboard-preview` | Código 0, sin advertencias; fixtures ausentes del bundle normal |
| `./scripts/Invoke-RutaFijaNativeRuntime.ps1 -Action Test` | Backend y CRM UP, puertos 8080/4300 |
| `./scripts/Test-RutaFijaLocalSecretPolicy.ps1 -Action Check -Execute` | Exclusión de configuración privada y ACL aprobadas |
| Edge real: ADMIN, consulta y descargas | Versión persistida leída; tres formatos físicos descargados; 0 excepciones JavaScript en la verificación completa |
| Pérdida controlada de respuesta de emisión | La respuesta se interrumpió después de confirmar el servidor; la recuperación envió la misma clave/período y devolvió la misma emisión |
| Pestaña real en segundo plano | **61 s, 0 refrescos** |
| Regreso a primer plano | **1 ronda consolidada** |
| Pestaña visible | **61 s, 1 ronda periódica** |
| Edge aislado: visor y presentación | Zoom, desplazamiento, restablecimiento, Escape y retorno del foco; 0 solicitudes API, 0 WebSocket y 0 errores JavaScript |

Los 31 casos nuevos cubren recuperación idempotente, respuestas incompatibles, descarga de contenido persistido, borrado por cambio de actor/contexto, selección y paginación de rutas, una invalidación pendiente por fuente, controles del visor, ráfagas de eventos, visibilidad, reconexión y limpieza de recursos. Java permaneció intacto; **no se volvió a ejecutar su suite** en esta fase. Las 261 pruebas seguras de backend corresponden al corte anterior de fase 3 y se referencian expresamente como antecedente.

Las mediciones por navegador son automatizadas; no se atribuye revisión visual humana, ensayo físico nuevo del POCO ni aprobación general de accesibilidad.

## Reportes de prueba e integridad de datos

Se conservaron **dos emisiones explícitas de prueba**, versiones **1 y 2** de una misma serie para el **26/10/2026**, con cohorte vacía. Cada intento de recuperación reutilizó su clave y no añadió otra revisión. La segunda versión mantiene el enlace a la primera. No se borró historia para dejar un resultado aparentemente limpio.

La primera ejecución alcanzó recuperación correcta, pero su comprobación de archivo terminó antes de validar Excel; el archivo completo se comprobó posteriormente como XLSX válido. El segundo ensayo guardó el checkpoint de recuperación y descargó los tres formatos; su comprobación con espera fija no confirmó la ronda de regreso a la pestaña. Se repitió esa verificación esperando la respuesta HTTP efectiva, mediante una consulta y descarga de la versión existente, **sin emitir una tercera versión**. Se conservan los registros de esos intentos y el resultado final completo. Un timeout inicial de navegación del visor aislado también se repitió con mayor espera del navegador, sin cambiar límites del producto.

Los archivos físicos de CSV, XLSX y PDF contienen el mismo identificador y SHA-256 canónico de la versión **2**, comprobado leyendo CSV, XML del XLSX y los streams del PDF. La huella canónica es:

```text
e1440b6395e91ee0326ad5c8b88e8f370de1b19de7bbbee9099f174e797428e8
```

Sus hashes de archivo individuales y tamaños están en [download-content-validation.json](../evidencia/dashboard-admin-fase-4-2026-10-26/download-content-validation.json). Los binarios descargados y las respuestas completas permanecen en el directorio privado.

Las huellas de las **105 tablas de `ruta_fija_test` permanecieron idénticas**. La base nativa mantiene **51 migraciones correctas, ninguna fallida y la misma cantidad de aceptaciones**. Sus cambios se limitan a sesiones/auditoría/mantenimiento de vigencias (`app_user`, `refresh_token`, `audit_event`, `driver`) y las cuatro tablas de emisión:

| Tabla | Filas antes → después |
| --- | --- |
| `report_emission_series` | 0 → 1 |
| `report_emission` | 0 → 2 |
| `report_emission_content` | 0 → 2 |
| `report_emission_request` | 0 → 2 |

Las demás tablas nativas conservaron sus huellas. El snapshot L-00 y los documentos legales inmutables mantienen sus SHA-256. No se realizaron limpiezas de base, migraciones ni cambios de credenciales. La evidencia de auditoría tiene su propio instante de consulta, separado de las huellas de tabla; los procesos normales de mantenimiento continúan activos.

## Evidencia y límites pendientes

- [Consulta, descargas y visibilidad con ADMIN real](../evidencia/dashboard-admin-fase-4-2026-10-26/browser-validation.json).
- [Recuperación controlada y misma operación](../evidencia/dashboard-admin-fase-4-2026-10-26/controlled-recovery-validation.json).
- [Diálogo con emisión confirmada](../evidencia/dashboard-admin-fase-4-2026-10-26/emision-confirmada.png).
- [Dashboard real de escritorio](../evidencia/dashboard-admin-fase-4-2026-10-26/dashboard-admin-real-1440.png), [390 px](../evidencia/dashboard-admin-fase-4-2026-10-26/dashboard-admin-real-390.png), [320 px](../evidencia/dashboard-admin-fase-4-2026-10-26/dashboard-admin-real-320.png) y [ampliación CSS al 150 %](../evidencia/dashboard-admin-fase-4-2026-10-26/dashboard-admin-real-zoom150.png).
- [Visor con controles sobre datos sintéticos](../evidencia/dashboard-admin-fase-4-2026-10-26/visor-aislado/visor-controles.png) y [validación de aislamiento](../evidencia/dashboard-admin-fase-4-2026-10-26/visor-aislado/browser-validation.json).
- [Efectos auditados de los reportes locales](../evidencia/dashboard-admin-fase-4-2026-10-26/native-report-effects.json).
- [Manifiesto SHA-256 de fuentes y evidencias](../evidencia/dashboard-admin-fase-4-2026-10-26/manifest.json).

Se conserva el límite conocido de fase 3: la **cabecera general del CRM desborda aproximadamente 7,73 px a 320 px**, fuera del main del Dashboard y con sus fuentes intactas. El Dashboard no desborda en las capturas verificadas. No existe una ruta real local con geometría para certificar aquí ese escenario integrado; se distingue expresamente la prueba aislada sintética.

La fase 5 realizará la revisión funcional, visual y de regresión integral del candidato. Esta fase 4 no marca aprobadas las fases restantes ni modifica actas históricas. No se crearon commits, push ni publicaciones remotas. API y CRM permanecen activos y el puente móvil conserva su configuración anterior.
