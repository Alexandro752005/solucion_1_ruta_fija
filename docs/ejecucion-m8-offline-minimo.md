# M8 - Offline minimo, limitado y visible

## Objetivo verificable

Permitir que una accion de disponibilidad, inicio de servicio o cierre de
servicio sobreviva a una falla temporal de red sin duplicar el cambio ni
adelantar eventos posteriores. M8 aporta 5 puntos a la matriz movil: el total
esperado pasa de 66/80 a 71/80 una vez que todas las pruebas tecnicas y
manuales concluyan.

## Alcance que debe cerrar la fase

1. El backend acepta `POST /api/v1/mobile/sync/events` para un lote ordenado
   de 1 a 10 eventos de conductor.
2. El backend usa recibos idempotentes para disponibilidad, inicio y cierre.
3. La aplicacion conserva una cola persistente, aislada por usuario y
   conductor, con contador y vista de pendientes.
4. El reintento conserva exactamente el mismo `eventId` y se ejecuta solo en
   primer plano por una accion visible.
5. Un conflicto o rechazo detiene la cola y requiere conciliacion explicita.

## Limites de M8

| Incluido | Excluido |
| --- | --- |
| Disponibilidad, inicio y cierre de servicio | GPS, ubicacion, fotos e incidencias |
| FIFO, maximo 10, persistencia y reintento manual | Segundo plano, polling o tarea programada |
| Idempotencia, version y conflicto | Aceptar/rechazar automaticamente una asignacion |
| Indicador y pantalla de pendientes | Comunicados, notificaciones push y mapas |

## Secuencia de construccion

### M8.1 - Contrato y persistencia del servidor

- Incorporar `AVAILABILITY_CHANGE` a los recibos moviles ya durables.
- Mantener `assignmentId` nulo solo para disponibilidad y obligatorio para
  inicio/cierre.
- Validar en el servidor los campos permitidos por tipo de evento.
- Bloquear el conductor al comparar su marca `updatedAt`; para asignaciones,
  reutilizar la version y la transicion existente.
- Registrar auditoria una sola vez por aplicacion real, nunca por replay.

### M8.2 - Cola movil en primer plano

- Crear el evento antes de intentar enviarlo y persistirlo de forma segura.
- Enviar el primer pendiente. Si la red falla temporalmente, conservarlo con
  contador de intento y proximo intento acotado: 5 segundos, duplicado hasta
  300 segundos como maximo.
- El boton **Sincronizar pendientes** puede intentar el primer pendiente aun
  cuando exista espera; no hay reintento silencioso.
- Tras `APPLIED` o `ALREADY_APPLIED`, borrar solo ese elemento y seguir FIFO.
- Tras `CONFLICT` o `REJECTED`, bloquear la secuencia y permitir descartar
  despues de leer el estado remoto.
- Al cerrar sesion, borrar solo la cola del usuario/conductor actual.

### M8.3 - Regresion y puerta manual

- Ejecutar pruebas backend, migraciones, contrato y pruebas Flutter.
- Ejecutar el auditor M8 que encadena las regresiones de M1 a M7 y verifica
  limite, tipos permitidos, persistencia, FIFO, UUID estable y prohibiciones
  de segundo plano.
- Realizar la prueba fisica descrita abajo.

## Prueba manual en Android fisico

Precondiciones: backend local iniciado, un conductor activo, una asignacion
propia apta para iniciar o cerrar y un dispositivo con depuracion USB.

### Preparación reproducible por USB

Desde la raíz inicie el runtime. En otra consola, prepare el teléfono y abra
Flutter desde `mobile/`:

~~~powershell
.\iniciar_ruta_fija.bat

$env:Path = 'D:\dev\flutter\bin;D:\Android\Sdk\platform-tools;' + $env:Path
adb devices
adb reverse tcp:8080 tcp:8080
Set-Location mobile
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

Compruebe que `adb devices` muestra el equipo como `device`, no como
`unauthorized`. Inicie sesión como un `CONDUCTOR` activo y vinculado. No incluya
corchetes, usuario, contraseña ni puerto de PostgreSQL en `RF_API_BASE_URL`.

Cuando se usa `adb reverse`, desactivar Wi-Fi o datos del teléfono **no** crea
una desconexión válida: el dispositivo aún puede llegar al backend por USB. Para
simular una falla temporal sin detener el CRM, ejecute desde la consola del
equipo:

~~~powershell
adb reverse --remove tcp:8080
~~~

Para restaurar el canal después de crear el pendiente:

~~~powershell
adb reverse tcp:8080 tcp:8080
~~~

1. Abra la aplicacion con conectividad y entre como conductor.
2. Verifique que el indicador de pendientes marca cero.
3. Quite temporalmente el reverse con `adb reverse --remove tcp:8080`.
4. Cambie la disponibilidad a un estado valido, por ejemplo `DISPONIBLE` a
    `DESCANSO`. La interfaz debe indicar que la accion quedo pendiente y el
    contador debe aumentar a uno.
5. Cierre y vuelva a abrir la aplicacion sin cerrar sesion. El pendiente debe
    continuar visible con el mismo UUID interno.
6. Restaure el reverse y pulse **Sincronizar pendientes**. Debe aparecer
    `APPLIED` o `ALREADY_APPLIED`, el contador vuelve a cero y el CRM refleja
    el estado una sola vez.
7. Repita con iniciar o cerrar una asignacion propia. Nunca deben aparecer
    dos transiciones para una misma accion.
8. Para validar FIFO, quite otra vez el reverse, deje pendiente primero un
    cambio de disponibilidad y luego un inicio/cierre válido de otra
    asignación. Desde el CRM cambie la disponibilidad del mismo conductor a un
    estado permitido antes de restaurar el reverse. Al sincronizar, la primera
    acción debe quedar `CONFLICT` y la segunda debe seguir esperando, sin
    ejecutarse.
9. Con un pendiente aún existente, deje la aplicación en segundo plano y
    restaure el reverse. Espere al menos 30 segundos: el contador no debe bajar
    ni debe ocurrir una transición hasta pulsar **Sincronizar pendientes**.
10. Cierre sesión. Preferentemente entre con otro conductor y confirme que no
    ve pendientes de la cuenta anterior; como mínimo, vuelva a entrar con la
    misma cuenta y compruebe que el cierre de sesión eliminó su cola local.

## Criterios para VERDE - M8

- Migracion y pruebas de backend correctas sobre PostgreSQL local.
- `flutter analyze`, pruebas Flutter y APK debug correctos.
- Auditor M8 con resultado `M8_OFFLINE_MINIMUM_AUDIT=PASS`.
- La prueba fisica valida desconexion, persistencia, replay idempotente,
  conflicto FIFO y limpieza al cerrar sesion.
- El usuario confirma manualmente el resultado verde. Hasta entonces M8 queda
  en ejecucion, aunque las pruebas automaticas sean correctas.
