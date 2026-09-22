# F2.4 - Cierre de G2: consolidacion ADMIN

## Proposito

El plan maestro denomina `G2` a la puerta final de la consolidacion de roles.
En la secuencia de trabajo se ejecuta como F2.4 para cerrar los Dias 3--4 sin
agregar funciones nuevas. No inicia F3, migraciones V7--V10 ni Flutter.

La puerta verifica que solo existan los roles activos `SUPER_ADMIN`, `ADMIN` y
`CONDUCTOR`; que `group_coordinator` sea historia tecnica sin autorizacion ni
escrituras; que sesiones antiguas se rechacen; que el aislamiento entre tenants
persista; y que el camino operativo siga siendo nativo, sin Docker.

## Ejecucion

Desde la raiz del repositorio, puede ejecutar primero la revision estructural:

~~~powershell
.\scripts\Test-RutaFijaG2AdminClosure.ps1 -SkipExecutionChecks
~~~

Para la puerta completa, mantenga libres los puertos 8080 y 4200 y ejecute:

~~~powershell
.\scripts\Test-RutaFijaG2AdminClosure.ps1
~~~

La comprobacion completa encadena, en este orden:

1. Auditoria CRM F2.3: fuentes Angular, typecheck y Vitest.
2. Auditoria de esquema V1--V6 sobre desarrollo en modo de lectura.
3. Auditoria local F1.6: loopback, SCRAM, minimo privilegio y secretos.
4. Pruebas unitarias e integracion sobre `ruta_fija_test`.
5. Smoke REST, refresh y WebSocket por el proxy Angular sobre la base aislada.

Los dos ultimos pasos limpian `ruta_fija_test`; nunca operan sobre
`solucion_ruta_fija_1` con datos de prueba.

## Controles propios de G2

| Control | Regla de aceptacion |
| --- | --- |
| Roles Java | `UserRole` contiene exactamente los tres roles objetivo. |
| Backend productivo | No hay `ADMINISTRADOR`, `COORDINADOR` ni rutas de coordinadores. |
| Historia de grupos | Solo entidad, identificador y repositorio marker de lectura; sin `save`, `delete` o `remove`. |
| Regresion | Las integraciones verifican tenant completo para ADMIN, 404 de endpoint retirado, OpenAPI y token invalidado tras cambio de rol. |
| Angular | No quedan guardas, rutas, UI ni cliente HTTP de coordinadores. |
| Operacion | Los BAT y runtime nativos no invocan Docker, Docker Compose ni Testcontainers. |

La salida correcta es:

~~~text
G2_ADMIN_CONSOLIDATION=PASS roles=SUPER_ADMIN,ADMIN,CONDUCTOR group_coordinator=history tenant=isolation legacy_sessions=rejected docker=0 checks=completa
~~~

## Nota sobre F1.2

`Test-RutaFijaRoleIsolation.ps1` es una caracterizacion previa a Flyway: espera
una base sin tablas de negocio. Tras V1--V6, que existan 13 tablas es correcto,
por lo que ese script ya no es una puerta de cierre vigente. F2.4 usa las
auditorias F1.6 y F1.3/F2.2 actuales para verificar seguridad, privilegios y
esquema sin confundir una linea base historica con una regresion.

## Criterio de salida

F2.4/G2 solo puede marcarse verde con la ejecucion completa aprobada, evidencia
versionada y un commit trazable. Despues de la aprobacion explicita del usuario
podra comenzar F3.1, la primera fase de API movil.
