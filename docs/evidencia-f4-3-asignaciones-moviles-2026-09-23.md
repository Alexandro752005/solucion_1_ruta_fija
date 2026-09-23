# Evidencia F4.3 — asignaciones móviles idempotentes

Fecha de cierre técnico: 2026-09-23.

## Resultado

~~~text
F4_3_MOBILE_ASSIGNMENTS=PASS flutter='Flutter 3.47.5 • channel stable • https://github.com/flutter/flutter.git' m1=8/80 m2=12/80 m3=8/80 m4=18/80 total=46/80 android=READY apk_bytes=181270225 docker=0
~~~

M4 aporta 18 puntos verificables. El cliente conductor llega a 46 de 80 puntos
con M1–M4; no se declaran como construidos M5–M9.

## Controles aprobados

| Control | Evidencia |
| --- | --- |
| Contrato y base nativa | `F3_3_MOBILE_OPERATIONS_AUDIT=PASS` y `F3_4_CONTRACT_REPORTS_AUDIT=PASS`: Flyway V1–V10, aislamiento de conductor, recibos idempotentes, reportes persistidos y frontera CRM. Las consultas PostgreSQL fueron solo de lectura. |
| Análisis Flutter | `flutter analyze`: sin incidencias. |
| Pruebas Flutter | `flutter test`: 17/17 aprobadas. Incluyen UI de lista/detalle/confirmación y reintento con el mismo evento tras un resultado de red desconocido. |
| Reintento durable | El comando se persiste antes del POST; un replay usa el mismo cuerpo y procesa `X-Idempotent-Replay: true`. Un conflicto de versión elimina el comando y obliga a recargar. |
| Autoridad | El body móvil solo contiene `clientEventId`, `version`, `occurredAt` y, en rechazo, motivo opcional. No envía tenant, usuario ni conductor como autoridad. |
| APK Android | `flutter build apk --debug` aprobado. Archivo: `mobile/build/app/outputs/flutter-apk/app-debug.apk`. |
| Metadatos APK | Paquete `pe.rutafija.conductor`, `minSdkVersion=24`, `targetSdkVersion=36`, etiqueta `Ruta Fija Conductor`. |
| Integridad APK | 181,270,225 bytes; SHA-256 `62963F236135AE250CEB523103C5B74C1C5812D08B666DE12FDE52648B15F5A2`. |
| Entorno y recursos | Android SDK 36, API 36, Build-Tools 36.0.0, NDK 28.2.13676358; Gradle limitado a 1 GB, un worker y sin daemon. No hay Android Studio, emulador ni artefactos Docker en `mobile/`. |

## Comportamiento entregado

- Lista paginada y filtrable de asignaciones propias del conductor.
- Detalle con ruta, horario, vehículo, grupo, plazo y marcas autenticadas.
- Aceptar o rechazar únicamente una solicitud `MOBILE_CONFIRMATION` pendiente.
- Iniciar solo una asignación reservada y completar solo una asignación en
  servicio, siempre sometido a la validación del servidor.
- Confirmación humana previa a cada mutación.
- Ante fallo de red, el conductor ve **Reintentar exactamente**: no se genera
  otro UUID, no se duplica la operación y no hay envío automático en segundo
  plano.

## Límites honestos

No se probó una cuenta real en teléfono físico ni se hizo un despliegue release;
la APK es debug. Tampoco se añadieron incidencias, comunicados, GPS,
notificaciones, fotos, mapas o una cola offline M8.

La siguiente aceptación manual requiere Spring Boot iniciado, un teléfono USB y
una asignación `MOBILE_CONFIRMATION` creada por ADMIN para el conductor:

~~~powershell
adb reverse tcp:8080 tcp:8080
Set-Location mobile
$env:Path = 'D:\dev\flutter\bin;' + $env:Path
flutter run --debug --dart-define=RF_API_BASE_URL=http://127.0.0.1:8080/api/v1
~~~

No se inicia F4.4 hasta `VERDE — F4.3`.
