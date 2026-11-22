# Estabilización de Ruta Fija V3.1 para Render y Neon

**Corte** · 22 de noviembre de 2026, America/Lima. Repositorio `Alexandro752005/solucion_1_ruta_fija`, rama local `deploy/render-neon-v3.1`. El punto de partida local y el remoto consultado coincidieron en `b2acf3ad3d8f54760c4865c193c5bf022b2de123`, con diferencia de commits 0/0 y sin cambios rastreados. `git status --short` agrupó 40 entradas sin rastrear (35 bajo `docs`, dos bajo `backend` y tres en `.codex`, `agents` y `artifacts`). El trabajo partió de la auditoría previa y del plan estratégico local.

Toda escritura SQL de ensayo usó PostgreSQL 16.15 recién inicializado en una exportación temporal, en loopback y puerto 55441. El arnés comprueba el directorio de datos, la base, el puerto y el usuario en el servidor antes de autorizar cada etapa. El dump y la restauración se hicieron con una cuenta sintética creada para el ensayo. La base original, Neon, Render, el remoto Git y los dispositivos quedaron fuera del ensayo.

## Riesgos y correcciones

| Hallazgo y severidad | Evidencia inicial y solución aplicada | Ensayo / autorización pendiente |
|---|---|---|
| Crítica · arranque de una copia | La configuración general habilita Flyway y salud JPA, y había barridos programados que no dependen de Flyway. El perfil `restore-inspection` impone JPA `validate`, Flyway, seed y SQL init desactivados, pool de solo lectura y cadena HTTP exclusiva. Excluye scheduling y el listener que inserta avisos. |`backend/src/main/resources/application.yml` L11–22, L38–53 y selección de rutas del inventario. JAR de ensayo con esquema V51 y lector restringido. La selección y validación de un rol real requiere autorización. |
| Crítica · HMAC persistido | Identidad e invitaciones compartían el material de JWT. Se conserva el fallback y se admite `RF_PERSISTED_HMAC_KEY_BASE64`. Un material incorrecto en identidad provoca conflicto y aviso sin huella ni material sensible. | `backend/src/main/java/pe/rutafija/shared/config/SecurityConfig.java` L146–168 y `backend/src/main/java/pe/rutafija/personidentity/application/PersonIdentityService.java` L174–194. Pruebas con claves sintéticas. Cotejo privado y política de sesiones antes de migrar. |
| Crítica · ownership y ACL | El archivo de restauración retiene ACL para revisión privada. El primer restore aplaza ACL. El replay controlado recupera revokes y grants revisados antes de conectar el backend. | `scripts/sql/RenderNeonPreflight.sql` L25–116, `scripts/qa/render_neon_postgres_rehearsal.py` L196–252. Catálogos del origen y Neon siguen pendientes. |
| Alta · proxy y puerto | El upstream de Compose era fijo. Nginx ahora recibe upstream, listener y esquema público por entorno. Verifica el certificado y SNI para HTTPS. El backend y su healthcheck aceptan `SERVER_PORT` y `PORT`. | `frontend/nginx/15-ruta-fija-proxy.envsh` L17–48, `frontend/nginx/default.conf.template` L2–72, `backend/Dockerfile` L23–28. Ensayo loopback. Falta el routing real del proveedor. |
| Alta · SMTP/OTP | Se conserva la política de remitente y destinatarios. Siete pruebas con mocks cubren entrega sintética y rechazos de credenciales, remitente, destinatario y proveedor. El perfil privado de escritorio se excluye del contexto Docker. | `backend/src/test/java/pe/rutafija/identity/infrastructure/SmtpOtpEmailSenderTest.java` L35–128 y `backend/.dockerignore` L9–23. Pendiente verificación del relay real y custodia de allowlists. |
| Media · Logback y compatibilidad | El commit inicial ajustó `backend/pom.xml`, `IncidentService`, `ReportExportService`, sus pruebas, plan y plantilla de asignaciones. Spring usa consola Logstash en `prod`. Los dos archivos locales Logback siguen como trabajo del usuario. | `backend/src/main/resources/application-prod.yml` L16–22. Diez pruebas del XML local pasaron en exportación aparte. Se mantiene fuera de los commits porque su adopción exige una suite backend completa. |
| Media · entorno Android | Se preservaron el ID normal, los permisos y los contratos Flutter. La APK probada usa identificador L08 y firma Android Debug. | `mobile/android/app/build.gradle.kts` L6–24, L38–46, L72–86. Verificación binaria en host. GPS, login y aceptación en un teléfono requieren ensayo autorizado. |

