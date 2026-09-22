# Contrato API F2.3 - ADMIN

Base: `/api/v1`. Fuente ejecutable: `GET /v3/api-docs`.

## Roles activos

| Rol | Alcance |
| --- | --- |
| `SUPER_ADMIN` | Resumen y administracion global de organizaciones. No opera recursos de tenant por herencia. |
| `ADMIN` | Todas las operaciones CRM dentro de su propia organizacion. |
| `CONDUCTOR` | Sin acceso al CRM administrativo; las rutas moviles se incorporan en F3/F4. |

La respuesta de `/auth/login`, `/auth/refresh` y `/auth/me` emite `ADMIN` para
una cuenta administrativa. Claims JWT antiguos no son una compatibilidad
valida: el backend los rechaza y la migracion V6 revoco sus refresh tokens.

## Usuarios y grupos

Las solicitudes `POST /users` y `PATCH /users/{userId}` admiten en el tenant:

~~~json
{"role":"ADMIN"}
~~~

o:

~~~json
{"role":"CONDUCTOR"}
~~~

`SUPER_ADMIN` no puede ser creado desde administracion de tenant.

La respuesta de `GET /groups` y las respuestas de crear/editar grupo contienen
identidad, nombre, descripcion, estado y fechas. Ya no contienen
`coordinators`.

No existen en OpenAPI ni en la aplicacion los endpoints:

~~~text
POST   /groups/{groupId}/coordinators
DELETE /groups/{groupId}/coordinators/{userId}
~~~

Una solicitud a esas rutas recibe `404`; no crea ni elimina datos historicos.

## Autorizacion

Las rutas CRM de tenant requieren `ADMIN`. La interfaz solo presenta acciones
permitidas, pero Spring Security vuelve a autorizar y todos los servicios
resuelven la organizacion desde el token. Un `ADMIN` nunca puede leer o escribir
recursos de otro tenant.

## Regresion de contrato

La prueba de integracion `AuthFlowIT` consulta `/v3/api-docs` y verifica:

1. enum `UserRole = [SUPER_ADMIN, ADMIN, CONDUCTOR]`;
2. ausencia del path de coordinadores de grupo;
3. descripcion OpenAPI que declara el rol `ADMIN`.

No se introducen rutas moviles en F2.3; pertenecen a la fase F3 de la Etapa 2.
