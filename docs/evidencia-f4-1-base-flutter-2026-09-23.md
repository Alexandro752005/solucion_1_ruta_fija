# Evidencia F4.1 — Base Flutter Android

- Fecha local: 2026-09-23.
- Fase: F4.1, M1 de la matriz móvil.
- Puntos demostrables: 8/80.
- Docker: no utilizado.
- Backend, CRM y PostgreSQL: no modificados.

## Entrega

| Control | Evidencia |
| --- | --- |
| SDK Flutter | Flutter 3.47.5 estable en `D:\dev\flutter`, verificado con SHA-256 oficial antes de extraerse. |
| PATH | `D:\dev\flutter\bin` agregado únicamente al `PATH` del usuario. |
| Proyecto | `mobile/`, paquete Dart `ruta_fija_conductor`, identificador Android `pe.rutafija.conductor`. |
| Arquitectura | `app/`, `core/navigation/` y `features/bootstrap/`; Flutter no accede a PostgreSQL. |
| Navegación | Rutas Material centralizadas y módulos no construidos identificados como pendientes. |
| Configuración | `RF_API_BASE_URL` valida HTTP(S), `/api/v1`, ausencia de credenciales, consulta y fragmento. |
| Honestidad funcional | No existen login, HTTP operativo, secure storage, GPS, permisos, offline, Firebase ni datos simulados. |
| VS Code | Se recomiendan Dart y Flutter; se retiró la recomendación de contenedores. |

## Validación reproducible

~~~powershell
.\scripts\Test-RutaFijaF41FlutterFoundation.ps1
~~~

Resultado obtenido:

~~~text
flutter analyze: No issues found
flutter test: 4 pruebas aprobadas
F4_1_FLUTTER_FOUNDATION=PASS ... m1=8/80 android=PENDING_OWNER_LICENSE docker=0
~~~

## Límite Android pendiente

`flutter doctor -v` confirmó que Android SDK aún no está instalado. La descarga y aceptación de la licencia Android es una decisión del propietario; no se aceptó ningún término ni se instaló Android Studio/emulador en su nombre. Por ello, no hay APK ni validación en dispositivo todavía. Esta observación no se cuenta como avance de M9.

## Siguiente dependencia

Antes de la prueba Android o APK, el propietario instala Android SDK de las herramientas oficiales, revisa y acepta sus licencias, y se vuelve a ejecutar `flutter doctor`. Una vez listo, F4.2 podrá implementar M2 (sesión/perfil) y M3 (disponibilidad) contra la API real de F3.4.