### Inventario de arranque y escrituras

| Entrada | Comportamiento observado en el código |
|---|---|
| `backend/src/main/java/pe/rutafija/identity/infrastructure/DevDataSeeder.java` L19–23 | Único `ApplicationRunner` encontrado. Perfil `dev`, con guard de seed. `restore-inspection` rechaza combinar `dev` y `test`. |
| `backend/src/main/java/pe/rutafija/operation/application/MobileOperationMaintenanceService.java` L39–45 | Barrido de expiraciones y ubicación programado. |
| `backend/src/main/java/pe/rutafija/qualification/application/QualificationMaintenanceService.java` L19–25 | Reevalúa vigencias de habilitación en el barrido. |
| `backend/src/main/java/pe/rutafija/identity/application/JwtService.java` L31–36 | Inicio valida TTL. |
| `backend/src/main/java/pe/rutafija/operation/application/OperationNoticeRecorder.java` L13–31 | Listener de avisos operativos con inserciones, desactivado en inspección. |
| `backend/src/main/java/pe/rutafija/operation/realtime/OperationStreamBroadcaster.java` L62–75 | Listener posterior al commit, publica al socket. |

## Archivos entregados

| Archivo desde la raíz | Líneas y propósito |
|---|---|
| `.gitattributes` | L18–19. LF para wrapper y entrada Nginx. |
| `backend/.dockerignore` | L9–23. Aísla datos locales, credenciales, archivos privados e ignora el perfil de escritorio. |
| `backend/Dockerfile` | L5–12, L23–28. Wrapper ejecutable incluso con checkout CRLF y liveness en el puerto efectivo. |
| `backend/src/main/java/pe/rutafija/RutaFijaApplication.java` | L18–22. Scheduling por perfil. |
| `backend/src/main/java/pe/rutafija/operation/application/OperationNoticeRecorder.java` | L13–19. Perfil del listener de avisos. |
| `backend/src/main/java/pe/rutafija/shared/config/RestoreInspectionEnvironment.java` | L22–60. Guard de arranque con rechazo sin divulgar valores. |
| `backend/src/main/java/pe/rutafija/shared/config/RestoreInspectionSecurity.java` | L12–24. Solo dos rutas de salud GET y denegación del resto. |
| `backend/src/main/resources/META-INF/spring.factories` | L1. Registro del guard de entorno. |
| `backend/src/main/resources/application-restore-inspection.yml` | L1–21. Valores seguros y loopback por defecto. |
| `backend/src/main/resources/application.yml` | L30–32. Resolución de puerto. |
| `backend/src/test/java/pe/rutafija/shared/config/RestoreInspectionEnvironmentTest.java` | L13–46. Carga de perfil, rechazos y regresión del perfil normal. |
| `backend/src/main/java/pe/rutafija/shared/config/SecurityConfig.java` | L49–52, L146–168. Selección de cadena y fábrica HMAC retrocompatible. |
| `backend/src/main/java/pe/rutafija/invitation/application/InvitationCodeHasher.java` | L10–18. Construcción explícita por configuración. |
| `backend/src/main/java/pe/rutafija/personidentity/application/PersonIdentityService.java` | L174–194. Diagnóstico saneado de discrepancia de clave. |
| `backend/src/test/java/pe/rutafija/shared/config/PersistedHmacConfigurationTest.java` | L37–75. Compatibilidad, fallback, cambio de JWT y error cerrado. |
| `backend/src/test/java/pe/rutafija/identity/infrastructure/SmtpOtpEmailSenderTest.java` | L35–128. Rechazos adicionales con datos simulados. |
| `frontend/.dockerignore` | L9–22. Exclusión de configuración y artefactos privados. |
| `frontend/Dockerfile` | L13–22. Nginx 1.30.7 fijado por digest, CA, plantillas y puerto configurable. |
| `frontend/nginx/15-ruta-fija-proxy.envsh` | L1–50. Validación y valores por defecto compatibles con Compose. |
| `frontend/nginx/default.conf.template` | L1–82. Sustituye `frontend/nginx/default.conf`, con TLS, HTTP, WebSocket y SPA. |
| `scripts/qa/render_neon_proxy_rehearsal.py` | L45–177. Ejercicio con upstream TLS y respuestas sintéticas. |
| `scripts/sql/RenderNeonPreflight.sql` | L1–140. Diagnóstico PostgreSQL en transacción de solo lectura. |
| `scripts/qa/render_neon_postgres_rehearsal.py` | L63–294. PostgreSQL desechable, migraciones, restore, ACL, inspección y selección de integración. |
| `docs/despliegue/MANUAL_RENDER_NEON_V3_1.md` | Diagnóstico, restauración y rollback de destino, variables y puertas. |
| `docs/despliegue/INFORME_ESTABILIZACION_RENDER_NEON_2026-11-22.md` | Inventario, resultados y límites de la preparación. |

