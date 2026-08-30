# Estado de la Fase 4 — cierre del CRM web

- Fase: 4 — cierre funcional, reportes exportables, auditoría y operación local.
- Estado: implementada y validada localmente el 2026-08-30.
- Dictamen: aprobada con observaciones de preparación para producción.
- Persistencia: PostgreSQL 16 real, únicamente dentro de Docker Desktop y con
  volumen local persistente.

## Resultado entregado

La solución queda como un CRM web administrativo con reportes reales. No crea
una aplicación móvil ni una base de datos externa. Las funcionalidades de las
fases anteriores permanecen integradas y se añadieron los controles de cierre:

| Capacidad | Resultado de Fase 4 |
| --- | --- |
| Reportes | Disponibilidad, asignaciones e incidencias calculadas desde PostgreSQL; descarga real en PDF y XLSX, además de CSV protegido contra fórmulas. |
| Auditoría | Pantalla y API de solo lectura para `ADMINISTRADOR`, con paginación, filtros, detalle por tenant y metadatos depurados. |
| Tablero | Indicadores administrativos obtenidos de reportes persistidos y refrescados ante eventos operativos permitidos. |
| Seguridad de sesión | La autoridad se contrasta con el usuario activo y su rol actual en la base; un cambio de rol o desactivación invalida el acceso efectivo y revoca refresh tokens. |
| Operación concurrente | Flyway V5 impone exclusiones PostgreSQL para impedir el solapamiento de conductor o vehículo incluso ante solicitudes simultáneas. |
| Tiempo real | WebSocket con ticket efímero, verificación de usuario activo y visibilidad por organización y grupo; no difunde eventos ajenos a coordinadores. |
| Inicio local | `iniciar_ruta_fija.bat` valida y levanta los tres servicios; `finalizar_ruta_fija.bat` los detiene sin eliminar `postgres_data`. |

## Estados operativos vigentes

| Recurso | Regla aplicada |
| --- | --- |
| Conductor | `DISPONIBLE → RESERVADO → EN_SERVICIO → DISPONIBLE`, con variantes administrativas controladas hacia `DESCANSO` y `NO_DISPONIBLE`. |
| Vehículo | Solo `DISPONIBLE`, `EN_SERVICIO`, `MANTENIMIENTO` e `INACTIVO`; una reserva futura no lo convierte físicamente en `RESERVADO`. |
| Asignación | Se crea directamente como `SCHEDULED`; no existe aceptación o rechazo ficticio del conductor. Puede reservarse, iniciar, completarse o cancelarse de forma auditada. |

## Criterios de salida

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| API y modelo de datos | Verificado | `backend/mvnw.cmd verify`: 21 pruebas unitarias y 13 de integración, todas aprobadas sobre PostgreSQL 16 efímero. |
| Reportes realmente exportables | Verificado | Integración comprueba cabeceras binarias XLSX (`PK`) y PDF (`%PDF`), además de consultas persistidas por tenant. |
| Auditoría inmutable consultable | Verificado | Solo existen `GET /api/v1/audit-events` y `GET /api/v1/audit-events/{id}`; las pruebas cubren rol y aislamiento entre organizaciones. |
| Frontend | Verificado | Typecheck correcto, 12 archivos y 30 pruebas aprobadas, y build Angular de producción correcto. |
| Despliegue local | Verificado | Los contenedores `postgres`, `backend` y `frontend` terminaron saludables mediante el iniciador Windows. |
| Cumplimiento Java medible | Verificado | [Reporte de cumplimiento Java](../REPORTE_CUMPLIMIENTO_JAVA.md): 68,71 % de líneas no vacías comparables. |

## Límites reconocidos

El cierre aprueba el alcance autorizado de CRM web administrativo + reportes.
Quedan fuera, de forma deliberada, aplicación móvil, aceptación/rechazo del
conductor, GPS, mapas, FCM, SMTP, S3, fotografía, una base PostgreSQL externa y
la certificación operativa de un ambiente productivo. La auditoría final detalla
los controles pendientes antes de ampliar el alcance.
