# Diseño de la Fase 3 — Operación web y reportes reales

## Decisiones de alcance

La Fase 3 mantiene el producto como **CRM web administrativo + reportes**. No
incluye aplicación móvil, GPS, mapas, captura de ubicación, FCM, SMTP ni una
base PostgreSQL externa. PostgreSQL 16 se ejecuta localmente dentro de Docker
Desktop y es la única base usada por la aplicación.

La creación de una asignación en el CRM entra directamente como `SCHEDULED`.
No existen `PENDING_RESPONSE` ni `REJECTED` en esta fase, porque no hay un
canal móvil real que pueda emitir una respuesta del conductor. El CRM tampoco
simula esa respuesta.

## Modelo operativo aprobado

| Recurso | Estados y transición controlada |
| --- | --- |
| Conductor | `DISPONIBLE → RESERVADO → EN_SERVICIO → DISPONIBLE`; desde los estados administrativos se permiten únicamente variantes controladas con `DESCANSO` y `NO_DISPONIBLE`. |
| Vehículo | Solo `DISPONIBLE`, `EN_SERVICIO`, `MANTENIMIENTO` e `INACTIVO`. `EN_SERVICIO` solo se obtiene al iniciar una asignación. |
| Asignación | `SCHEDULED → EN_SERVICIO → COMPLETED`, con cancelación auditada desde `SCHEDULED` o `EN_SERVICIO`. |

La reserva es una acción explícita del coordinador para apartar al conductor;
no representa aceptación del conductor. Durante ella el vehículo continúa
físicamente `DISPONIBLE`. Las reservas futuras se exponen como indisponibilidad
calculada desde las asignaciones programadas y se bloquean por solapamiento de
horarios del conductor o vehículo.

La migración V4 elimina `DESCONECTADO`, estado que pertenecía a un futuro canal
móvil/telemetría y no sería veraz en el CRM web.

## Persistencia y seguridad

- Flyway V3 crea `assignment`, `incident` y `announcement`, con claves por
  organización, índices de consulta, restricciones de rango e idempotencia de
  creación de asignaciones.
- Flyway V4 ajusta de forma compatible la restricción de disponibilidad del
  conductor para retirar el estado móvil `DESCONECTADO`.
- Las mutaciones usan bloqueo optimista por versión y devuelven conflicto ante
  una edición concurrente.
- Los permisos se vuelven a validar en API: administrador y coordinador operan
  según sus grupos visibles; los reportes son exclusivos de administrador.
- Las operaciones, incidencias y comunicaciones dejan auditoría persistida.

## Incidencias, comunicaciones y tiempo real

Las incidencias se registran manualmente desde el CRM como fuente `CRM_WEB`,
con categorías `AVERIA`, `ACCIDENTE`, `RETRASO` y `OTRO`, y el ciclo
`OPEN → FOLLOW_UP → RESOLVED`. No se presenta una incidencia como si viniera
de una aplicación móvil.

Los anuncios se dirigen a toda la organización o a un grupo visible. La
confirmación de lectura queda deliberadamente deshabilitada para esta fase.

El CRM recibe actualizaciones por WebSocket mediante tickets de un solo uso,
de vida corta y ligados al tenant. El ticket se emite con sesión autenticada,
el origen se valida durante el handshake y la difusión ocurre después de la
confirmación de la transacción. El diseño es local de una instancia; una futura
escala horizontal requerirá un bus de eventos compartido.

## Reportes reales

Los reportes no usan tarjetas precargadas ni datos inventados. Consultan datos
persistidos de asignaciones e incidencias y ofrecen:

- disponibilidad actual de conductores y vehículos;
- reservas futuras, servicios en curso e incidencias abiertas;
- asignaciones por estado y rango de fecha;
- incidencias por estado, categoría y rango de fecha;
- exportación CSV del resultado que el usuario consulta.

Cada rango se limita a 90 días y 10 000 registros. No se declara soporte de PDF
ni XLSX porque no se implementó un generador real de esos formatos.
