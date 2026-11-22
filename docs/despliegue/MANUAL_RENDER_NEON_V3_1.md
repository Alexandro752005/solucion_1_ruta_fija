# Procedimiento controlado para Render y Neon

**Estado** · Preparación local. La migración, el despliegue y la entrega de APK requieren decisiones del titular.

## 1. Diagnóstico privado y solo lectura

1. Identificar proyecto y rama/base de Neon y confirmar la base de origen. Registrar versión, capacidad, región, TLS y rol. Usar alias privados de libpq por conexión directa. En Neon, validar nombre del servidor y CA con `verify-full`. Conservar credenciales y salidas en custodia privada.
2. Ejecutar desde la raíz. Sustituir los marcadores por nombres de base confirmados. El script exige nombre exacto, fase válida y transacción de solo lectura. En origen y copia restaurada exige V1–V51 exitosas, checksums presentes y `btree_gist` instalado. En fase vacía revisa ausencia de relaciones de usuario en `public`.

~~~powershell
psql -X -v ON_ERROR_STOP=1 --dbname='service=rf_origen_lectura' -v expected_database='<base-origen>' -v phase=baseline -f scripts/sql/RenderNeonPreflight.sql
psql -X -v ON_ERROR_STOP=1 --dbname='service=rf_neon_lectura' -v expected_database='<base-destino>' -v phase=empty -f scripts/sql/RenderNeonPreflight.sql
~~~

3. Comparar historial con los archivos del artefacto exacto mediante Flyway `validate` sobre una copia y un usuario de lectura del historial. Confirmar objetos, propietarios, restricciones, eventos protegidos, permisos efectivos y cuentas seleccionadas. El script emite totales y huellas de definiciones. Autorizar una comparación privada por identidad y relaciones que compruebe cuentas, correo verificado, términos exactos, estados y periodos vigentes. Repetir con fase `restored` tras la restauración. Las huellas de definiciones pueden variar al cambiar la versión mayor.

## 2. Restauración manual y permisos

Mantener desconectados backend, CRM y APK del destino hasta verificar datos y permisos. Primero ensayar en una copia desechable. Acordar alcance, datos necesarios, cifrado, acceso y descarte. Para otro restore exigir una base vacía confirmada en su rama de Neon.

~~~powershell
pg_dump --format=custom --lock-wait-timeout=5s --dbname='service=rf_origen_dump_aprobado' --file="$env:RF_DUMP_PRIVADO"
pg_restore --list --file="$env:RF_TOC_PRIVADO" "$env:RF_DUMP_PRIVADO"
pg_restore --single-transaction --exit-on-error --no-owner --no-acl --dbname='service=rf_destino_restore_aprobado' "$env:RF_DUMP_PRIVADO"
~~~

`--no-owner` hace propietario al rol efectivo del restore. `--no-acl` pospone la aplicación de los grants y revokes hasta revisar los propietarios y los roles. Conservar el destino sin acceso de clientes durante esta ventana. Preservar las ACL dentro del archivo de custodia y generar su lista y SQL en privado.

~~~powershell
Get-Content -LiteralPath $env:RF_TOC_PRIVADO | Where-Object { $_ -match '^\d+;\s+\d+\s+\d+\s+(?:DEFAULT ACL|ACL)\s' } | Set-Content -Encoding ascii -LiteralPath $env:RF_ACL_TOC_PRIVADO
pg_restore --schema-only --no-owner --use-list="$env:RF_ACL_TOC_PRIVADO" --file="$env:RF_ACL_SQL_PRIVADO" "$env:RF_DUMP_PRIVADO"
~~~

Revisar esa salida antes de aplicarla. Si coinciden los propietarios y los roles nombrados, reproducir las entradas de ACL con `pg_restore --schema-only --no-owner --use-list` y `--single-transaction --exit-on-error`. La alternativa para propietarios o roles distintos requiere un SQL revisado para el destino. Ejecutar el paso aprobado únicamente contra la copia. El ensayo reproduce dos veces el catálogo de ACL sintético y comprueba permisos efectivos de auditoría, correcciones V51, historial Flyway y autoridades de retención.

~~~powershell
psql -X -v ON_ERROR_STOP=1 --dbname='service=rf_neon_lectura' -v expected_database='<base-destino>' -v phase=restored -f scripts/sql/RenderNeonPreflight.sql
~~~