## Pruebas ejecutadas y cómo repetirlas

Ejecutar en una exportación limpia, sin configuración privada ni `.git` para los arneses. Usar Java 21 y Maven 3.9.15, Node 24.16.0 con npm 11.13.0, Flutter 3.47.5 y PostgreSQL 16.15. La sesión de pruebas usó un settings Maven vacío y dependencias cacheadas. Las variables privadas quedaron fuera de los procesos de validación.

~~~powershell
# Backend. Desde el directorio backend.
$MavenCfg = (New-TemporaryFile).FullName
Set-Content -Encoding ascii -LiteralPath $MavenCfg -Value '<settings/>'
mvn -B -ntp -o -s $MavenCfg -DskipTests package
mvn -B -ntp -o -s $MavenCfg '-Dincludes=org.apache.commons:commons-lang3,com.google.guava:guava,ch.qos.logback:*,org.slf4j:*,org.flywaydb:*,org.postgresql:postgresql' dependency:tree
mvn -B -ntp -o -s $MavenCfg '-Dtest=ReportExportObservabilityTest,SmtpOtpEmailSenderTest,OperationStreamAuthorizationTest,OperationStreamBroadcasterTest,SecurityConfigTest,InvitationCodeHasherTest,CorrelationIdFilterTest,ApiOriginValidationFilterTest,PersistedHmacConfigurationTest,RestoreInspectionEnvironmentTest' '-DfailIfNoTests=true' package

# CRM. Desde el directorio frontend.
npm ci --prefer-offline
npm run typecheck
npm run test:ci
npm run build

# Flutter. Desde el directorio mobile. Fuerza el ID aislado en la exportación.
flutter pub get --offline --enforce-lockfile
flutter analyze --no-pub
flutter test --no-pub test/core test/features
flutter test --no-pub test/app
Add-Content -LiteralPath android/gradle.properties -Value "`nrutaFijaL08Isolated=true" -Encoding ascii
flutter build apk --debug --no-pub --target-platform android-arm64 --dart-define=RF_API_BASE_URL=https://fixture.rutafija.test/api/v1
aapt2 dump badging build/app/outputs/flutter-apk/app-debug.apk
apksigner verify --print-certs build/app/outputs/flutter-apk/app-debug.apk

# Desde la raíz de la exportación, tras empaquetar el backend.
# $PgBin, $Java21, $Maven, $Nginx, $Sh y $OpenSsl apuntan a las herramientas de ensayo.
python -X utf8 scripts/qa/render_neon_postgres_rehearsal.py --project . --pg-bin $PgBin --java $Java21 --maven $Maven
python scripts/qa/render_neon_proxy_rehearsal.py --project . --nginx $Nginx --shell $Sh --openssl $OpenSsl
~~~

