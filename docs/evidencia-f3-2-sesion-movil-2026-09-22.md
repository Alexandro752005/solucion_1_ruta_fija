# Evidencia de cierre F3.2 - Sesion movil

- Fecha: 2026-09-22.
- Alcance certificado: login, refresh y logout movil; no operacion movil.
- Plataforma: PostgreSQL 16 nativo en loopback, Java 21 y Angular local.
- Docker: no utilizado.

## Entrega comprobada

| Control | Resultado |
| --- | --- |
| Rutas permitidas | Solo `POST /api/v1/mobile/auth/login`, `refresh` y `logout`. |
| Identidad | Solo `CONDUCTOR` activo con `driver.user_id` vinculado y del mismo tenant. |
| Autoridad | `organizationId` y `driverId` se emiten desde servidor; el cliente no los presenta para decidir acceso. |
| Canal web/movil | Cookie web y refresh JSON movil usan formatos opacos distintos y no se pueden cruzar. |
| Rotacion | Cada refresh entrega sucesor; reutilizacion revoca la familia. |
| Concurrencia | PostgreSQL serializa la fila mediante `SELECT ... FOR UPDATE`. |
| Errores | Sobre uniforme con `code`, `correlationId`, `path`, `errors` y `Cache-Control: no-store`. |
| Auditoria | Registra acciones y codigos no sensibles; la prueba confirma que no guarda el refresh crudo. |
| Esquema | Flyway V1-V8; no se introdujeron V9/V10. |

## Hallazgo corregido antes del cierre

La prueba concurrente inicial detecto dos renovaciones exitosas simultaneas.
La causa fue que el bloqueo pesimista de Hibernate se aplicaba como bloqueo
posterior al cargar relaciones. Se sustituyo por una consulta nativa directa a
PostgreSQL sobre `refresh_token` con `FOR UPDATE`.

La prueba final lanza dos refresh con el mismo token: exactamente uno responde
`200`, el otro responde `401` por reutilizacion y el sucesor del primero queda
invalidado junto con la familia. El hallazgo no quedo abierto ni se aplico una
migracion de datos para resolverlo.

## Evidencia ejecutada

~~~text
F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native
F3_2_SESSION_AUDIT=PASS flyway=V1-V8 mobile_auth=login,refresh,logout conductor_bound=1 refresh=body_rotating_for_update errors=code,correlationId operational_mobile_endpoints=0 docker=0
F3_2_VSCODE_TASK=PASS
F3_2_NATIVE_VERIFY=PASS flyway=V1-V8 mobile_session=PASS docker=0
F1_1B_RUNTIME=PASS backend=UP frontend=UP ports=8080,4200
F3_2_RUNTIME_CLEANUP=PASS ports=8080,4200
~~~

Las pruebas focalizadas `MobileAuthFlowIT` aprobaron 7 casos de integracion y
las unidades aprobaron 24 casos. La verificacion integral nativa
`verificar_ruta_fija.bat` registro 24 pruebas unitarias y 21 de integracion,
sin fallos, errores ni omisiones, contra `ruta_fija_test`.

## Limites confirmados

No se implementaron `/mobile/me`, asignaciones, aceptar/rechazar/iniciar,
ubicacion, consentimiento, incidencias, comunicados, idempotencia V10, Flutter
ni reportes de estados moviles. PostgreSQL permanece privado y los puertos
8080/4200 fueron cerrados despues del smoke de runtime.

## Puerta siguiente

F3.2 queda lista para revision del usuario. Solo con `VERDE - F3.3` se podran
abrir endpoints operativos y reglas de negocio movil.
