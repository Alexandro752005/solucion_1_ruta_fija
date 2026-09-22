# Evidencia F2.3 - CRM ADMIN

Fecha: 2026-09-22.

## Objetivo verificado

F2.3 adapta el CRM Angular y el contrato OpenAPI al modelo de tres roles
vigentes: `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR`. No cambia la estructura de la
base productiva ni inicia funciones de la siguiente fase.

## Resultado de controles

| Control | Evidencia | Resultado |
| --- | --- | --- |
| Modelo y UI Angular | `Test-RutaFijaF23AdminUi.ps1` | PASS: 0 roles antiguos activos, 0 operaciones de coordinadores, 10 rutas ADMIN, typecheck y 40 pruebas aprobadas. |
| Backend y OpenAPI | Maven/Failsafe sobre `ruta_fija_test` | PASS: 23 pruebas unitarias y 14 de integracion; `AuthFlowIT` valida enum ADMIN y ausencia de endpoints retirados. |
| Esquema de desarrollo | `Test-RutaFijaMigratedSchemaF13.ps1` | PASS: V1--V6, roles actuales, auditoria append-only y restricciones operativas activas. |
| Flujo real por proxy | `Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify` | PASS: login, `/auth/me`, refresh, WebSocket listo y rechazo de ticket repetido. |
| Aislamiento de ejecucion | Scripts nativos | PASS: pruebas sobre `ruta_fija_test`; la base `solucion_ruta_fija_1` no se modifica durante las pruebas. |
| Contenedores | Auditorias y scripts | PASS: Docker no se inicia ni se requiere. |

## Salidas reproducibles

~~~text
F2_3_FRONTEND_AUDIT=PASS active_legacy_roles=0 coordinator_ui=0 admin_routes=10 tests=aprobados docker=0
F2_2_SCHEMA_AUDIT=PASS
F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0
~~~

El informe Failsafe de la ejecucion nativa registra `completed=14`,
`errors=0` y `failures=0`; las pruebas unitarias registran 23 casos sin
fallos. El nombre historico `F2_2_NATIVE_VERIFY` del script se conserva para
no romper la automatizacion existente, pero en esta ejecucion tambien valida
la regresion incorporada por F2.3.

## Cobertura funcional comprobada

- Una cuenta `ADMIN` puede acceder a los diez modulos de tenant: Resumen,
  Usuarios, Organizacion, Grupos, Conductores, Vehiculos, Asignaciones,
  Incidencias, Comunicados, Reportes y Auditoria.
- `SUPER_ADMIN` queda delimitado al resumen y a Organizaciones.
- `CONDUCTOR` no entra al CRM administrativo.
- El formulario de usuarios solo permite `ADMIN` o `CONDUCTOR` y parte de
  `CONDUCTOR` como valor de minimo privilegio.
- La UI, DTO y cliente HTTP de grupos no gestionan coordinadores.
- Los clientes que aun invoquen endpoints o campos de coordinadores reciben la
  incompatibilidad intencional del contrato, sin borrar historia de datos.

## Dictamen de salida

F2.3 queda tecnicamente listo para la revision visual del usuario y no abre
F2.4 ni F3. La siguiente fase solo puede empezar tras la confirmacion verde
explicita del usuario.
