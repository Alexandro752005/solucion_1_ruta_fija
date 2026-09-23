# ADR-006 - Operaciones moviles propias e idempotencia durable

- Estado: aceptada e implementada en F3.3.
- Fecha: 2026-09-22.
- Alcance: API operativa de conductor de los dias 5 y 6; no incluye Flutter ni reportes de F3.4.

## Contexto

F3.1B ya introdujo el contrato de estados de asignacion y la ubicacion vigente
en V7/V8. F3.2 separo la sesion movil JSON de la cookie del CRM. Faltaba una
frontera operativa que permitiera al conductor autenticado actuar solo sobre
sus recursos, resistir reintentos offline y mantener al CRM actualizado sin
permitir que un administrador simule la respuesta humana del conductor.

La aplicacion sigue siendo un monolito modular. Spring Boot conserva la
autoridad de tenant, conductor, reloj y transiciones; PostgreSQL 16 conserva
las restricciones y los recibos durables. Angular y el futuro cliente Flutter
son consumidores del contrato, no fuentes de autoridad.

## Decision

### Rutas propias de conductor

Las rutas bajo `/api/v1/mobile` requieren simultaneamente una sesion JWT con
`sessionChannel=MOBILE`, rol real `CONDUCTOR` y una relacion activa
`app_user -> driver` del mismo tenant. El cuerpo nunca decide `organizationId`,
`userId` o `driverId`.

| Recurso | Operaciones de F3.3 | Limite de seguridad |
| --- | --- | --- |
| Perfil y disponibilidad | `GET /me`, `GET/PUT /availability` | Solo la identidad de la sesion; no permite cambiar `RESERVADO` ni `EN_SERVICIO` manualmente. |
| Ubicacion vigente | `PUT /location/consent`, `PUT/DELETE /location/current` | Solo con consentimiento, atestacion de permiso y estado permitido; nunca crea historial. |
| Asignaciones | lista, detalle, `accept`, `reject`, `start`, `complete` | Solo asignaciones propias del conductor y tenant actual. |
| Incidencias | lista, detalle y creacion | Solo incidencias propias; las creadas por movil se etiquetan `MOBILE_APP`. |
| Comunicados | lista, detalle y `read` | Solo audiencia de organizacion o grupo visible; lectura idempotente por usuario. |

Todas las respuestas sensibles usan `Cache-Control: no-store`. Las rutas de
operacion movil no aceptan un JWT emitido para el canal web aunque el usuario
tenga rol `CONDUCTOR`.

### Estados y autoridad de la respuesta

`ADMIN_DIRECT` sigue el flujo CRM existente. `MOBILE_CONFIRMATION` nace en
`PENDING_RESPONSE` y solo puede ser aceptada o rechazada por el conductor
vinculado mediante su sesion movil. El CRM no tiene un endpoint que escriba
`accepted_at` o `rejected_at` como si fuese el conductor.

```text
PENDING_RESPONSE --aceptar movil--> SCHEDULED + conductor RESERVADO
PENDING_RESPONSE --rechazar movil--> REJECTED
PENDING_RESPONSE --vencer servidor--> EXPIRED
SCHEDULED --iniciar movil--> EN_SERVICIO
EN_SERVICIO --completar movil--> COMPLETED
```

El vehiculo nunca adopta el estado `RESERVADO`: permanece `DISPONIBLE` hasta
el inicio real del servicio. El vencimiento se resuelve por reloj de servidor
en cada comando y por tarea programada; no se deja al cliente declarar una
expiracion.

### Idempotencia de comandos

V10 incorpora `mobile_command_receipt` con `event_id` global, huella SHA-256
del comando, conductor, organizacion, asignacion, resultado escalar y tiempos.
No conserva tokens, cuerpos JSON ni coordenadas. Los cuatro comandos de estado
requieren `clientEventId`, version optimista y `occurredAt`.

Un reintento identico devuelve la respuesta previamente aplicada y
`X-Idempotent-Replay: true`. Reutilizar el mismo evento con otro conductor,
asignacion, tipo o huella produce `MOBILE_EVENT_CONFLICT` y no ejecuta una
segunda transicion. PostgreSQL usa un bloqueo asesor por evento para cerrar la
carrera entre lectura e insercion.

Los rechazos funcionales tambien dejan recibo durable. La transaccion de cada
comando no revierte un `ApplicationException`: las validaciones ocurren antes
de mutar la flota y una expiracion es una transicion deliberada que debe quedar
persistida junto con su resultado.

### Privacidad, auditoria y tiempo real

V8 conserva como maximo un punto en `driver_current_location`; el UPSERT por
`driver_id` reemplaza el anterior. Al revocar consentimiento o salir de un
estado permitido se elimina el punto. Coordenadas, precision, tokens y cuerpos
de autenticacion no se guardan en auditoria, errores ni eventos WebSocket.

Los cambios de asignacion, disponibilidad, vehiculo y expiracion publican
eventos acotados al tenant y grupo mediante `OperationEventPublisher`. Los
eventos incluyen identificadores, estado y version, no datos de ubicacion.

## Migraciones fisicas

| Version | Contenido |
| --- | --- |
| V9 | `incident.source` (`CRM_WEB` o `MOBILE_APP`) y `announcement_receipt` por usuario. |
| V10 | `mobile_command_receipt`, restricciones de resultado/huella y indices de consulta de recibos. |

La aplicacion de V9/V10 esta protegida por un backup `pg_dump`, restauracion a
una base de recuperacion nueva y ensayo Flyway antes de alterar desarrollo.
`rf_app` recibe solamente DML sobre las nuevas tablas, nunca `CREATE` de
esquema.

## Consecuencias

La API ya puede ser consumida por Flutter sin mocks de negocio. A cambio, todo
cliente debe conservar el evento de una accion offline hasta conocer su
resultado; no debe inventar otro UUID al reintentar. F3.4 seguira siendo
necesaria para OpenAPI completo, matriz exhaustiva de pruebas y reportes reales
de los estados de respuesta. Firebase/FCM, fotos, GPS de fondo, historial de
rutas y pantallas Flutter no se habilitan con esta decision.
