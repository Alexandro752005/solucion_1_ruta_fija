# Evidencia F4.4 — Incidencias y comunicados móviles

Fecha de cierre técnico: 2026-09-24.

## Resultado certificado

~~~text
F4_4_MOBILE_INCIDENTS_ANNOUNCEMENTS=PASS flutter='Flutter 3.47.5 • channel stable • https://github.com/flutter/flutter.git' m1=8/80 m2=12/80 m3=8/80 m4=18/80 m5=7/80 m6=5/80 total=58/80 android=READY apk_bytes=199271633 apk_v2=PASS docker=0
~~~

M5 aporta 7 puntos y M6 aporta 5 puntos. El cliente conductor llega a 58 de
80 puntos con M1–M6; no se declara construido M7, M8 ni M9.

## Controles aprobados

| Control | Evidencia |
| --- | --- |
| Base contractual y nativa | `F3_3_MOBILE_OPERATIONS_AUDIT=PASS` y `F3_4_CONTRACT_REPORTS_AUDIT=PASS`: Flyway V1–V10, rutas propias, privacidad, idempotencia de asignaciones, reportes persistidos y PostgreSQL nativo sin Docker. |
| Regresión M4 | `F4_3_MOBILE_ASSIGNMENTS=PASS`: M1–M4, comandos idempotentes y APK continúan aprobados. |
| Análisis Flutter | `flutter analyze`: sin incidencias. |
| Pruebas Flutter | `flutter test`: 24/24 aprobadas, incluyendo listado/reporte propio de incidencia y acuse explícito de comunicado. |
| M5: autoridad | El body de incidencia contiene únicamente categoría, descripción, `occurredAt` técnico y asociación opcional; no incluye tenant, usuario, conductor, origen ni estado. `reportedAt` se conserva con el reloj del backend. |
| M5: resultado desconocido | Una falla de red bloquea el reenvío automático y solicita revisar la lista. No existe almacenamiento, temporizador ni cola offline para incidencias. |
| M6: lectura auténtica | Abrir no ejecuta `markRead`; el botón humano confirma mediante `POST /mobile/announcements/{id}/read`, que el backend trata de forma idempotente. |
| Límites de plataforma | No se agregaron permisos de ubicación, cámara o notificaciones, ni dependencias de GPS, fotos, Firebase, trabajo en segundo plano u offline. |
| APK Android | `flutter build apk --debug` aprobado. Archivo `mobile/build/app/outputs/flutter-apk/app-debug.apk`. |
| Integridad APK | 199,271,633 bytes; SHA-256 `F930EEA54F680A80267010C030EC8147E7AAC789BC08244046A3B212B3B07B8C`; `apksigner` confirma esquema v2. |
| Entorno | Android SDK API 36, Build-Tools 36.0.0 y NDK 28.2.13676358; Gradle permanece limitado a 1 GB, un worker y sin daemon persistente. |

## Comportamiento entregado

- **Incidencias:** lista paginada de registros propios, detalle con estado y
  seguimiento visible, y formulario validado para reportar categoría y
  descripción. El servidor conserva la autoridad sobre conductor, tenant,
  origen `MOBILE_APP`, estado `OPEN` y hora de recepción.
- **Comunicados:** lista solo de la audiencia autorizada, detalle sin acuse
  implícito y confirmación de lectura únicamente cuando el backend la exige.
- **Navegación:** los destinos M5 y M6 ya no son pantallas pendientes; se
  inyectan repositorios reales desde la raíz de composición Flutter.

## Límites honestos y puerta manual

La certificación es técnica y usa contratos reales con dobles HTTP en las
pruebas Flutter. Aún falta la comprobación manual de M5/M6 en teléfono Android
físico con Spring Boot local: reporte de incidencia visible en CRM y comunicado
con `readAt` después de la confirmación humana. La guía exacta está en
[Ejecución F4.4](ejecucion-f4-4-incidencias-comunicados.md).

No se inicia M7 hasta que el titular comunique `VERDE — F4.4` tras esa
validación. Las mejoras transversales de apariencia, validaciones,
redundancias y comentarios se mantienen registradas para la Etapa 3, Día 18;
no se mezclaron con esta entrega funcional.
