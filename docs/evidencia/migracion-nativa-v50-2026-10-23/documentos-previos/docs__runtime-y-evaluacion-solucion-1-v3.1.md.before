# Ruta Fija V3.1 — runtime y evaluación de la Solución 1

Fecha: 23/10/2026, America/Lima. Corte preparado para revisión del commit de entrega. Se contrasta el mensaje solicitado con el código, las actas y los resultados disponibles; no se activan servicios ni se migra ninguna base para redactarlo.

## Propósito y arquitectura

Ruta Fija organiza la gestión y el despacho de transporte urbano de ruta fija: personas, organizaciones, conductores, vehículos, rutas, ofertas, ejecución de servicios, incidencias, comunicados, reportes y auditoría. Busca sustituir registros y coordinación manuales por operaciones autenticadas, límites por organización e historial verificable. Su implementación permite evaluar esas capacidades localmente; no mide todavía una mejora real de informalidad, productividad o servicio a pasajeros.

La Solución 1 mantiene un **monolito modular de backend con dos clientes**. Tres aplicaciones y sus procesos no equivalen a tres microservicios:

| Componente | Versión y responsabilidades verificadas |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16, Spring Security/JWT, permisos por rol y organización, JPA/Hibernate, PostgreSQL16 y Flyway |
| CRM | Angular **22.2.3**, componentes standalone, guardas de sesión/términos, interceptores, tokens de diseño y comunicación de sesión entre pestañas mediante BroadcastChannel |
| Android conductor | Flutter **3.47.5**, Dart **3.13.4**, paquete `pe.rutafija.conductor`; sesión, perfil, disponibilidad, asignaciones, avisos y ubicación consentida con app visible |

Referencias: `backend/pom.xml`, `frontend/package.json` y su lockfile, `mobile/pubspec.yaml`, el SDK verificado y `mobile/android/app/build.gradle.kts`. Angular22.1.4 no corresponde a las dependencias actuales y no se fuerza una degradación para coincidir con el borrador del commit.

## Inicio y compuertas

1. `iniciar_ruta_fija.bat` resuelve rutas desde su carpeta, comprueba archivos privados y delega la gestión de PostgreSQL a una lease de propiedad. Un servicio ya activo no se adquiere como propio.
2. El launcher valida la configuración y las ACL, consulta Flyway mediante SELECT con transacción de solo lectura y timeout y rechaza esquemas incompatibles antes de lanzar Java/Angular. No ejecuta migraciones ni semillas en el arranque.
3. La política compartida `scripts/RutaFijaRuntimeSchemaPolicy.ps1` exige V1…V50 completas, cero fallos y la identidad exacta de la base. Para `solucion_ruta_fija_1` son **50 migraciones versionadas, sin repetibles de pruebas**. Para `ruta_fija_test` son **50 versionadas más `R__native_test_role_privileges.sql`**: 51 éxitos totales.
4. Esa repetible está en `backend/src/main/resources/db/test-migration/`; `application-test.yml` añade su ubicación. Su SQL rechaza toda base diferente de `ruta_fija_test`. No existe V0 ni V51 en este inventario. El commit incorpora las 50 fuentes de runtime y la repetible de pruebas en sus respectivas carpetas.
5. Con el esquema aprobado se inicia Spring Boot en `127.0.0.1:8080` y Angular en `127.0.0.1:4300`. El CRM reenvía `/api` y `/ws` al backend mediante `proxy.conf.json`. El proxy permite al navegador usar el mismo origen; la autenticación y las restricciones de origen en servidor siguen vigentes.
6. Se registran PID, ejecutable, inicio UTC, puerto y marcador de comando y se consulta la salud. Un puerto ajeno no se libera terminando procesos por nombre.

**Estado real:** desarrollo sigue en V12. El inicio normal queda bloqueado hasta una migración planificada con respaldo y ensayo; la corrección del conteo no migra esa base. El siguiente comando permite exclusivamente una comprobación de UI y salud sobre el esquema de pruebas V50:

```powershell
.\iniciar_ruta_fija.bat --smoke-read-only --test-database
.\finalizar_ruta_fija.bat
```

Ese modo restringe las conexiones JDBC/Hikari a lectura y desactiva los dos trabajos de mantenimiento solo en su proceso. No permite operar servicios ni sustituye un entorno normal. El smoke histórico comprobó salud, readiness y login renderizado sin escrituras; la nueva corrección de la política de esquema se verificó con **12 casos sintéticos y 42 comprobaciones de regresión**, sin volver a iniciar aplicaciones ni consultar PostgreSQL.

