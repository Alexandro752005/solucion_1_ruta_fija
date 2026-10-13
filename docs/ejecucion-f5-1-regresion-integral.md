# F5.1 — Regresión integral web–móvil–base de datos

## Objetivo verificable

Comprobar en una sola ejecución que el flujo construido hasta M9 conserva su
contrato de extremo a extremo: ADMIN origina la operación, CONDUCTOR actúa
mediante la API móvil, PostgreSQL persiste el resultado y el CRM lo consulta en
operación, reportes y auditoría sin romper el aislamiento por organización.

F5.1 es una puerta de regresión. No agrega comportamiento funcional ni sustituye
la validación manual del propietario.

## Ejecución

Desde la raíz del proyecto:

~~~powershell
.\scripts\Test-RutaFijaF51IntegralRegression.ps1
~~~

También está disponible la tarea de VS Code **Ruta Fija: regresion integral
web-movil-base de datos (F5.1)**.

El auditor inicia PostgreSQL solo si se encontraba detenido y lo devuelve al
estado detenido al finalizar. Las pruebas se ejecutan en secuencia y aplican
límites de memoria a Maven y Node para evitar una carga simultánea innecesaria.

## Cobertura de la puerta

| Capa | Verificación |
| --- | --- |
| PostgreSQL | Conexión nativa, Flyway V1–V12 y pruebas de integración reales. |
| Backend web/móvil | Creación ADMIN, aceptación/inicio/cierre CONDUCTOR, idempotencia, incidencias y ubicación vigente. |
| Visibilidad CRM | La asignación completada y la incidencia móvil pueden ser consultadas por ADMIN. |
| Reportes | Datos persistidos y exportaciones PDF, XLSX y CSV seguro. |
| Auditoría | Eventos móviles de asignación e incidencia vinculados con la entidad real. |
| Seguridad | Aislamiento multiempresa en REST y WebSocket; errores sin filtrar recursos ajenos. |
| Automatización | Solo caduca una respuesta móvil pendiente; no cambia por sí sola la disponibilidad del conductor o vehículo. |
| Angular | Typecheck, 42 pruebas y build de producción. |
| Flutter | Análisis estático y 41 pruebas. |

La salida aprobada termina con:

~~~text
F5_1_INTEGRAL_REGRESSION=PASS backend_unit=24 backend_integration=33 frontend=typecheck,test,build mobile=analyze,41-tests flow=ADMIN-CONDUCTOR-DB-CRM reports=PDF,XLSX,CSV audit=PASS multitenancy=PASS automation=expiry_only_without_fleet_mutation
RF_POSTGRESQL_STOP=PASS status=Stopped startup=Manual
~~~

## Criterio de cierre

- Todas las pruebas y builds terminan sin fallos.
- PostgreSQL vuelve a `Stopped/Manual` cuando el auditor lo inició.
- Los puertos de aplicación quedan libres al terminar.
- La evidencia queda registrada y el propietario confirma explícitamente
  `VERDE — F5.1` antes de iniciar F5.2.
