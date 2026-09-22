# Ruta Fija — CRM Web Administrativo y Reportes

Ruta Fija es un monolito modular para administrar organizaciones, usuarios,
grupos, conductores, vehículos, asignaciones, incidencias, comunicados,
reportes y auditoría. El alcance vigente es únicamente el CRM web
administrativo y reportes; no incluye aplicación móvil, GPS, FCM, SMTP ni
servicios externos.

La operación local usa PostgreSQL 16 instalado en Windows, Java 21 y Angular.
No se necesita Docker para iniciar, probar, detener ni recuperar el sistema.

## Arquitectura y tecnologías

| Componente | Decisión |
| --- | --- |
| CRM | Angular 22 en http://localhost:4200 |
| API | Java 21, Spring Boot 3.5.16 en http://127.0.0.1:8080 |
| Persistencia | PostgreSQL 16 local, puerto 5432 |
| Esquema | Flyway V1–V5 y Hibernate con ddl-auto=validate |
| Seguridad | JWT, refresh cookie HttpOnly, roles separados de migración, aplicación y pruebas |
| Tiempo real | WebSocket con ticket efímero por medio del proxy Angular |

El backend está dividido en identity, organization, fleet, operation, audit y
shared. PostgreSQL conserva las reglas críticas: aislamiento de roles,
auditoría append-only, UTC, extensión btree_gist y prevención de solapamientos
de conductor y vehículo.

## Requisitos de una estación nueva

- Windows 10/11, PowerShell y Git.
- PostgreSQL 16 instalado como servicio local, con psql disponible.
- Java 21.
- Node.js 24.16.x y npm.
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

5. Ejecute Flyway una sola vez y audite el esquema.

   ~~~powershell
   .\scripts\Invoke-RutaFijaFlywayF13.ps1
   .\scripts\Test-RutaFijaMigratedSchemaF13.ps1
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

La salida aprobada termina con F1_4_NATIVE_VERIFY=PASS. Para comprobar el
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

## Automatización y documentación

Las tareas Ruta Fija de VS Code cubren aprovisionamiento, arranque, pruebas,
proxy WebSocket, auditoría F1.6 y evidencia de recuperación F1.7.

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
- [Plan de migración nativa](docs/plan-f1-postgresql-nativo-sin-docker.md)

Los archivos de Compose y Dockerfile permanecen como compatibilidad histórica
y no forman parte del camino operativo aprobado.