| Ensayo | Resultado y límite |
|---|---|
| Java en el punto de partida | PASS. JAR y 34 JUnit dirigidas, incluyendo CSV, XLSX y PDF. Maven resolvió Commons Lang 3.18.0, Guava 33.8.0-jre, Logback Classic/Core 1.5.34, SLF4J 2.0.18, Flyway 11.7.2 y pgJDBC 42.7.12. |
| Java candidato | PASS. 53 JUnit, 0 fallos, 0 errores y 0 omitidas. JAR `ruta-fija-backend-0.1.0-SNAPSHOT.jar` de 94.560.570 bytes. Solo selección dirigida. |
| Logback de la copia de trabajo | PASS. 10 JUnit en exportación aparte con `mvn -Dtest=LogbackConfigurationTest test`. La variante local añade rotación en escritorio y el logging estructurado de `prod` ya funciona en el JAR base. Suite completa de la variante NO EJECUTADA. |
| Angular | PASS. Instalación estricta por lockfile, typecheck, 471 pruebas en 65 archivos y build de producción. La plantilla de asignaciones compiló tal como estaba en `b2acf3a`. |
| Flutter | PASS. Lockfile, analizador sin incidencias y 261 pruebas de Dart (235 de `core`/`features` y 26 de `app`). APK debug de ensayo de 98.316.020 bytes, ID `pe.rutafija.conductor.l08`, firma Android Debug, SHA-256 `84E3DF016FF764D4722B962AC3E4AEFDFFBF7A8A28A5162FB34BAEEF3FDAD317`. El primer build se descartó como candidato de ensayo porque su identificador era el normal. El segundo se comprobó con herramientas Android. |
| PostgreSQL sintético | PASS. V1–V51 aplicadas, historial con checksums idénticos, cuenta ADMIN activa y verificada con su organización, 106 tablas ordinarias en `public` contando `flyway_schema_history`, y replay de ACL ejecutado dos veces. Los conteos describen el clúster de ensayo. |
| Arranque de inspección | PASS. El backend normal verificó `PORT`. En inspección prevaleció `SERVER_PORT`. Liveness y readiness dieron UP. API, login, socket, info y logout devolvieron 403. Durante la ventana de barridos no se observaron sentencias mutantes del lector. El primer arranque del candidato detectó dos cadenas de seguridad generales. Se corrigió la selección por perfil y se repitió. |
| Integración Java | PASS. Failsafe ejecutó 34 casos, 0 fallos, 0 errores y 0 omitidas: 9 `AuthFlowIT`, 11 `MobileAuthFlowIT`, 4 `MobileContractReportIT` y 10 `PersonIdentityFlowIT`. Los primeros intentos se detuvieron por la clave sintética requerida y por visibilidad de sesiones en el catálogo. Se resolvieron generando una clave para el clúster y concediendo `pg_read_all_stats` solo a su migrador. Quedan fuera la suite completa y `DriverIdentityReviewCorrectionIT`. El ensayo de restore sí verificó los privilegios efectivos de la firma V51. |
| Nginx | PASS. Nativo 1.30.7 con CA de ensayo en loopback. Defaults Compose, configuración inválida rechazada, `nginx -t`, SPA, JSON con no-store, HTTPS/SNI al upstream, headers, cookie y handshake 101. También rechazó origen cruzado, segundo uso del ticket y tenant sintético ajeno. El ticket sintético no apareció en logs Nginx. El registro público confirmó el digest del tag Alpine. Nginx 1.30.7 incorpora las correcciones de seguridad publicadas para la rama estable. [Avisos de seguridad de Nginx](https://nginx.org/en/security_advisories.html). |
| Docker / Compose y Render | NO EJECUTADO. Revisión de Dockerfiles y contextos exportados. Este host carece de Docker CLI. Faltan la imagen Linux, Compose, TLS público, autenticación del relay y pruebas de navegador/dispositivo contra Render. |

Con Docker disponible, sobre la misma exportación ejecutar `docker build -t ruta-fija-backend-qa ./backend`, `docker build -t ruta-fija-crm-qa ./frontend` y `docker compose config --quiet` con variables sintéticas. El arranque de contenedores requiere una red de ensayo y la BD desechable verificada. En Render quedan como hipótesis el dimensionamiento, los límites de conexiones, el tiempo de arranque y la latencia. Empezar con una instancia del backend tras una autorización, ya que los tickets WS viven en memoria.

El escaneo estático dirigido al diff buscó claves PEM, tokens GitHub, identificadores AWS, JWT, credenciales en URLs PostgreSQL y asignaciones de secretos. Detectó cero candidatos nuevos. La exportación limpia presentó un archivo con nombre de configuración privada en backend, excluido por `backend/.dockerignore` L23, y cero en frontend. `git diff --check` y el atributo `eol=lf` de los dos puntos de entrada pasaron. Se comprobaron 45 referencias de línea y la coincidencia de los archivos de ensayo. La prueba del contexto aquí es estática. Construir en CI desde un commit y sin archivos locales adicionales completa esa comprobación.

## Matriz de regresión y seguridad

| Invariante | Evidencia y límite |
|---|---|
| Tenant y comandos móviles | El proxy rechazó tenant ajeno en el upstream sintético. `backend/src/test/java/pe/rutafija/operation/api/MobileContractReportIT.java` L175–385 pasó reporte sin datos de otro tenant, autoridad de comandos móviles, transición e idempotencia sobre PostgreSQL desechable. Los archivos del contrato móvil y REST conservan las mismas definiciones. |
| Flyway e integridad | Los archivos de migración conservan sus bytes, incluidas las reglas históricas. Historial V1–V51 comparado en la restauración local. La comparación real de origen/destino y los checksums del artefacto quedan como puerta obligatoria. |
| JWT/HMAC | Pruebas con suites dirigidas para compatibilidad histórica, independencia de JWT y clave equivocada. Custodia y comparación privada pendientes. |
| Refresh y cookies | `backend/src/test/java/pe/rutafija/identity/api/AuthFlowIT.java` L92–228 y `backend/src/test/java/pe/rutafija/identity/api/MobileAuthFlowIT.java` L237–288 comprobaron refresh, revocación por reuso, logout, separación de canales y rechazo de origen ajeno. CRM cubre refresh, interceptor y coordinación entre pestañas. Nginx conservó atributos de una cookie sintética. Pendiente validación con TLS y navegador sobre Render. |
| WebSocket | Pruebas JUnit de autorización y broadcaster. Ensayo del proxy para 101, vida del ticket y logs. Falta handshake y confidencialidad en la capa Render y verificación multiinstancia; configuración recomendada de una instancia inicial. |
| Roles SQL y auditoría append-only | Ensayo de grants/revokes efectivos para auditoría, historial, corrección V51 y autoridades de retención. Usar catálogos y control de cuentas para aprobar la matriz real. |
| Cron, seed y salud | Bypass explícito por perfil, protección de entorno y arranque de JAR comprobados con lector desechable. |
| CSV, XLSX y PDF | Seis pruebas dirigidas verifican contenido, formato, sanitización y observabilidad del exportador. |
| CRM y Flutter | Build Angular, pruebas CRM/Dart y APK de ensayo. Física de GPS, consentimientos y rendimiento se verifican en dispositivo y red autorizados. |

## Dictamen y decisiones

| Puerta | Dictamen | Condición pendiente |
|---|---|---|
| `CODE_READY` | PARCIAL | Builds, 53 pruebas Java dirigidas y 34 integraciones aceptadas. Faltan el build y arranque de imágenes Docker Linux y una ejecución completa de backend compatible con las protecciones de datos. |
| `DB_RESTORE_READY` | PARCIAL | Contrastar en privado los catálogos de origen y destino, versión, TLS, `btree_gist`, capacidad, permisos, material HMAC y casos de cuentas/términos/vínculos. Solo el titular autoriza respaldo y restore. |
| `RENDER_DEPLOY_READY` | PARCIAL | Faltan perfiles y secretos reales aprobados, imagen Linux, DNS/TLS, prueba WebSocket/HTTP/cookies, relay SMTP, métricas de arranque y prueba de APK en dispositivo. |

La secuencia propuesta empieza por una sola etapa, diagnóstico autorizado de solo lectura. La custodia del dump requiere aprobación aparte. El restore y la revisión de permisos se hacen en una copia con tráfico cerrado. La configuración de Render, las pruebas HTTP/cookies/WS y la prueba de APK requieren sus propias aprobaciones. El manual versionado contiene comandos, variables, permisos y aborto operativo.

Para revertir los cambios después de revisarlos, usar `git revert <sha>` desde el commit más reciente hacia el más antiguo. Si se decide retirar un ajuste aislado, comprobar antes sus dependencias. Los archivos locales preexistentes quedan fuera de los commits.

Commits locales de código y pruebas, sin push.

- `94187f17a4941da52568771bc1703ed182edbe31` · Rechazos SMTP.
- `26eed57d3cbc20ddc4661769dc348a4641a77319` · Inspección del backend, puerto, HMAC persistido.
- `ab8eac663cfa10c5482ea9617395b5ed0d679047` · Preparación Docker y proxy CRM.
- `d8248f768b821d64c91c66641573b284d93de048` · Preflight y ensayo PostgreSQL.

La entrega técnica queda **PARCIAL**. Las correcciones tienen pruebas reproducibles locales y los pasos de infraestructuras reales quedan sujetos a aprobación del titular.

Referencias de plataforma. [Render Web Services](https://render.com/docs/web-services), [Render WebSocket](https://render.com/docs/websocket), [restauración en Neon](https://neon.com/docs/import/migrate-from-postgres), [roles Neon](https://neon.com/docs/manage/roles), [entrypoint Nginx](https://github.com/nginx/docker-nginx/blob/master/entrypoint/20-envsubst-on-templates.sh).
