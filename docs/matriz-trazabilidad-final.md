# Matriz de trazabilidad final

La matriz relaciona el alcance solicitado, los documentos de especificación
revisados y la evidencia ejecutable de la entrega final. Las indicaciones de
documentos que requieren móvil o infraestructura externa se registran como
futuras, porque el usuario acotó expresamente la solución al CRM web local.

| Necesidad | Implementación verificable | Evidencia | Estado |
| --- | --- | --- | --- |
| PostgreSQL real sin aplicación externa | Servicio `postgres:16-alpine` en Compose y volumen `postgres_data`. | `compose.yaml`, healthchecks y arranque con `iniciar_ruta_fija.bat`. | Cumplido |
| Multi-organización y RBAC | Organización derivada de autenticación, filtros tenant-aware y roles `SUPER_ADMIN`, `ADMINISTRADOR`, `COORDINADOR`, `CONDUCTOR`. | `AuthFlowIT`, `FleetManagementIT`, `OperationFlowIT`. | Cumplido |
| Administración de flota | CRUD de grupos, conductores, vehículos y relaciones, con estados administrados. | API Angular, servicios Spring y `FleetManagementIT`. | Cumplido |
| Estados operativos honestos | Asignación directa `SCHEDULED`; reserva explícita del coordinador; vehículo no adopta estado ficticio `RESERVADO`. | `OperationService`, V4, pruebas de flujo. | Cumplido |
| Agenda sin solapamientos | Validación de aplicación más exclusiones GiST por conductor y vehículo. | `V5__assignment_overlap_exclusion_constraints.sql` y `OperationFlowIT`. | Cumplido |
| Incidencias y anuncios CRM | Registro, seguimiento, resolución, audiencia por organización/grupo y auditoría. | Rutas `/incidents`, `/announcements` y prueba de integración. | Cumplido |
| Actualización web controlada | Ticket de un uso, origen validado, sesión activa y difusión por organización/grupo. | `OperationStreamBroadcasterTest` y configuración WebSocket. | Cumplido |
| Reportes reales | Consultas PostgreSQL limitadas por rango/volumen para disponibilidad, asignaciones e incidencias. | `ReportService`, `OperationFlowIT`. | Cumplido |
| Descarga PDF/XLSX | Generación backend con PDFBox y Apache POI; respuesta adjunta sin caché. | `/api/v1/reports/*/export` y pruebas de firma binaria. | Cumplido |
| Auditoría consultable e inmutable | Tabla append-only existente; API y pantalla exclusivamente de lectura con filtros seguros. | `AuditEventController`, `AuditQueryService`, `audit.page`. | Cumplido |
| Arranque y cierre Windows | Dos scripts `.bat`, validación previa, espera de salud y preservación de datos al apagar. | `iniciar_ruta_fija.bat`, `finalizar_ruta_fija.bat`. | Cumplido |
| Calidad reproducible | Backend, frontend, imagen Docker y medición Java ejecutables localmente. | [Evidencia Fase 4](evidencia-fase-4-2026-08-30.md) y [reporte Java](../REPORTE_CUMPLIMIENTO_JAVA.md). | Cumplido |
| Aplicación móvil, GPS y aceptación del conductor | No implementados para no simular información que el CRM web no posee. | Decisión de alcance y modelo `SCHEDULED`. | Diferido por alcance |
| Base de datos administrada / operación productiva | No implementada; solo PostgreSQL 16 local dentro de Docker. | Configuración y auditoría final. | Diferido por alcance |
