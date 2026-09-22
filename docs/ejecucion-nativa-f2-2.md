# F2.2 - Consolidacion backend de ADMIN

## Proposito y limite

Esta fase aplica la migracion V6 y deja el backend preparado para un unico
rol administrativo de tenant: `ADMIN`. No modifica el frontend Angular; esa
adaptacion pertenece exclusivamente a F2.3. Por ello, tras esta fase la API
esta consolidada, pero el CRM web no debe presentarse como compatible con
`ADMIN` hasta que F2.3 cambie sus guardas, menus y formularios.

## Prerrequisitos

- F2.1A aprobada, con dump pre-V6 verificable.
- F2.1B aprobada, con V6 ensayada en una recuperacion aislada.
- PostgreSQL 16 nativo disponible solo en `127.0.0.1:5432`.
- API y CRM detenidos para que no existan sesiones o escrituras durante la
  aplicacion.
- Configuracion privada existente en `backend/.local/ruta-fija-native.env`.

No se usa Docker ni se deben copiar credenciales a comandos, documentos o Git.

## Aplicacion controlada

Desde la raiz del repositorio:

~~~powershell
.\scripts\Invoke-RutaFijaF22AdminMigration.ps1
~~~

El script se niega a continuar si la base de desarrollo no esta exactamente en
V1--V5, si V6 no es la unica pendiente, si falta el respaldo F2.1A, si API o
CRM ocupan sus puertos, o si la migracion operativa no coincide byte a byte con
la candidata ensayada. La huella SHA-256 certificada de ambas copias es:

~~~text
9be06c6d0267e055e4db75c16aa15774f6dad7cbdfc0c69d0282f1aac6f8b622
~~~

La linea vacia final de V6 se conserva deliberadamente porque forma parte de
esa evidencia de bytes. `.gitattributes` fuerza LF para ambas copias incluso
en estaciones Windows.

El comentario inicial de la SQL es historial del ensayo F2.1B y se preserva
por el mismo checksum; el limite efectivo de esta fase es el backend. La
compatibilidad visual del CRM queda pendiente de F2.3.

Una aplicacion correcta termina con:

~~~text
F2_2_FLYWAY=PASS migration=V6 target=development
F2_2_ADMIN_MIGRATION=PASS ... flyway=V1-V6 roles=SUPER_ADMIN,ADMIN,CONDUCTOR ... docker=0
~~~

El script no es un comando de uso diario: V6 se aplica una sola vez. Para una
instalacion nueva, el bootstrap normal de Flyway ya incluye V1--V6.

## Efecto funcional y de seguridad

1. `ADMINISTRADOR` y `COORDINADOR` se convierten a `ADMIN`.
2. Los refresh tokens vigentes de las cuentas convertidas se revocan antes del
   cambio de rol. Un JWT cuyo claim de rol no coincida con el rol actual de la
   cuenta tampoco recibe autoridad.
3. El backend acepta como roles persistentes unicamente `SUPER_ADMIN`, `ADMIN`
   y `CONDUCTOR`.
4. Un `ADMIN` consulta y opera todos los grupos, conductores, vehiculos,
   asignaciones, incidencias, comunicados, reportes y auditoria de su propia
   organizacion, nunca los de otra organizacion.
5. Se retiran los endpoints de asignacion de coordinadores y el campo
   `coordinators` de la respuesta de grupos. `group_coordinator` se conserva
   solo como historia tecnica y no tiene operaciones de escritura ni efecto de
   autorizacion.

## Verificacion posterior

~~~powershell
.\scripts\Test-RutaFijaMigratedSchemaF13.ps1
.\verificar_ruta_fija.bat
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify
~~~

La primera orden es una auditoria de esquema sin iniciar la aplicacion. La
segunda usa solamente `ruta_fija_test`. La tercera crea datos temporales en esa
misma base aislada, comprueba login, refresh, ticket de un uso y WebSocket, y
la limpia al terminar. Mantenga libres los puertos 8080 y 4200 para el smoke.

## Contrato transitorio para F2.3

La API ya no debe recibir ni emitir `ADMINISTRADOR` o `COORDINADOR`. F2.3 debe
actualizar tipos Angular, rutas, menus, formularios de usuario, pruebas y
textos antes de reanudar la demostracion completa del CRM. No se debe crear
una compatibilidad silenciosa en frontend que vuelva a introducir esos roles.
