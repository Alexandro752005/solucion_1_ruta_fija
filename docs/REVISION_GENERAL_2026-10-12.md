# Revisión general de Ruta Fija — 12 de octubre de 2026

**Dictamen: AMARILLO — MVP avanzado, con base técnica consistente y bloqueos de integración y entrega pendientes.**

El proyecto tiene backend, CRM, aplicación Android y persistencia implementados, además de evidencias anteriores de regresión aprobada. La copia local todavía no constituye una entrega reproducible: contiene trabajo importante sin confirmar en Git, no incorpora todos los cambios de mantenimiento de main y el recorrido de confirmación de asignaciones desde el CRM hacia el conductor está incompleto. No existe evidencia suficiente para declarar G5 aprobado o certificar esta copia para producción.

Esta revisión rápida y dirigida combina inspección de código, configuración, Git, scripts y resultados guardados. Se revisaron backend, frontend/móvil e infraestructura en paralelo. No se ejecutaron compilaciones, suites completas ni una demo; los resultados históricos se identifican expresamente como tales. Solo se crea este informe.

Fecha de referencia: **2026-10-12, America/Lima**. Objeto revisado: la carpeta local `solucion_1_ruta_fija`; no se consultó el estado vivo de GitHub.

| Aspecto | Condición comprobada |
|---|---|
| Rama abierta | `etapa-2/f02-postgresql-local`, HEAD `0872024`. |
| Versionado | 76 archivos rastreados modificados y 49 entradas sin rastrear, antes de crear este informe. Algunas entradas son carpetas. |
| Diferencia con main local | HEAD tiene 1 commit exclusivo y le faltan 5 commits de main; main y origin/main locales apuntan a `3617354`. Estas referencias no se actualizaron por red. |
| Backend | Java 21, Spring Boot 3.5.16; monolito modular con capas API, aplicación, dominio e infraestructura. |
| CRM | Angular 22.1.x en esta copia; main local ya contiene Angular 22.2.1. |
| Móvil | Flutter/Dart, almacenamiento seguro, geolocalización y cola de acciones offline implementados. |
| Persistencia | Existen migraciones V1–V12 y validación de esquema mediante Hibernate. El esquema realmente aplicado en PostgreSQL no se consultó. |
| Estado de ejecución | Servicio PostgreSQL detenido y sin listeners encontrados en 8080, 4200 o 4300. Esto indica entorno apagado, no una avería demostrada. |

## Capacidades existentes

| Área | Implementación localizada y límite de la revisión |
|---|---|
| SUPER_ADMIN | Resumen y gestión de organizaciones en el CRM. No se encontraron pantallas independientes de administradores o auditoría global. |
| ADMIN | Resumen, usuarios, organización, grupos, conductores, vehículos, asignaciones, incidencias, comunicados, reportes y auditoría. |
| CONDUCTOR | Inicio, perfil, disponibilidad, asignaciones, incidencias, comunicados, ubicación puntual con consentimiento y gestión de pendientes offline. |
| Seguridad | JWT con comprobación del usuario y organización activos, permisos por rol, filtros por tenant y conductor, y separación de credenciales de aplicación, migración y pruebas. |
| Reportes y trazabilidad | Consultas reales, exportaciones CSV/XLSX/PDF y registros de auditoría. |
| Datos | Restricciones de solapamiento, migraciones incrementales e idempotencia de comandos móviles. |
| Operación local | Scripts de inicio, cierre, pruebas, migración y respaldo. Se encontraron los ocho dumps de la depuración de recovery en `backups/recovery-cleanup-20261003_000030/`. |

Evidencias principales: `frontend/src/app/app.routes.ts`; `mobile/lib/app/ruta_fija_router.dart`; `backend/src/main/java/pe/rutafija/shared/config/SecurityConfig.java:124`; `backend/src/main/java/pe/rutafija/operation/infrastructure/AssignmentRepository.java:59`; `backend/src/main/resources/db/migration/`.

## Hallazgos que requieren atención

### H01 — Alta: la entrega local no está consolidada en Git

