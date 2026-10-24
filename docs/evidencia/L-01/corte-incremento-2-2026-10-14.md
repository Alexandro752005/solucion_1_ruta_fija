# L-01 — Corte técnico de continuación 2

**Fecha:** 2026-10-14, America/Lima  
**Estado del lote:** `EN_CURSO`. No se inició L-02 ni se cierra L-01.  
**Alcance de esta evidencia:** discrepancia SMTP, invitaciones, recuperación asistida CONDUCTOR, aceptación versionada sintética, cliente SUPER_ADMIN y accesibilidad offline heredada.

## SMTP: discrepancia reproducida sin exponer valores

- Fuente privada revisada: `backend/.local/ruta-fija-native.env`. Contiene una sola definición de cada variable SMTP requerida. No se imprimieron valores.
- El valor configurado en `SPRING_MAIL_USERNAME` continúa sin coincidir con el remitente autorizado por `app.otp.email.allowed-sender` en `backend/src/main/resources/application-local.properties`. No se cambió la lista permitida.
- `application-local.properties` contiene placeholders de entorno, no credenciales literales. `backend/ruta-fija-native.env.example` es solo un ejemplo. El importador rechaza claves duplicadas y archivos con variables no admitidas. No se encontraron otras definiciones activas SMTP en las ubicaciones `.env`/`.vscode` inspeccionadas.
- `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Runtime` carga el archivo privado al entorno del proceso; `scripts/Invoke-RutaFijaNativeRuntime.ps1` ejecuta ese importador antes de iniciar Java. Un inicio directo del IDE no lo ejecuta automáticamente. `-Scope Test` omite las variables SMTP y transforma el resto en perfil `test`.
- En la comprobación no había backend Ruta Fija activo ni listener en 8080, ni variables SMTP en los ámbitos Process, User o Machine del proceso diagnóstico. No es posible atribuir una fuente efectiva a un backend inexistente ni reconstruir qué fuente utilizó el intento SMTP anterior.
- No se abrió conexión Gmail, no se autenticó y no se envió correo.

Si se necesita una corrección manual, la única ubicación/variable afectada es `backend/.local/ruta-fija-native.env` → `SPRING_MAIL_USERNAME`; debe alinearse con el remitente que ya permite `application-local.properties`. Después se requiere una nueva comparación segura. La prueba de entrega sigue detenida hasta entonces.

## Incrementos implementados

### Invitaciones de CONDUCTOR

- Backend emite código de seis dígitos asociado al correo destinatario, conserva HMAC, muestra el código una sola vez, lista resúmenes sin código/hash y permite revocar dentro del tenant.
- CRM ADMIN incorpora emisión, historial paginado y revocación. La interfaz muestra el código solo tras emitirlo; no envía correo ni crea enlace/QR.
- La validación del código exige CONDUCTOR autenticado, activo, verificado y correo coincidente. La respuesta no revela organización.
- Sigue pendiente Q-018: no hay aceptación autenticada que consuma el código, ni solicitud `PENDING_REVIEW`, alta/enlace transaccional idempotente o decisión del inicio de sesión de un CONDUCTOR aún no asociado.

### Recuperación asistida CONDUCTOR (Q-015)

- ADMIN verificado opera solo sobre un CONDUCTOR activo, verificado y vinculado a driver dentro de su organización. Se exigen confirmaciones de revisión del expediente y evidencia independiente de uno de los métodos v3.1.
- Al requerir nueva contraseña, validar OTP no cambia todavía el correo, la contraseña ni las sesiones. El cambio de correo+contraseña, consumo del token y revocación se completan de forma transaccional al consumir el token de un uso. Expiración o cancelación conservan el estado previo.
- Motivo/referencia se guardan en la solicitud de recuperación y no se duplican en `audit_event`; no se guardan adjuntos ni DNI.
- Falta interfaz CRM/app que orqueste la entrega segura al conductor y recoja OTP/token. La vigencia de 30 minutos del token de contraseña es provisional; retención y eliminación de metadatos aún no están decididas.

### Términos versionados

- CRM y backend consultan versión/huella, presentan documento y registran solo una aceptación expresa asociada a ADMIN, organización, documento, versión, SHA-256 y hora de servidor; la operación es idempotente. Los documentos `synthetic=true` solo se exponen en el perfil `test`.
- Las pruebas insertan un documento `SYNTHETIC-TEST-ONLY` claramente marcado en `ruta_fija_test`. No se aprobó ni publicó v0.2.0 y no hubo aceptación real.
- El guard CRM condiciona la navegación, pero todavía no existe una exigencia backend transversal de aceptación para todas las operaciones ADMIN. La elegibilidad de organización según Q-008 sigue pendiente.

### Cliente de recuperación SUPER_ADMIN

- `SuperAdminRecoveryCliBackendIT` pasó contra Tomcat/Spring real en puerto aleatorio, con perfil `test`, `ruta_fija_test`, cuenta temporal, clave Ed25519 temporal bajo el directorio efímero fuera del repositorio y `OtpEmailSender` simulado.
- Completa inscripción y recuperación sobre HTTP, verifica el correo temporal, cambio de contraseña y nuevo inicio de sesión. No genera, recibe ni custodia una clave del titular. Este ensayo no sustituye la inscripción/custodia operativa del titular.

## Límites y aislamiento comprobados

