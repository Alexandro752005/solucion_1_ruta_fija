# Ruta Fija — CRM Web Administrativo y Reportes

Ruta Fija es un monolito modular para administrar organizaciones, usuarios,
grupos, conductores, vehículos, asignaciones, incidencias, comunicados,
reportes y auditoría. El alcance vigente incluye el CRM web administrativo, la
API móvil operativa y Flutter Android del conductor (M1–M3, 28/80 puntos).
F3.4 completa OpenAPI/DTOs, la matriz de aislamiento e idempotencia y los
reportes reales de estados móviles. F4.1 aporta bootstrap, navegación, tema y
configuración de entorno; F4.2 agrega sesión JSON real, refresh protegido,
perfil propio y disponibilidad sin adelantar asignaciones, GPS, FCM, SMTP ni
servicios externos.

La operación local usa PostgreSQL 16 instalado en Windows, Java 21 y Angular.
No se necesita Docker para iniciar, probar, detener ni recuperar el sistema.

## Arquitectura y tecnologías

| Componente | Decisión |
| --- | --- |
| CRM | Angular 22 en http://localhost:4200 |
| API | Java 21, Spring Boot 3.5.16 en http://127.0.0.1:8080 |
| Persistencia | PostgreSQL 16 local, puerto 5432 |
| Esquema | Flyway V1–V10 y Hibernate con ddl-auto=validate |
| Seguridad | JWT, refresh en cookie HttpOnly web y JSON móvil, roles separados de migración, aplicación y pruebas |
| Tiempo real | WebSocket con ticket efímero por medio del proxy Angular |
| Móvil | Flutter 3.47.5, Android `pe.rutafija.conductor`, M1–M3 reales: sesión, perfil y disponibilidad |

El backend está dividido en identity, organization, fleet, operation, audit y
shared. PostgreSQL conserva las reglas críticas: aislamiento de roles,
auditoría append-only, UTC, extensión btree_gist y prevención de solapamientos
de conductor y vehículo.

## Requisitos de una estación nueva

- Windows 10/11, PowerShell y Git.
- PostgreSQL 16 instalado como servicio local, con psql disponible.
- Java 21.
- Node.js 24.16.x y npm.
- Flutter 3.47.5 estable para `mobile/`; Android SDK API 36, Build-Tools 36.0.0, platform-tools y NDK 28.2.13676358 permiten compilar la APK sin Android Studio ni emulador.
- Visual Studio Code, recomendado.

La instancia debe estar limitada a 127.0.0.1 y ::1. No se publique el puerto
5432 hacia la red. La tarea F1.6 permite comprobarlo.

## Primera preparación nativa

1. Clone el repositorio y entre a su carpeta.

   ~~~powershell
   git clone https://github.com/Alexandro752005/solucion_1_ruta_fija.git
   Set-Location solucion_1_ruta_fija
   ~~~

2. Cree en PostgreSQL una base vacía llamada solucion_ruta_fija_1 y un usuario
   administrativo local que será su bootstrap. El usuario debe poder crear los
   tres roles técnicos durante el primer aprovisionamiento. No use ni comparta
   la contraseña de otra persona.

3. Cree la configuración privada. La contraseña se solicita de manera oculta y
   el archivo resultante queda ignorado por Git.

   ~~~powershell
   .\scripts\Initialize-RutaFijaNativeConfig.ps1
   ~~~

4. En una base vacía, cree los roles rf_migrator, rf_app y rf_test, junto con
   la base aislada ruta_fija_test.

   ~~~powershell
   .\scripts\Initialize-RutaFijaPostgresqlRoles.ps1
   ~~~

5. Elija una sola ruta según el estado de la base.

   - Para una base nueva y vacía, el bootstrap actual aplica V1-V10 y luego
     concede el DML mínimo de aplicación:

     ~~~powershell
     .\scripts\Invoke-RutaFijaFlywayF13.ps1
     .\scripts\Grant-RutaFijaApplicationPrivilegesF13.ps1
     .\scripts\Test-RutaFijaF34ContractReports.ps1
     ~~~

   - Para una base existente que ya está exactamente en V8, no use el
     bootstrap. Aplique V9/V10 mediante el migrador protegido de F3.3:

     ~~~powershell
     .\scripts\Invoke-RutaFijaF33V9V10Migration.ps1
     .\scripts\Test-RutaFijaF34ContractReports.ps1
     ~~~

6. Instale las dependencias del CRM y valide el entorno.

   ~~~powershell
   Set-Location frontend
   npm.cmd ci
   Set-Location ..
   .\verificar_ruta_fija.bat
   ~~~

Los scripts de los pasos 3 a 5 protegen los destinos: desarrollo,
ruta_fija_test y recuperación no se pueden intercambiar de forma accidental.
No ejecute nuevamente el bootstrap inicial contra una base con datos.