La demo comparte la misma política normal. Se comprueban puertos y esquema antes de abrir un túnel; una herramienta ausente no se descarga automáticamente. La preparación de este commit no publica ninguna demo.

## Flujo administrativo

El ADMIN se autentica con su propia cuenta. Las guardas web y la autorización del backend exigen la aceptación personal de los términos vigentes antes de operar. La aceptación autenticada registra documento, versión, SHA-256, organización y hora del servidor. **No es una firma criptográfica personal con Ed25519**, ni basta el hash para afirmar que hubo una firma digital legal.

El ADMIN consulta y modifica recursos de su organización según los permisos de servidor; SUPER_ADMIN tiene responsabilidades globales diferenciadas. Se gestionan conductores, vehículos y rutas versionadas. Una oferta `MOBILE_CONFIRMATION` requiere la respuesta del conductor y conserva los estados y plazos de su contrato.

Los estados de disponibilidad (`DISPONIBLE`, `RESERVADO`, `EN_SERVICIO`) y los estados de asignación (`PENDING_RESPONSE`, `SCHEDULED`, `EN_SERVICIO`, `COMPLETED`, entre otros) pertenecen a conceptos distintos. No se presenta una cadena que mezcle ambos como un único enum. Hay pruebas y evidencia de exclusión de solapamientos, respuestas y transiciones; no se garantiza ausencia universal de toda carrera en cualquier despliegue.

Los eventos de operación del CRM pueden recibirse mediante WebSocket. BroadcastChannel sincroniza el estado de sesión entre pestañas y no reemplaza la autorización del servidor.

## Flujo del conductor y telemetría

El conductor se autentica, conserva su sesión mediante almacenamiento seguro y conmuta su disponibilidad. `flutter_secure_storage`10.3.4 emplea Android Keystore y cifrado para proteger el refresh token persistido; el access token se mantiene en memoria. La configuración actual de la librería no debe describirse como EncryptedSharedPreferences ni como una garantía de respaldo físico de claves o autenticación biométrica. El cifrado de la sesión y el cifrado AES256/GCM de backups son mecanismos diferentes.

El conductor consulta su bandeja de avisos y ofertas. La bandeja se carga al abrir/refrescar; **no se acreditan push Android ni avisos externos entregados**. La aceptación de asignación utiliza `clientEventId`, versión y fecha del comando. `X-Idempotent-Replay` es una **cabecera de respuesta** del backend para indicar un resultado repetido; no es una cabecera que este cliente envíe para crear idempotencia en todas las operaciones.

Al iniciar el servicio y mantener la app visible, el controlador intenta capturar/enviar ubicación a intervalos de 30s, con consentimiento, permiso y proveedor habilitado. Usa capturas puntuales `getCurrentPosition`; el flujo actual **no activa un servicio Android persistente con notificación continua**. La dependencia declara un servicio que soportaría otro modo, pero esa declaración no demuestra que este flujo lo utilice. Al pasar a background se suspende la captura; al retornar se reevalúa su elegibilidad.

Tras más de120s sin fix confirmado se muestra señal degradada cuando se actualiza el ciclo; la cadencia nominal de30s no garantiza un plazo fijo si una captura o petición sigue pendiente. Se conserva el servicio y no se inventan coordenadas0,0 ni se encolan puntos para aparentar entregas posteriores. La app ofrece un flujo conectado; la presencia de código legado de outbox no acredita operación offline habilitada. Al concluir, retirar consentimiento o cerrar sesión se detiene la captura correspondiente.

La falta de fix está ensayada por simulación; la pérdida física de satélites continúa como condición de campo. Los 959s de FIS02 con batería30→28%, pantalla y GPS prueban ese ensayo concreto; no son una garantía de autonomía diaria ni un máximo de consumo para otros equipos.

## Cierre y verificación segura

`finalizar_ruta_fija.bat` verifica la identidad registrada antes de terminar Java y Node y comprueba la liberación de8080/4300. Si existe una demo, retira primero su túnel identificado. PostgreSQL se conserva si ya estaba activo, hay clientes ajenos, no existe lease propia o falta observabilidad suficiente. La parada global del servicio es una acción expresa separada.

`verificar_ruta_fija.bat` delega en la allowlist unitaria inspeccionada del backend: compila fuentes reales con salidas aisladas y evita las integraciones que escriben en la fixture compartida. `Clean` está rechazado antes de conectar. No usar un `mvn test` genérico como sustituto de este runner seguro sin revisar sus integraciones.

## Evaluación de objetivos

