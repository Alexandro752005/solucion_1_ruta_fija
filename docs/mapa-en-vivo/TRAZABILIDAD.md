# Trazabilidad funcional y técnica

Referencias comprobadas sobre el código de esta entrega. Las líneas son puntos de entrada reales; el inventario SHA-256 fija la versión inspeccionada. Los archivos de prueba usan datos sintéticos. Los límites físicos y de despliegue permanecen expresos en README.

| Criterio | Implementación principal | Comprobación ejecutada |
|---|---|---|
| MAP-01 · mapa personal | `mobile/lib/features/location/domain/mobile_service_location_controller.dart:401`, `_capturePersonal`; `presentation/mobile_map_page.dart:17` | `mobile_map_location_controller_test.dart`, `mobile_map_page_test.dart`; HTTP etapa complete demuestra posición local posterior sin PUT |
| MAP-02 · aceptación no autoriza GPS | `_cycle` exige EN_SERVICIO, acceptedAt y startedAt antes de `_captureAndSend` | Regresión del controlador de servicio y nueva prueba personal; backend rechaza estado distinto |
| MAP-03 · único pipeline | Controlador `_track`/`tick` y `beforeComplete:480`; repositorio móvil ya existente | Pruebas de concurrencia, antes de completar con consulta bloqueada; HTTP publish confirma captura en el feed |
| MAP-04 · roles prohibidos | `backend/.../livemap/api/LiveMapController.java:13`, `LiveMapService.java:27` | `unauthenticatedAndOtherRolesCannotReadEitherEndpoint`; API real 401/403 con gates de términos e identidad activos |
| MAP-05 · aislamiento | `LiveMapReadDao.java:19`, joins por organización y actor derivado en servicio | `mobileReceptionFeedsOnlyTheSameTenantAndUsesTheExactMinimalProjection`, grupos ajenos vacíos |
| MAP-06 · filtros/límites | DAO `vehicles`; `frontend/.../core/live-map/live-map-api.service.ts:12`; fachada `:15` | Integración PostgreSQL para filtros y límites; Brave con API real; rechazo de payload inválido en adaptador |
| MAP-07 · diagnóstico OS | `_serviceDeviceAllowed`, `_deviceUnavailable`, página móvil | Widgets: permiso denegado y GPS apagado en servicio; abre ajustes de ubicación sin pedir permiso ya concedido |
| MAP-08 · frescura/TTL | DAO edad ≥120 s y TTL actual; `live-map.models.ts` / `LiveMapFacade` reloj monotónico | PostgreSQL 119/120/299/300 s; fachada caduca sin renovar capturedAt; fixtures visuales nunca se presentan como ejecución real |
| MAP-09 · cierre contextual | `assignment_detail_page.dart:171`, `_openMap` delega a `_submit`; `mobile_map_page.dart` | Widget negativo y positivo de confirmación/retorno; HTTP complete real retira snapshot y deja mapa personal sin subida |
| MAP-10 · background/bloqueo | `setForeground:178`, epoch y descarte de resultados pendientes | Tests de lifecycle y retorno; teléfono bloqueado físicamente pendiente por falta de dispositivo |
| MAP-11 · vacío y máximo20 | Adaptador, facade y `live-map-clusters.ts:5`; SQL LIMIT | 0/1 con API real, cinco en referencia visual; arrays sintéticos y prueba de treinta entradas agrupadas limitadas a veinte. No se crearon treinta cuentas saltando cuotas |
| MAP-12 · revocación | `consentChanged:203`; `MobileDriverService.java:144` y evento cleared `:154` | Carreras reales esperando lock del PID concreto; Flutter revoke y observador Brave abierto retiran la unidad |
| MAP-13 · cambio de contexto | Fachada page-scoped y guards existentes; contexto de sesión | Specs de callbacks tardíos/otro actor, hidden, destrucción y pérdida de permisos; sin suscripciones o datos conservados |
| MAP-14 · errores/red/teselas | Estados de fachada/mapa, cliente tile separado y validación HTTPS XYZ | Fallo de red purga; error tile conserva listado y atribución; cancelación normal no se trata como error; reconexión sin cola |
| MAP-15 · regresiones | Tres superficies y navegación existente | Backend261+18, CRM457, Flutter regresión completa, builds y revisión independiente |
| MAP-16 · costo/capacidad | `CARTOGRAFIA_Y_PRIVACIDAD.md`, dependencias exactas, licencias, UA/caché | HEAD único de OSM y ensayo serial local20lecturas; cuotas4ADMIN/20CONDUCTOR preservadas por decisión del usuario. Ampliación5×40 y despliegue externo pendientes, sin compras |