| Mecanismo | Regla/frontera | Evidencia de esta revisión |
|---|---|---|
| OTP | 6 dígitos; 5 min; uso único; 3 envíos en ventana móvil de 60 min; 2 fallos; bloqueo de 30 min desde el segundo fallo | `EmailOtpChallengeTest`, `EmailOtpFlowIT` y regresión completa. La marca exactamente en el corte de 60 min deja de contar; el código vence en `expiresAt == now`; el bloqueo termina en `blockedUntil == now`. Los hashes/tokens no se guardan en claro. |
| Invitación | 6 dígitos; hasta 120 h; un uso por destinatario; 5 fallos/15 min por cuenta → 30 min; 50 fallos/15 min por origen → pausa 15 min | `InvitationFlowIT` acredita TTL de 120 h, expiración exacta, corte estricto de ventana, umbrales y rutas limitadas; `InvitationServiceBoundaryTest` acredita fin exacto de bloqueo/pausa. Invitación y OTP mantienen contadores independientes. |
| Aislamiento | ADMIN emisor/verificado del tenant; revocación ajena oculta como 404; validación solo por CONDUCTOR con correo destinatario; aceptación de términos vinculada al actor/tenant/documento | `InvitationFlowIT`, `AdminTermsFlowIT`, `ConductorRecoveryFlowIT` y `AuthFlowIT`. Estas pruebas cubren sus contratos L-01; no equivalen a una auditoría de todas las rutas del producto. |

## Offline heredado en ejecución

- El endpoint backend `/api/v1/mobile/sync/events` permanece en el código y es ejercitable **solo** bajo perfil `test` cuando `app.mobile.offline-sync.enabled=true`; la configuración base declara `false`. No se registra con el perfil local estándar. `MobileOfflineSyncIT` es una prueba de regresión que lo habilita expresamente y no prueba disponibilidad del producto.
- En Flutter, `PendingSyncPage` y el núcleo offline siguen presentes como código legado y en pruebas. El router/dependencies del producto no los registra; la ruta desconocida `/sync-pending` vuelve a la pantalla normal y no muestra acción offline. La prueba de widget dirigida pasó.
- El runtime vigente usa almacén en memoria por proceso para comandos inciertos del flujo online; no restaura ni reenvía una cola tras reinicio. La app no declara funcionalidad offline v3.1. No se inició el backend local contra la base de desarrollo.

## Comprobaciones ejecutadas

1. Backend: `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Test`; luego `Set-Location backend` y ` .\mvnw.cmd --batch-mode --no-transfer-progress verify` — `BUILD SUCCESS`, 46 unitarias + 62 de integración; cero fallos/errores/omitidas. Flyway migró únicamente `ruta_fija_test` de V16 a V17 (`assisted conductor recovery`). La suite integra `ConductorRecoveryFlowIT` 5/5, `AdminTermsFlowIT` 7/7, `InvitationFlowIT` 5/5 y `SuperAdminRecoveryCliBackendIT` 1/1.
2. CRM: `npm.cmd run typecheck`, `npm.cmd run test:ci` y `npm.cmd run build` — correctos; 22 archivos y 62 pruebas aprobadas. El bundle de producción incluye las páginas de invitación y términos.
3. Flutter: `flutter analyze` — sin incidencias; `flutter test` — 47 pruebas aprobadas en el incremento que deshabilita la persistencia/reenvío offline. Además, `flutter test test/app/ruta_fija_app_test.dart --plain-name "does not expose the legacy pending-sync screen or action"` — 1 aprobada en esta revisión.

La instantánea `backups/L-00-2026-10-13-final3/source-snapshot.zip` conserva SHA-256 `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`; `docs/evidencia/L-00/source-manifest.sha256` conserva SHA-256 `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`. No se regeneraron.

Durante las primeras corridas se detectaron dos aserciones de frontera defectuosas: una medía el bloqueo OTP desde la emisión y no desde el segundo fallo; otra comparaba nanosegundos no representables por la precisión de timestamps PostgreSQL. Se corrigieron las aserciones para medir la regla real y respetar la precisión persistida; la corrida completa posterior pasó.

## Pendientes concretos dentro de L-01

1. Corregir localmente `SPRING_MAIL_USERNAME` en la ubicación indicada y avisar para repetir la comparación; luego ejecutar una sola prueba controlada al destinatario ya autorizado. Ningún envío real se realizó en este corte.
2. Completar la ficha factual del piloto y recibir aprobación expresa del texto; v0.2.0 sigue borrador. Decidir si la aceptación debe bloquear también el acceso backend a operaciones ADMIN, además del guard CRM.
3. Aprobar el procedimiento de alta inicial y reemplazo ADMIN. Propuesta: SUPER_ADMIN crea cuenta/organización en `PENDING_ACTIVATION`; la persona verifica correo y establece su contraseña; para reemplazo se confirma por contacto empresarial preexistente o presencialmente y se revoca al titular saliente al activar al reemplazo, conservando como máximo un ADMIN activo salvo decisión expresa.
4. Resolver Q-018: modelo mínimo de solicitud, cuenta CONDUCTOR no asociada, aceptación autenticada y consumo atómico/idempotente.
5. Confirmar TTL del token de restablecimiento CONDUCTOR (30 min provisional) y retención/eliminación de la metadata de recuperación; integrar el flujo con CRM/app.
6. Definir custodia separada y ensayo del titular SUPER_ADMIN. El cliente solo se probó con material efímero y no toca la clave real.
7. Mantener validación con dispositivo físico en L-08.

No hay evidencia para declarar cerrados todos los criterios obligatorios de L-01. Mantener `EN_CURSO`; no iniciar L-02.