| Objetivo | Evidencia lograda | Límite y conclusión |
|---|---|---|
| Despacho y trazabilidad | Flujo CRM→oferta→respuesta→inicio→cierre, historial y pruebas transaccionales | Cubierto en los contratos y ensayos locales; el impacto operativo urbano no está medido |
| Telemetría y consumo | 56 pruebas históricas de ubicación, incluidas6 de no-fix simulado; FIS02POCO30→28%/959s | Cubierto localmente; no implica GPS permanente en background, no-fix físico ni autonomía diaria |
| Seguridad y auditoría | Roles, frontera por organización, aceptación de términos, ledger histórico de altas1/3/8 y2aceptaciones conservadas | El ledger de altas no se confunde con todos los eventos de auditoría ni con una certificación jurídica |
| Continuidad | Backup AES256/GCM y restauración SQL sintética íntegra en415.026s | RTO SQL local; quedan pendientes medio/clave independientes, RPO horario efectivo, alertas/guardia24/7 y recuperación de pérdida total |
| Accesibilidad | TalkBack humano, contraste corregido y diálogo/controles con escala150% en el ensayo | Evidencia sobre pantallas/casos probados; no certificación WCAG completa de toda la plataforma |
| Calidad del código | Auditoría731PASS:257Java/241Angular/233Flutter;7conectadasSKIP; javac0avisos y snapshotIDE0/0 | Resultados fechados del código auditado; no equivalen a ejecutar todos los flujos en producción |
| Alcance de entrega | L-00…L-08 verificados dentro de sus contratos locales; L-08contrato0.9.0 y GOlocal | No equivale al100% del objetivo social, de la rúbrica académica ni de la operación real |

La medición actual de participación Java es51.87% (25152/48492líneas de producto); el51.83% del actaL-08 corresponde a su corte anterior y se conserva. No se dispone de un umbral de rúbrica que permita certificar cumplimiento académico total.

**Dictamen:** Solución1 cubre un candidato funcional local del problema, con9/9lotes formalmente verificados en su alcance aceptado. La evidencia no permite afirmar que ya resolvió el100% del problema urbano ni que está libre de deuda o requisitos pendientes. Permanecen la migración nativaV12, la distribución/API real de Android y las compuertas operativas del piloto.

## Frontera de la entrega y piloto

Sigue vigente **GO PARA CANDIDATO LOCAL DE ENTREGA / NO_GO_PILOTO_REAL**. La release Android local está firmada con un certificado candidato y usa `https://api.rutafija.invalid/api/v1`, que no ofrece servicio real; no se instaló esa release en el POCO. El ensayo físico corresponde a la APKdebugv4, con otro certificado/API. La firma técnica del acta y la firma Android no sustituyen la ceremonia personal Ed25519 de recuperación del titular.

El [protocolo vinculante](legal/protocolo-transicion-piloto-real.md) conserva PR-01privacidad efectiva, PR-02ADMIN legítimo/aceptación personal, PR-03ceremoniaUSB/recuperación, PR-04HTTPS/dominio/firma/distribución, PR-05continuidad independiente y PR-06participantes/títulos/campo. Todos requieren comprobación real antes del encendido. Son requisitos previos con trabajo pendiente, no una certificación de preparación sin deuda.

## Evidencia y evolución de fuentes

[ActaL-08](actas/L-08-cierre-funcional-global.md), [informe de auditoría](../AUDITORIA_INTEGRAL_IDE_Y_EJECUTABLES_2026-10-23.md), [prueba consolidada731](evidencia/auditoria-ide-2026-10-23/verificacion-final.json), [QA móvil de afirmaciones](entrega/2026-10-23-commit-v3.1/qa-mobile-claims.json), [QA runtime/objetivos](entrega/2026-10-23-commit-v3.1/qa-runtime-objectives.json) y [doce casos nuevos de la política](entrega/2026-10-23-commit-v3.1/schema-policy-verified.json).

Los archivos de los cortes sellados no se regeneran. El manual y dos launchers vivos evolucionan después de la auditoría; sus bytes anteriores se respaldan en el directorio de preparación del commit y se registra su sustitución. Un manifiesto histórico que apunta a un archivo vivo identifica la versión anterior preservada, no implica que el archivo de trabajo actual deba conservar para siempre el mismo hash.

El repositorio incorpora fuentes y documentación saneada. Secretos, logs, dumps, APK/JAR/ZIP y respaldos privados permanecen fuera de Git; los manifiestos conservan sus referencias y huellas locales. Un clon de fuentes no contiene por sí solo todo el archivo físico de aceptación ni permite ejecutar un verificador que requiere binarios/backups excluidos.
