# Evidencia F4.2 — sesión, perfil y disponibilidad del conductor

Fecha de cierre técnico: 2026-09-23.

## Resultado

~~~text
F4_2_MOBILE_SESSION_PROFILE_AVAILABILITY=PASS flutter='Flutter 3.47.5 • channel stable • https://github.com/flutter/flutter.git' m1=8/80 m2=12/80 m3=8/80 total=28/80 android=READY apk_bytes=181219104 docker=0
~~~

La fase entrega 28 de 80 puntos verificables: M1 (8), M2 sesión y perfil (12)
y M3 disponibilidad (8). No declara construidos los módulos M4–M9.

## Controles aprobados

| Control | Evidencia |
| --- | --- |
| Fundación Flutter M1 | `F4_1_FLUTTER_FOUNDATION=PASS`, Android listo, manifiesto aprobado y Docker fuera de la ejecución. |
| Contrato y persistencia móvil | `F3_3_MOBILE_OPERATIONS_AUDIT=PASS` y `F3_4_CONTRACT_REPORTS_AUDIT=PASS`: Flyway V1–V10, rutas propias de conductor, idempotencia, privacidad y reportes persistidos. Las consultas a PostgreSQL fueron de solo lectura. |
| Análisis Flutter | `flutter analyze`: sin incidencias. |
| Pruebas Flutter | `flutter test`: 13/13 aprobadas. Incluyen login antes de datos protegidos, refresh, cierre de sesión, perfil propio, reintento controlado y bloqueo cliente de `RESERVADO`/`EN_SERVICIO`. |
| APK Android | `flutter build apk --debug` aprobado. Archivo: `mobile/build/app/outputs/flutter-apk/app-debug.apk`. |
| Metadatos de APK | Paquete `pe.rutafija.conductor`, `minSdkVersion=24`, `targetSdkVersion=36`, etiqueta `Ruta Fija Conductor`. |
| Integridad de APK | 181,219,104 bytes; SHA-256 `DE56F324591BC6596E5C66EEF7888E1075CD7844A97C5E94603CE7C4F1D9B542`. |
| Herramientas Android | `flutter doctor -v` confirma Android SDK 36.0.0, API 36, Build-Tools 36.0.0, licencias aceptadas y NDK 28.2.13676358. No se instaló Android Studio ni emulador. |

## Controles de diseño aplicados

- El access token vive solo en memoria; el refresh token usa
  `flutter_secure_storage` y no se registra en consola.
- El cliente reutiliza el contrato JSON de Spring Boot y no abre PostgreSQL ni
  conoce credenciales, roles administrativos o una cookie del CRM.
- La base release exige HTTPS. El HTTP de desarrollo está aislado a
  `src/debug` para ADB/emulador local.
- `RESERVADO` y `EN_SERVICIO` no son estados seleccionables por el conductor.
  El servidor continúa siendo la autoridad de esas transiciones.
- Gradle queda limitado a 1 GB, un worker y sin daemon para el equipo de 8 GB.

## Límites y siguiente verificación

No se realizó una instalación en teléfono físico ni un login contra una cuenta
real desde un dispositivo; no había teléfono Android conectado y no se usa
emulador. La APK es **debug**, no una entrega release ni la evidencia M9.

Antes de conceder el verde de F4.2, el titular puede realizar esta aceptación
manual con Spring Boot iniciado y una cuenta `CONDUCTOR` activa y vinculada:

~~~powershell
adb reverse tcp:8080 tcp:8080
Set-Location mobile
$env:Path = 'D:\dev\flutter\bin;' + $env:Path
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

La siguiente fase, solo tras `VERDE — F4.2`, será F4.3: asignaciones móviles
idempotentes y respuesta real del conductor, sin simular aceptaciones desde el
CRM.
