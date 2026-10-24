# ADR-008: Offline minimo M8 para operaciones moviles

- Estado: Aceptada
- Fecha: 2026-09-25
- Decision: cola persistente, limitada, visible y conciliada solamente en
  primer plano.

## Contexto

La aplicacion de conductor ya puede consultar y cambiar datos operativos. Una
interrupcion breve de red no debe hacer que el conductor pierda una accion de
disponibilidad, inicio de servicio o cierre de servicio. A la vez, una cola no
puede inventar una aceptacion, omitir conflictos ni ejecutar operaciones fuera
del contexto autenticado del conductor.

M8 agrega un mecanismo de recuperacion acotado. No es un motor generico de
sincronizacion ni una promesa de trabajo en segundo plano.

## Decision

Se mantiene una unica cola M8 persistente por sesion de conductor. La cola:

- admite como maximo 10 eventos pendientes;
- conserva el orden FIFO de creacion;
- se identifica por usuario autenticado y conductor, para impedir que datos de
  una sesion se mezclen con otra;
- genera `eventId` una sola vez y conserva el mismo UUID, fecha y carga en
  cada reintento;
- se muestra con un indicador visible y una pantalla de pendientes;
- se procesa solo cuando el conductor pulsa una accion visible de sincronizar
  o realiza otra accion operativa en primer plano;
- se limpia al cerrar sesion, y se conserva al cerrar o reiniciar normalmente
  la aplicacion.

Los unicos tipos permitidos son:

| Tipo | Agregado | Datos de concurrencia |
| --- | --- | --- |
| `AVAILABILITY_CHANGE` | conductor propio | `expectedAvailabilityUpdatedAt` |
| `SERVICE_START` | asignacion propia | `version` de asignacion |
| `SERVICE_COMPLETE` | asignacion propia | `version` de asignacion |

No se encolan GPS, ubicacion, incidencias, fotos, comunicados, asignaciones
nuevas, aceptacion/rechazo de asignacion, datos de sesion ni credenciales.
Aceptacion y rechazo siguen siendo acciones manuales M4 y no avanzan por una
cola automatica.

## Contrato de conciliacion

El cliente usa un token de conductor y envia:

```http
POST /api/v1/mobile/sync/events
Content-Type: application/json
Authorization: Bearer <token-conductor>
```

La carga contiene entre 1 y 10 eventos, en el orden local:

```json
{
  "events": [
    {
      "eventId": "3d787beb-6f2c-4f3b-8ceb-e7f90e889b14",
      "type": "AVAILABILITY_CHANGE",
      "occurredAt": "2026-09-25T18:00:00Z",
      "targetAvailability": "DISPONIBLE",
      "expectedAvailabilityUpdatedAt": "2026-09-25T17:45:00Z"
    }
  ]
}
```

Para `SERVICE_START` y `SERVICE_COMPLETE`, el evento contiene `assignmentId`,
`version` y `occurredAt`; no contiene campos de disponibilidad. Para
`AVAILABILITY_CHANGE`, no contiene `assignmentId` ni `version`.

La respuesta devuelve un resultado escalar por evento procesado:

```json
{
  "results": [
    {
      "eventId": "3d787beb-6f2c-4f3b-8ceb-e7f90e889b14",
      "status": "APPLIED",
      "serverState": "DISPONIBLE",
      "errorCode": null
    }
  ]
}
```

Los estados tienen esta semantica obligatoria:

| Estado | Accion de la cola |
| --- | --- |
| `APPLIED` | Eliminar el evento y continuar con el siguiente. |
| `ALREADY_APPLIED` | Eliminar el evento: es la respuesta idempotente al mismo `eventId` y carga. |
| `CONFLICT` | Detener FIFO, conservar el evento y pedir conciliacion visible. |
| `REJECTED` | Detener FIFO, conservar el evento y mostrar el error funcional. |
| Error temporal | Conservar el evento, registrar intento y aplicar espera acotada antes de un nuevo intento. |

Un resultado definitivo (`CONFLICT` o `REJECTED`) nunca se salta. La persona
debe refrescar el estado del servidor y decidir si descarta el evento bloqueado
o toma una nueva accion permitida.

La espera de un error temporal inicia en 5 segundos y se duplica por intento
hasta un maximo de 300 segundos. Es una marca informativa y de control: no
activa un temporizador ni envio automatico. El boton manual de sincronizacion
puede solicitar un intento anticipado y sigue conservando el mismo evento.

## Idempotencia, concurrencia y seguridad

- El backend registra el recibo de cada `eventId` con tipo, agregado y huella
  de la carga. Repetir el mismo evento devuelve `ALREADY_APPLIED`; reutilizar
  el UUID para otra carga es un conflicto.
- La disponibilidad compara el instante `expectedAvailabilityUpdatedAt` con
  el conductor bloqueado. Si cambio en el servidor, responde `CONFLICT` y no
  reemplaza el estado actual.
- Inicio y cierre reutilizan el control de version y las transiciones de
  asignacion existentes.
- El endpoint exige el rol `CONDUCTOR`, aplica el alcance del conductor y no
  acepta un identificador de conductor provisto por el cliente.
- La API no devuelve ubicacion, secretos, token de sesion ni una copia de la
  carga original.

## Restricciones explicitas

- No hay polling, listener de conectividad, `WorkManager`, servicio Android,
  alarma, tarea programada ni sincronizacion al arrancar silenciosamente.
- No existe envio en segundo plano ni ejecucion cuando la aplicacion esta
  cerrada.
- No se reordena, agrupa ni compacta una secuencia FIFO.
- El limite de 10 evita que una perdida prolongada de red convierta el
  dispositivo en fuente operativa no controlada.

## Consecuencias

La recuperacion de red es transparente solo hasta el limite definido: el
conductor ve los pendientes y decide cuando conciliarlos. El resultado es mas
seguro que intentar inferir conectividad o cambiar estados en segundo plano,
pero exige una prueba manual con desconexion real antes de declarar M8 verde.
