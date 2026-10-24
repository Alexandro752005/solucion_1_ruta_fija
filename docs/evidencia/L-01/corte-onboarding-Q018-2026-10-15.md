# Corte técnico L-01: incorporación CONDUCTOR y Q-018

**Fecha:** 2026-10-15, America/Lima  
**Estado del lote:** EN_CURSO  
**Alcance de este corte:** aceptación de invitación, solicitud PENDING_REVIEW y consulta restringida del expediente. No cierra L-01 ni inicia L-02.

## Decisiones aplicadas

- La identidad preliminar del postulante está separada de AppUser y Driver. La contraseña se establece durante el inicio de la solicitud y se guarda como hash; verificar el correo y aceptar una invitación no emite una sesión operativa.
- El código de invitación solo se consume al verificar por OTP la aceptación y crear la solicitud PENDING_REVIEW. Ambos cambios ocurren en una transacción. Repetir la misma verificación devuelve la solicitud previa.
- El listado de solicitudes omite nombre, correo y teléfono. El ADMIN autenticado y verificado solo puede consultar esos datos desde la ruta de expediente restringido y dentro de su organización; la consulta se audita.
- Tras aprobar documentos, ADMIN elegirá un grupo activo. La aplicación posterior crea AppUser y Driver vinculados. Si la aprobación ocurre sin grupo, el acceso operativo permanece deshabilitado hasta asignarlo.
- La persona usuaria confirmó como límites de creación del piloto 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR. Si ya se consumieron las cuatro creaciones ADMIN, se bloquea cualquier reemplazo adicional. Los límites aún no están aplicados al código.
- El borrador ADMIN v0.2.0 sigue sin aprobación. No se registran aceptaciones reales.

## Cambios realizados

- Migraciones Flyway V18 y V19: identidad preliminar, desafío OTP, solicitud, deduplicación idempotente, unicidad por invitación, una solicitud pendiente por identidad/organización y tablas de límites de intentos.
- API pública de POST para iniciar la incorporación, verificar OTP y reenviar OTP. El reenvío rota el token de verificación; el token anterior deja de servir.
- API ADMIN para listar solicitudes sin PII y abrir un expediente restringido. La consulta de expediente exige ADMIN verificado, filtra por organización y registra auditoría.
- Se mantuvieron límites independientes de invitación: correo de destinatario, una sola aceptación, vigencia máxima de 120 horas, cinco errores en 15 minutos con bloqueo de 30 minutos y freno de origen de 50 errores en 15 minutos con pausa de 15 minutos.
- El flujo nuevo de OTP conserva el código de seis dígitos, hash HMAC, vigencia de cinco minutos, máximo de tres envíos en ventana móvil de 60 minutos, bloqueo tras dos códigos incorrectos por 30 minutos y desafío de 30 minutos.
- Los correos de la suite se sustituyen con un doble. Este corte no conectó a Gmail ni envió correo real.

## Evidencia ejecutada

| Comprobación | Resultado |
|---|---|
| Backend main compile | BUILD SUCCESS |
| Backend test-compile | BUILD SUCCESS; 35 fuentes de prueba compiladas |
| Backend verify dirigido | BUILD SUCCESS; 46 unitarias y 4 integraciones dirigidas, sin fallos, errores ni omitidas |
| ConductorOnboardingFlowIT | 4/4: aceptación idempotente, no creación prematura de AppUser/Driver, correo/código incorrectos, bloqueo OTP, límite de envío, acceso ADMIN, redacción del listado y aislamiento por organización |
| PostgreSQL | Solo ruta_fija_test; Flyway aplicó V18 y V19 y dejó el esquema en V19 |
| SMTP | No hubo conexión ni envío real |

## Alcance y evidencia adicional — 2026-10-15

La revisión del contrato L-01 y de Q-003 confirma que ADMIN debe aprobar o rechazar la incorporación del conductor. Verificar el correo o aceptar una invitación no sustituye esa revisión. Esta capacidad pertenece a L-01 y no se propone moverla a otro lote; su ejecución depende del catálogo documental que continúa abierto. El criterio `AC-V3-OPEN-REQUIRED-DOCUMENTS-01/02` exige revisar los documentos obligatorios antes de habilitar operación y prohíbe tratar la elección del método de catálogo como aprobación de una lista aún inexistente.

La respuesta posterior del titular fija como método un contrato documental versionado, con secciones separadas para conductor y vehículo. No define todavía la modalidad del piloto, la lista obligatoria, sus campos/vigencias/excepciones ni si se guardarán archivos originales. Ninguno de esos datos se infirió. La etapa implementada para Q-018 termina en `PENDING_REVIEW`; no hay pertenencia activa, grupo, `AppUser`, `Driver` ni sesión operativa. La regla aprobada para una implementación posterior conserva que ADMIN elige un grupo activo tras la aprobación documental; si no lo asigna, no se habilita acceso operativo.