| Rol | Facultades previstas | Puerta de verificación |
|---|---|---|
| Dueño / restaurador dedicado | Poseer objetos restaurados o asumir por ventana controlada el rol `rf_migrator`. Instalar la extensión si el destino lo admite. | La cuenta runtime carece de esta membresía. Las funciones `SECURITY DEFINER` tienen dueño coherente con sus guards. |
| `rf_migrator` | Aplicar cambios de esquema autorizados por separado y gestionar owner/ACL. | Credenciales ausentes del runtime. Historial y extensión cotejados. |
| `rf_app` | `USAGE` en `public` y operaciones por tabla, secuencia y función según matriz del origen. Lectura/inserción en auditoría, lectura del historial de correcciones y ejecución de la firma V51. | Sin propietario, DDL, `TRUNCATE`, acceso a tablas de autoridad ni bypass de RLS. Sin membresía en `neon_superuser`. |
| Lector de inspección | `CONNECT`, `USAGE` y lectura necesaria para salud y validación. Configurar transacciones de solo lectura a nivel de rol y conexión. | Sin DML/DDL ni ejecución de funciones de escritura. Uso exclusivo para `restore-inspection`. |
| `PUBLIC` y `rf_test` | Conservar revokes del origen en esquema, tablas protegidas y funciones. `rf_test` queda exclusivo de pruebas. | Verificar `EXECUTE`, grants por defecto, membresías y triggers. Excluir `db/test-migration` en destino. |

Para las funciones `SECURITY DEFINER`, revisar firma y dueño. Mantener los revokes a `PUBLIC` de V46, de las rutinas de retiro personal V49 y de las funciones protegidas V51. Confirmar sólo las firmas que V46/V49/V51 y los catálogos del origen conceden al runtime. Los triggers operan en sus tablas y la capacidad de modificarlos queda con el dueño. Cotejar privilegios de secuencias explícitamente si existen en el origen. Verificar índices GiST, las restricciones tenant y los triggers activos comparando las definiciones y las pruebas de regresión.

Con la matriz aprobada, preparar un SQL manual e idempotente de `ALTER ... OWNER TO`, `REVOKE`, `GRANT` y `ALTER DEFAULT PRIVILEGES FOR ROLE ...`. Seleccionar cada objeto y firma. Revisar que `public` no conceda `CREATE` al runtime. Tras ejecutar en un destino autorizado, volver a consultar el catálogo bajo dueño y runtime. La matriz y el script final de ACL dependen de los catálogos reales.

Si falla identidad, historia, TLS, privilegios, espacio, constraints o cotejo de datos, cortar la conexión de clientes al destino y conservar evidencia privada. Reiniciar desde otra base o rama desechable autorizada. Evitar `repair`, `clean`, `--clean`, `--create` y desactivar triggers. El origen sigue separado de la restauración.

## 3. Identidad y sesiones

- `RF_PERSISTED_HMAC_KEY_BASE64` recibe por custodia privada el material histórico exacto que validaba las huellas de identidad e invitación. El fallback a `JWT_SECRET_BASE64` conserva el comportamiento existente cuando la variable está ausente. Para separar destinos, cotejar en privado el HMAC-SHA256 en hexadecimal del literal UTF-8 `ruta-fija/invitation-origin/v1:person-identity:key-fingerprint:v1` con `person_identity_hash_key.key_fingerprint` para `id=1`. Calcular el HMAC fuera de PostgreSQL y emitir solo la coincidencia. Si falta la fila o difiere la huella, detener el candidato y revisar las vinculaciones históricas con un operador autorizado antes de activar esas operaciones.
- `JWT_SECRET_BASE64` y `JWT_ISSUER` identifican las nuevas sesiones del destino y requieren una política aprobada. El OTP que usa la clave JWT requiere tratamiento de desafíos pendientes. Se preservan credenciales persistidas y documentos.
- Autorizar, solo en la copia y antes de abrir tráfico, una decisión sobre familias de refresh web/móvil, tokens de acceso de incorporación, desafíos OTP y recuperación copiados. Inventariarlos por estado. Si se aprueba la revocación, aplicar procedimientos revisados para esa copia y comprobar rechazo del material previo y reautenticación. Preservar auditoría, aceptaciones y documentos.

