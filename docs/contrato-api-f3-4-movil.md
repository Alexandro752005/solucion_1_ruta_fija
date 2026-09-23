# Contrato API F3.4 — CRM, conductor móvil y reportes reales

- Estado: implementado y verificado localmente.
- Fecha: 2026-09-22.
- Base técnica: Spring Boot, Angular y PostgreSQL 16 nativo; sin Docker.

## Propósito y autoridad

Este contrato será consumido por Flutter en la siguiente fase. El backend es la fuente de autoridad para identidad, tenant, conductor vinculado, reloj, transiciones y retención de ubicación. Angular y Flutter son clientes: no pueden enviar organizationId, driverId o una hora de servidor como autoridad de una acción propia.

| Canal | Identidad exigida | Alcance |
| --- | --- | --- |
| CRM /api/v1/* | JWT web de ADMIN | Solo el tenant del ADMIN autenticado. |
| Móvil /api/v1/mobile/* | JWT de CONDUCTOR, sessionChannel=MOBILE y driverId vigente | Solo el conductor vinculado del tenant. |
| Reportes /api/v1/reports/* | JWT web de ADMIN | Solo datos persistidos de su tenant. |

Una sesión web de conductor no sirve como sesión móvil. Una UUID ajena, incluso la de otro conductor del mismo tenant, se devuelve como recurso inexistente.

## Asignaciones y compatibilidad CRM

| responseMode | Estado inicial | Quién puede responder | Límite CRM |
| --- | --- | --- | --- |
| ADMIN_DIRECT | SCHEDULED | No aplica | ADMIN puede reservar, iniciar, completar o cancelar según el estado. |
| MOBILE_CONFIRMATION | PENDING_RESPONSE | Solo su conductor móvil vinculado | ADMIN puede verla o cancelarla; nunca aceptarla o rechazarla. |

Omitir responseMode mantiene compatibilidad y equivale a ADMIN_DIRECT. Angular crea el flujo directo. La API admite MOBILE_CONFIRMATION para que Flutter la atienda cuando exista, sin permitir que el CRM sustituya la decisión humana.

~~~
ADMIN_DIRECT: SCHEDULED --reservar--> SCHEDULED(reserved_at)
                       --iniciar--> EN_SERVICIO --completar--> COMPLETED

MOBILE_CONFIRMATION: PENDING_RESPONSE --aceptar móvil--> SCHEDULED(reserved_at)
                                      --rechazar móvil--> REJECTED
                                      --vencer servidor--> EXPIRED
                                      --cancelar ADMIN--> CANCELLED
~~~

El vehículo nunca adopta RESERVADO. La aceptación auténtica reserva al conductor; el vehículo continúa DISPONIBLE hasta iniciar el servicio. PostgreSQL bloquea solapamientos de PENDING_RESPONSE, SCHEDULED y EN_SERVICIO.

## Rutas móviles

Todas las respuestas de estas rutas usan Cache-Control: no-store.

| Recurso | Rutas | Límite |
| --- | --- | --- |
| Sesión | POST /mobile/auth/login, /refresh, /logout | JSON separado de la cookie HttpOnly del CRM. El refresh se guarda solo en almacenamiento seguro del SO. |
| Perfil | GET /mobile/me, GET/PUT /mobile/availability | Solo identidad propia; no fuerza RESERVADO ni EN_SERVICIO. |
| Ubicación | PUT /mobile/location/consent, PUT/DELETE /mobile/location/current | Punto propio, vigente y efímero; no hay lectura de ajenos. |
| Asignaciones | GET /mobile/assignments, GET /mobile/assignments/{id}, POST /{id}/accept, /reject, /start, /complete | Todas las mutaciones son propias, versionadas e idempotentes. |
| Incidencias | GET/POST /mobile/incidents, GET /mobile/incidents/{id} | Solo hechos propios; la fuente es MOBILE_APP. |
| Comunicados | GET /mobile/announcements, GET /mobile/announcements/{id}, POST /{id}/read | Solo audiencia visible de organización o grupo propio. |

El prefijo completo es /api/v1; por ejemplo, POST /api/v1/mobile/assignments/{assignmentId}/accept.

## DTOs críticos

### Alta administrativa

~~~json
{
  "driverId": "uuid",
  "vehicleId": "uuid",
  "originText": "Terminal Norte",
  "destinationText": "Terminal Sur",
  "scheduledAt": "2026-09-23T14:00:00Z",
  "scheduledEndAt": "2026-09-23T15:00:00Z",
  "responseMode": "MOBILE_CONFIRMATION",
  "responseDeadlineAt": "2026-09-23T12:00:00Z"
}
~~~

responseDeadlineAt es obligatorio solo con MOBILE_CONFIRMATION: UTC, posterior al reloj del servidor y anterior a scheduledAt. La respuesta de una asignación incluye responseMode, responseDeadlineAt, acceptedAt, rejectedAt, rejectionReason y expiredAt. Las marcas auténticas nunca se escriben desde el body CRM.

### Comando móvil idempotente

~~~json
{
  "clientEventId": "uuid-global-y-durable",
  "version": 0,
  "occurredAt": "2026-09-23T11:57:00Z"
}
~~~

Para reject se añade opcionalmente reason, máximo 300 caracteres. El cliente conserva exactamente el mismo UUID, versión, instante y motivo al reintentar. La primera aplicación responde X-Idempotent-Replay: false; el reintento idéntico devuelve el resultado durable con X-Idempotent-Replay: true. Reutilizar el UUID en otro comando, conductor, asignación o contenido genera MOBILE_EVENT_CONFLICT.

### Ubicación vigente

~~~json
{
  "latitude": -12.046374,
  "longitude": -77.042793,
  "accuracyM": 8.2,
  "capturedAt": "2026-09-23T11:57:00Z",
  "permissionGranted": true
}
~~~

Antes debe existir locationConsent=true. permissionGranted=false se rechaza; es una atestación del cliente, no una prueba criptográfica de Android. El servidor calcula retención y fuente, conserva máximo una fila por conductor con UPSERT y la elimina al revocar consentimiento o salir de estado permitido.

## Errores relevantes

| Código | Acción del cliente |
| --- | --- |
| MOBILE_SESSION_REQUIRED | Obtener JWT del canal móvil, no reutilizar el web. |
| MOBILE_USER_NOT_DRIVER / DRIVER_INACTIVE | Corregir vínculo o estado de la cuenta. |
| RESOURCE_NOT_FOUND | No revelar ni reintentar UUID ajena. |
| ASSIGNMENT_INVALID_TRANSITION | Recargar el estado; no forzar la transición. |
| ASSIGNMENT_RESPONSE_EXPIRED | La decisión ya fue determinada por servidor. |
| RESOURCE_VERSION_CONFLICT | Actualizar versión y crear un evento nuevo solo si corresponde. |
| MOBILE_EVENT_CONFLICT | No reutilizar ese clientEventId para contenido distinto. |
| LOCATION_CONSENT_REQUIRED / LOCATION_PERMISSION_NOT_REPORTED | Solicitar consentimiento o permiso antes de enviar el punto. |

El error uniforme incluye status, code, message, path, correlationId y detalle de campo cuando aplica. No revela tokens ni coordenadas.

## Reportes reales

GET /api/v1/reports/assignments?from=YYYY-MM-DD&to=YYYY-MM-DD consulta assignment persistida, aislada por tenant y por scheduled_at dentro de los días locales de la organización. No usa porcentajes, filas o tarjetas simuladas.

totalsByStatus siempre mantiene este orden:

~~~
PENDING_RESPONSE, SCHEDULED, EN_SERVICIO, COMPLETED,
REJECTED, CANCELLED, EXPIRED
~~~

Un valor 0 significa que SQL no encontró filas persistidas de ese estado en el período; no es una proyección. PDF/XLSX llaman al mismo servicio de reporte y anexan los mismos totales. El CRM etiqueta los tres estados móviles en filtros, detalle y reportes.

## Privacidad y exclusiones

- No existe historial GPS, recorridos, mapas ni telemetría de fondo.
- Latitud, longitud y precisión no se guardan en auditoría, WebSocket, reportes genéricos ni respuestas de error.
- Flutter, FCM, push, fotos, GPS en segundo plano y offline avanzado no son parte de F3.4.
- El contrato vivo se consulta localmente en http://127.0.0.1:8080/v3/api-docs cuando el runtime está iniciado.