## Entradas de implementación

| Archivo | Método/función/punto de entrada |
|---|---|
| `backend/src/main/java/pe/rutafija/livemap/api/LiveMapController.java:17` | `vehicles`, GET autorizado y no-store |
| `backend/src/main/java/pe/rutafija/livemap/application/LiveMapService.java:27` | `vehicles`, valida rol y límites; `groups:36` |
| `backend/src/main/java/pe/rutafija/livemap/infrastructure/LiveMapReadDao.java:19` | `vehicles`, proyección actual; `groups:63` |
| `backend/src/main/java/pe/rutafija/operation/application/MobileDriverService.java:144` | `changeLocationConsent`; `upsertCurrentLocation:166`, lock propio `:252` |
| `frontend/src/app/core/live-map/live-map.models.ts:15` | `parseLiveMapSnapshot`; grupos `:32`, vigencia y configuración de teselas |
| `frontend/src/app/features/live-map/live-map.facade.ts:15` | Dueño de consultas, invalidación, identidad, selección y TTL |
| `frontend/src/app/features/live-map/live-map-canvas.component.ts:24` | Leaflet, markers DOM reutilizados, selección, viewport y atribución |
| `frontend/src/app/features/live-map/live-map-clusters.ts:5` | Agrupamiento por proximidad sin extrapolar posiciones |
| `frontend/src/app/features/live-map/live-map.page.ts:14` | Main aprobado y composición encapsulada |
| `mobile/lib/features/location/domain/mobile_service_location_controller.dart:324` | `_captureAndSend`, reutiliza receptor existente; `_capturePersonal:401`, `beforeComplete:480` |
| `mobile/lib/features/location/presentation/mobile_map_page.dart:17` | Mapa compartido personal/servicio, permisos, atribución y callback de cierre |
| `mobile/lib/features/assignments/presentation/assignment_detail_page.dart:171` | `_openMap`, contexto confirmado y operación de cierre vigente |
| `mobile/lib/features/location/presentation/foreground_location_page.dart:187` | `_clearCurrentLocation`, retirada temporal explicada separadamente de revocar consentimiento |
| `mobile/lib/features/bootstrap/presentation/conductor_navigation_shell.dart:122` | Mapa y Más; se conservan los módulos consultivos anteriores |

## Evidencia y límites de interpretación

- `verificacion/artefactos/auditoria-final.json`: auditoría de artefactos, hashes y resultados realmente leídos; no sustituye las pruebas.
- `verificacion/artefactos/archivos-modificados.csv`: archivos añadidos/modificados; el listado distingue código de documentación y no implica que todo archivo sea una función nueva.
- `verificacion/browser/result-initial.json`: Angular, autenticación, API y PostgreSQL reales con sensor/tiles de ensayo.
- `verificacion/browser/result-visual.json`: solo autenticación y Angular reales; feed de cinco unidades interceptado explícitamente para composición. No demuestra cinco vehículos en la base.
- `verificacion/mobile/screenshots/mapa-servicio-render-sintetico.png`: renderizador Flutter, fuentes SDK y cuadrícula ficticia; no es un dispositivo físico ni un mapa de calles de producción.
- JaCoCo se limita a los paquetes nuevos y datos agregados compatibles; los avisos de otras clases históricas se conservan. Ningún informe declara cobertura total, pentest completo, WCAG certificado, recorrido real o despliegue de producción.