Decisiones documentales que faltan, agrupadas:

| Grupo | Decisión pendiente |
|---|---|
| Modalidad del piloto | Confirmar la modalidad real de transporte que usará la versión piloto del contrato. |
| Documentos del conductor | Aprobar la lista exacta de obligatorios y opcionales, campos de cada documento, reglas de vigencia/renovación y excepciones justificadas. |
| Documentos del vehículo | Aprobar por separado la lista exacta, campos, vigencias y excepciones aplicables al vehículo. |
| Custodia de evidencias | Decidir si solo se guardan datos estructurados o también archivos originales; si hay originales, definir ubicación, acceso autorizado, protección y retención/eliminación. |

La decisión metodológica de versionar el contrato y separar sus secciones sí está aprobada. Los cuatro grupos anteriores siguen abiertos; hasta resolverlos no se implementa la aprobación documental ni se habilita un conductor.

Se añadió a la app Flutter el formulario de invitación, la verificación OTP, el reenvío con rotación del token y la confirmación de solicitud pendiente. La clave de idempotencia se envía como encabezado y se mantiene durante los reintentos de la operación. La app limpia los datos personales y la contraseña preliminar al pasar a OTP; tras la aceptación no inicia sesión. La prueba de widget verifica que el estado permanezca `unauthenticated`. El listado y expediente solo existen en API; la interfaz CRM aún no está conectada.

| Comprobación adicional | Resultado |
|---|---|
| Backend regresión completa | `verify`: BUILD SUCCESS, 46 unitarias + 68 integraciones, sin fallos/errores/omitidas; solo `ruta_fija_test`, Flyway V19. Incluyó pruebas heredadas offline en perfil test. Esta ejecución antecedió la aserción de login agregada después. |
| Backend Q-018 final | `verify '-Dit.test=ConductorOnboardingFlowIT'`: BUILD SUCCESS, 46 unitarias + 4 integraciones, sin fallos/errores/omitidas. Verifica que login web y móvil rechacen la identidad preliminar tras aceptar la invitación. |
| App Flutter | `flutter analyze`: sin incidencias. `flutter test`: 54/54. Incluye aceptación de invitación hasta `PENDING_REVIEW`, no sesión, contrato HTTP e idempotency header. Todas las llamadas fueron simuladas. |
| SMTP saneado | Archivo privado presente; una definición de `SPRING_MAIL_USERNAME` y una de `SPRING_MAIL_PASSWORD`; el usuario continúa sin coincidir con el remitente de `application-local.properties`. Las variables no están en el proceso de diagnóstico; no hay servidor local en 8080 ni socket activo a 587. No hubo conexión a Gmail. |
| Snapshot | SHA-256 de `final3` ZIP y manifiesto coincide con las huellas anteriores; no se regeneró ni modificó. |

Comando de integración ejecutado desde backend: importar el entorno con scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Test y ejecutar .\mvnw.cmd --batch-mode --no-transfer-progress verify '-Dit.test=ConductorOnboardingFlowIT'. El importador reportó solo RUTA_FIJA_NATIVE_ENV=LOADED.

## Límites y pendientes concretos

1. Falta cerrar OPEN-REQUIRED-DOCUMENTS: modalidad del piloto; listas concretas y separadas de documentos del conductor y vehículo; campos, vigencias y excepciones justificadas; y si se almacenarán archivos originales y cómo se conservarán. Sin esto no se aprueba el expediente ni se habilita operación.
2. La aprobación/rechazo documental, devolución, subsanación, reenvío, historial de rondas Q-020 y alta posterior de AppUser/Driver aún no están implementados. Es criterio de L-01 y depende de la decisión anterior.
3. La app ya completa el flujo hasta `PENDING_REVIEW`. Falta conectar CRM al listado y a la vista restringida; esta última no puede presentar documentos hasta definirlos.
4. El ocultamiento global de PII sigue incompleto. Las APIs y pantallas heredadas de usuarios, conductores, auditoría, incidencias, reportes, invitaciones y perfiles aún requieren revisar sus superficies y exportaciones.
5. Los límites de creación piloto 1/4/20 y la regla de bloqueo de reemplazos ADMIN aún requieren persistencia, enforcement concurrente y pruebas.
6. La discrepancia del remitente SMTP y la entrega controlada autorizada siguen pendientes. Se mantiene detenida la prueba real.
7. Los términos v0.2.0 permanecen como borrador; las pruebas de términos siguen siendo sintéticas.
8. Custodia/ensayo del titular SUPER_ADMIN y comprobación con dispositivo físico permanecen pendientes; el dispositivo pertenece a L-08.

Se conserva final3 sin regenerar. L-01 permanece EN_CURSO; no se inicia L-02.