V11, V12, el servicio de sincronización offline, DTO utilizados por controladores, pruebas y scripts/evidencias F5 están entre las entradas sin rastrear. Un clon del HEAD actual no reproduce todo lo que existe en este PC. Un commit parcial puede dejar referencias a clases ausentes.

La diferencia rastreada alcanza 2.503 inserciones y 275 eliminaciones en 76 archivos. Esto no implica que el trabajo esté mal, pero sí impide tratar el estado local como una versión publicable y trazable.

**Acción:** revisar y agrupar cambios completos por funcionalidad, incorporar el mantenimiento de main y validar la integración antes de publicar. No hacer un commit indiscriminado de cachés o exportaciones.

### H02 — Alta: la rama abierta conserva problemas de CI ya resueltos en main

El índice local registra `backend/mvnw` con modo `100644`, mientras CI lo invoca como `./mvnw` en Linux; esa combinación impide ejecutarlo directamente. Main local ya lo registra como `100755`.

El workflow de esta rama solo contiene backend y frontend. Main incorpora además Flutter, acciones fijadas por SHA, enmascaramiento de claves efímeras y privilegios adicionales para la base de pruebas. Las versiones Angular/jsdom también difieren entre ambas ramas.

**Acción:** integrar las correcciones existentes antes de reconstruir pipelines. No se certificó el último run remoto ni se ejecutó una nueva auditoría de vulnerabilidades.

Evidencia: `.github/workflows/ci.yml:80`, índice Git de `backend/mvnw` y comparación con los archivos de main.

### H03 — Alta para la demo: falta completar el flujo de aceptación desde el CRM

El formulario de asignaciones crea únicamente `ADMIN_DIRECT`; la modalidad `MOBILE_CONFIRMATION` queda disponible por API. Flutter sí implementa aceptar y rechazar, pero el administrador no puede originar ese recorrido desde el formulario actual.

Aceptar e iniciar son transiciones separadas. No está materializado el comportamiento solicitado de una acción de aceptación que también inicie el servicio con reglas de tiempo claramente definidas.

**Acción:** acordar la semántica final de confirmación, reserva, inicio y medición del tiempo; conectar el formulario con la modalidad móvil y probar el recorrido completo en Android.

Evidencia: `frontend/src/app/features/assignments/assignments.page.html:59`; `mobile/lib/features/assignments/domain/mobile_assignment_models.dart:116`; `backend/src/main/java/pe/rutafija/operation/application/MobileAssignmentService.java:104`.

**Precisión sobre el error anterior:** el código actual de reserva administrativa sí marca al conductor como RESERVADO (`OperationService.java:213` y `Driver.java:162`). No se ha reproducido en esta revisión el fallo mostrado anteriormente. Las interfaces habilitan iniciar con SCHEDULED y reservedAt, mientras el backend también exige disponibilidad RESERVADO; esa diferencia puede mostrar una acción que luego sea rechazada.

### H04 — Media: F1.3 y G2 conservan una dependencia histórica eliminada

F1.3 sigue intentando conectarse a `ruta_fija_recovery_20260919_f04`, y el control completo G2 invoca F1.3. La eliminación de esa copia fue ejecutada y documentada previamente; sus dumps permanecen disponibles. PostgreSQL estaba detenido en esta revisión, por lo que no se reconsultó el catálogo.

**Consecuencia:** si la copia no fue recreada, estos controles fallarán aunque la base principal esté correcta. El inicio habitual no depende de ese auditor histórico.

**Acción:** actualizar los controles para el ciclo de vida actual de los respaldos y las migraciones, conservando evidencia verificable sin exigir una copia temporal permanente.

Evidencia: `scripts/Test-RutaFijaMigratedSchemaF13.ps1:264`; `scripts/Test-RutaFijaG2AdminClosure.ps1:161`; `scripts/Invoke-RutaFijaNativeRuntime.ps1:25`.

### H05 — Media: el PDF pierde parte del texto Unicode

`safePdfText` solo conserva caracteres entre U+0020 y U+00FF. Flechas, comillas tipográficas y otros caracteres de nombres o descripciones desaparecen silenciosamente. Los acentos españoles habituales dentro de ese intervalo no son la causa de este hallazgo.

**Acción:** usar una fuente PDF con cobertura Unicode y probar que CSV, XLSX y PDF conserven la misma información.

