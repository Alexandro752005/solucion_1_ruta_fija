# Fase 1 — auditoría y línea base

Inspección actual 11/11/2026, no inferida de documentación histórica. Fuente respaldada: 1043 archivos y sus SHA-256 en `verificacion/baseline/fuentes-sha256.json`. No se ejecutó Git. No hay backend/CRM escuchando 8080/4300 al inicio; PostgreSQL compartido 5432 está disponible pero no se usa para pruebas de escritura.

## Línea base ejecutada antes de cambios funcionales

| Superficie | Comando real | Resultado |
|---|---|---|
| Backend | `scripts/Test-RutaFijaBackendUnitSafe.ps1 -Execute` | 261 pruebas, 51 clases, sin BD; PASS. XML y logs en `docs/evidencia/auditoria-ide-2026-10-23/backend/run-75f35159-bc38-4a89-879e-3bc09f257897` |
| CRM | `npm.cmd run typecheck` y `npm.cmd run test:ci` | Typecheck PASS; 58 archivos / 419 pruebas PASS |
| Flutter | `flutter analyze` y `flutter test` | Analyze sin incidencias; 241 pruebas PASS y 7 omisiones preexistentes; no prueba física |

Herramientas reales: Java21, Maven3.9.15 cacheado, Angular22.2.3, TypeScript6.0.3, Vitest4.1.12; Flutter3.47.5/Dart3.13.4. Android SDK disponible; `adb devices -l` no detectó dispositivo conectado en esta fase.

## Existente / reutilizable / faltante

Backend: `operation/api/MobileDriverController`, `application/MobileDriverService`, `infrastructure/DriverCurrentLocationStore` ya exponen consentimiento, captura y retirada propios. Persistencia PostgreSQL `driver_current_location` vinculada al tramo y puntos de servicio originales de siete días. TTL configurable PT5M. Lock por organización más refresh/lock de conductor protege captura y revocación. Cierre/interrupción/relevo eliminan snapshot. Faltan feed ADMIN mínimo por tenant y evento de retirada al revocar.

CRM: `topbar.model.ts` solo contiene accesos planned, sin feature ni dependencia cartográfica. Reutilizar `OperationRealtimeService`, guardas auth/rol/términos, `crmSessionContextKey` y desmontaje del shell. Añadir feature lazy y fachada de alcance página; preservar topbar visual. El renderizador SVG de rutas del dashboard no es mapa de calles ni fuente GPS.

Flutter: `MobileServiceLocationController` global ya es dueño del emisor 30 s, single-flight y epoch para descartar resultados tardíos. `GeolocatorDeviceLocationGateway` conserva tiempo/precisión del sensor. Falta snapshot efímero para visualización personal, motor cartográfico y contexto de mapa. La captura manual de `/location` necesita usar el dueño común; revocación debe notificarlo inmediatamente. Navegación actual seis destinos; usuario confirmó ahora cinco más pantalla Más, conservando los destinos existentes. minSdk29 se conserva; aceptación nueva Android12+.

## Persistencia y seguridad

No hace falta nueva tabla ni migración del módulo. Se preservan V1–V51 y políticas históricas. El harness legado tiene limpieza global y marcadores humanos protegidos; no se toca ni se omiten sus guards. Se construye un clúster PostgreSQL16 independiente en puerto54432, base `rf_map_test`, bajo `.runtime/live-map-isolated`, para migraciones y datos sintéticos. No se conecta a 5432 en ese harness.

## Resolución de decisiones DEC-01–12

| DEC | Resolución / límite |
|---|---|
| 01 | Reutilizar consentimiento revocable ya existente; nunca irreversible |
| 02 | Complete existente valida la declaración; no crear segundo cierre ni afirmar recorrido físico |
| 03 | Usuario confirmó cinco accesos con Mapa y Más el 11/11/2026 |
| 04 | Emisor global sigue entre pantallas de app foreground; no depende de visualizar mapa |
| 05 | GPS≈30s existente; reconciliación CRM15s; gris desde120s; TTL backend5m configurable |
| 06 | Cartografía local sin facturación y términos revisados; escala pública/hosting no aprobados automáticamente |
| 07 | PUT/DELETE GPS existentes; GET live-map nuevo según contrato coordinado; librerías auditadas antes de incorporar |
| 08 | No buscador de zonas: no aparece en Main aprobado |
| 09 | Datos verificados únicamente; sin extrapolación ni rutas calculadas |
| 10 | Preservar historial original7d; el feed nuevo solo última ubicación |
| 11 | Conservar shell/topbar; activar metadatos de accesos ya previstos |
| 12 | Permiso OS, GPS y consentimiento separados; diálogo real Android no simulado |

## Puerta de implementación

Auditorías reales por tres agentes independientes y revisión del coordinador. Contratos de interfaz y propiedad en `CONTRATO_MODULO.md` y `estado_fases.md`. Se habilitan incrementos independientes con tests nuevos y revisión cruzada. No se ha acreditado todavía aceptación física, integración completa, capacidad de producción ni cumplimiento legal de un despliegue real.
