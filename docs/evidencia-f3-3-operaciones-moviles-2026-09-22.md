# Evidencia de cierre F3.3 - Operaciones moviles

- Fecha local: 2026-09-22.
- Plataforma: PostgreSQL 16 nativo en loopback, Java 21 y Angular local.
- Docker: no utilizado.
- Base de desarrollo: `solucion_ruta_fija_1`, sin credenciales expuestas.

## Entrega comprobada

| Control | Resultado |
| --- | --- |
| Esquema | Flyway V9/V10 aplicadas sobre V1-V8; desarrollo termina en V1-V10. |
| Ensayo previo | Un dump pre-V9/V10 fue restaurado y migrado en una base de recuperacion aislada antes de tocar desarrollo. |
| Respuesta autentica | `MOBILE_CONFIRMATION` solo se acepta o rechaza desde una sesion movil real de su conductor. |
| Estados de flota | Aceptar reserva al conductor; el vehiculo se mantiene `DISPONIBLE` hasta iniciar servicio. |
| Idempotencia | Los cuatro comandos conservan recibo por evento; reintento igual se reproduce y evento distinto reutilizado entra en conflicto. |
| Vencimiento | El servidor expira pendientes sin cambiar conductor ni vehiculo. |
| Ubicacion | Consentimiento, atestacion de permiso, UPSERT de un unico punto y eliminacion al pasar a estado no permitido. |
| Privacidad | No hay tabla de historial ni coordenadas en `audit_event`; WebSocket no publica coordenadas. |
| Privilegios | `rf_app` tiene DML sobre recibos, sin `CREATE` en `public`. |

## Migracion protegida ejecutada

La ejecucion real completo el backup bajo
`backups/f3-3/solucion_ruta_fija_1_20260923T021621Z_f33_v8.dump`, creo la copia
`ruta_fija_recovery_20260922_f33`, aprobo V9/V10 alli y despues aplico las dos
migraciones sobre desarrollo. El manifiesto de datos se comparo antes y despues
de la migracion. La copia de recuperacion no fue eliminada ni sobrescrita.

~~~text
F3_3_REHEARSAL_FLYWAY=PASS migrations=V9,V10 target=isolated-recovery
F3_3_FLYWAY=PASS migrations=V9,V10 target=development
F3_3_MIGRATION=PASS ... flyway=V1-V10 v9=receipts v10=idempotency privileges=DML_without_DDL docker=0
~~~

## Hallazgo corregido antes del cierre

La primera prueba de expiracion detecto que un rechazo funcional devolvia el
error correcto, pero perdia el recibo de V10 por rollback de la transaccion
exterior. Se ajustaron los cuatro comandos de asignacion para no revertir un
`ApplicationException`. Las validaciones ocurren antes de mutar recursos; la
expiracion es una mutacion intencional y ahora queda persistida junto con su
recibo rechazado.

La prueba focalizada posterior aprobo 24 pruebas unitarias y 4 pruebas de
integracion de `MobileOperationFlowIT`, sin fallos, errores ni omisiones. Ese
flujo cubre reintento aplicado de aceptar, rechazar, iniciar y completar, ademas
del rechazo durable, conflicto de evento, expiracion, ubicacion y audiencia. La
verificacion integral nativa posterior aprobo 24 pruebas unitarias y 25 pruebas
de integracion en 5 suites, tambien sin fallos, errores ni omisiones, contra
`ruta_fija_test`; al finalizar la limpio y mantuvo V1-V10.

~~~text
F3_3_MOBILE_OPERATION_IT=tests:4;failures:0;errors:0;skipped:0
F3_3_MOBILE_OPERATIONS_AUDIT=PASS flyway=V1-V10 mobile=own_routes idempotency=durable location=current_only privacy=no_coordinate_audit privileges=DML_without_DDL docker=0
F3_3_NATIVE_VERIFY=PASS flyway=V1-V10 mobile_operations=PASS docker=0
F3_3_TEST_DATABASE_POST_VERIFY=1,2,3,4,5,6,7,8,9,10|0|0|0|0
F3_3_CURRENT_BOOTSTRAP_COMPATIBILITY=PASS flyway=V1-V10 grants=current docker=0
F3_3_RUNTIME_SMOKE=PASS health=UP frontend=200 openapi=mobile_routes docker=0
F3_3_RUNTIME_CLEANUP=PASS ports=8080,4200
~~~

## Limites confirmados y siguiente puerta

No se implementaron pantallas Flutter, FCM, GPS de fondo, fotos ni historial
de ubicaciones. OpenAPI exhaustivo, matriz ampliada y reportes de estados
móviles fueron completados posteriormente en F3.4. Flutter permanece para la
siguiente fase autorizada.
