# Evidencia F2.2 - Consolidacion backend ADMIN

Fecha: 2026-09-22. Entorno: PostgreSQL 16 nativo en loopback. Docker: no usado.

## Aplicacion real de V6

La base de desarrollo `solucion_ruta_fija_1` estaba en V1--V5 y sin datos de
negocio en la linea base F2.1A. Se aplico la candidata certificada sin alterar
su checksum. Resultado registrado por el ejecutor protegido:

~~~text
F2_2_FLYWAY=PASS migration=V6 target=development
F2_2_ADMIN_MIGRATION=PASS backup=solucion_ruta_fija_1_20260922T070433Z_f21a.dump sha256=de413296e4ce9bd41e2e37a478af8672dd1560a76062bda714808ef696542eaf flyway=V1-V6 roles=SUPER_ADMIN,ADMIN,CONDUCTOR group_coordinator=history docker=0
~~~

La conversion de registros legacy con usuarios y refresh tokens fue ejercitada
previamente en F2.1B sobre una recuperacion aislada y datos deterministas; la
base de desarrollo no contenia esos registros al aplicar V6.

## Auditoria de esquema

~~~text
F2_2_SCHEMA_AUDIT=PASS
flyway=V1-V6 roles=SUPER_ADMIN,ADMIN,CONDUCTOR btree_gist=activo audit=append-only exclusion_constraints=activas
rf_app=DML_selectivo_sin_DDL historia_Flyway=protegida
F2.2 no inicio Spring Boot, API, CRM ni Docker.
~~~

## Pruebas de backend

| Alcance | Resultado |
| --- | --- |
| Compilacion Maven | 156 fuentes compiladas, aprobada |
| Unitarias | 23 ejecutadas, 0 fallos, 0 errores |
| Integracion Failsafe | 13 ejecutadas, 0 fallos, 0 errores |
| Multitenancy y retiro de endpoint de coordinadores | Cubierto por `FleetManagementIT` |
| JWT de rol antiguo | Cubierto por `SecurityConfigTest` y `CurrentUserProviderTest` |
| Broadcast de operaciones | Cubierto por `OperationStreamBroadcasterTest` |
| Reporte de cobertura | Generado sin desajuste entre clases y datos de ejecucion |

## Smoke de integracion local

~~~text
F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0
F1_4_CLEAN=PASS database=ruta_fija_test
~~~

El nombre del smoke conserva su identificador historico F1.5; en F2.2 se
reutilizo para verificar que el nuevo usuario demo `ADMIN` puede autenticarse,
renovar sesion y abrir un WebSocket por el proxy Angular. El frontend visual se
actualizara en F2.3.

## Restriccion declarada

F2.2 no modifica Angular ni pretende cerrar la experiencia de interfaz de
ADMIN. El siguiente trabajo autorizado, solo tras aprobacion verde, es F2.3.