## 4. Arranque de inspección

Un operador autorizado puede ejecutar el JAR contra una copia aprobada con un lector restringido.

~~~powershell
$env:SPRING_PROFILES_ACTIVE = 'prod,restore-inspection'
java -jar <jar-candidato>
~~~

El perfil escucha solo en loopback por defecto. Exige Flyway y seed desactivados, SQL init `never`, JPA `validate`, Hikari en solo lectura y la inicialización de transacciones de sesión en solo lectura. Rechaza sobrescrituras inseguras antes de crear el contexto. Desactiva la programación de barridos, excluye el listener de avisos y permite únicamente GET de liveness/readiness. Confirmar ambos `UP` y retirar el proceso tras cotejar. El rol del lector y el destino se validan por separado.

## 5. Puertas del despliegue a cargo del titular

| Puerta | Configuración y criterio |
|---|---|
| Backend Render Docker | Directorio `backend`, rama autorizada y auto-deploy desactivado durante el ensayo. Usar `PORT` y, si se configura, alinear `SERVER_PORT`. `8080` es una elección compatible. En operación escuchar en `0.0.0.0`. Salud de Render en `/actuator/health/readiness`. `SPRING_PROFILES_ACTIVE=prod`, `SPRING_FLYWAY_ENABLED=false`, `APP_SEED_ENABLED=false`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `JWT_SECRET_BASE64`, `JWT_ISSUER`, `RF_PERSISTED_HMAC_KEY_BASE64`, `REFRESH_COOKIE_SECURE=true`, `APP_CORS_ALLOWED_ORIGINS` con el origen exacto del CRM. Comenzar con una instancia y medir memoria y arranque. |
| CRM Render Web Service | Directorio `frontend`. `PORT`, `RF_PROXY_UPSTREAM_HOST`, `RF_PROXY_UPSTREAM_SCHEME`, `RF_PROXY_UPSTREAM_PORT`, `RF_PROXY_PUBLIC_SCHEME=https`. Para upstream público exigir HTTPS y SNI. Para red privada verificar servicio receptor y región. Mantener `/api/v1`, `/ws/operations`, headers TLS del origen público, `Set-Cookie`, same-origin y denegación cruzada. Validar `101` real y confidencialidad de tickets en la capa Render. |
| Correo | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED`, `APP_OTP_EMAIL_ALLOWED_SENDER`, `APP_OTP_EMAIL_ALLOWED_RECIPIENTS`. Confirmar relay, remitente verificado y lista permitida. El adaptador actual exige coincidencia entre usuario y remitente. |
| APK | `RF_API_BASE_URL` con HTTPS y sufijo `/api/v1`. En una exportación desechable fijar `rutaFijaL08Isolated=true` en `mobile/android/gradle.properties` y compilar en debug. Verificar el identificador `pe.rutafija.conductor.l08` y la firma con herramientas Android antes de cualquier instalación. Probar login, refresh, aceptación por conductor, consentimiento, GPS en primer plano e idempotencia. La firma y publicación de release quedan fuera del ensayo. |

Para JDBC usar el endpoint directo durante la validación inicial, exigir TLS y validación de hostname y CA (`sslmode=verify-full`) y conservar la URL completa dentro de la configuración privada. Construir las imágenes desde una exportación limpia del commit candidato. Conservar la política de remitentes y destinatarios en entornos privados. El perfil de escritorio `application-local.properties` queda fuera del contexto Docker del backend.

Render documenta que sus Web Services gratuitos no reciben tráfico de red privada y bloquean la salida SMTP por 25, 465 y 587. El puerto 25 está bloqueado en todos los planes. La disponibilidad de otro puerto depende del plan y del relay, y se valida después de autorizarlos. [Límites de Render Free](https://render.com/docs/free#other-limitations).

Secuencia posterior. Diagnóstico de origen/destino → autorización de respaldo → restauración de ensayo y validación → autorización de la copia Neon → Render con prueba HTTPS/cookies/WebSocket → APK autorizada.

Referencias de plataforma. [Puerto y Web Services](https://render.com/docs/web-services), [WebSocket](https://render.com/docs/websocket), [dump y restore Neon](https://neon.com/docs/import/migrate-from-postgres) y [roles Neon](https://neon.com/docs/manage/roles).
