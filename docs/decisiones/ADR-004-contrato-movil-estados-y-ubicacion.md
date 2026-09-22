# ADR-004 - Contrato de asignaciones móviles y ubicación vigente

- Estado: V7/V8 implementadas en F3.1B; sesión y endpoints móviles pendientes de F3.2/F3.3.
- Fecha: 2026-09-22.
- Alcance: días 5 y 6 de la Etapa 2.

## Contexto

La puerta G2 está aprobada. La línea base productiva usa PostgreSQL 16 nativo,
Flyway V1-V8, los roles `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`, y un monolito
modular Spring Boot/Angular. No hay una API móvil ni Flutter implementados aún.

En V3-V5, una asignación existe en `SCHEDULED`, `EN_SERVICIO`, `COMPLETED` o
`CANCELLED`. `reserved_at` representa una reserva real: cambia al conductor de
`DISPONIBLE` a `RESERVADO`, pero nunca convierte al vehículo en `RESERVADO`.
Las exclusiones GiST de PostgreSQL ya impiden solapamientos de conductor y
vehículo para `SCHEDULED` y `EN_SERVICIO`.

La Etapa 2 incorpora una aplicación Flutter para el rol `CONDUCTOR`. Sin un
contrato previo, sería fácil introducir dos errores graves: simular desde el
CRM que un conductor aceptó una asignación, o almacenar un historial GPS sin
necesidad operativa ni consentimiento funcional.

## Decisión

### 1. Fuente de autoridad

El backend conserva una única máquina de estados y es la fuente de autoridad
para identidad, tenant, conductor, fechas de servidor y reglas operativas.
Flutter y Angular son clientes: no deciden el tenant ni pueden forzar una
transición. La API móvil futura resolverá `organizationId`, `userId` y
`driverId` desde la sesión y la relación `driver.user_id`; no los aceptará como
autoridad desde el cuerpo de una acción propia.

### 2. Dos modos de creación de asignación

V7 añadirá `response_mode` con exactamente estos valores:

| Modo | Creador | Estado inicial | Finalidad |
| --- | --- | --- | --- |
| `ADMIN_DIRECT` | `ADMIN` | `SCHEDULED` | Conserva el flujo CRM actual sin respuesta móvil. |
| `MOBILE_CONFIRMATION` | `ADMIN` | `PENDING_RESPONSE` | Solicita una respuesta auténtica al conductor vinculado. |

Para conservar compatibilidad, las filas V1-V6 y una solicitud web que omita
temporalmente `responseMode` se interpretarán como `ADMIN_DIRECT`. El servicio
de aplicación deberá enviar el valor de forma explícita cuando se actualicen
todos los clientes.

`MOBILE_CONFIRMATION` exigirá antes de persistir una cuenta activa con rol
`CONDUCTOR`, vinculada de manera única al conductor activo, dentro del mismo
tenant. También exige vehículo vinculado y agenda sin solapamiento. Un plazo de
respuesta UTC (`response_deadline_at`) será obligatorio, posterior al reloj del
servidor y anterior a `scheduled_at`.

No se habilita una acción web que simule la aceptación o el rechazo de un
conductor. En particular:

- `ADMIN` puede crear, editar según las reglas vigentes, cancelar y administrar
  la operación, pero no puede escribir `accepted_at`, `rejected_at` ni
  `expired_at` como si fuera el conductor.
- La reserva CRM existente es válida exclusivamente para `ADMIN_DIRECT`; no
  convierte una solicitud `MOBILE_CONFIRMATION` pendiente en aceptada.
- La aceptación y el rechazo de `MOBILE_CONFIRMATION` serán acciones propias
  del `CONDUCTOR` autenticado en F3.3. La auditoría registrará a ese usuario
  real, nunca a un actor sustituido por el CRM.

### 3. Máquina de estados y efectos sobre recursos

Los estados de asignación que V7 materializará son:

```text
PENDING_RESPONSE, SCHEDULED, EN_SERVICIO, COMPLETED,
REJECTED, CANCELLED, EXPIRED
```

El estado `RESERVADO` pertenece al conductor, no a `assignment` ni a
`vehicle`. El flujo acordado es:

```text
ADMIN_DIRECT
SCHEDULED --reservar CRM--> SCHEDULED(reserved_at)
          --iniciar--> EN_SERVICIO --completar--> COMPLETED

MOBILE_CONFIRMATION
PENDING_RESPONSE --aceptar conductor--> SCHEDULED(reserved_at)
                 --rechazar conductor--> REJECTED
                 --vencer servidor--> EXPIRED
                 --cancelar ADMIN--> CANCELLED
SCHEDULED --iniciar--> EN_SERVICIO --completar--> COMPLETED
```

Las reglas invariables son las siguientes:

1. Una fila `PENDING_RESPONSE` bloquea el mismo intervalo en las exclusiones
   GiST, pero no cambia físicamente al conductor ni al vehículo. La futura
   indisponibilidad se deriva de la asignación programada.
2. Al aceptar antes del plazo, una transacción atómica pone la asignación en
   `SCHEDULED`, registra `accepted_at` y `reserved_at`, y pasa el conductor de
   `DISPONIBLE` a `RESERVADO`. El vehículo sigue `DISPONIBLE` hasta `start`.
3. Un rechazo guarda `rejected_at` y, como máximo, un `rejection_reason` de 300
   caracteres; no altera conductor ni vehículo.
4. Solo el reloj del servidor determina que el plazo venció. F3.3 verificará el
   plazo dentro de cada comando y normalizará pendientes vencidas; el cliente no
   puede declarar una expiración.
5. `CANCELLED` desde pendiente no libera recursos porque ninguno fue reservado.
   Desde una asignación reservada o en servicio conserva las reglas actuales de
   liberación del conductor y, si corresponde, del vehículo.
