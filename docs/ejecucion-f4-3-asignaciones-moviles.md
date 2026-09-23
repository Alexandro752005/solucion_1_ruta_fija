# F4.3 — Asignaciones móviles idempotentes del conductor

## Objetivo y alcance

F4.3 continúa los días 7–9 de la Etapa 2 después de F4.2. Implementa M4 con
la API que F3.3/F3.4 ya dejó protegida y verificada; no agrega tablas,
migraciones, rutas backend, dependencias ni acciones CRM.

| Módulo | Puntos | Estado después de F4.3 |
| --- | ---: | --- |
| M1. Base, arquitectura y navegación | 8 | Cerrado. |
| M2. Sesión y perfil | 12 | Cerrado. |
| M3. Disponibilidad | 8 | Cerrado. |
| M4. Asignaciones | 18 | Lista, detalle y acciones propias idempotentes. |
| **Total verificable** | **80** | **46/80** |

Quedan fuera de esta fase: incidencias (M5), comunicados (M6), ubicación y
consentimiento (M7), una cola offline automática (M8), release/APK final y
evidencia de dispositivo físico (M9).

## Autoridad y rutas consumidas

Flutter no recibe ni envía `organizationId`, `userId` o un `driverId` como
autoridad de una acción. Spring Boot determina el conductor de la sesión
`MOBILE`, el tenant, la hora y cada transición; PostgreSQL conserva el recibo
idempotente.

| Pantalla | Ruta propia | Acción disponible |
| --- | --- | --- |
| Lista | `GET /mobile/assignments` | Filtrar y paginar solo asignaciones propias. |
| Detalle | `GET /mobile/assignments/{id}` | Consultar el estado actual propio. |
| Respuesta | `POST /{id}/accept` o `/reject` | Solo `PENDING_RESPONSE` con `MOBILE_CONFIRMATION`. |
| Operación | `POST /{id}/start` o `/complete` | Solo cuando el servidor permite la transición. |

El CRM conserva la frontera aprobada: un ADMIN puede crear o cancelar una
solicitud, pero no puede aceptar ni rechazar en lugar del conductor.

## Reintento seguro de un comando

~~~text
Detalle con versión actual
        |
crear UUID + occurredAt una sola vez
        |
guardar comando protegido antes de enviar
        |
POST propio con clientEventId, version y occurredAt
  | éxito / replay -----------------> borrar comando y recargar detalle
  | red o resultado desconocido ----> conservar y mostrar “Reintentar exactamente”
  | resultado 4xx definitivo -------> borrar, recargar estado del servidor
~~~

El reintento no crea otro UUID ni otro instante. Si el servidor ya procesó el
comando, responde `X-Idempotent-Replay: true` y devuelve la transición durable
sin duplicarla. Si la aplicación se cierra después de un fallo de red, el
comando queda en el almacenamiento protegido y solo se muestra al mismo
conductor cuando vuelva a iniciar sesión.

Esto **no** es todavía una cola offline M8: no hay envío automático, trabajo en
segundo plano ni sincronización masiva. El conductor confirma explícitamente el
reintento desde el detalle.

## Seguridad y experiencia

- `flutter_secure_storage` mantiene los comandos pendientes en un espacio
  Android separado del refresh token; no guarda contraseñas, access tokens ni
  cuerpos de autenticación.
- La aplicación muestra confirmación antes de aceptar, rechazar, iniciar o
  completar. El rechazo admite un motivo opcional de hasta 300 caracteres.
- La interfaz bloquea acciones incompatibles como ayuda al conductor, pero el
  backend continúa validando versión, plazo, vehículo y estado de la flota.
- No se implementan acciones de asignaciones ajenas, aceptación desde el CRM,
  GPS, mapas, FCM, fotos ni Docker.

## Validación de cierre

Desde la raíz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaF43MobileAssignments.ps1
~~~

La auditoría primero comprueba F3.4 y PostgreSQL nativo en modo lectura. Luego
revisa el contrato Flutter, la persistencia previa al envío, la ausencia de una
falsa aceptación CRM, análisis, pruebas y APK debug. Una salida válida es:

~~~text
F4_3_MOBILE_ASSIGNMENTS=PASS ... m4=18/80 total=46/80 android=READY ... docker=0
~~~

## Prueba manual posterior

Con Spring Boot iniciado y un teléfono Android USB, sin exponer PostgreSQL:

~~~powershell
adb reverse tcp:8080 tcp:8080
Set-Location mobile
$env:Path = 'D:\dev\flutter\bin;' + $env:Path
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

El ADMIN crea una asignación `MOBILE_CONFIRMATION` propia del conductor. En el
teléfono se comprueba lista, detalle, confirmación, aceptación/rechazo y la
protección del reintento. F4.4 no comienza hasta `VERDE — F4.3`.
