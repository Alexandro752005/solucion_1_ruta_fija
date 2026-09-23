# Evidencia de cierre F3.4 — Contrato y reportes reales

- Fecha local: 2026-09-22.
- Plataforma: PostgreSQL 16 nativo en loopback, Java 21 y Angular local.
- Docker: no utilizado.
- Desarrollo: solucion_ruta_fija_1, sin credenciales expuestas.

## Entrega comprobada

| Control | Evidencia |
| --- | --- |
| Contrato OpenAPI | La descripción global y las etiquetas separan CRM, sesión móvil, operaciones propias, ubicación y reportes. |
| DTOs | AssignmentCreateRequest/AssignmentResponse documentan modo, plazo y marcas auténticas; comandos móviles documentan clientEventId, version y occurredAt. |
| Frontera CRM | No hay ruta CRM de accept/reject; Angular muestra estados móviles y solo permite cancelar una pendiente. |
| Idempotencia | La respuesta móvil expone X-Idempotent-Replay; CORS permite la clave CRM y expone la cabecera de replay. |
| Reportes | ReportService consulta SQL persistido y publica los siete estados en orden estable, con cero explícito solo cuando no existen filas. |
| Exportación | PDF/XLSX usan el mismo AssignmentReportResponse y sus totalsByStatus. |
| Privacidad | Ubicación vigente única, consentimiento, TTL servidor, sin historial ni coordenadas en auditoría. |
| Sin migración artificial | El esquema sigue exactamente Flyway V1–V10. |

## Prueba focalizada

La ejecución focalizada de MobileContractReportIT aprobó cuatro casos de integración contra ruta_fija_test:

~~~text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
~~~

La matriz cubrió OpenAPI, camino positivo/replay, UUID ajena en mismo tenant, cruce de tenant, transición inválida, vencimiento, reportes reales, exportación XLSX, consentimiento, permiso, UPSERT y limpieza de ubicación.

La compilación de producción anterior a la verificación integral también aprobó:

~~~text
backend: BUILD SUCCESS
frontend: Application bundle generation complete
~~~

## Auditoría nativa

~~~text
F3_3_MOBILE_OPERATIONS_AUDIT=PASS flyway=V1-V10 mobile=own_routes idempotency=durable location=current_only privacy=no_coordinate_audit privileges=DML_without_DDL docker=0
F3_4_CONTRACT_REPORTS_AUDIT=PASS flyway=V1-V10 contract=openapi_crm_mobile reports=persisted privacy=current_only docker=0
~~~

La auditoría no modifica desarrollo. Lee el contrato, verifica la cadena F3.3 y rechaza migraciones distintas de V1–V10.

## Verificación integral y ejecución nativa

La verificación nativa completa aprobó contra la base aislada `ruta_fija_test`:

~~~text
Pruebas unitarias: 24, errores: 0, fallos: 0
Pruebas de integración: 29, errores: 0, fallos: 0, omitidas: 0
F3_4_CONTRACT_REPORTS_AUDIT=PASS
F3_4_TEST_CLEANUP=PASS assignment_location_receipt=0|0|0
~~~

El conjunto Angular también aprobó 17 archivos de prueba y 42 pruebas. El arranque real mediante `iniciar_ruta_fija.bat` aprobó con API `UP` en 8080, CRM HTTP 200 en 4200 y OpenAPI publicado; `finalizar_ruta_fija.bat` detuvo únicamente esos procesos controlados y dejó ambos puertos libres. PostgreSQL permaneció como servicio nativo local.

## Límites confirmados

F3.4 deja lista la API real para Flutter, pero no implementa Flutter, FCM, notificaciones push, GPS en segundo plano, fotos, historial GPS, mapas ni despliegue externo. La siguiente puerta es F4.1.
