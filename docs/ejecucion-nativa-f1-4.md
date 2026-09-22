# F1.4 — Pruebas de integración nativas

F1.4 elimina la dependencia operativa de Testcontainers para las pruebas de
integración. La única base permitida es PostgreSQL 16 local en
`ruta_fija_test`; desarrollo (`solucion_ruta_fija_1`) y recuperación quedan
fuera del flujo.

## Ejecutar la puerta completa

Desde la raíz del repositorio:

~~~text
.\verificar_ruta_fija.bat
~~~

El equivalente en PowerShell es:

~~~powershell
.\scripts\Invoke-RutaFijaNativeTest.ps1 -Action Verify
~~~

Para una comprobación sin ejecutar Maven:

~~~powershell
.\scripts\Invoke-RutaFijaNativeTest.ps1 -Action Preflight
~~~

El wrapper carga `backend/.local/ruta-fija-native.env`, nunca lo versiona y no
imprime contraseñas. También puede ejecutarse directamente desde `backend`:

~~~powershell
.\mvnw.cmd verify
~~~

Las pruebas leen la misma configuración local de forma protegida cuando las
variables aún no están presentes en la consola.

## Controles de seguridad

- Solo se acepta `jdbc:postgresql://127.0.0.1:5432/ruta_fija_test` con
  `rf_test` para la aplicación de pruebas y `rf_migrator` para Flyway.
- Se rechaza cualquier URL de desarrollo, recuperación, host remoto o puerto
  diferente antes de abrir el contexto Spring.
- El perfil `test` aplica V1–V5 y la migración repetible
  `R__native_test_role_privileges.sql`. Esta entrega DML a `rf_test` y
  revoca `CREATE` sobre `public`.
- Antes y después de cada prueba de integración se limpian solo las tablas de
  negocio de `ruta_fija_test`, conservando `flyway_schema_history`.
- Failsafe corre en un único fork y sin paralelismo para no mezclar fixtures.

Al aprobar la puerta se informa:

~~~text
F1_4_NATIVE_VERIFY=PASS unit=21 integration=13 flyway=V1-V5 docker=0
~~~

La base de pruebas conserva su esquema para la siguiente ejecución, pero queda
sin datos de negocio. Docker, Compose y Testcontainers no intervienen.