## Inicio y cierre diario

Desde la raíz:

~~~powershell
.\iniciar_ruta_fija.bat
~~~

Cuando el inicio sea correcto:

- CRM: http://localhost:4200
- API: http://127.0.0.1:8080/api/v1
- Salud: http://127.0.0.1:8080/actuator/health
- OpenAPI: http://127.0.0.1:8080/v3/api-docs

Para detener únicamente el backend y el CRM, conservando PostgreSQL como
servicio local:

~~~powershell
.\finalizar_ruta_fija.bat
~~~

El inicio normal no crea usuarios de demostración. Las semillas se mantienen
desactivadas para no introducir cuentas o contraseñas conocidas en la base
local.

## Pruebas y tiempo real

Ejecute las pruebas de backend contra la base aislada ruta_fija_test:

~~~powershell
.\verificar_ruta_fija.bat
~~~

La salida aprobada termina con F3_4_NATIVE_VERIFY=PASS. Para comprobar el
proxy REST y WebSocket desde Angular, con 8080 y 4200 libres:

~~~powershell
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify
~~~

El smoke genera datos temporales solamente en ruta_fija_test, verifica login,
refresh, ticket de un solo uso y stream.ready, y luego limpia la base.

## Seguridad local y recuperación

Abra una consola como Administrador una sola vez para aplicar el listener
loopback de PostgreSQL. El script solo modifica listen_addresses mediante
ALTER SYSTEM y reinicia el servicio PostgreSQL 16.

~~~powershell
.\scripts\Set-RutaFijaPostgresqlLoopbackF16.ps1
.\scripts\Test-RutaFijaNativeSecurityF16.ps1
~~~

La auditoría confirma listener local, HBA con SCRAM, mínimo privilegio para
rf_migrator/rf_app/rf_test, archivos secretos ignorados y CI nativa.

El respaldo post-migración usa pg_dump y pg_restore nativos, nunca borra una
base de recuperación existente y conserva el archivo en backups/, ruta
excluida de Git:

~~~powershell
.\scripts\Invoke-RutaFijaRecoveryEvidenceF17.ps1
~~~

Antes de la migración de roles V6, ejecute la línea base F2.1A con API y CRM
detenidos. El control crea un dump pre-V6 y restaura una copia nueva sin tocar
desarrollo:

~~~powershell
.\scripts\Invoke-RutaFijaF21aBaseline.ps1
~~~

F2.1B ensaya V6 contra una recuperación temporal con datos legacy controlados.
No aplica V6 al CRM todavía; eso se hará de forma atómica en F2.2:

~~~powershell
.\scripts\Invoke-RutaFijaF21bV6Rehearsal.ps1
~~~

La aplicación real de V6, solo después de F2.1A/F2.1B y con API/CRM detenidos,
está protegida por su propio comando:

~~~powershell
.\scripts\Invoke-RutaFijaF22AdminMigration.ps1
~~~

