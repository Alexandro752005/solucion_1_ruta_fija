# Conexión local PostgreSQL — F0.2

Fecha de verificación: 2026-09-19. Base de desarrollo elegida por el propietario: `solucion_ruta_fija_1`.

| Parámetro | Valor verificado |
|---|---|
| Registro en pgAdmin | `CN_LOCAL` (etiqueta; no es el host JDBC) |
| Host / puerto | `127.0.0.1:5432` |
| Base | `solucion_ruta_fija_1` |
| Propietario y rol validado | `soporte` |
| Servidor / controlador | PostgreSQL 16.15 / pgJDBC 42.7.11 |
| Codificación | UTF8, servidor y cliente |
| Perfil Spring | `local`, definido en `backend/src/main/resources/application-local.properties` |
| Contraseña | Variable de proceso `SPRING_DATASOURCE_PASSWORD`; no se guarda en el proyecto |
| Tiempo de la sesión de aplicación | UTC, mediante inicialización de la conexión Hikari |

El perfil local tiene valores predeterminados de URL y usuario y permite sobreescribirlos mediante `SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`. Para utilizar esta base se debe activar explícitamente el perfil `local`; una variable antigua de datasource tiene precedencia y debe revisarse. Las credenciales `.env` de Compose no se usan en esta comprobación.

## Resultado y alcance

Conexión autenticada comprobada con `psql` y con Java usando el controlador resuelto por Maven para el backend. La prueba JDBC carga el mismo archivo de configuración local, consulta catálogos en modo de solo lectura y revierte la transacción.

La base es nueva: **cero tablas de negocio, sin `flyway_schema_history` y sin `btree_gist` instalada**. La extensión está disponible y el propietario tiene permisos para crearla. La ejecución de V1–V5 queda pendiente de la fase nativa posterior al respaldo y su prueba de restauración.

Esta comprobación no inicia el servidor web. Flyway sigue habilitado y JPA conserva `ddl-auto=validate`, por lo que arrancar Spring completo en una fase futura sí aplicará las migraciones. Se han desactivado las semillas en el perfil `local`. JWT y el lanzador nativo se completarán en F1.

## Repetir solamente la prueba JDBC

Desde una consola PowerShell interactiva, en el directorio `backend`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress dependency:build-classpath '-Dmdep.outputFile=target/f02-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'No se pudo resolver el classpath del backend.' }

$jdbcClasspath = (Get-Content -LiteralPath 'target/f02-classpath.txt' -Raw).Trim()
java --class-path $jdbcClasspath '..\scripts\VerifyLocalPostgresql.java' 'src\main\resources\application-local.properties'
if ($LASTEXITCODE -ne 0) { throw 'La conexión no fue aprobada.' }
```

Si no existe `SPRING_DATASOURCE_PASSWORD` en el proceso, Java pide la contraseña con entrada oculta. Una consola no interactiva debe recibir esa variable mediante su mecanismo de secretos. No se requiere JWT para este diagnóstico. El resultado esperado es `JDBC_PREFLIGHT=PASS (conexion de solo lectura)`.

El verificador rechaza una URL distinta a la base aprobada antes de autenticar, evitando probar por accidente contra otra base.

## Pendientes ya asignados

- F0.3: respaldo de `solucion_ruta_fija_1`. Al estar vacía, su respaldo no representa los antiguos datos del entorno Docker; cualquier recuperación histórica debe identificar su fuente por separado.
- F0.4: restauración en `ruta_fija_recovery_YYYYMMDD`, con identificación explícita de destino.
- F1: base independiente `ruta_fija_test`, arranque nativo y separación de rol migrador/aplicación; `soporte` es superusuario y se utiliza aquí para el preflight, no como diseño de mínimos privilegios definitivo.
- F1: restringir listener a loopback. Actualmente escucha en todas las interfaces, pero HBA solo autoriza loopback con SCRAM; no se amplió acceso de red.

F0.2 tiene aprobación técnica. La siguiente subfase necesita el verde explícito del propietario.
