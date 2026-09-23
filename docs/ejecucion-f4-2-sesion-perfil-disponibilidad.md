# F4.2 — Sesión, perfil y disponibilidad reales del conductor

## Objetivo y alcance

F4.2 continúa los días 7–9 de la Etapa 2 después de F4.1. Implementa M2 y M3
contra la API móvil ya aprobada en F3.4, sin crear migraciones, tablas,
credenciales de prueba ni datos simulados.

| Módulo | Puntos | Resultado de F4.2 |
| --- | ---: | --- |
| M1. Base, arquitectura y navegación | 8 | Cerrado en F4.1. |
| M2. Sesión y perfil | 12 | Login JSON, refresh rotativo, logout y perfil propio. |
| M3. Disponibilidad | 8 | Consulta y cambio real restringido por reglas del servidor. |
| **Total verificable** | **80** | **28/80** |

Permanece fuera de F4.2: asignaciones, aceptación/rechazo, incidencias,
comunicados, permisos/GPS, mapas, FCM, fotografías, cola offline y cualquier
función de M4–M9.

## Arquitectura aplicada

~~~
Flutter UI
  Login / Perfil / Disponibilidad
        |
  SessionController ---- RefreshTokenStore (Android Keystore cifrado)
        |                         |
  MobileApiClient ----------- HTTP JSON / Bearer / correlation ID
        |
Spring Boot /api/v1/mobile
  auth/login, auth/refresh, auth/logout, me, availability
        |
PostgreSQL 16 privado en el equipo
~~~

- El access token solo vive en memoria. El refresh token se guarda únicamente
  con `flutter_secure_storage` y se rota de forma serializada; dos solicitudes
  que expiran a la vez no reutilizan la familia de refresh en paralelo.
- `MobileApiClient` adjunta Bearer solo a rutas protegidas, genera un
  `X-Correlation-ID`, reintenta una vez después de un refresh exitoso y
  convierte el sobre uniforme `{status, code, message, correlationId, errors}`
  en errores de interfaz. No registra tokens, contraseñas ni cuerpos HTTP.
- El cliente llama solamente a `GET /mobile/me`, `GET /mobile/availability` y
  `PUT /mobile/availability`, además de los tres endpoints de sesión. No recibe
  `organizationId` o `driverId` como autoridad desde la interfaz.
- El conductor puede solicitar solamente `DISPONIBLE`, `DESCANSO` y
  `NO_DISPONIBLE`. `RESERVADO` y `EN_SERVICIO` son estados de asignación y la
  aplicación los explica, pero no los fuerza.

## Red y seguridad Android

- El manifiesto principal declara `INTERNET`, deshabilita `allowBackup` y no
  habilita HTTP global. Una compilación release rechaza una `RF_API_BASE_URL`
  que no sea HTTPS.
- `usesCleartextTraffic=true` existe únicamente en `src/debug`; permite el
  desarrollo local por emulador o ADB, nunca una APK release.
- Para probar con un teléfono USB sin abrir PostgreSQL ni exponer la red, el
  titular puede usar después:

~~~powershell
adb reverse tcp:8080 tcp:8080
cd mobile
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

Esto exige que Spring Boot esté iniciado localmente y que el ADMIN haya creado
una cuenta `CONDUCTOR` activa y vinculada. No se documentan cuentas ni
contraseñas en el repositorio. Para una demostración externa o release se usará
una URL HTTPS temporal en una fase posterior; PostgreSQL continúa privado.

## Herramientas y recursos limitados

El equipo tiene alrededor de 8 GB de RAM. `mobile/android/gradle.properties`
limita Gradle a 1 GB, un worker, sin daemon persistente y compilación Kotlin en
el proceso. No se instala Android Studio ni emulador.

Los verificadores resuelven Flutter desde `D:\dev\flutter` aunque la variable
`PATH` de Windows no lo tenga registrado. Para usar `flutter` manualmente en
esta sesión de PowerShell, sin modificar el sistema, se puede ejecutar:

~~~powershell
$env:Path = 'D:\dev\flutter\bin;' + $env:Path
~~~

En otra computadora se sustituye `D:\dev\flutter` por la ruta de instalación
local del SDK.

`flutter_secure_storage` 10.3.4 requiere activos nativos de Flutter 3.47 al
empaquetar Android; por ello se instaló Android NDK `28.2.13676358` además de
API 36, Build-Tools 36.0.0 y platform-tools. El NDK no es Docker ni un
emulador; se usa solo durante la compilación local.

## Verificación de cierre

Desde la raíz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaF42MobileSessionProfileAvailability.ps1
~~~

La auditoría revisa dependencias, almacenamiento seguro, serialización del
refresh, fronteras M2/M3, manifiestos debug/release, límite de recursos,
análisis, pruebas Flutter y la APK debug. La salida válida es:

~~~
F4_2_MOBILE_SESSION_PROFILE_AVAILABILITY=PASS ... total=28/80 android=READY ... docker=0
~~~

F4.2 queda lista para evaluación cuando la salida anterior sea positiva. La
siguiente puerta será F4.3: asignaciones móviles idempotentes, sin fingir una
aceptación desde el CRM.

> Estado histórico: este documento describe el cierre de F4.2. F4.3 incorpora
> posteriormente M4; consulte [Ejecución F4.3](ejecucion-f4-3-asignaciones-moviles.md)
> para su alcance y controles propios.