V6 deja como roles de aplicación `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. F2.3
alinea el CRM Angular al mismo contrato: ADMIN recibe toda la navegación de su
tenant y no existe administración de coordinadores de grupo en la interfaz.
F2.4 ejecuta la puerta G2: consolida la evidencia de roles, tenant, sesiones,
historia de grupos y operación nativa antes de iniciar la API móvil.

F3.1B añade V7 y V8 al esquema ya consolidado: `ADMIN_DIRECT` y
`MOBILE_CONFIRMATION` distinguen las asignaciones directas de las que requieren
respuesta auténtica del conductor; `driver_current_location` retiene solo un
punto vigente por conductor. F3.2 añade sesión JSON separada para un
`CONDUCTOR` vinculado y activo. F3.3 agrega V9/V10 y las rutas propias bajo
`/api/v1/mobile`: perfil, disponibilidad, asignaciones, respuesta, inicio,
finalización, incidencias, comunicados, acuses y ubicación vigente. Una acción
del CRM no puede fingir aceptación o rechazo de un conductor. Para una base
existente en V8, ejecute primero el comando protegido de F3.3 que crea backup,
ensayo aislado y solo después aplica V9/V10.

F3.4 no altera Flyway: publica el contrato OpenAPI/DTO completo, alinea el CRM
para mostrar estados móviles sin fingir una respuesta del conductor y reporta
`PENDING_RESPONSE`, `REJECTED` y `EXPIRED` desde filas persistidas. El arranque
nativo ahora valida F3.4 antes de iniciar backend y CRM.

## Automatización y documentación

Las tareas Ruta Fija de VS Code cubren aprovisionamiento, arranque, pruebas,
proxy WebSocket, auditoría F1.6, evidencia de recuperación F1.7, auditoría de
sesión móvil histórica F3.2, auditoría de operaciones móviles F3.3 y contrato
con reportes reales F3.4.

La integración continua crea una instancia PostgreSQL 16 efímera del runner
para las pruebas de integración. No requiere un motor de contenedores instalado
en el equipo del desarrollador.

Documentos principales:

- [Uso del Sistema](<docs/Uso del Sistema.md>)
- [Ejecución nativa F1.4](docs/ejecucion-nativa-f1-4.md)
- [Ejecución nativa F1.5](docs/ejecucion-nativa-f1-5.md)
- [Seguridad local F1.6](docs/ejecucion-nativa-f1-6.md)
- [Evidencia F1.6](docs/evidencia-f1-6-seguridad-local-2026-09-22.md)
- [Recuperación F1.7](docs/ejecucion-nativa-f1-7.md)
- [Evidencia F1.7](docs/evidencia-f1-7-recuperacion-post-migracion-2026-09-21.md)
- [Línea base F2.1A](docs/ejecucion-nativa-f2-1a.md)
- [Caracterización pre-V6](docs/f2-1a-caracterizacion-pre-v6.md)
- [Evidencia F2.1A](docs/evidencia-f2-1a-linea-base-pre-v6-2026-09-22.md)
- [Ensayo aislado F2.1B](docs/ejecucion-nativa-f2-1b.md)
- [ADR-002 de V6](docs/decisiones/ADR-002-candidata-v6-ensayo-aislado.md)
- [Evidencia F2.1B](docs/evidencia-f2-1b-ensayo-v6-2026-09-22.md)
- [Ejecución nativa F2.2](docs/ejecucion-nativa-f2-2.md)
- [Auditoría backend F2.2](docs/auditoria-f2-2-backend-admin.md)
- [Evidencia F2.2](docs/evidencia-f2-2-consolidacion-admin-2026-09-22.md)
- [ADR-003 de historia de grupos](docs/decisiones/ADR-003-group-coordinator-historia-sin-autorizacion.md)
- [Ejecución nativa F2.3](docs/ejecucion-nativa-f2-3.md)
- [Contrato API F2.3](docs/contrato-api-f2-3-admin.md)
- [Auditoría CRM F2.3](docs/auditoria-f2-3-frontend-admin.md)
- [Evidencia de cierre F2.3](docs/evidencia-f2-3-crm-admin-2026-09-22.md)
- [Cierre G2 / F2.4](docs/ejecucion-nativa-f2-4-g2.md)
- [Evidencia G2 / F2.4](docs/evidencia-g2-consolidacion-admin-2026-09-22.md)
- [ADR-004: contrato móvil y ubicación vigente](docs/decisiones/ADR-004-contrato-movil-estados-y-ubicacion.md)
- [Ejecución F3.1A](docs/ejecucion-f3-1a-adr-contrato-movil.md)
- [Ejecución F3.1B: V7 y V8](docs/ejecucion-f3-1b-v7-v8.md)
- [Evidencia F3.1B](docs/evidencia-f3-1b-v7-v8-2026-09-22.md)
- [ADR-005: sesión móvil separada](docs/decisiones/ADR-005-sesion-movil-separada.md)
- [Ejecución F3.2](docs/ejecucion-f3-2-sesion-movil.md)
- [Evidencia F3.2](docs/evidencia-f3-2-sesion-movil-2026-09-22.md)
- [ADR-006: operaciones móviles e idempotencia](docs/decisiones/ADR-006-operaciones-moviles-e-idempotencia.md)
- [Ejecución F3.3](docs/ejecucion-f3-3-operaciones-moviles.md)
- [Evidencia F3.3](docs/evidencia-f3-3-operaciones-moviles-2026-09-22.md)
- [Contrato API F3.4](docs/contrato-api-f3-4-movil.md)
- [Ejecución F3.4](docs/ejecucion-f3-4-contrato-reportes.md)
- [Evidencia F3.4](docs/evidencia-f3-4-contrato-reportes-2026-09-22.md)
- [Ejecución F4.1: base Flutter](docs/ejecucion-f4-1-base-flutter.md)
- [Ejecución F4.2: sesión, perfil y disponibilidad](docs/ejecucion-f4-2-sesion-perfil-disponibilidad.md)
- [Evidencia F4.2](docs/evidencia-f4-2-sesion-perfil-disponibilidad-2026-09-23.md)
- [Manual móvil Flutter](mobile/README.md)
- [Plan de migración nativa](docs/plan-f1-postgresql-nativo-sin-docker.md)

Los archivos de Compose y Dockerfile permanecen como compatibilidad histórica
y no forman parte del camino operativo aprobado.
