# F2.3 - CRM Angular y contrato ADMIN

## Alcance

F2.3 completa la unificacion de roles en el cliente web despues de la
migracion V6 y del backend F2.2. No aplica una nueva migracion de base de
datos: consume el contrato vigente V1--V6 y elimina del CRM los conceptos de
`ADMINISTRADOR`, `COORDINADOR` y la administracion de coordinadores de grupo.

El resultado visible es una sola navegacion de tenant para `ADMIN`. El rol
`SUPER_ADMIN` mantiene solo Resumen y Organizaciones; `CONDUCTOR` no puede
ingresar al CRM web administrativo.

## Cambios aplicados

- Tipos de sesion, guardas, rutas y menu usan solo `SUPER_ADMIN`, `ADMIN` y
  `CONDUCTOR`.
- Las diez rutas de tenant pertenecen exclusivamente a `ADMIN`.
- Usuarios permite crear o editar `ADMIN` y `CONDUCTOR`; una cuenta nueva
  inicia en `CONDUCTOR` por minimo privilegio.
- La pantalla y el cliente HTTP de Grupos no muestran, envian ni administran
  coordinadores. La relacion historica permanece solo en PostgreSQL/backend.
- Dashboard, Conductores, Vehiculos y Comunicados usan la capacidad de
  `ADMIN` en lugar de ramas por roles ya retirados.
- OpenAPI declara el modelo de roles actual y no expone endpoints de grupo
  para coordinadores.

## Verificacion reproducible

Desde la raiz del repositorio:

~~~powershell
.\scripts\Test-RutaFijaF23AdminUi.ps1
.\verificar_ruta_fija.bat
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify
~~~

La primera orden revisa fuentes activas, rutas, cliente HTTP, typecheck y
pruebas Angular. La segunda usa solamente `ruta_fija_test` para backend. La
tercera prueba login de un usuario demo `ADMIN`, refresh y WebSocket por el
proxy Angular; deja limpia la base de pruebas. Ninguna orden usa Docker.

Para la comprobacion visual, con los puertos 8080 y 4200 libres:

~~~powershell
.\iniciar_ruta_fija.bat
~~~

Abra `http://localhost:4200`, autentique una cuenta `ADMIN` existente y
confirme que aparecen Usuarios, Organizacion, Grupos, Conductores, Vehiculos,
Asignaciones, Incidencias, Comunicados, Reportes y Auditoria. Finalice con
`finalizar_ruta_fija.bat`.

## Contrato de transicion cerrado

Clientes que envien o esperen `ADMINISTRADOR`, `COORDINADOR`, el campo
`coordinators` de un grupo, o los endpoints
`/groups/{id}/coordinators` quedan incompatibles de forma intencional. No se
debe agregar una capa de compatibilidad silenciosa: un cliente debe migrar a
`ADMIN` y a la respuesta actual de grupos.