Evidencia: `backend/src/main/java/pe/rutafija/operation/application/ReportExportService.java:277`.

### H06 — Alcance limitado: ubicación y offline todavía no son funciones avanzadas

La ubicación obtiene un punto a petición del usuario. La cola tiene procesamiento FIFO y reintento manual para determinadas operaciones; no equivale a seguimiento GPS continuo ni sincronización automática de todo el sistema.

**Acción:** describir este alcance en la demo y reservar GPS en segundo plano, sincronización avanzada y notificaciones para su fase correspondiente.

Evidencia: `mobile/lib/features/location/data/geolocator_device_location_gateway.dart:7`; `mobile/lib/core/offline/mobile_offline_sync_controller.dart:203`, `:254`.

## Riesgos por validar, sin fallo demostrado en ejecución

- **Reservas futuras:** aceptar marca al conductor como RESERVADO de inmediato, lo que impide aceptar otra asignación aunque los horarios no se solapen. Es una limitación del comportamiento actual; requiere decisión de negocio antes de cambiarla. Evidencia: `MobileAssignmentService.java:104` y `fleet/domain/Driver.java:157`.
- **Horario de inicio:** las transiciones revisadas no exigen estar dentro de la ventana programada. Definir tolerancias y comprobar inicio temprano/tardío. Evidencia: `operation/domain/Assignment.java:271`.
- **Coherencia de reportes:** detalle y totales se consultan por separado. Con aislamiento READ COMMITTED y cambios concurrentes pueden corresponder a instantes distintos. Verificar aislamiento real y reproducir antes de calificarlo como defecto. Evidencia: `operation/application/ReportService.java:91`, `:108`.
- **Calidad estática:** `npm run lint` ejecuta typecheck; no representa una revisión de estilo con ESLint. Conviene distinguir ambas garantías.
- **Portabilidad de documentación:** los catálogos de agentes detectados anteriormente están fuera del repositorio. Esta revisión no certifica un manual de instalación limpio desde cero ni una restauración reciente de la base principal.

## Evidencia de pruebas y condición de las fases

Los XML guardados del 30 de septiembre registran **24 pruebas unitarias y 33 de integración**, sin fallos, errores ni omisiones en esas ejecuciones. Se excluyó del recuento unitario un XML residual de integración del 25 de septiembre para evitar duplicarlo.

La evidencia documental F5.1 registra **42 pruebas Angular y 41 Flutter**, además de análisis, compilación, reportes y aislamiento aprobados. Son resultados históricos; no garantizan que los 125 cambios/entradas actuales estén cubiertos por una nueva ejecución.

| Puerta | Condición documentada |
|---|---|
| F5.1 | Aprobada históricamente y con VERDE registrado. |
| F5.2 | Infraestructura de demo y comprobaciones previas existentes; validación funcional humana pendiente. |
| F5.3 | No se encontró evidencia de cierre en los documentos/scripts revisados. |
| G5 | No hay evidencia suficiente de aprobación final. |

Fuentes: `backend/target/surefire-reports/`, `backend/target/failsafe-reports/`, `docs/evidencia-f5-1-regresion-integral-2026-09-30.md:19` y `docs/evidencia-f5-2-demo-temporal-2026-09-30.md:5`.

## Orden recomendado de trabajo

1. Consolidar la copia local y los cambios de mantenimiento de main en una rama revisable.
2. Completar y probar el flujo ADMIN → confirmación del CONDUCTOR → inicio → cierre → CRM.
3. Ajustar los controles históricos de recuperación y corregir la pérdida de caracteres del PDF.
4. Ejecutar regresión backend con PostgreSQL de pruebas, Angular y Flutter; realizar el recorrido real en Android.
5. Generar un respaldo actual de la base principal y comprobar su restauración aislada.
6. Repetir la demo temporal, documentar el cierre de la exposición y completar F5.3/G5 con evidencia.

**Condición de entrega:** hay una base aprovechable para continuar. La prioridad es integrar, verificar y cerrar los recorridos existentes antes de ampliar funcionalidad. El entorno no quedó arrancado y esta revisión no modifica código de aplicación ni bases de datos.