6. `DESCANSO` y `NO_DISPONIBLE` permanecen variantes administrativas
   controladas del conductor. `RESERVADO` y `EN_SERVICIO` no se cambian de forma
   manual. El vehículo conserva solo `DISPONIBLE`, `EN_SERVICIO`,
   `MANTENIMIENTO` e `INACTIVO`.

### 4. Contrato físico de V7

F3.1B añadirá a `assignment`, sin crear otra entidad de reserva, estos campos:

| Campo | Regla acordada |
| --- | --- |
| `response_mode` | `ADMIN_DIRECT` o `MOBILE_CONFIRMATION`; todas las filas históricas quedan en `ADMIN_DIRECT`. |
| `response_deadline_at` | Obligatorio solo para una respuesta móvil pendiente. |
| `accepted_at` | Solo registra aceptación auténtica del conductor. |
| `rejected_at` | Solo registra rechazo auténtico del conductor. |
| `rejection_reason` | Opcional, máximo 300 caracteres y solo con rechazo. |
| `expired_at` | Solo registra expiración determinada por servidor. |

V7 recreará las restricciones `ck_assignment_status` y de coherencia temporal,
conservará PK, FK, versión e idempotencia de creación actuales, y recreará las
dos exclusiones GiST para `PENDING_RESPONSE`, `SCHEDULED` y `EN_SERVICIO`.
También incorporará índices por conductor, estado y plazo de respuesta. La
migración no cambia estados históricos, identificadores, reservas ni sesiones.

La idempotencia de creación actual de `assignment` no se reutilizará como
recibo de comandos móviles. El identificador de evento móvil y su recibo físico
pertenecen a V10; F3.1A solo reserva esta frontera para no duplicar transiciones
cuando Flutter reintente una acción.

### 5. Ubicación vigente y privacidad de V8

V8 creará exclusivamente `driver_current_location`. Su PK será `driver_id`, de
modo que PostgreSQL conservará como máximo una ubicación vigente por conductor.
No se crea una tabla de historial, recorrido, mapa ni telemetría en segundo
plano.

| Campo | Regla acordada |
| --- | --- |
| `driver_id` | PK y FK al conductor. |
| `organization_id` | No nulo, con FK compuesta `(driver_id, organization_id)` al conductor para impedir cruces de tenant. V8 añadirá la clave única técnica necesaria en `driver`. |
| `latitude` / `longitude` | `NUMERIC(9,6)`, dentro de `[-90,90]` y `[-180,180]`. |
| `accuracy_m` | `NUMERIC(8,2)` no negativo. |
| `captured_at` | Hora declarada por dispositivo, validada contra una tolerancia de reloj del servidor. |
| `received_at` | Hora generada por servidor. |
| `expires_at` | Hora generada por servidor con TTL configurable; el valor inicial será cinco minutos y nunca lo impone el cliente. |
| `source` | Solo `MOBILE_APP`; no es elegible por el cliente. |

Se aplicará UPSERT por `driver_id` y un índice `(organization_id, expires_at)`.
Un punto vencido no se devuelve y se elimina en la limpieza de vigencia. Revocar
el consentimiento, desactivar al conductor o salir de `DISPONIBLE`/
`EN_SERVICIO` elimina su fila vigente para minimizar retención.

El permiso del sistema operativo y el consentimiento funcional son conceptos
distintos. Flutter debe comprobar el permiso Android antes de enviar. El
backend no puede demostrar criptográficamente ese permiso, por lo que no se le
atribuye esa autoridad; sí aplica el consentimiento funcional
`driver.location_consent`, el estado permitido, la identidad propia y la
validación de coordenadas. Si más adelante el contrato transporta una señal
`permissionGranted`, será una atestación de cliente para rechazar un `false`, no
una prueba de autorización.

La exposición queda limitada desde el diseño:

- `CONDUCTOR` solo crea, reemplaza o elimina su propia ubicación; nunca consulta
  ubicaciones ajenas.
- Un eventual `ADMIN` solo podrá consultar una ubicación vigente de su propio
  tenant y requerirá contrato y auditoría explícitos en F3.3; `SUPER_ADMIN` no
  hereda operación de tenant.
- Latitud, longitud y precisión no se guardan en `audit_event`, logs, errores,
  reportes genéricos, OpenAPI de perfil ni broadcasts WebSocket. La auditoría
  solo registra la acción y metadatos no sensibles.

### 6. Fronteras de las siguientes subfases

F3.1B implementó únicamente V7 y V8, ensayadas desde un esquema vacío y una
base histórica V5 que avanza por V6. F3.2 definirá login, refresh y logout
móviles con transporte separado de la cookie web y errores uniformes. F3.3
construirá los endpoints, la transición autenticada, la limpieza por plazo y el
UPSERT. F3.4 publicará OpenAPI, pruebas de aislamiento/privacidad/idempotencia
y reportes reales de `PENDING_RESPONSE`, `REJECTED` y `EXPIRED`.

V9, V10, Firebase/FCM, fotos, GPS en segundo plano, historial de rutas y
Flutter quedan fuera de F3.1A. PostgreSQL seguirá privado en `127.0.0.1:5432`;
no se introduce Docker ni se expone la base de datos a la aplicación móvil.

## Consecuencias

El CRM mantiene su flujo directo sin inventar decisiones de conductores, y la
app móvil tendrá un contrato verificable antes de construir sus pantallas. El
costo es ampliar cuidadosamente restricciones, DTOs, pruebas y reportes en las
subfases siguientes. Esa complejidad es necesaria para conservar aislamiento
multi-tenant, trazabilidad real y privacidad de la ubicación.
