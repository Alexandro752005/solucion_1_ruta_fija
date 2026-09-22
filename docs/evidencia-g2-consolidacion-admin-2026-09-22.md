# Evidencia F2.4 / G2 - Consolidacion ADMIN

Fecha: 2026-09-22.

## Alcance cerrado

F2.4 materializa la puerta G2 del plan maestro. No incorpora F3, endpoints
moviles, Flutter ni migraciones posteriores a V6. Su objetivo es demostrar que
la unificacion `ADMINISTRADOR` + `COORDINADOR` hacia `ADMIN` esta completa,
segura, trazable y operable sin Docker.

## Resultado de la puerta

| Control | Resultado comprobado |
| --- | --- |
| Contrato activo | `SUPER_ADMIN`, `ADMIN` y `CONDUCTOR` son los unicos valores de `UserRole`. |
| CRM Angular | 16 archivos de prueba y 40 pruebas aprobadas; 10 rutas de tenant son exclusivas de `ADMIN`. |
| Backend y OpenAPI | 23 pruebas unitarias y 14 integraciones sin errores; enum API actual y rutas de coordinadores ausentes. |
| Esquema | V1--V6, constraint de roles actual, auditoria append-only y restricciones operativas aprobadas. |
| Seguridad nativa | PostgreSQL loopback, SCRAM, minimo privilegio y secretos fuera de Git aprobados. |
| Tiempo real | Login, `/auth/me`, refresh, `stream.ready` y rechazo de ticket repetido comprobados por proxy Angular. |
| Limpieza | `ruta_fija_test` limpiada y puertos 4200/8080 sin listeners al finalizar. |
| Docker | Ningun BAT ni runtime operativo depende de Docker, Docker Compose o Testcontainers. |

## Salidas observadas

~~~text
F2_3_FRONTEND_AUDIT=PASS active_legacy_roles=0 coordinator_ui=0 admin_routes=10 tests=aprobados docker=0
F2_2_SCHEMA_AUDIT=PASS
F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native
F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0
G2_RUNTIME_CLEAN=PASS ports=4200,8080
F1_4_CLEAN=PASS database=ruta_fija_test
~~~

El resumen Failsafe de la ejecucion actual registra `completed=14`,
`errors=0` y `failures=0`. El log del smoke registra un handshake WebSocket
`101 SWITCHING_PROTOCOLS` seguido del rechazo del segundo intento con el mismo
ticket. El verificador G2 completo encadena estos controles y termina con
`G2_ADMIN_CONSOLIDATION=PASS` cuando todos concluyen correctamente.

## Auditoria residual

Las coincidencias restantes de terminos retirados fueron clasificadas antes del
dictamen:

| Ubicacion | Clasificacion | Estado |
| --- | --- | --- |
| V1, V2 y V6 de Flyway | Historia y migracion certificada | Permitida; no es autorizacion activa. |
| F2.1A/F2.1B y fixture legacy | Ensayo reproducible previo a V6 | Permitida; no se usa como runtime. |
| `GroupCoordinator`, identificador y repositorio marker | Historia tecnica de lectura | Permitida por ADR-003; sin escritura ni efecto de permisos. |
| Pruebas negativas de claims antiguos y ruta retirada | Seguridad de regresion | Requerida para impedir compatibilidad silenciosa. |
| Evidencias de fases anteriores | Registro historico | Conservada y no presentada como guia operativa vigente. |

No se encontro una autorizacion productiva basada en roles retirados ni en
`group_coordinator`.

## Nota de auditoria historica

El chequeo F1.2 de aislamiento fue creado para una base previa a Flyway y
espera cero tablas de negocio. Tras V6 detecta correctamente las 13 tablas
actuales, por lo que no representa un fallo de privilegios ni una puerta G2
vigente. La seguridad posterior se verifica con F1.6 y el esquema actual con
F1.3/F2.2.

## Dictamen

**G2 APROBADA tecnicamente.** F2 queda cerrada a la espera de la validacion
verde del usuario. El siguiente trabajo autorizado sera F3.1, no antes.
