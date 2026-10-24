> **Estado vigente al 2026-10-17: L-01 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../../actas/L-01-cierre-funcional-local-2026-10-17.md) y [matriz final](matriz-final-funcional-local-2026-10-17.md). Los cortes anteriores siguientes se conservan como antecedentes. Piloto bloqueado; L-02 sin iniciar y sin autorizar.

# Evidencia técnica L-01 — OTP por correo

**Corte:** 2026-10-14, America/Lima  
**Estado:** EN_CURSO. Esta evidencia demuestra pruebas automáticas con un transporte simulado; no demuestra una entrega SMTP real.

## Implementado y comprobado

- `spring-boot-starter-mail` está declarado. El perfil local configura `smtp.gmail.com:587`, autenticación SMTP, STARTTLS habilitado y obligatorio y timeouts de conexión/lectura/escritura de 5000 ms.
- `OtpEmailSender` separa el dominio del adaptador `SmtpOtpEmailSender`. El perfil local restringe el remitente a `rutafija.dev@gmail.com` y el destinatario a `sandrito10xyz@gmail.com`. La capa rechaza configuraciones de remitente/destinatario no permitidas antes de invocar el transporte.
- `V13__email_verification_otp.sql` agrega `email_verified_at` y `email_otp_challenge`. Flyway validó 14 migraciones y aplicó V13 únicamente en la base aislada `ruta_fija_test` durante la integración.
- Los flujos web y móvil devuelven un desafío sin sesión si el correo aún no está verificado. La sesión se crea después de consumir el código. El login web limpia una cookie refresh previa al devolver el desafío.
- Las pruebas de integración usan `@MockitoBean OtpEmailSender`; las pruebas del adaptador usan dobles de `JavaMailSender`. No se conectan a Gmail.
- Se verificó hash HMAC del OTP, hash del token de verificación, uso único, vigencia, bloqueo tras dos fallos, límite de tres envíos por ventana móvil de sesenta minutos y rechazo de direcciones/remitentes fuera de la política del piloto.

## Comprobaciones ejecutadas

| Superficie | Comando | Resultado observado |
|---|---|---|
| Backend unitario | `backend: .\mvnw.cmd test` | BUILD SUCCESS; 29 pruebas, cero fallos/errores/omitidas |
| Backend e integración completa | `backend: .\mvnw.cmd verify` | BUILD SUCCESS; 29 pruebas unitarias y 36 de integración, cero fallos/errores/omitidas. Flyway validó 14 migraciones y la base `ruta_fija_test` quedó en V13. |
| CRM | `frontend: npm.cmd run typecheck` | Exit 0 |
| CRM | `frontend: npm.cmd run test:ci` | 17 archivos de prueba; 43 pruebas, todas pasaron |
| Flutter | `mobile: flutter test --no-pub` | 43 pruebas, todas pasaron; incluye transición a OTP y sesión aún ausente antes de verificar |
| Flutter | `mobile: flutter analyze --no-pub` | Sin incidencias |

La regresión completa incluyó `MobileOfflineSyncIT`, código heredado del alcance previo. Sus pruebas no cambian la regla v3.1: funcionamiento y colas offline siguen excluidos. No se ejecutó un envío externo. La suite de integración cargó internamente `backend/.local/ruta-fija-native.env` para obtener las credenciales del PostgreSQL de pruebas aislado; no mostró ni usó sus variables SMTP y el archivo no se modificó. La elegibilidad de la cuenta para contraseña de aplicación, autenticación real STARTTLS, recepción y cuota S/0 siguen pendientes. Las pruebas SMTP/OTP reales autorizadas se restringirán al destinatario indicado; no se probará con otras direcciones. Se comprobó que el SHA-256 de `backups/L-00-2026-10-13-final3/source-snapshot.zip` y de `docs/evidencia/L-00/source-manifest.sha256` siguen coincidiendo con la línea base L-00; `final3` permanece intacto.

## Carga local de credenciales para la prueba controlada

Editar localmente `backend/.local/ruta-fija-native.env` y añadir o completar:

```dotenv
SPRING_MAIL_USERNAME=rutafija.dev@gmail.com
SPRING_MAIL_PASSWORD=PEGAR_LOCALMENTE_LA_CONTRASENA_DE_APLICACION
```

