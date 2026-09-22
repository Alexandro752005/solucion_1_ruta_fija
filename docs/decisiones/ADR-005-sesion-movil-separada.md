# ADR-005 - Sesion movil de conductor separada del navegador

- Estado: aceptado e implementado en F3.2.
- Fecha: 2026-09-22.
- Alcance: Dia 5 de la Etapa 2; solo sesion movil.

## Contexto

El CRM web conserva un refresh token opaco en una cookie `HttpOnly`, limitada
al prefijo web `/api/v1/auth`. Una aplicacion Flutter no puede depender de esa
cookie del navegador: debe conservar su refresh token en el almacenamiento
seguro del sistema operativo y enviarlo deliberadamente bajo HTTPS.

La fuente de autoridad sigue siendo el backend. Un cliente movil no puede
elegir el tenant, el conductor ni un rol para operar. El contrato V7/V8 ya
existe en PostgreSQL, pero F3.2 no habilita comandos de asignacion, ubicacion,
incidencias ni comunicados.

## Decision

### Rutas y transporte

Se publican exclusivamente estas rutas, bajo `/api/v1`:

| Ruta | Transporte | Resultado |
| --- | --- | --- |
| `POST /mobile/auth/login` | JSON | Autentica una cuenta `CONDUCTOR` vinculada y activa. |
| `POST /mobile/auth/refresh` | JSON con `refreshToken` | Rota el refresh movil. |
| `POST /mobile/auth/logout` | JSON con `refreshToken` | Revoca la familia y responde `204`. |

Las tres respuestas usan `Cache-Control: no-store` y nunca emiten,
consumen ni eliminan la cookie web. El login y el refresh devuelven:
`accessToken`, `refreshToken`, `tokenType`, `expiresIn`,
`refreshExpiresIn` y la identidad minima `{ id, driverId, organizationId,
fullName, role }`.

### Vinculo conductor, tenant y claims

El login acepta credenciales, pero el servidor obtiene el usuario y exige,
antes de emitir sesion, que se cumplan todas estas condiciones:

1. El usuario es activo y tiene rol `CONDUCTOR`.
2. Existe exactamente una relacion `driver.user_id` para esa cuenta.
3. Conductor y usuario pertenecen a la misma organizacion.
4. El conductor esta activo.

Si no existe el vinculo se responde `MOBILE_USER_NOT_DRIVER`; si el conductor
esta inactivo, `DRIVER_INACTIVE`. Ambos siguen el sobre uniforme de error con
`code`, `correlationId`, `path` y lista `errors` ya usado por la API.

El access token emitido por el servidor contiene el sujeto, la organizacion,
el rol, `driverId` y `sessionChannel=MOBILE`. En las futuras rutas propias del
conductor, F3.3 resolvera la identidad desde esos claims y la relacion de base;
no recibira `organizationId` ni `driverId` como autoridad desde Flutter.

### Separacion y rotacion de refresh

El refresh movil es opaco, de 256 bits, y se almacena en PostgreSQL solamente
como hash SHA-256. Lleva el formato `m1.<base64url>` para separarlo del formato
web y el hash incluye el prefijo completo. Por ello, quitar o agregar el
prefijo no permite reutilizar una sesion en el otro canal.

Cada refresh produce un sucesor. El token anterior queda marcado como usado;
una reutilizacion revoca toda la familia. La lectura del token se realiza con
`SELECT ... FOR UPDATE` nativo en PostgreSQL dentro de la transaccion. Esto
serializa dos renovaciones simultaneas: una puede tener exito y la otra detecta
la reutilizacion, dejando tambien invalidado el sucesor.

Los secretos crudos no se escriben en logs ni en `audit_event`. La auditoria
solo registra acciones y, si corresponde, un codigo funcional no sensible.

## Consecuencias

- El navegador mantiene su contrato de cookie y sus sesiones no se mezclan con
  la aplicacion movil.
- Flutter debera usar almacenamiento seguro del sistema operativo; esa
  integracion queda para F4, no se simula aqui.
- No se agregan V9/V10 ni se modifica el esquema V1-V8.
- No existen aun `/mobile/me`, asignaciones, respuesta de conductor,
  ubicacion, incidencias, comunicados o reportes moviles. Esas capacidades
  requieren las puertas posteriores de F3.3 y F3.4.
- Fuera de `localhost`, el cuerpo que contiene el refresh solo puede viajar por
  HTTPS. PostgreSQL sigue privado en `127.0.0.1:5432` y Docker no interviene.
