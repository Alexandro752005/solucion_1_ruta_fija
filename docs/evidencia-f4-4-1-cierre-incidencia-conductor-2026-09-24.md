# Evidencia F4.4.1 — Cierre de incidencias por conductor

Fecha de verificación: 24 de septiembre de 2026.

## Alcance aprobado

El conductor puede cerrar únicamente una incidencia que él mismo creó desde la
aplicación móvil. El cierre no elimina el registro ni crea una incidencia nueva:
queda trazado con el estado `CLOSED_BY_DRIVER`, el actor y la fecha de cierre.

Solo se permite desde `OPEN` o `FOLLOW_UP`; una repetición segura devuelve el
estado ya cerrado sin duplicar auditoría. El conductor no puede reabrirla. Un
ADMIN conserva la facultad de atenderla posteriormente y llevarla a
`FOLLOW_UP` o `RESOLVED`, conservando el hecho histórico del cierre del
conductor.

## Migración y seguridad de datos

- Flyway V11 añade el estado y los campos de cierre de conductor.
- La migración local fue respaldada con `pg_dump` en formato custom y el
  respaldo se validó con `pg_restore --list` antes de aplicar el cambio.
- La comprobación de desarrollo confirmó V1–V11 y que `rf_app` conserva DML
  operativo sin privilegios DDL.
- La base de pruebas nativa confirmó el manifiesto ordenado V1–V11; la
  comprobación evita el error de comparar versiones Flyway como texto
  lexicográfico.
- No interviene Docker.

## Regresión automatizada ejecutada

| Superficie | Evidencia | Resultado |
| --- | --- | --- |
| Backend unitario | Maven Surefire | 24 pruebas aprobadas, 0 fallos y 0 errores. |
| Backend integrado | Maven Failsafe sobre PostgreSQL 16 nativo | 30 pruebas aprobadas, 0 fallos y 0 errores. |
| Flujo F4.4 | `MobileOperationFlowIT` | Cierre propio, sin borrado ni duplicación, auditoría única y atención posterior por ADMIN. |
| Datos/API | Auditorías F3.3 y F3.4 | `PASS`, contrato CRM/móvil, privacidad e idempotencia conservados. |
| CRM Angular | Vitest | 17 archivos y 42 pruebas aprobadas. |
| CRM Angular | `typecheck` y `build` | Aprobados; build de producción generado. |
| Flutter | `flutter analyze` y `flutter test` | Sin incidencias de análisis y 26 pruebas aprobadas. |
| Android | APK debug | Construido y verificado con firma APK Signature Scheme v2. |
| Auditoría F4.4 | `Test-RutaFijaF44MobileIncidentsAnnouncements.ps1` | `F4_4_MOBILE_INCIDENTS_ANNOUNCEMENTS=PASS`; Android listo y Docker=0. |

## Puerta de fase

**Puerta técnica F4.4: PASS.**

La aceptación funcional permanece deliberadamente pendiente de una prueba
manual cruzada CRM–Android. No se debe declarar `VERDE — F4.4` ni iniciar M7
hasta verificar en un dispositivo real que:

1. el conductor crea una incidencia móvil;
2. el mismo conductor la cierra y recibe confirmación irreversible;
3. el CRM conserva una sola incidencia, muestra el cierre y no la cuenta como
   abierta;
4. un ADMIN la atiende sin borrar la marca del conductor; y
5. el conductor no puede volver a cerrarla ni reabrirla.