El archivo se mantiene privado. No enviar la contraseña por chat ni colocarla en un argumento de comando o registro. Google requiere la verificación en dos pasos para crear contraseñas de aplicación y puede ocultar la opción según la seguridad de la cuenta: [ayuda de Google sobre contraseñas de aplicación](https://support.google.com/accounts/answer/185833?hl=es).

## Criterios de lote todavía abiertos

La entrega controlada y su confirmación de recepción no se han ejecutado. Además, el lote completo incluye provisión y gestión de organización/usuarios por rol, aceptación de términos, invitaciones con límites independientes, recuperación del conductor y el procedimiento de recuperación de `SUPER_ADMIN` con comprobación independiente. Ninguno se considera satisfecho por esta evidencia. La validación física de la app continúa en L-08. El snapshot baseline `final3` no se regeneró.

## Actualización — prueba SMTP controlada del 2026-10-14

La configuración privada ya contiene las variables SMTP y el remitente coincide con el aprobado. Se realizó un intento controlado con el adaptador SmtpOtpEmailSender; falló con el error genérico del adaptador. Una conexión diagnóstica adicional, que exigía STARTTLS pero no envió mensaje, recibió AuthenticationFailedException de Gmail. No se confirmó una entrega y no se repitió el envío. El auxiliar informó solo tipos de resultado; no expuso credenciales ni OTP. El indicador no sensible de formato esperado de la contraseña fue falso. Detalle: prueba-smtp-controlada-2026-10-14.md.

El estado L-01 continúa EN_CURSO: falta corregir localmente la contraseña de aplicación y confirmar una entrega/recepción autorizada. También siguen sin verificación los criterios de provisión de ADMIN por SUPER_ADMIN, términos, invitaciones, recuperación del conductor y recuperación de SUPER_ADMIN. La revisión estática y sus límites están descritos en la evidencia enlazada; no se afirma que esas capacidades estén implementadas.

## Actualización de decisiones — 2026-10-14

El usuario autorizó preparar el borrador de términos ADMIN `docs/legal/terminos-admin-v0.1.0-borrador.md` para revisión. Sigue sin aprobación ni vigencia, por lo que no existe una versión aceptable en el producto y el criterio queda pendiente. El borrador identifica la información aún no proporcionada y conserva el requisito de aceptación expresa, no premarcada y ligada a cuenta, organización, documento, versión, huella de contenido y hora del servidor.

El usuario definió la comprobación independiente de recuperación SUPER_ADMIN: código de emergencia más firma de reto con clave pública del titular inscrita previamente, más verificación del nuevo correo. La clave privada queda fuera del repositorio, servidor y `ruta-fija-native.env`, separada del código de emergencia. Un `ADMIN` no autoriza la recuperación y el flujo no cambia la clave pública. En el corte original de esta sección, la implementación estaba pendiente; la actualización posterior registra las pruebas y el estado vigente. La decisión no altera las pruebas históricas ni el resultado SMTP fallido descrito arriba.

## Actualización posterior — recuperación SUPER_ADMIN y regresión — 2026-10-14

El usuario confirmó que se cambia la contraseña durante la recuperación y que al finalizar se rota y muestra una sola vez un nuevo código de emergencia, manteniendo la clave pública inscrita.

Se implementó el flujo backend con `V14__super_admin_recovery.sql`, rutas de inscripción, reto, prueba, reenvío OTP y finalización. Incluye reto de cinco minutos, firma Ed25519 del contenido UTF-8 canónico, código de emergencia almacenado con BCrypt, máximo de tres pruebas inválidas por reto, OTP del nuevo correo, cambio de contraseña, consumo del código y reto, revocación de refresh/access tokens y auditoría sin secretos. La clave privada no se recibe ni almacena en la aplicación.

| Superficie | Comando/consulta | Resultado observado |
|---|---|---|
| Preflight de PostgreSQL | `scripts/Invoke-RutaFijaNativeTest.ps1 -Action Preflight` | PASS; `ruta_fija_test`, PostgreSQL 16, Docker no requerido. |
| Recuperación SUPER_ADMIN | `backend: .\mvnw.cmd --batch-mode --no-transfer-progress verify` | `SuperAdminRecoveryFlowIT`: 4/4 pruebas pasan dentro de la suite, incluido código de emergencia inválido con firma válida. |
| Regresión backend completa | `backend: .\mvnw.cmd --batch-mode --no-transfer-progress verify` | BUILD SUCCESS; 29 unitarias + 40 de integración, cero fallos/errores/omitidas. Flyway validó el esquema V14 en PostgreSQL 16.15. |
| Historial en base de pruebas | Consulta de solo lectura a `ruta_fija_test` | Versiones aplicadas `1,2,3,4,5,6,7,8,9,10,11,12,13,14`. La base de desarrollo no fue modificada. |

La primera prueba de recuperación expuso IDs diferentes entre el reto en la respuesta y el reto guardado. Se corrigió y la suite dirigida pasó. La suite completa encontró fixtures heredadas con usuarios sin correo verificado, que ya no satisfacían el contrato de autenticación; se actualizaron esas fixtures y la regresión completa quedó verde. Las pruebas siguen usando un mock de `OtpEmailSender`; no hubo envío SMTP externo. CRM y Flutter no se volvieron a ejecutar porque este cambio fue backend.

El código y las pruebas acreditan el backend, no el recorrido operativo completo. Sigue pendiente construir la interfaz/herramienta cliente para inscribir la clave y firmar el reto localmente, decidir el medio concreto de custodia separada de clave y código, y validar la entrega SMTP del OTP. También siguen abiertos los demás criterios de L-01 enumerados en el contrato y la ejecución. El borrador de términos ADMIN continúa sin aprobación ni vigencia. El resultado SMTP fallido de la sección anterior no cambió ni se repitió.

## Revalidación de L-01 — 2026-10-14

Se corrigió el límite de autorización en gestión de usuarios: los endpoints ADMIN ya no permiten listar, crear, consultar, activar, desactivar o editar cuentas ADMIN; las listas y altas de ADMIN dentro de una organización quedan limitadas a CONDUCTOR. Además, la edición general ya no cambia el correo de un CONDUCTOR: responde `409 RESOURCE_CONFLICT` y exige el flujo de recuperación asistida que implementa Q-015, todavía pendiente. Se añadió cobertura de integración para el rechazo de gestión de ADMIN y para impedir el cambio de correo por esa ruta. La prueba de token obsoleto se ajustó para aislar la validación de rol persistido, sin atribuir a ADMIN una facultad que v3.1 reserva a SUPER_ADMIN.

| Superficie | Comprobación ejecutada | Resultado observado |
|---|---|---|
| Backend completo | `backend: .\mvnw.cmd --batch-mode --no-transfer-progress verify` | `BUILD SUCCESS`; 36 pruebas unitarias y 42 de integración; cero fallos, errores u omitidas. `FleetManagementIT`: 4/4; `SuperAdminRecoveryFlowIT`: 4/4; `SuperAdminRecoveryCliTest`: 7/7. |
| PostgreSQL de pruebas | Logs de Flyway durante `verify` | `ruta_fija_test`, PostgreSQL 16.15, esquema V14 vigente; no se aplicó una migración nueva y no se usó la base de desarrollo. |
| CRM | `npm.cmd run typecheck`, `npm.cmd run test:ci`, `npm.cmd run build` | Typecheck y build con salida 0; Vitest 17 archivos, 43/43 pruebas. |
| Flutter | `flutter analyze`, `flutter test` | Sin incidencias de análisis; 43/43 pruebas. No se conectó ni validó un dispositivo físico (L-08). |
| Snapshot final3 | SHA-256 de `backups/L-00-2026-10-13-final3/source-snapshot.zip` | Coincide con la huella de L-00: `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`. |

La suite backend heredada incluye `MobileOfflineSyncIT`; su ejecución no acepta colas ni modo offline, que continúan fuera del alcance vigente. Los resultados de clientes verifican la regresión del checkout, no términos, invitaciones o recuperaciones que todavía no tienen interfaces/servicios. El cliente local de recuperación SUPER_ADMIN tiene siete pruebas con claves y servidor HTTP de prueba, pero no se generó ni usó clave del titular y no se ejercitó la API real.

El preflight SMTP del mismo corte encontró remitente local distinto del permitido. La contraseña está presente y su formato local coincide con el patrón comprobado, pero no se autenticó. No se intentó conexión ni envío. Ver [el registro saneado del preflight](prueba-smtp-controlada-2026-10-14.md).

## Continuación incremental de L-01 — 2026-10-14

La evidencia y resultados vigentes se consolidan en [corte técnico de continuación 2](corte-incremento-2-2026-10-14.md). En esta revisión la discrepancia SMTP sigue reproducible en memoria: `SPRING_MAIL_USERNAME` de `backend/.local/ruta-fija-native.env` no coincide con `app.otp.email.allowed-sender` de `backend/src/main/resources/application-local.properties`. El diagnóstico anterior de que el remitente coincidía queda reemplazado por esta comprobación posterior; no se alteró la allowlist ni se hizo conexión/envío. Tampoco había proceso backend activo del cual leer una fuente efectiva.

Se completaron y ejecutaron backend de invitaciones, recuperación asistida CONDUCTOR (Q-015) y la aceptación versionada de términos con documento sintético; se añadió pantalla CRM ADMIN para emitir/listar/revocar invitaciones. `verify` quedó `BUILD SUCCESS` en `ruta_fija_test` V17 (46 unitarias y 62 integraciones). CRM typecheck y 62 pruebas; Flutter mantiene 47 pruebas/análisis sin incidencias según la validación del incremento offline, y se repitió la prueba de widget de ruta heredada (1/1). El cliente SUPER_ADMIN pasó 1 IT con servidor HTTP real de perfil `test`, usuario/clave Ed25519 temporales y correo simulado; no se usó material del titular.

L-01 permanece `EN_CURSO`: SMTP real detenido por discrepancia; no hay flujo positivo de alta/reemplazo ADMIN; invitación no consume Q-018; falta gate backend transversal para términos y aprobación real; recuperación CONDUCTOR aún carece de cliente, TTL de token de restablecimiento (30 min) y retención están pendientes; custodia y ensayo del titular SUPER_ADMIN siguen fuera de lo ejecutado. Offline persiste como código/test heredado, pero el endpoint backend solo se registra con perfil `test` y propiedad habilitada; app Flutter no registra la ruta/página. No iniciar L-02.

## Gate transversal de términos y reconciliación factual — 2026-10-15

Se añadió `AdminTermsGateAuthorizationManager` para requerir que ADMIN haya aceptado expresamente el documento vigente para su misma organización. El gate valida el actor persistido, documento actual, versión, organización, marcador sintético y SHA-256 calculado; deniega si no existe documento vigente o hay discrepancia. Se dejan accesibles la consulta/aceptación de términos y `GET /api/v1/auth/me` para que ADMIN pueda llegar al flujo de aceptación. La configuración normal lo activa por defecto y `application-test.yml` lo desactiva, salvo que una prueba dedicada lo active expresamente.

`AdminTermsGateFlowIT` ejercita con un documento sintético: bloqueo antes de aceptar, consulta/aceptación, acceso posterior, aislamiento entre organizaciones e invalidación al publicar versión posterior. No se crea aceptación en producción ni se usa el borrador v0.2.0 como documento vigente.

| Superficie | Comprobación | Resultado |
|---|---|---|
| Compilación backend | `backend: .\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests compile` | BUILD SUCCESS. |
| Compilación de tests | `backend: .\mvnw.cmd --batch-mode --no-transfer-progress -DskipTests test-compile` | BUILD SUCCESS; 34 fuentes de test compiladas. |
| Regresión backend | `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Test` y `backend: .\mvnw.cmd --batch-mode --no-transfer-progress verify` | BUILD SUCCESS; 46 unitarias + 64 integraciones, cero fallos/errores/omitidas. Solo `ruta_fija_test`, Flyway V17 sin migración nueva. Incluye `AdminTermsGateFlowIT` 2/2. |
| SMTP | Preflight local en memoria, con salida booleana | Una definición de cada variable; usuario discrepante; remitente/destinatario permitidos intactos; ningún proceso/listener de backend 8080. No hubo conexión ni envío. |
| Snapshot | `final3` | No regenerado ni modificado en este corte. |

La primera regresión no pasó: la autorización inicial cambió el 401 anónimo a 403 y el fixture sintético no satisfacía el marcador exigido por V15. Ambos defectos se corrigieron; la segunda regresión completa pasó. La revisión estática confirma que `MobileOfflineSyncController` solo se registra con perfil `test` y `app.mobile.offline-sync.enabled=true`; `application.yml` lo deja apagado, y Flutter no registra ruta/acción de sincronización. Las pruebas `MobileOfflineSyncIT` siguen siendo regresión heredada, no capacidad aceptada.

La reconciliación de ficha y respuestas posteriores está en [reconciliación factual L-01](reconciliacion-ficha-factual-2026-10-15.md). L-01 continúa EN_CURSO; no se inició L-02.

## Q-018 hasta PENDING_REVIEW y recheck SMTP — 2026-10-15

Se añadió el flujo Flutter de incorporación hasta PENDING_REVIEW, con identidad separada, OTP/invitación y clave de idempotencia. La app limpia los datos personales y la contraseña preliminar al llegar a OTP; luego muestra código de solicitud y no crea sesión. Backend prueba la aceptación atómica/idempotente, la inexistencia de AppUser/Driver y rechaza login web/móvil para la identidad preliminar. `flutter analyze` pasó sin incidencias y `flutter test` pasó 54/54.

`mvnw verify` completo pasó 46 unitarias + 68 integraciones (incluida prueba offline heredada solo en perfil `test`); Flyway trabajó exclusivamente sobre `ruta_fija_test`, V19. Tras agregar las aserciones de login, la ejecución dirigida final pasó 46 unitarias + 4 integraciones. No se conectó correo real.

Q-003 confirma que aprobación/rechazo documental del conductor pertenece a L-01. OPEN-REQUIRED-DOCUMENTS es dependencia: solo está elegido el método de un contrato versionado separado para conductor y vehículo; modalidad, documentos concretos, campos, vigencias, excepciones y conservación de originales siguen pendientes. No se mueve el criterio a otro lote ni se habilita una pertenencia activa. La regla futura aprobada asigna a ADMIN la elección de grupo activo tras aprobar; sin grupo no se permite acceso operativo.

SMTP se revalidó sin mostrar secretos: una definición de cada credencial en `backend/.local/ruta-fija-native.env`; `SPRING_MAIL_USERNAME` sigue discrepante del remitente permitido. No había Runtime local en 8080, ni variables mail en el proceso de diagnóstico. No se modificó la allowlist ni hubo conexión/envío. Ver [recheck SMTP](diagnostico-smtp-configuracion-recheck-2026-10-15.md). `final3` sigue intacto y con huellas verificadas.
## Incremento verificado: incorporación Q-018 — 2026-10-15

Se implementó el primer tramo de incorporación con identidad preliminar separada: alta privada con contraseña hasheada, OTP, aceptación atómica del código de invitación y creación de solicitud PENDING_REVIEW. La verificación no crea AppUser, Driver ni sesión operativa. La repetición del mismo token devuelve la solicitud existente.

El listado de solicitudes ADMIN no incluye PII. Nombre, correo y teléfono solo salen por el endpoint de expediente restringido: exige ADMIN verificado, limita la consulta a la organización y audita el acceso. La interfaz CRM aún no está conectada a este contrato.

Flyway V18 y V19 quedaron aplicadas solo en ruta_fija_test; estado V19. Comprobaciones: backend compile y test-compile exitosas; verify dirigido a ConductorOnboardingFlowIT: 46 unitarias + 4 integraciones dirigidas, cero fallos/errores/omitidas. Los dobles de correo impidieron cualquier envío externo. Evidencia detallada: [corte Q-018](corte-onboarding-Q018-2026-10-15.md).

Pendientes: cerrar el catálogo documental OPEN-REQUIRED-DOCUMENTS; implementar revisión, devolución/rechazo y aprobación con grupo activo; crear AppUser/Driver después de aprobar; integrar CRM/app; completar ocultamiento transversal de PII y exports; aplicar límites piloto 1/4/20 y bloqueo de reemplazo ADMIN; resolver discrepancia SMTP y ensayo autorizado. Los términos v0.2.0 siguen sin aprobación. final3 permanece vigente; L-01 EN_CURSO y L-02 sin iniciar.

## Corte vigente de integración — Chiclayo, 2026-10-15

La [evidencia del incremento](incremento-Chiclayo-2026-10-15.md) y el [contrato aprobado](../../contratos/L-01-documental-Chiclayo-v0.1.0.md) prevalecen para el estado actual. El diseño documental y OP-ADMIN-01/02 ya están aprobados; no se trasladó la aprobación personal a L-02. Revisiones personales, observaciones, ampliación, rechazo/aprobación y provisión están implementados con historial, tenant, versión e idempotencia; no habilitan conducción por sí solos.

SMTP: entrega y entrada confirmadas tras corregir la configuración de remitente permitido, sin modificar credenciales ni destinatarios. No se acredita consumo HTTP real por esa prueba del adaptador. Términos actualizados en borrador 0.3.0; ninguna aceptación real.

Los nuevos flujos y las rutas heredadas se someten a regresión centralizada; consultar el resultado final y sus límites en el corte, sin sumar totales de ejecuciones distintas como si fueran casos nuevos. L-01 EN_CURSO; final3 preservado; sin Git, despliegue, L-02 ni migración fuera de pruebas.

## Estado posterior V26 — evidencia actualizada

[Corte ejecutado](incremento-Chiclayo-2026-10-15.md): ID-01/RET-02 aprobados; identidad común/gate y regularización restringida implementados, pruebas dedicadas con gate activo. Backend completo 56 unitarias/155 IT, suplementario documental posterior 56/21; CRM 121/typecheck/build; Flutter 74/analyze reutilizados. Solo ruta_fija_test V26. Corrección del fixture documental fallido conservada en historial; observaciones/motivos no repiten nombre revisado de otro requisito. No afirmar privacidad universal de textos libres.

Candidato ADMIN 1.0.0 y ficha en revisión expresa solicitada por titular, sin aprobación/publicación/aceptación real. Validador OTP preparado y probado contra Tomcat/correo doble; no ejecutado con SMTP real. Permanecen consumo real persistido, custodia/inscripción/ensayo titular y clientes conectados pendientes según [matriz](matriz-cierre-L01-revision-2026-10-14.md). Privacidad separada antes de datos reales. L-01 EN_CURSO, final3 intacto, L-02 no iniciada.

## Corte actual — revisión y clientes conectados

[Detalle y resultados actuales](incremento-Chiclayo-2026-10-15.md): T-03…06 confirmadas, candidato corregido completo/ficha con nueva huella20ffd952…, sin aprobar/publicar/aceptar realmente. Aviso separado de privacidad preparado incompleto/no aprobado.

Lanzador completo PASS: build CRM y57 unitarias/6 IT. Navegador Angular y pantallas Flutter con HTTP/PostgreSQL reales de test cubren los recorridos L-01 de la matriz; solo correo/almacén de dispositivo dobles. Analyze actual limpio. CRM121/Flutter74 y completas56/155 y56/21 son antecedentes reutilizados, no sumados. CLI check-key offline y recuperación con copia temporal verifican revocación/rotación/pública; tres IT nuevas cubren contexto/caducidad/concurrencia.

Titular solo confirmó PC Windows; custodia/copia/inscripción reales pendientes. Helper OTP compilado/guardas estáticas pasan; entrada local solicitada al titular, ningún correo nuevo por agente. [Matriz única](matriz-cierre-L01-revision-2026-10-14.md): CI01 integral, CI03 y CI07 pendientes. L-01 EN_CURSO; sin migración nueva/fuera de test, despliegue, cambio final3 o L-02.

## Continuación vigente — CI-L01-01, 2026-10-16

El titular sí ejecutó el caso manual 3e9c0bf5-237b-4370-a87f-cea684e0b1d8: emisión/recepción/persistencia reales, fallo local por Enter vacío, sin consumoHTTP. Limpieza/ledger/envío conservados comprobados mediante inspección de solo lectura. Dos relanzamientos se detuvieron en preflight. Continuación selectiva preparada, sin envío externo del agente, sin reset ni limpieza global: [evidencia y comandos](otp-recuperacion-selectiva-2026-10-16.md). Consola ficticia real PASS sin DB/SMTP;12 unitarias/4IT rollback-only PASS con mail doble. No sustituye consumo real pendiente. CI01,CI03 yCI07 mantienen L01 EN_CURSO; L02 no iniciada. Los cortes anteriores son históricos salvo referencia explícita posterior.

## Corte vigente — 2026-10-17

[Correspondencia real y publicación aprobada](cierre-parcial-CI01-y-terminos-2026-10-17.md): **CI01 VERIFICADO** tras reporte del titular y SQL solo lectura del runfa616262…/caso3e9c0bf5…, con consumo dentro de5min, solicitud pendiente, sin cuenta operativa y limpieza/ledger correctos. Evidencia archivada fuera de target e intento fallido conservado. Se levanta suspensión de limpiezas solo test por ese caso; no se ejecutaron aquí ni hubo nuevo SMTP.

Términos1.0.0 aprobados expresamente y publicados localmente/mismo hash20FFD952…, registrados solo en ruta_fija_test con syntheticfalse; cero aceptaciones. Corregida conservación de espacios/saltos del contenido y comprobada en20 unitarias/1IT rollback-only. CI03 sigue pendiente de aceptación real por ADMIN legítimo. CI07 sigue pendiente: titular soloPCWindows, medios no disponibles; kit público/plantilla preparados sin claves. L-01 EN_CURSO/L-02 no iniciada; final3 intacto y sin migraciones nuevas/fuera de test.

## Ensayos humanos sintéticos: preparación posterior del17

Autorizados por el titular, sin equivalencia a personas/custodia legítimas. [Guía operativa](../../operacion/L-01-ensayos-humanos-sinteticos.md) y [evidencia de preparación](preparacion-ensayos-humanos-CI03-CI07-2026-10-17.md). Lanzador test/CRM/CLI con correo simulado y ambos gates activos; 10unitarias y arranque real sin fixture/migración/limpieza PASS. Inspección solo lectura confirma testV26/rf_test, ledgerSA0/ADMIN1/CONDUCTOR0, sin ADMIN sintético pendiente elegible y términos aprobados/aceptaciones0. Todavía sin caso humano ni clave/aceptación temporales creados. Suspensión automática de limpiezas al iniciar -Serve, con marcador persistente ante interrupción y archivo antes de liberación.

Los dos ensayos quedan PENDIENTES de intervención personal. Contrato original aún exige aceptación ADMIN legítima y custodia independiente; propuesta SC-03/SC-07 de cierre funcional local preparada pero no aprobada ni aplicada. CI01 no se repite; L01 EN_CURSO/L02 no iniciada.

## Seguimiento actual — aprobación SC condicionada y bootstrap interrumpido

SC-03/SC-07 ya aprobados por el titular bajo condición de acreditar ambos ensayos; **no aplicados**, sin pedir otra aprobación equivalente. [Revisión de12CI](revision-integral-previa-SC-2026-10-17.md) conserva los demás requisitos y las evidencias válidas. Los dos PASS humanos siguen pendientes.

Caso140c10a1 interrumpido antes de preparar cuenta/contraseña/cupoSA. [Corrección mínima y continuación](bootstrap-recuperacion-140c10a1-2026-10-17.md): mismo marcador intacto, proceso detenido y protección persistente de limpiezas; ledger0/1/0. Primer motivo exacto no registrado, segundo por nuevoUUID sin continuación.19 unitarias PASS y JDBCsololectura; continuación personal existente -Serve -ResumeRunId ahora implementada con guardas, sin crear otro mecanismo ni tocar ejecución normal. L01 EN_CURSO, piloto bloqueado, L02 sin iniciar; final3 intacto y ningún SMTP nuevo.

## Estado vigente — ADMIN activo y gate previo faltante

[Evidencia específica del caso140c10a1](gate-omitido-140c10a1-2026-10-17.md), lectura08:46:39Z test/rf_testV26: ADMIN92ebc07c activo/COMPLETED, aceptaciónc496b63c1.0.0/hashaprobado persistida08:39:10.296Z, secuencia contraseña/correo/aceptación/activación y auditoría correctas, login observado. El titular confirmó `gate` después de aceptar: rechazo de precondición por aceptación existente, no ejecución del chequeo previo409/401. **CI03 PARCIAL/PENDING**, no PASS por equivalencia; CI07 pendiente sin credencial. SC-03/SC-07 condicionales sin aplicar.

Ledger1SA/2ADMIN/0CONDUCTOR, cupos0/2/20; lock/marcador activos y TEST_CLEANUP_RELEASED=false. Mantener caso, no borrar/reabrir aceptación ni reenviar correo. Diagnóstico archivado saneado; ninguna modificación del código de aplicación, test destructivo o nueva suite. Términos/final3 verificados intactos; CI01 permanece VERIFICADO, L01 EN_CURSO y L02 sin iniciar.

Actualización posterior: [observación humana1792227077200 contrastada](contraste-observacion-1792227077200-2026-10-17.md), SHA9319F045…B8A719 y lateral exactos. **admin-ok ya declarado/archivado**, aceptación de la cuenta sintética y todos sus vínculos/auditoría/orden conformes. CI03 sigue PENDING solo por gate previo ausente; CI07 PENDING/sin retos o credencial. No nuevo ensayo o limpieza. Propuesta REC-CI03-01 adicional pendiente, SCsinaplicar y protección vigente. Spec0.8.8; L01 EN_CURSO/L02 sin iniciar.

**Corte vigente0.8.9:** REC-CI03-01 **no autorizado por ahora** por el titular. [Historial existente revisado](revision-registros-gate-140c10a1-2026-10-17.md):21auditoría/6activación, dos LOGIN_FAILED previos porINVALID_CREDENTIALS, no rechazo específico por términos ni trazaHTTP pertinente archivada. Aceptación y login posterior válidos; gateCI03 pendiente sin reconstrucción o cambioSC03. CI07 puede continuar, preparaciónlocalpersonal todavía sin resultado reportado; sin credencial inscrita09:00:13Z. Protección/ledger1/2/0 intactos, sin HTTP/SMTP/escriturasBD/pruebas/limpieza en la revisión. L01EN_CURSO/L02sininiciar.

**Corte0.8.10, CI07:** [diagnóstico de inscripción401](ci07-enrollment-401-140c10a1-2026-10-17.md). Doscheck-key reportados, igualdadhuellas pendiente de declaración explícita. No keyenrollmentpersistido; SAactivo/verificado, dosloginsconsolafallidos, posterior loginCRM exitoso09:27:03.664Z confirmado. Revisar entrada consola sinreset/regeneración; no afirmarcausa exacta401 descartado. Protección/ledger/aceptación intactos y sin accionesHTTP/SMTP delagente. CI07PENDING/CI03gatependiente/SCsinaplicar/L01EN_CURSO/L02sininiciar.

Actualización: lecturaocultaficticiaPASS reportadaporelhumano, modoexistentesinred; mensajesdecomandosinválidos aclaradossinarchivarsusvalores. Separarcomando/prompt/password permitió **LOGIN_SUCCESS CLI09:37:09.373Z**, auditadomismocaso. Solicitud de inscripcióncontroladacon tokennuevo pendiente de resultado; cero credenciales a09:38:40Z, no recuperación aún. CI07PENDING/marcadorconservado.

## Estado vigente al corte2026-10-17T09:44Z — inscripción temporal verificada

[CI07 inscripción verificada](ci07-inscripcion-verificada-140c10a1-2026-10-17.md): una credencial pública y una auditoría para el SUPER_ADMIN sintético del RUN_ID140c10a1, huella persistida igual a la observadaHTTP. El operador confirmó coincidencia con principal/copia; recuperación manual aún PENDING. Consulta compilada de solo lectura/rollback, sin escrituras de base, HTTP o SMTP, sin leer claves privadas o hashes de códigos. No suites ni cambios de producción. Cliente91913FF7... y términos20FFD952... sin cambios.

Siguiente paso personal: recover con copia existente por guía apartados5.3…6, sin reenvío de OTP simulado; login nuevo y copia-ok/recuperacion-ok/verificar para archivar. No asumir su éxito. CI03 conserva aceptación sintética pero gate previo pendiente; no otra cuenta autorizada. Test/rf_test/V26, ledger1/2/0, cupos0/2/20, lock/marcador activos; TEST_CLEANUP_RELEASED=false. CI01 VERIFICADO sin repetir envío, SC condicionado sin aplicar, L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Custodia independiente y ADMIN legítimo originales no acreditados.

## CI07 — Reto vencido antes de la prueba, código aún no identificado

Corte2026-10-17T09:56:36.782756800Z: informe `docs/evidencia/L-01/ci07-reto-sin-codigo-140c10a1-2026-10-17.md`, JSON SHA06F0EAA79C85B6102086E4AB9EA52BFAFC434E11E1D6FF76137548A9EF292A4D. Caso140c10a1, test/rf_test/V26; reto b4429e57 creado09:50:04.922Z y vencido09:55:04.922Z, failed_proof_attempts0/proof_verified_atnull/consumed_atnull, sin recuperación completada. La inscripción temporal y la huella pública anteriores permanecen válidas; sessionVersion0, ledger1/2/0, cupos0/2/20 y aceptación ADMIN conservados. Diagnóstico SELECT/rollback sin escrituras, HTTP o SMTP; ninguna rotación/reinscripción o reinicio por agente.

El operador se detuvo en la confirmación de firma al no identificar el código de emergencia; pérdida definitiva NO confirmada. Se indicó N y Enter para cancelar localmente antes de firmar/enviar proof, y localizar solo en el historial visible de B la línea de43 caracteres mostrada tras el encabezado de código en enroll. La salida del cliente y la disponibilidad del código siguen pendientes de respuesta. No se ha ejecutado otra recuperación. Si está disponible se retomará el mismo flujo con reto nuevo, sin borrar el vencido ni alterar límites; si no, CI07 sigue pendiente porque no hay consulta/reemisión del código en el protocolo actual. No reconstruirlo, extraerlo de memoria o sustituir factores.

CI03 conserva gate pendiente y otro ADMIN no autorizado. MANUAL_RECOVERY_RESULT=PENDING, SC condicionado sin aplicar, TEST_CLEANUP_RELEASED=false; A/marcador/lock mantenidos. CI01 no se repite; términos/final3 preservados, piloto bloqueado, L01 EN_CURSO/L02 sin iniciar. Evidencia previa de inscripción y diagnóstico401 conservados; no PASS manual inferido.

## CI07 — Código no visible: entrega pendiente de acreditar

Aclaración del operador: el código no aparece en ninguna línea visible; no atribuir pérdida después de una entrega confirmada. Informe `docs/evidencia/L-01/ci07-codigo-no-visible-140c10a1-2026-10-17.md`: fuente y bytecode del JAR91913FF7... contienen la impresión; 1 prueba existente de enrollment con terminal simulada/HTTP falso PASS. Esto no demuestra la consola real. No hubo cambio de cliente/protocolo o credencial. Se solicitaron únicamente SHA del `$trialJar` y `java -version` de B, sin otra inscripción/recuperación; resultados pendientes.

SELECT posterior10:05:31.649Z test/rf_test/V26 conserva una inscripción, reto vencido sin intentos fallidos ni recuperación completada, sessionVersion0, ledger1/2/0 y cupos0/2/20, aceptación y lock/marcador intactos. JSON SHA CB98F33EB195BB43EBB2C07C7662A5E8367F3A717C13C4FC217D04764F1862DD. CI07 manual PENDING por entrega del factor no acreditada y recuperación incompleta; CI03 gate pendiente/otro ADMIN no autorizado. SC sin aplicar, TEST_CLEANUP_RELEASED=false, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. No reset, rotación, correo real, nuevas cuentas ni cambios de términos/final3.

## CI07 — JAR/Java confirmados; consola técnica visible, servidor detenido

Corte2026-10-17T15:34:27.598580200Z: `docs/evidencia/L-01/ci07-consola-java21-140c10a1-2026-10-17.md`. Operador confirmó JAR91913FF7... y Java21.0.10+8-LTS-217. El adaptador del mismo JAR muestra printf/println/43 letras ficticias en ConPTY local con ese build; no acredita la salida de B anterior ni entrega original. La comprobación de B sigue pendiente; no causa exacta probada ni cambio del cliente.

SELECT test/rf_test/V26 conserva credencial/pública/aceptación, ledger1/2/0/cupos0/2/20, un reto vencido con cero fallos/completados y sessionVersion0. Servidor PID25932 ausente, sin listeners4311/8091 ni lock de sesión; marcador exacto sigue presente y protege las limpiezas. TEST_CLEANUP_RELEASED=false. No se reinició el servidor ni se leyó la clave privada del runtime. El parámetro existente permite reanudar un caso preparado, pero no reconstruye observaciones volátiles del proceso anterior.

Preparada REC-CI07-02 v0.1.0, propuesta exclusiva de reparación del fixture: `docs/operacion/L-01-REC-CI07-02-reparacion-sintetica-propuesta.md`, SHA B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2. Requiere aprobación expresa y salida ficticia positiva en B, autenticación/reautenticación humanas, guardas exactas y repetir oculto el código sustituto antes de guardar su hash; auditoría y máximo una reparación satisfactoria. NO aprobada/implementada/ejecutada al corte; ningún código rotado. Después exigiría recuperación normal completa y nuevas observaciones de sesión del segmento reanudado. No acredita entrega original ni produce PASS.

CI07 manual PENDING, CI03 gate pendiente/otro ADMIN no autorizado. SC03/07 condicionales sin aplicar; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. CI01 no se repite, final3/términos preservados; sin Git/GitHub, despliegue o migración de desarrollo/piloto.

## REC-CI07-02 — Autorizada e implementada; reparación humana pendiente

El operador confirmó marcadores/valor ficticio visibles en B y autorizó la propuesta0.1.0 SHA B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2. Registro `docs/operacion/L-01-REC-CI07-02-autorizacion-2026-10-17.json`, SHA EF4E1B5B5C03C448F77B4F2F9B744BC5E710DF75A36902D2FCE91B97EBE9CFC4. Implementación compilada solo en fuentes test: comando reparar-codigo en el mismo arnés; dos autenticaciones normales, sesión vigente, guardas exactas, sustituto mostrado una vez y repetición oculta antes de codificar/escribir, transacción y auditoría de una única reparación. Código/protocolo normal sin cambios.

Evidencia `docs/evidencia/L-01/REC-CI07-02-implementacion-2026-10-17.md`:35 unitarias finales PASS (21 arnés/14 reparación); dobles de store/transacción/concurrencia, sin equivalencia con ensayo humano. Preflight real ruta_fija_test/rf_test/V26 a15:54:38.700Z PASS: bloqueos de filas/advisory de cupos existentes y EXPLAIN sin ANALYZE; ningún UPDATE/INSERT ejecutado ni código del caso generado. Se respetó SELECT-only del ledger, sin grants; los intentos intermedios se conservaron. JSON final SHA8893D08A7F7714375BDFF0860D6F77923D344F798F350F100D7014AAA05E4CF5. Ledger1/2/0, cupos0/2/20, aceptación/credencial y términos intactos. final3 ZIP38065106... y manifiestoE78D7002... verificados sin regenerar.

Guía vigente `docs/operacion/L-01-REC-CI07-02-ejecucion.md`. Intervención solicitada: A interactiva desde root, Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId140c10a1...; conservar B, reportar mismoRUN/login cargado y esperar comprobación de lock/marcador antes de reparar-codigo. No pedir contraseña nueva de arranque, recrear cuenta o reinscribir. Se distinguen nuevo segmento y sesiones observadas del proceso anterior; no se reconstruyen tokens/secretos históricos. Reparación real aún0, recuperación manual PENDING; marcador protege limpiezas con servidor detenido al corte, TEST_CLEANUP_RELEASED=false. CI03 gate pendiente/otroADMIN no autorizado, SC03/07 condicionales sin aplicar, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. CI01 no se repite/SMTP externo0/Git0/despliegue0/migraciones dev-piloto0.


## REC-CI07-02 — Reanudación personal y protección comprobadas

Corte **2026-10-17T16:04:33.653016700Z**: [evidencia de reanudación](REC-CI07-02-reanudacion-2026-10-17.md). El titular confirmó el mismo RUN_ID140c10a1 y el login cargado. Solo lectura en ruta_fija_test/rf_test/V26 confirma marcador exacto, advisory lock activo y rechazo de limpiezas por la guarda; listeners127.0.0.1:4311/8091 del PID18632. Nuevo segmento **82bffcf1-6fab-41fd-a57b-3cbbbf18b123**; las observaciones anteriores perdidas al terminar el proceso no se reconstruyen. Historial1/2/0, cupos0/2/20, una inscripción, clave pública y aceptación ADMIN preservados; no hay reparación previa, reto vigente ni recuperación completada. Inspección archivada SHA B4F61E07DA643891DA13BF5A14270F9394441458D0765F54B6E421DEEE453F5B. El inspector separado usa PID0: su guarda de lock propio=false es esperada y no contradice cleanupLockHeld=true; el comando interactivo vuelve a exigir su propio lock.

Siguiente intervención: en A, `Ensayo L01>`, ejecutar una vez `reparar-codigo`; dos entradas ocultas de la contraseña SA, leer y conservar el sustituto local y repetirlo oculto antes de guardar. Comunicar solo tres resultados públicos; detener ante fallo y esperar contraste antes de recover. Reparación aún no ejecutada; ADMIN PENDING por gate previo y recuperación PENDING. TEST_CLEANUP_RELEASED=false; SC03/07 sin aplicar, L01 EN_CURSO, piloto bloqueado y L02 sin iniciar. No se ejecutaron nuevas pruebas, correo externo, cuentas, cambios de términos/final3 ni escrituras de base por el agente. Este corte prevalece sobre el estado histórico de servidor detenido.


## REC-CI07-02 — Reparación personal persistida, recuperación pendiente

Corte **2026-10-17T16:11:48.111015900Z**: [contraste archivado](REC-CI07-02-reparacion-verificada-2026-10-17.md), SHA del JSON **33BF7FEF8AF9C7F1099205B7A9654A17F45A442DACC9AF5D7788CE4915F81FDE**. Operador comunicó PASS_PRECONDITION_ONLY, contador1 y segmento **82bffcf1-6fab-41fd-a57b-3cbbbf18b123**; SELECT test/rf_test/V26 confirma auditoría única **d791653a-6379-4f02-ac1d-1db020dde403**, fecha servidor **2026-10-17T16:10:02.365128Z**, propuesta aprobada y confirmación oculta antes de escribir. La reparación fue personal mediante el arnes, no una rotación inferida por el agente. Una inscripción, misma cuenta/instalación/pública, aceptación ADMIN y términos publicados conservados; ledger1/2/0 y cupos0/2/20 intactos. Reto viejo vencido sin fallos/consumo; sessionVersion0, cero recuperaciones completas, marcador y lock activos. TEST_CLEANUP_RELEASED=false.

Continuación personal ya indicada: en B `java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy`; mismo correo inicial y nuevo sintéticos, revisar reto5min, sustituto confirmado oculto, Enter sin reenvío, `correo` en A para OTP simulado, contraseña nueva/confirmación y conservar código final una vez. Login recuperado en ventana privada y /platform-admins; solo tras realizarlo, `copia-ok`, `recuperacion-ok`, `verificar`. Comunicar solo MANUAL_RECOVERY_RESULT/EVIDENCE_FILE/EVIDENCE_SHA256, detener ante fallo sin repetir o reenviar; no `salir`. Guía REC-CI07-02-ejecucion v1.0.1, pasos1/2 ya realizados.

Reparación sola no acredita CI07 ni entrega original. Recuperación manual PENDING, CI03 gate previo PENDING sin otro ADMIN autorizado. SC03/07 condicionales sin aplicar; L01 EN_CURSO, piloto bloqueado, L02 sin iniciar. Copia misma PC no acredita custodia independiente. CI01 VERIFICADO sin repetir, sin SMTP externo, Git/GitHub, limpieza global, migraciones de desarrollo/piloto o cambios de final3. Solo consultas y compilación del lector; no nuevas suites ni secretos en evidencia.


## CI07 — Apertura local de la copia pendiente, sin reto nuevo

Corte **2026-10-17T16:34:14.586167200Z**: [diagnóstico de solo lectura](ci07-clave-local-140c10a1-2026-10-17T163414Z.json), SHA **B4784672DA91521564C96CB11921AC5FD404DFE20A97AEB6BA87BC9F92B292F3**. El operador informó que introdujo la contraseña de acceso SUPER_ADMIN donde se solicitaba la del archivo cifrado. El cliente vigente descifra la clave antes de crear el reto HTTP; no se capturó la excepción criptográfica exacta. SELECT ruta_fija_test/rf_test/V26 confirma solo el reto anterior vencido, cero fallos de prueba, sin prueba validada/consumo/completado; sustituto sin consumir. Reparación única, pública e instalación iguales, aceptación preservada, ledger1/2/0 y cupos0/2/20 intactos, lock y marcador activos.

Siguiente paso exclusivamente local: si recuerda con certeza la contraseña elegida en keygen y usada en check-key/enroll, ejecutar una vez en B `java -jar $trialJar check-key $trialCopy`; introducirla oculta y comunicar solo LOCAL_ENCRYPTED_KEY_CHECK/PUBLIC_KEY_SHA256 o error saneado. Sin API/correo/código de emergencia. Si no la recuerda, comunicarlo sin compartir secretos ni probar combinaciones; conservar archivos y caso, no regenerar/inscribir ni repetir reparación. Esperar contraste antes de recover. Recuperación manual PENDING, CI03 gate PENDING, TEST_CLEANUP_RELEASED=false, SC sin aplicar, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar; CI01 no se repite.


## CI07 — Check-key fallido aclarado; cifrado recordado, verificación local pendiente

[Diagnóstico y límites](ci07-check-key-diagnostico-140c10a1-2026-10-17.md). Operador aclaró que el check-key fallido se hizo con la contraseña de acceso SUPER_ADMIN y luego confirmó recordar con certeza la de cifrado elegida en keygen. No se conoce ni solicita el valor. Se indicó una única comprobación local con la contraseña de cifrado confirmada; resultado todavía PENDING, sin API/correo/código de emergencia. No repetir recover, reparación, inscripción o generación hasta contrastar.

Metadatos a2026-10-17T16:37:59.3668730Z: principal y copia previstos existen con477 bytes, archivos públicos coinciden con la huella inscrita y JAR canónico verificado. Sin abrir privadas ni calcular sus hashes; esto no prueba igualdad/integridad del contenido cifrado ni la variable efectiva de B. Mensaje genérico no distingue lectura/descifrado/par y no permite atribuir una causa criptográfica exacta. Evidencia JSON SHA **DACB4D89F597B8CD88D7E166461D87B41E9FED4C006957DCB27F0B0996B53F9B**. Se reutiliza la lectura DB16:34, sin nueva consulta por este comando offline. Reparación única y anterior evidencia preservadas; CI07 manual PENDING, CI03 gate PENDING, TEST_CLEANUP_RELEASED=false, SC sin aplicar, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. No nuevas suites, envíos, cuentas ni cambios de final3/términos/protocolo.


## CI07 — Cifrado no identificado; REC-CI07-03 propuesto, sin autorizar

[Estado y límites](ci07-cifrado-no-identificado-140c10a1-2026-10-17.md). Aclaración vigente del operador: solo identifica las contraseñas de acceso SUPER_ADMIN/ADMIN y no la del archivo cifrado; sustituye la declaración previa de recuerdo. No más intentos de contraseña ni comandos de recuperación/generación/inscripción. El protocolo no permite restablecer ese cifrado mediante login/código, ni una segunda inscripción o cambio de pública durante recuperación. No se afirma corrupción o causa criptográfica exacta.

SELECT **2026-10-17T16:42:08.244527300Z**, ruta_fija_test/rf_test/V26, JSON SHA **E8FA6BA7F8082B7CF4285A23DB61F44C065F27D86C35ACB5C884F028014502E6**: misma credencial/pública/instalación, una reparación REC02 preservada, solo reto antiguo vencido sin fallos/prueba/consumo/completado, versión0, aceptación ADMIN e historial1/2/0/cupos0/2/20 intactos; lock y marcador activos, TEST_CLEANUP_RELEASED=false. No nuevas escrituras, HTTP, SMTP, privadas leídas, códigos generados o suites.

Propuesta **REC-CI07-03 v0.1.0**, docs/operacion/L-01-REC-CI07-03-clave-temporal-propuesta.md, SHA **1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8**: nueva clave/copia temporales generadas y comprobadas personalmente; única sustitución de pública por mantenimiento del mismo fixture con auth/reauth/guardas/auditoría y preservación de cuenta, instalación, código, aceptación, archivos, historial y cupos. Se requiere aceptar expresamente esa preparación excepcional; la nueva pública no se afirmará inscrita mediante un segundo HTTP. Después sigue obligatorio todo el protocolo normal y conservación de la nueva pública durante esa recuperación. **NO APROBADA, NO IMPLEMENTADA, NO EJECUTADA.** La autorización REC02 preservaba la pública y no cubre esto. Alternativa: mantener caso pendiente hasta identificar personalmente el cifrado original, sin intentos por rutina.

No reaprobación de SC03/07 originales: siguen condicionales sin aplicar y falta acreditar ambos ensayos. CI03 gate PENDING sin otro ADMIN autorizado, CI07 manual PENDING; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Originales reales pendientes para piloto; CI01 no se repite. Sin cambios de términos, final3 o controles normales.


## REC-CI07-03 — Autorizada, implementada y probada; intervención personal pendiente

[Implementación y límites](REC-CI07-03-implementacion-2026-10-17.md). Titular autorizó REC-CI07-03 v0.1.0 SHA1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8; registro público docs/operacion/L-01-REC-CI07-03-autorizacion-2026-10-17.json SHA434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6. No volver a pedir esta aprobación. Implementación solo test con comando preparar-clave: auth/reauth normales, pública Ed25519 y huella, confirmación humana de principal/copia y disponibilidad del sustituto;17 guardas, transacción, UPDATE solo pública/timestamp y auditoría única, sin otro código/cuenta/cupo/instalación o inscripción HTTP. Baseline pública nueva anclada a auditoría aprobada; recuperación normal y humana completa todavía exigida.

Última ejecución dirigida **54/54** (23 arnes/17 preparación/14 regresión REC02), SHA log F774BF52FB613DBF39D96DAEC08D1A0D6E7ECF2BE929AC4F84E8DFC7B780A9E1. Fallo inicial del doble JDBC corregido y preservado; logs intermedios no se suman. Concurrencia/rollback con dobles, no commits reales de preparación. Consulta PostgreSQL **2026-10-17T17:01:03.669095800Z** test/rf_test/V26:17/17 guardas con referencia prestada del PID titular39116, sin adquirir/desbloquear el lock; EXPLAIN UPDATE/INSERT sin ANALYZE, sin DML/DDL/commit/HTTP/SMTP/privadas/códigos. JSON SHAB92FC8A4B938C9D928597CC880C756A4947C3ABADCC650FC995248D9467B7231. El comando real volverá a exigir lock de su propia sesión. Ledger1/2/0, cupos0/2/20, aceptación, originalREC02 y pública anterior intactos. Manifiesto17 archivos SHA2B210DB1880CA1693C9A88FC6834F0DA9ABDF70E157BB2C715039D6E914934A3. final3 ZIP38065106.../manifiestoE78D7002..., JAR91913FF7..., términos20FFD952... y observación original9319F045... verificados sin regenerar.

Guía docs/operacion/L-01-REC-CI07-03-ejecucion.md v1.0.0. **Etapa personal actual solicitada:** A EnsayoL01> verificar una vez y salir coordinadamente para recarga; debe conservar marcador y TEST_CLEANUP_RELEASED=false porque ambos PENDING. En PS> ejecutar una vez .\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'. MismoRUN, sin password de arranque, lista incluye preparar-clave y login4311 carga; reportar solo esos hechos. Esperar comprobación de marcador/lock antes de nuevas claves. Esta salida coordinada permitida archiva y NO habilita limpiezas, no es un cierre ni retirada manual del marcador.

Después: destino nuevo REC-CI07-03 fuera del repositorio, operador keygen/copia y dos check-key separados (no pegar varios comandos interactivos juntos); variables trialNewPrimary/trialNewCopy preservan los originales. Comunicar dos checks y huella antes de preparar. A preparar-clave exige DOS passwords de ACCESO SA, línea pública .pub, SHA64 y literal CLAVE_COPIA_Y_CODIGO_CONFIRMADOS; nunca recibe privada/passwordcifrado/código. Contrastar auditoría antes de recover con trialNewCopy; el sustituto REC02 sigue vigente. La inspección existente -InspectRunId ya informa auditoría pública nueva/huella y correspondencia; no reutilizar tras cambio el lector oneoff que exige la pública original. No enroll ni reparar-codigo nuevos.

**Preparación real todavía NO ejecutada; clave nueva del operador NO generada.** CI07 manual PENDING y CI03 gate PENDING, otro ADMIN no autorizado. TEST_CLEANUP_RELEASED=false, SC03/07 condicionales sin aplicar; L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Originales reales pendientes para piloto, solo PC confirmada. CI01 VERIFICADO sin repetir; sin Git/GitHub, despliegue, SMTP externo o migraciones de desarrollo/piloto. Este corte sustituye las solicitudes pendientes de autorización REC03 anteriores sin alterar sus antecedentes.


## REC-CI07-03 - Recarga contrastada y error de pegado corregido (2026-10-17 Lima)

[Diagnostico y limites](rec03-error-pegado-140c10a1-2026-10-17.md). Corte UTC **2026-10-18T00:24:49.089356600Z**: mismo RUN_ID140c10a1-be0f-4d08-a6d4-c796e2ed107a, test/rf_test/V26, segmento **e140a035-8211-4037-82e6-388329f1cfc8**, PID22648 loopback4311/8091. Marcador y lock activos; ledger1/2/0, cupos0/2/20, aceptacion sintetica1, REC02 unica y publica original conservados. Recarga y archivos previos contrastados; el operador comunico solo RUN_ID, sin reconstruir salida historica de limpieza ni prueba visual del navegador.

Bloque pegado en una linea sin separadores: **ParserError/UnexpectedToken**, antes de ejecutar ese envio. Destino nuevo/clave/copia ausentes al corte00:24:47Z. GuiaREC03 **1.0.1** agrega separadores y parada segura: analisis sintactico0errores multilinea y0 en una linea, sin ejecutar el bloque ni generar claves; ACL aun no comprobada en ejecucion. **Siguiente paso humano: solo ejecutar el bloque corregido de directorio una vez, comunicar L01_TEMPORARY_KEY_WORKSPACE y detenerse antes de keygen.** Ante fallo no repetir/borrar; A permanece abierta.

CI01 VERIFICADO sin repetir correo; CI03 PENDING solo por gate previo, aceptacion preservada y otro ADMIN no autorizado; CI07 PENDING, sustitucion publica0. SC03/07 condicionales sin aplicar, **TEST_CLEANUP_RELEASED=false**, limpieza suspendida, L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Sin SMTP externo, pruebas que limpien, claves definitivas, cambios de final3/terminos/protocolo, Git/GitHub, despliegue o migraciones de desarrollo/piloto. JSON SHA **11614CEE91A9460225D8A125554F863895AF955C3E2A62B2A698CE38F961C6D2**.


## REC-CI07-03 - Directorio y ACL verificados; keygen personal pendiente (2026-10-17 Lima)

[Directorio listo y limites](rec03-directorio-ready-140c10a1-2026-10-17.md). El operador comunico **L01_TEMPORARY_KEY_WORKSPACE=READY_NO_KEYS_CREATED**. Metadatos UTC2026-10-18T00:32:21Z confirman directorio nuevo, herencia protegida y unica regla FullControl del propietario; principal/publica/copia ausentes. SELECT00:32:22Z en test/rf_test/V26 confirma marcador/lock activos, ledger1/2/0, cupos0/2/20, aceptacion1, REC02 unica y publica original conservados; sustituciones0, sin reto vigente ni recuperacion completa.

GuiaREC03 **1.0.2**: mantener A abierta, en la misma B ejecutar **solo** `java -jar $trialJar keygen $trialNewPrimary`, elegir/confirmar/conservar personalmente el cifrado nuevo y comunicar exito/error antes de copiar o comprobar. No repetir preparacion del directorio. Generacion por operador todavia no reportada; el agente no genero claves, no abrio privadas y no ejecuto suites ni SMTP. CI01 VERIFICADO, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Evidencia JSON SHA **1F2B7EA85B99C559F9FE00A67C5703BABE0A75516B57C4E203F22D4412D18686**.


## REC-CI07-03 - Keygen personal comunicado; copia y lectura pendientes (2026-10-17 Lima)

[Generacion comunicada y limites](rec03-keygen-personal-140c10a1-2026-10-17.md). Operador comunico **Par Ed25519 creado** y conservacion del cifrado nuevo. Corte UTC2026-10-18T00:36:57Z: principal477 bytes por metadatos, publica Ed25519 canonica SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**, copia ausente. Solo publica/metadatos consultados, no privada ni hash del archivo cifrado; descifrado todavia no acreditado. SELECT00:36:58Z test/rf_test/V26 conserva marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02. Publica inscrita original5b727e9a... intacta y sustituciones0.

GuiaREC03 **1.0.3**: operador crea copia local con destinos desocupados y overwrite=false; solo tras exito comprueba una vez el **principal** con el cifrado nuevo y comunica resultados/huella. Esperar antes de comprobar copia o preparar publica. Instruccion de copia validada por analizador, con y sin saltos, sin ejecutarla. No repetir keygen; dos archivos mismaPC no acreditan independencia. CI01 VERIFICADO sin repetir correo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**. L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpiezas, SMTP, cambios de final3/terminos/protocolo, Git/GitHub, despliegue o migraciones ajenas a test. JSON SHA **5928DA632E6F801DEF31F9C4C8D8298CA84AD83D4D096AF887AB1F4781FFCC04**.


## REC-CI07-03 - Principal PASS reportado; comprobar copia (2026-10-17 Lima)

[Resultado y limites](rec03-principal-pass-140c10a1-2026-10-17.md). Operador reporto **LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE** del principal, SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. Verificacion basada en resultado humano del CLI solicitado y coincidencia del archivo publico; el agente no descifro. Metadatos UTC2026-10-18T00:41:54Z confirman principal/copia477bytes y publicas iguales; no igualdad de privadas ni lectura de copia. Linea de creacion de copia no reportada, no reconstruirla. SELECT00:41:55Z test/rf_test/V26 confirma marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02 conservados, publica inscrita original y sustitucion0.

GuiaREC03 **1.0.4**: mantener A, ejecutar personalmente una vez en B **solo `java -jar $trialJar check-key $trialNewCopy`** con el cifrado nuevo que paso en principal; comunicar resultado/huella y detenerse antes de preparar publica. No repetir copia/generacion/principal. CI01 VERIFICADO sin correo nuevo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpieza, SMTP, claves por agente o cambios de final3/terminos/protocolo. Copia mismaPC no acredita independencia. JSON SHA **14B7D678FCF1961A5DD7F6C2D19CC1855CFF9556A9820ADD5A73A79D8B09BA2F**.


## REC-CI07-03 - Dos checks locales PASS; preparar publica (2026-10-17 Lima)

[Dos lecturas y limites](rec03-copia-pass-140c10a1-2026-10-17.md). Operador reporto **LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE** tambien para la copia nueva; SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433** igual a principal y ambas publicas. Se conserva evidencia previa del principal SHA14B7D678...; resultados humanos del cliente solicitado, no descifrado por agente ni comparacion de privadas. SELECT UTC2026-10-18T00:45:27Z test/rf_test/V26 confirma marcador/lock, ledger1/2/0, cupos0/2/20, aceptacion y REC02; publica inscrita original, sustitucion0, sin reto vigente ni recuperacion completa.

GuiaREC03 **1.0.5**, solo apartado4: operador confirma personalmente disponibilidad del sustituto vigente, consulta .pub en B y ejecuta una vez **preparar-clave** en A con autenticacion/reautenticacion de ACCESO SA, publica, huella y confirmacion humana; esperar contraste de auditoria antes de recuperar. Si falta sustituto, detenerse. No nueva reparacion del codigo/inscripcion HTTP. Ambos checks **no acreditan PASS_LOCAL_TEMPORARY_COPY_ONLY** ni independencia de custodia. CI01 VERIFICADO sin correo nuevo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites, limpiezas, SMTP o cambios de final3/terminos/protocolo. JSON SHA **83682662898A7F2D8B980A526BFCDC3202A4382F48B6E7651126995141BA05B5**.


## REC-CI07-03 - Sustitucion ejecutada y auditada; recuperacion normal pendiente (2026-10-17 Lima)

[Sustitucion contrastada y limites](rec03-preparacion-verificada-140c10a1-2026-10-17.md). Operador ejecuto **preparar-clave**: PASS_PRECONDITION_ONLY, sustitucion1 y segmento e140a035-8211-4037-82e6-388329f1cfc8. SELECT UTC2026-10-18T01:19:53.372925200Z en test/rf_test/V26 verifica auditoria unica **58b926a7-bf2d-4ff1-a978-feea67d1e9c4**, fecha servidor01:18:58.039Z, aprobacion/propuesta/cuenta/instalacion/caso/segmento correctos y publica actual **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**; correspondencia persistida true. Confirmacion humana previa incluye ambos checks y disponibilidad del sustituto. Inscripcion HTTP original1 con publica historica; sustitucion1 contabilizada por separado. REC02 unica/ledger1/2/0/cupos0/2/20/aceptacion/marcador/lock conservados. Sin reto vigente/recuperacion completa y sessionVersion0 al corte. Guardas de no repetir preparacion quedan false de forma esperada; publica/auditoria actuales validadas por separado.

GuiaREC03 **1.0.6**, apartado5: recuperacion normal personal una vez con copia nueva/cifrado nuevo/sustituto vigente, reto5min, OTP simulado sin reenvio, password nueva/codigo final mostrado una vez/login; solo despues copia-ok, recuperacion-ok y verificar. Esperar resultados y archivo antes de acreditar PASS_LOCAL_TEMPORARY_COPY_ONLY. La preparacion ya verificada no lo sustituye. CI01 VERIFICADO sin nuevo correo, CI03 gate PENDING sin otroADMIN, CI07 PENDING, SC03/07 sin aplicar, **TEST_CLEANUP_RELEASED=false**, L01 EN_CURSO/piloto bloqueado/L02 sin iniciar. Sin suites/limpiezas/SMTP ni escrituras de inspeccion; codigo/hash/privadas no consultados. Copia mismaPC no acredita independencia. Sin cambios de final3/terminos/protocolo normal, Git/GitHub, despliegue o migraciones de desarrollo/piloto. JSON SHA **88F6A6B64A3655616409C07B96AC2C4C772FD6E7CACFAA200C51406D8001053F**.


## CI07 - PASS manual local contrastado; unico gate local pendiente CI03 (2026-10-17 Lima)

[Informe y revision de los doce criterios](CI07-manual-PASS-2026-10-17.md). **PASS_LOCAL_TEMPORARY_COPY_ONLY verificado**: archivo humano1792287275662, SHA **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**, mismoRUN140c10a1 y segmento e140a035. Predicado vigente del arnes true; native SELECT test/rf_test/V26 UTC2026-10-18T01:40:55Z confirma reto fa9aa2ae consumido01:30:47.240995Z, OTP/capacidad retirados, misma cuenta, publica5569... conservada, sesion0a1 y login. Observador archivado rechaza3access+3refresh; auditoria revoca5refresh,14previos figuran revocados. Comparaciones de codigo anterior/nuevo proceden del observador ya archivado, sin consultar secretos. Archivo publica milisegundos, nativo conserva995microsegundos adicionales; no se altera archivo. **Hubo2OTP simulados y1reenvio**, dentro3/60: se documenta desviacion de instruccion sin reenviar, sin afirmar cero ni repetir el ensayo.

Ledger1/2/0, cupos0/2/20, aceptacion1, reparacionREC02=1, sustitucionREC03=1 e inscripcionHTTP original1 conservados. Doce hashes de archivos (final3/terminos/JAR/aceptacion/archivo nuevo y6logs previos) coinciden; no se repiten suites. **CI07 es ensayo tecnico local VERIFICADO, no custodia real cumplida.** Los doce criterios se revisan en el informe enlazado. **CI03 sigue PENDING solo por gate previo**, sin otro ADMIN autorizado. Condicion SC07 acreditada, SC03 no; adenda conjunta sin aplicar, originales reales pendientes de piloto. **TEST_CLEANUP_RELEASED=false**, marcador/lock y suspension de limpiezas conservados; mantener A y no repetir recovery/salir. L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Siguiente accion: via expresamente autorizada para gate; despues adenda/archivo/salida/revisionfinal, sin reaprobacion de lo ya autorizado. Consolidado SHA **C50BB7B7864491D6841B1616445137AF37A11A6F0365A3CA5B856D059254769E**, nativo SHA **A746503800344BB3EBA2C699A665156E4FF1C9931549092156F3C431B913F2EC**.


## Cierre funcional local acreditado — 2026-10-17

**L-01 VERIFICADA; tipo FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Se aplican SC-03/SC-07 y la [precision aprobada SC-03 1.1.0](../../contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md), [registro de aplicacion](../../contratos/SC-L01-LOCAL-01-aplicacion-v1.1.0-2026-10-17.json). El contrato anterior y la aprobacion condicionada quedan conservados como antecedentes; no se cambian silenciosamente ni se solicita otra aprobacion equivalente.

- CI03: aceptacion humana sintetica original intacta (c496b63c), gate anterior omitido en ese ensayo conservado como historia. [Complemento automatizado separado](REC-CI03-01-automatizado-2026-10-17/README.md): capacidad de activacion valida, POST complete409 RESOURCE_CONFLICT especificamente por terminos pendientes, aceptacion automatizada de un unico ADMIN adicional y mismo POST200 COMPLETED; login/grupos200. Resultado compuesto **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**; no se inventa PASS_MANUAL_SYNTHETIC_ONLY ni se atribuye al titular la aceptacion automatizada.
- CI07: **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado y contrastado, sin repetir recuperacion. Copia en la misma PC; no independencia de custodia. CI01 permanece VERIFICADO sin nuevo SMTP.
- Los doce criterios se revisaron con evidencia, entorno y limites en la [matriz final](matriz-final-funcional-local-2026-10-17.md). Se reutilizan regresiones validas y se ejecutaron27 unitarias dirigidas del arnes y el complemento real HTTP/PG, separados de la evidencia humana. Ningun criterio obligatorio del alcance local queda pendiente.
- Archivo previo a salida del arnes: **TEST_CLEANUP_RELEASED=true**, marcador ausente/lock libre, servidores cerrados. Limpieza selectiva del ADMIN adicional por API normal, inactivo y sesiones revocadas403 FORBIDDEN_ROLE; aceptacion e historial conservados. Sin limpieza global, estados forzados o reinicio de cupos. Ledger1/2/0 pasa a **1/3/0**, cupos **0/1/20**, aceptaciones2; ADMIN manual, SUPER_ADMIN y caso CI01 intactos. Las futuras pruebas autorizadas que limpian solo ruta_fija_test pueden reanudarse tras el archivo.
- Requisitos originales pendientes **antes del piloto**: aceptacion propia de ADMIN legitimo; preparacion, medios/custodia independiente e inscripcion/ensayo del titular real; privacidad/identificacion y condiciones de conservacion; infraestructura/base/HTTPS/copias/restauracion y preparacion de participantes/recursos/titulos. No se registran como satisfechos por equivalencia. Solo una PC Windows confirmada; no se presupone medio adicional.
- Terminos1.0.0 SHA20FFD952... y final3 ZIP38065106.../manifiestoE78D7002... comprobados sin cambios. Sin SMTP adicional, claves definitivas, migraciones de desarrollo/piloto, despliegue o Git/GitHub. S/0 y sin offline. **L-02 NO INICIADA / NO AUTORIZADA; OK VERDE separado.**

El [acta](../../actas/L-01-cierre-funcional-local-2026-10-17.md) contiene el alcance, referencias, limites y bloqueos. Las observaciones historicas PENDING del ensayo humano anterior al complemento no representan un pendiente actual del alcance local tras aplicar la adenda.
