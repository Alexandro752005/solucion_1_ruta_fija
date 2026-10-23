# L-01 — Incorporación Chiclayo y continuidad de acceso

Fecha: 2026-10-15, America/Lima. **L-01 EN_CURSO; L-02 NO INICIADA.** Este corte prevalece sobre los estados anteriores sin reemplazar su evidencia histórica.

## Decisiones incorporadas

- [Catálogo L01-CHICLAYO 0.1.0](../../contratos/L-01-documental-Chiclayo-v0.1.0.md): M1 documentado por modalidad/título; colectivo sedán/station wagon; revisión personal, vehículo y empresa separados; POR_CONTRASTAR bloqueante. DD-07…15 aprobados expresamente con esos límites.
- DD-13A: datos estructurados y cotejo visual sin fotos, PDF o cargas. DD-14A/B: deshabilitar al cierre y eliminar expediente a los treinta días del fin salvo excepción justificada; ADMIN responsable. No se acredita todavía una eliminación ejecutada ni se inventa acreditación de títulos/rutas reales.
- DC-01A: identidad preliminar separada. DC-02B: creaciones históricas 1/4/20, sin devolución de cupo al desactivar/eliminar. No se preguntaron otra vez.
- [OP-ADMIN-01/02](../../contratos/L-01-alta-reemplazo-admin.md) aprobados: alta pendiente, contraseña propia, credencial de un uso/24 h, OTP, términos antes de activar; reemplazo verificado y revocación atómica, incluida revocación preventiva. No se impone un ADMIN único.
- Canal del proyecto `rutafija.dev1@gmail.com`, atención cada dos o tres días sin disponibilidad permanente; función declarada del aprobador: titular, desarrollador y responsable técnico. El soporte operativo usará el número que ADMIN registre en incidencias de su organización; no se inventó un número ni se implementó otro lote.
- [Borrador de términos 0.3.0](../../legal/terminos-admin-v0.3.0-borrador.md) actualizado con hechos confirmados; no aprobado ni publicado. Los borradores anteriores se conservaron.

## Cambios concretos

### Incorporación personal

Acceso `oa1` con hash SHA-256, treinta minutos absolutos, sin JWT/cookies/refresh/rol operativo. El solicitante consulta su propia solicitud e introduce correcciones sin obtener pertenencia activa. La vista ordinaria no devuelve sus campos personales.

CRM permite apertura expresa del expediente restringido por ADMIN de la misma organización, cotejo estructurado, observaciones, devolución, ampliación motivada, aprobación, rechazo y provisión separada. Se conservan catálogo/versión/huella, revisiones e historial inmutables, versión optimista e idempotencia por actor/operación.

Devolución suma una ronda; reenvío no. Plazo de 48 horas y ampliación con motivo; vencimiento no rechaza automáticamente. Reiteración desde cuatro rondas y espera de siete días con nueva invitación y misma identidad. La repostulación comparte los límites de invitación y no permite evadirlos por otra ruta.

La cuenta definitiva toma el nombre del CON-01 revisado, conservando la declaración inicial y las revisiones. La provisión exige aprobación personal y grupo activo; crea Driver inactivo. No habilita conducción, oferta ni unidad por haber verificado correo o aceptado invitación. Requisitos desconocidos, vencidos o sin justificación no son conformes; una habilitación aplicable requiere vencimiento documental comprobado.

Las altas y cambios de identidad heredados no pueden eludir el expediente. Se mantienen los módulos y sus pruebas, con fixtures históricos explícitamente sintéticos cuando necesitan conductores previamente aprobados.

### Cupos y privacidad

Ledger persistente y triggers transaccionales con bloqueo por rol. V23 corrige la anotación de altas ignoradas por `ON CONFLICT DO NOTHING` y revalida cupo en el registro definitivo para impedir el exceso en una inserción de varias filas. Recuperar el mismo ID no consume otra creación.

Se ocultan campos personales ordinarios en respuestas de cuentas, perfiles, conductores, invitaciones, recuperación, autores, operación y auditoría; se usan referencias. Los nuevos JWT omiten correo y nombre; siguen comprobándose cuenta, organización, rol y versión de sesión. Las claims heredadas de correo, si existen, deben coincidir. CONDUCTOR no puede obtener sesión CRM.

Los formularios de identidad heredados se sustituyeron en la navegación por Solicitudes; sus handlers también impiden POST/PATCH de alta/edición. Se preservan recuperación y gestión compatible. Las credenciales temporales expresamente necesarias para activar o recuperar se muestran una vez y se limpian; no se confunden con datos de perfil.

**Límites:** estos cambios no demuestran redacción universal de nombres u otros datos que alguien introduzca en textos libres. El DNI/vínculo HMAC evita duplicar una persona CONDUCTOR revisada; correo único no acredita unicidad física entre todos los roles con correos distintos. El ledger se inicia con cuentas presentes: no reconstruye eliminaciones anteriores sin evidencia independiente.

### ADMIN

API y CRM incorporan gestión SUPER_ADMIN y activación pública con `ac1`/`aa1` separados. La preparación no concede rol efectivo; términos actuales y verificación son requisitos previos. La ausencia de documento aprobado impide finalizar una activación real. Las pruebas usan documentos inequívocamente sintéticos.

## SMTP real

La discrepancia nueva procedía del remitente permitido antiguo; el usuario SMTP privado ya coincidía con el remitente autorizado `rutafija.dev1@gmail.com`. Se corrigió la configuración permitida exacta, sin ampliar destinatarios ni modificar credenciales.

Un solo envío del adaptador fue aceptado por Gmail y el destinatario autorizado confirmó recepción en entrada. [Evidencia y límites](smtp-entrega-confirmada-2026-10-15.md). No prueba consumo HTTP de un OTP persistido ni se repitió el envío automáticamente.

## Registro de comprobaciones

| Ejecución | Resultado observado y alcance |
|---|---|
| Primera dirigida de onboarding, antes de correcciones | 46 unitarias; 6 IT de acceso correctas; 11 IT de incorporación con 6 fallos/1 error. Expuso invalidación con fechas iguales bajo reloj congelado y precisión de PostgreSQL. No se ocultó como éxito. |
| Intento siguiente con redirección PowerShell | Interrumpido por `ErrorActionPreference=Stop` heredado del importador ante stderr de Mockito, antes de las IT. Se corrigió la invocación a `Continue`; no fue fallo de autenticación Gmail. |
| Dirigida integrada de diez clases | 47 unitarias y 68 IT; 8 fallos de fixtures/serialización/expectativas. Corregidos ADMIN de fixture sin correo verificado, campos opcionales omitidos, estado de grupo sin su fixture previo y expectativas de PII. No se borraron pruebas. |
| Regresión completa anterior a OP-ADMIN | 47 unitarias correctas; 109 IT con un único fallo de expectativa antigua `driverClosedByName`, que recibía el código aprobado en vez del nombre personal. Se corrigió la expectativa y queda pendiente la repetición final del checkout ampliado. |
| Flutter completo actual | `flutter analyze --no-pub`: sin incidencias. `flutter test --no-pub --reporter expanded`: **74/74** correctas. Son pruebas de cliente con dobles, no dispositivo físico ni sesión conectada al backend real. |
| CRM, cortes de agentes | Regresión 105 pruebas correcta; tres pruebas posteriores de revocación correctas (9/9 dirigidas del módulo). Coordinación está reproduciendo typecheck, suite y build completos del checkout final. |
| OP-ADMIN y continuidad | En validación coordinada. No se dan por ejecutadas únicamente por haberse escrito las IT. El resultado final se añadirá sin sustituir este historial. |

Los comandos backend cargan únicamente `Import-RutaFijaNativeEnvironment.ps1 -Scope Test`; usan `ruta_fija_test`, correo simulado y Flyway con migrador separado. Los registros completos están en `backend/target/l01-*.log`; las pruebas Flutter en `mobile/.dart_tool/l01-*.log` y CRM en `frontend/.angular/l01-*.log`. Son artefactos locales de trabajo; el resumen saneado permanece aquí.

## Límites de cierre

No se publica ni acepta realmente ningún borrador. Siguen pendientes los criterios obligatorios que requieran aprobación final de términos, custodia/ensayo del titular SUPER_ADMIN, eliminación/conservación efectiva y evidencia adicional de privacidad/unicidad según su alcance. El consumo integral con SMTP real no está acreditado por la entrega aislada. Los títulos externos no presentados permanecen desconocidos. La validación física corresponde a L-08.

No se ejecutó Git/GitHub, no se desplegó, no se migró desarrollo/piloto ni se regeneró `final3`. Los resultados finales de las comprobaciones y las huellas se añadirán al terminar el incremento. No se solicita cambio de lote.

## Corte ejecutado V25 — 2026-10-15, 15:11 America/Lima

Las filas de «en validación» anteriores conservan el estado de sus primeras ejecuciones; los resultados posteriores son los siguientes. Este corte precede a la nueva implementación ID-01 autorizada después.

| Comprobación | Resultado observado |
|---|---|
| Regresión `l01-regression-final.log` | BUILD SUCCESS: 47 unitarias + 130 IT, V25, antes de los últimos controles de escritura. |
| Regresión `l01-regression-guards.log` | 49 unitarias correctas; 140 IT con 1 error. La nueva aceptación concurrente detectó interbloqueo al elevar el bloqueo de organización desde la consulta JPA de cuenta. No se considera una ejecución satisfactoria. |
| Corrección dirigida `l01-lockfix-directed.log` | BUILD SUCCESS: 49 unitarias + 47 IT. Se quitó el EntityGraph de organización de `AppUserRepository.findByIdForUpdate`: la cuenta se bloquea sin elevar implícitamente la organización a UPDATE. Incluye ADMIN 15, documental 19, escritura concurrente 5 y términos 8. El filtro escrito contenía además `OnboardingApplicantAccessFlowIT`, que no coincide con una clase real; no se cuenta como ejecutada por esta selección. |
| Regresión completa `l01-lockfix-final.log` | **BUILD SUCCESS: 49 unitarias + 141 IT**, sin fallos, errores ni omitidas. Flyway valida V25. Incluye `ConductorOnboardingAccessIT` 7, documental 19, cuotas 11, guardas heredadas 4, autorización de escritura 5, términos 9, gate términos 2 y CLI contra Tomcat real 1. Términos concurrentes de la misma cuenta y de dos ADMIN de la misma organización pasan. |
| Suplementaria `l01-offline-final.log` | **BUILD SUCCESS: 52 unitarias + 15 IT**, sin fallos, errores ni omitidas. Son las 49 unitarias más tres de registro condicional offline; IT MobileOperation 6 y términos 9. No sumar ambas ejecuciones como pruebas únicas. |
| CRM consolidado | `npm.cmd run typecheck`, `npm.cmd run test:ci`, `npm.cmd run build`: correctos, **27 archivos/108 pruebas**. Logs `frontend/.angular/l01-typecheck.log`, `l01-tests.log`, `l01-build.log`. |
| Flutter consolidado | `flutter analyze --no-pub`: limpio; `flutter test --no-pub --reporter expanded`: **74/74**. Logs `mobile/.dart_tool/l01-analyze.log`, `l01-tests.log`. |

### Comandos backend reproducidos

Desde `backend`, cada ejecución importa únicamente el destino Test. El importador fija preferencia Stop; se restaura Continue para que el stderr informativo de Mockito no interrumpa PowerShell antes de las IT. Las credenciales no son argumentos ni se imprimen.

```powershell
. ..\scripts\Import-RutaFijaNativeEnvironment.ps1 -Scope Test
$ErrorActionPreference = 'Continue'
.\mvnw.cmd --batch-mode --no-transfer-progress verify '-Djacoco.destFile=target/jacoco-l01-lockfix-final.exec' '-Djacoco.dataFile=target/jacoco-l01-lockfix-final.exec' *> target/l01-lockfix-final.log

# Ejecución suplementaria tras añadir la prueba negativa offline:
.\mvnw.cmd --batch-mode --no-transfer-progress verify '-Dit.test=MobileOperationFlowIT,AdminTermsFlowIT' '-Djacoco.destFile=target/jacoco-l01-offline-final.exec' '-Djacoco.dataFile=target/jacoco-l01-offline-final.exec' *> target/l01-offline-final.log
```

Se usaron archivos JaCoCo nuevos por ejecución para no mezclar datos de clases anteriores. No se afirma un porcentaje de cobertura. Los 141 IT incluyen tres regresiones offline explícitamente habilitadas solo en su contexto de prueba; no equivalen a capacidad de producto.

La prueba negativa ejecuta el POST heredado con un CONDUCTOR sintético y obtiene **404** en la aplicación iniciada sin la habilitación excepcional. Tres pruebas de contexto comprueban: perfil local con flag accidentalmente true no registra controlador; test por defecto tampoco; solo test+flag explícito lo registra para conservar la regresión. Flutter conserva su prueba de ruta/acción no expuesta. No se ha usado un dispositivo físico.

### Revocación y concurrencia

`CurrentUserProvider` relee organización y cuenta tras adquirir los bloqueos de una escritura. Mantiene SHARE de organización y UPDATE de actor hasta finalizar la transacción; no usa una entidad de sesión potencialmente cacheada para autorizar. Cinco IT con bloqueo PostgreSQL real verifican revocación de cuenta, cambio de sessionVersion, organización desactivada, SUPER_ADMIN revocado y el orden de confirmación cuando la escritura ya fue autorizada. Las mutaciones de incorporación también revalidan `oa1` después de esperar sus bloqueos y conservan el bloqueo del token frente al logout.

Esto prueba los casos descritos, no cada posible intercalado de toda la plataforma. La consulta de bloqueo de cuenta ya no carga/bloquea organización por EntityGraph; acceso lazy ocurre dentro de la transacción y el bloqueo de ciclo de vida de organización permanece explícito.

### Huellas de este corte

- Regresión completa: `4D8760AE54C5EA5D3A5872522871B0E822ADFBA23584E2A69AB01A40FE3C803B`.
- Suplementaria: `4771785CD57B0AFC73AC6D551074613D57481939E874CC0700DC9B431E14BF7F`.
- Catálogo aprobado 0.1.0 sin cambios: `A46FAE25801BA607EE1A0B2B07FEA32192C326882A55506018D41B7D80A5B8D7`.
- `final3` ZIP sin cambios: `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`.
- Manifiesto final3 sin cambios: `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`.

## Aprobaciones recibidas después del corte V25

El titular aprobó expresamente **RET-02 con sus límites** e **ID-01 con revisión restringida**. RET-02 confirma la propuesta de deshabilitar ADMIN/CONDUCTOR al cierre y anonimizar sus datos personales a treinta días conservando referencias/historial/cupos; no borra SUPER_ADMIN ni cambia plazos de las otras categorías por inferencia. Conservar un HMAC vinculable no acredita anonimización irreversible: el procedimiento debe identificar qué se retira y qué queda.

ID-01 autoriza registro común de persona para los tres roles, HMAC DNI y comprobación manual restringida. El alias provisional PERSON-01 de la matriz se refiere a esta misma decisión; la identificación del responsable del texto legal es otro asunto. Se implementa V26 por incremento; las pruebas V25 anteriores **no acreditan todavía ID-01**. El usuario no aprobó términos, custodia real ni cambio a L-02.

## Corte ejecutado V26 — identidad común y términos en revisión

Este corte prevalece sobre los pendientes históricos de PERSON-01/ID-01: **la decisión ya está aprobada y su implementación se ha probado con identidades sintéticas**. No se inscribió identidad, clave ni aceptación del titular real.

### Implementación e integración

- V26 crea el registro común HMAC DNI, puente de conductores revisados y huella de la clave de derivación, sin almacenar DNI administrativo original. Unicidad por persona/cuenta, bloqueo transaccional y vínculo inmutable impiden otra alta con el mismo DNI entre roles/organizaciones. No se atribuye verificación estatal ni identidad física infalible a una declaración manual.
- El gate es obligatorio fuera del perfil exclusivamente test, también frente a una propiedad accidentalmente desactivada. Las regresiones históricas que lo aíslan no acreditan ID-01: `PersonIdentityFlowIT` lo habilita; `PersonIdentityTermsBootstrapIT` habilita simultáneamente identidad y términos. El estado mínimo es excepción GET exacta, sin privilegio operativo.
- SUPER_ADMIN regulariza su propia identidad mediante reautenticación y declaración expresa; después revisa ADMIN. Alta/reemplazo/activación ADMIN exigen la revisión correspondiente. CRM incluye estos controles y la regularización restringida de CONDUCTOR existente, conservando cuenta, Driver y cupo. No se infiere DNI desde nombre/correo ni se adoptan cuentas por coincidencia.
- El primer registro del CONDUCTOR existente desactiva Driver y revoca sesiones; repetir la misma revisión no vuelve a revocar. Un DNI distinto del campo legado no lo reescribe: deja la conciliación pendiente y registra solo la diferencia booleana. Los tickets operativos revisan identidad al emitir y consumir.
- La revisión independiente detectó tres brechas (consulta mínima atrapada por términos, regularización de CONDUCTOR legado y colisión con DNI de Driver anterior); se corrigieron y se cubren en las suites dedicadas. La consulta de bloqueo Driver deja de cargar el grafo empresarial y bloquea cuenta antes de Driver, conservando el orden de autorización. Dos ADMIN concurrentes sobre el mismo conductor confirman un solo vínculo y una sola revocación inicial.
- RET-02 queda como decisión aprobada y procedimiento preparado, no como purga realizada. V26 mantiene vínculos inmutables que también deberán incorporarse al retiro controlado futuro. No ampliar L-01 a toda la automatización de retención del producto.

### Evidencia ejecutada

| Comprobación | Resultado y límites |
|---|---|
| Backend completo `backend/target/l01-v26-final.log` | **BUILD SUCCESS: 56 unitarias + 155 IT**, cero fallos/errores/omitidas. Flyway valida 27 migraciones, esquema V26, exclusivamente `ruta_fija_test`. Es el corte completo anterior al refuerzo adicional de observaciones documentales descrito por separado al final. |
| Identidad con gate habilitado | `PersonIdentityFlowIT`: **10 IT**; `PersonIdentityTermsBootstrapIT`: **1 IT** con ambos gates. Enrolamiento autorizado y negativas, cuenta sin revisión bloqueada, duplicación interrol/tenant concurrente, rollback/cupo, idempotencia, regularización legacy, secreto de proyección y bootstrap con términos. No es inscripción del titular. |
| Flujos existentes | La misma regresión incluye ciclo ADMIN 15, documental 20, incorporación 11, acceso preliminar 7, cuotas 11, guardas heredadas 4, autorización concurrente 5, términos 9 y gate 2; recuperación SUPER_ADMIN 4 + CLI HTTP real 1. Correo simulado. |
| CRM final | Typecheck y build correctos; **27 archivos/121 pruebas** en `frontend/.angular/l01-person-final-{typecheck,tests,build}.log`. Incluye identidad administrativa y revisión del CONDUCTOR existente. Cliente con dobles; no E2E conectado. |
| Flutter | Se reutiliza evidencia válida del código móvil sin cambios posteriores: analyze limpio y **74/74**, `mobile/.dart_tool/l01-analyze.log` y `l01-tests.log`. No dispositivo físico ni recorrido UI conectado. |
| Validador OTP manual | `Invoke-RutaFijaL01OtpManual.ps1 -Prepare` correcto; compilación/classpath sin DB/SMTP. Ejecución por defecto: `MANUAL_OTP_STATIC_GUARDS=PASS; DB_CONNECTED=false; SMTP_ATTEMPTS=0`. Rechazo sin consola interactiva correcto. **No se ejecutó `-Execute` ni hubo envío nuevo**. |
| Validador con backend real de pruebas | `ManualOtpValidationHarnessIT`: **1 IT** incluida en las 155. Tomcat/HTTP y PostgreSQL reales, correo doble y destinatario sintético. Emisión persistida, consumo HTTP, PENDING_REVIEW sin sesión operativa y limpieza selectiva, conservando datos ajenos/ledger. No equivale a consumo de OTP entregado por Gmail. |

La herramienta interactiva exige confirmación local explícita y captura OTP sin eco; usa exclusivamente la configuración privada vigente, compara por separado username/remitente/destinatario autorizados, STARTTLS obligatorio y límites de tiempo. No reintenta automáticamente. [Procedimiento y precondiciones](validacion-otp-http-manual-2026-10-15.md). La entrega real aislada ya confirmada sigue siendo evidencia válida de transporte/recepción; no se extiende al flujo persistido real aún pendiente.

### Intentos no satisfactorios conservados

1. `l01-person-directed.log`: fallo en fixture unitario de autenticación JWT; se corrigió el constructor autenticado y se añadió la negativa no autenticada, sin debilitar el gate.
2. `l01-person-directed2.log`: referencia sintética contenía el nombre protegido y se rechazó correctamente; la limpieza del validador intentaba revocar una invitación ya usada contra el CHECK. Se corrigieron referencia opaca y limpieza solo de invitación no consumida; éxito se informa después de limpiar.
3. `l01-person-directed3.log`: otra referencia sintética repetía el nombre administrativo y se reemplazó por referencia no personal. La regresión completa V26 posterior pasó. Ninguno de estos intentos envió correo externo.

### Términos: contenido presentado, sin publicación

La elección expresa fue «Función/canal técnico; privacidad separada antes del piloto». Se preparó [candidato ADMIN 1.0.0](../../legal/terminos-admin-v1.0.0-candidato.md) y [ficha de revisión](../../legal/revision-candidato-terminos-admin-1.0.0.md). El titular pidió ambos textos completos antes de decidir: **no aprobó, publicó ni aceptó**. Se mantienen sus bytes para esa revisión y los borradores anteriores.

No hay marcadores incompletos en el candidato. Fechas efectivas, referencias de organizaciones, teléfono, infraestructura y condiciones de retiro/privacidad, títulos reales y ensayos pendientes están identificados en la ficha sin inventarlos. Privacidad e identificación del tratamiento se preparan separadamente antes de datos reales; función técnica/contacto no las sustituyen.

**Observación de A-QA sobre la redacción, fuera de los archivos congelados:** la ficha atribuye al candidato una separación expresa persona/grupo/unidad que este no desarrolla por completo. Se recomienda hacer explícito que la aprobación personal puede quedar sin grupo y que no habilita unidad ni conducción. La regla ya está en el contrato/código; no se presenta esa aclaración como texto incluido ni se modifica el candidato bajo revisión. Si se incorpora después, se emitirá otra huella y revisión del contenido exacto.

### Huellas verificadas de este corte

- Regresión completa V26: `8B2C0455683D22DAC72E044295EE536C90A5F3EFCF4FEA5E902251027A4DADA2`.
- CRM 121 pruebas: `7B8D6694BBE83A3B19BB22DAC00A04F4A2F8D1BB3AE82DB94973D5B554D0B245`.
- V26 aplicada, sin editar: `74C73FAAB92E56639A6DDE3ECC3F892706FFE2FB7B9ACB326EFD0EAAF550C9EC`.
- Candidato: `48179D0B95DDDAE14363E9BCF006EFF1FC0B7484B296016A2647A2E74C09FCAF`.
- Ficha: `A07F8165964AD0B8E48CA95CFF94F24BFC3494AA554F178F3153202F08757167`.
- final3 ZIP: `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`; manifiesto: `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`, ambos nuevamente comprobados sin regenerar.

**L-01 EN_CURSO.** Falta aprobación/publicación y aceptación real de términos, consumo persistido del OTP real, inscripción/custodia/ensayo del titular y evidencia de recorridos UI conectados que no sustituyen los dobles. No se exige fingir purga pospiloto ni títulos reales inexistentes para cerrar pruebas sintéticas. No se inicia L-02, no se migra desarrollo/piloto, no se despliega ni se ejecuta Git/GitHub.

### Suplementaria documental — cierre del refuerzo de privacidad

Se amplió `ConductorDocumentaryService.publicText` para impedir que una observación/motivo visible al solicitante repita el nombre revisado del expediente, además de número, licencia y correo. La comprobación cruza todos los requisitos del comando y los ya guardados: no se limita al documento al que pertenece la observación. El expediente restringido conserva los datos estructurados autorizados; estado/historial ordinarios no deben copiarlos.

La nueva IT usa un nombre documental sintético diferente del nombre preliminar: rechaza copiarlo a observación de otra exigencia sin guardar una revisión; permite guardar el expediente correcto; rechaza copiarlo al motivo de devolución sin cambiar estado; comprueba que el resumen del solicitante no devuelve ese nombre. Esto no detecta cualquier nombre de un tercero desconocido en texto libre ni certifica privacidad universal.

- Primer intento `l01-document-privacy.log`: 56 unitarias correctas, 21 IT con **1 fallo**. La nueva prueba esperaba 400 de privacidad pero su fixture omitía emisor obligatorio y recibía 409 antes de esa validación. Se añadió el emisor sintético al fixture sin cambiar el contrato ni el código para eludir el rechazo.
- Repetición `l01-document-privacy-final.log`: **BUILD SUCCESS, 56 unitarias + 21 IT documentales**, cero fallos/errores/omitidas. Solo `ruta_fija_test` V26, doble de correo, sin envío externo. Es una ejecución dirigida posterior al corte completo 56/155; **no sumar sus casos repetidos ni presentar una suite completa de 156 como ejecutada**.

Comando desde backend, después de importar Scope Test y fijar Continue:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify '-Dit.test=ConductorDocumentaryFlowIT' '-Djacoco.destFile=target/jacoco-document-privacy-final.exec' '-Djacoco.dataFile=target/jacoco-document-privacy-final.exec' *> target/l01-document-privacy-final.log
```

SHA-256 de esa repetición dirigida: `5138ADE0E144387EE04B4066E1584DB4B705A1459DFFF26799746EC9F3C784D9`.

## Continuación del prompt de revisión — T-01…07 y clientes conectados

Fuente de esta interacción: `C:/Users/Ale/Downloads/Ruta_Fija_L01_Prompt_y_Clausulas_Revision_2026-10-15.md`, adoptado expresamente por el titular. Se contrastaron contrato/matriz/memoria y código vigente, conservando los resultados V26 anteriores. Las cifras 56/155, 56/21, CRM121 y Flutter74 de esos cortes son **evidencia reutilizada, no resultados nuevos**. No se cambió esquema ni seguridad de la API; los cambios funcionales nuevos se limitan al cliente local de recuperación y los arneses de comprobación. No se repite toda la regresión de integraciones sin ese motivo; se ejecutan los afectados.

### Decisiones y candidato corregido

T-01/T-02 expresan separación persona/grupo/unidad y sustitución ADMIN condicionada al cupo histórico ya aprobadas. Las siguientes precisiones se confirmaron en una sola ronda y se registraron en el addendum 0.8.0:

- **T-03:** cinco referencias; con cuatro creaciones ADMIN, al menos una organización sin operación. No nuevos cupos, cuenta compartida o cambio de pertenencia.
- **T-04:** retiro de HMAC/vínculos personales ADMIN/CONDUCTOR también a treinta días del fin, conservando IDs/historial mínimo/cupos. Excepciones con finalidad y vencimiento; SUPER_ADMIN excluido por inferencia.
- **T-05:** ADMIN identifica/solicita antes de deshabilitar su cuenta; responsable técnico ejecuta retiro limitado y auditado, sin reactivar operación o ampliar acceso ordinario al expediente.
- **T-06:** inicio efectivo al habilitar; fin/cierre en el mismo acto siete días después. Es el origen de los plazos ligados al fin, no agosto ni 2026-10-26 estimado.

El candidato/ficha anteriores quedaron en `docs/legal/historico/` con sus huellas originales 48179d0b… y a07f8165… comprobadas antes de corregir. La fuente canónica sigue siendo [candidato ADMIN 1.0.0](../../legal/terminos-admin-v1.0.0-candidato.md), sin publicación; nueva huella **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**. [Ficha concordante y diferencias](../../legal/revision-candidato-terminos-admin-1.0.0.md), SHA **5E702CD8BCB79DB4A9C01D00EA22466357C47737C5F96B55B9F8B6A4C8C2F9B3**. T-07 se contrasta sobre el archivo real: no marcadores pendientes ni escapes añadidos desde el chat. Confirmar reglas no aprueba el texto completo ni lo publica.

Se preparó el [aviso separado de identificación/privacidad](../../legal/aviso-identificacion-privacidad-piloto-v0.1.0-borrador.md), **borrador incompleto/no aprobado**: hechos confirmados, categorías/finalidades/acceso/proveedores, plazos y datos pendientes. No inventa responsable jurídico, domicilio, destinos geográficos de Gmail o plazos legales. Antes de nuevos datos reales faltan identificación efectiva, condiciones/procedimientos y revisión correspondiente. No es un criterio nuevo de despliegue/retención agregado a L-01.

### Recorrido conectado ejecutado

Nuevo `ConnectedL01ClientsIT`, opt-in mediante `RF_L01_CONNECTED_CLIENTS=true`, inicia Tomcat real y PostgreSQL `ruta_fija_test`, gates identidad/términos habilitados. Un servidor transitorio enlazado exclusivamente a loopback sirve el build real Angular y reenvía la API; su control de fixtures usa una capacidad aleatoria en memoria, no se compila como endpoint del producto. El navegador Chromium/Edge headless se controla mediante CDP con Node estándar; no se instalaron paquetes ni se cambió el lockfile.

Flutter dibuja las páginas reales en el motor de pruebas y utiliza `MobileApiClient`/repositorios HTTP de producción contra esa misma instancia real. Se retira únicamente el bloqueo de red propio del framework de tests; **no hay MockClient ni gateways HTTP falsos**. La bandeja de correo y el almacén protegido del dispositivo se sustituyen por dobles. Son recorridos de interfaz+backend efectivos de test; no Android en dispositivo físico ni ensayo de custodia del titular.

| Recorrido | Evidencia observada en la ejecución satisfactoria |
|---|---|
| SUPER_ADMIN/alta ADMIN | Login por formulario, revisión propia de identidad, alta ADMIN inactiva con revisión restringida/cupo; credencial mostrada una vez. |
| Activación ADMIN | Contraseña propia, OTP de bandeja simulada, documento sintético identificado, casilla inicialmente falsa, aceptación explícita y activación. El candidato real nunca se carga como documento vigente. |
| Invitaciones/incorporación | Emisión por CRM, código ocultado y listado sin destinatario personal; Flutter registra/verifica y muestra PENDING_REVIEW. Sin AppUser CONDUCTOR ni Driver previo a aprobar. |
| Observación/subsanación | ADMIN abre expediente expresamente, devuelve con motivo; Flutter usa oa1, consulta/subsana/reenvía y cierra la consulta por la UI. Historial/ronda permanecen. |
| Aprobación/grupo | CRM guarda cotejo y aprueba personalmente. Petición directa con versión actual y sin grupo recibe **409 RESOURCE_CONFLICT**; aprobación se conserva. Selección de grupo activo y provisión desde CRM crean una sola cuenta/Driver; Driver permanece inactivo. |
| Recuperación CONDUCTOR | ADMIN autoriza comprobación asistida en CRM; Flutter verifica correo simulado y restablece contraseña. Misma cuenta y cupo; no sesión implícita. |
| Rechazo | Segunda identidad sintética invitada y verificada en Flutter; ADMIN rechaza desde el expediente CRM con motivo/historial y sin crear otra cuenta CONDUCTOR. |
| Reemplazo ADMIN | Revisión/solicitud independiente, candidato nuevo, activación con contraseña/OTP/términos sintéticos; sesión saliente rechazada y sustituto autorizado. Conserva organización/historial. |
| Permisos/aislamiento | ADMIN sustituto no accede a `/platform/admins` (403); grupo real de la otra organización por API directa denegado (404); CONDUCTOR no obtiene login CRM (403). La política normal no se deshabilita para el test. |

El cierre SQL del recorrido comprueba una sola cuenta/alta histórica CONDUCTOR, dos aceptaciones únicamente sintéticas y cero documentos publicados no sintéticos. Limpieza del arnés exclusivamente sobre la base de test validada, sin coincidir con una ejecución manual OTP. Las fronteras de cupos/invitaciones/OTP y todos los módulos no ejercitados aquí conservan sus evidencias dedicadas anteriores; no se declara cobertura universal por este recorrido.

### SUPER_ADMIN y OTP: comprobaciones nuevas y límites

- CLI añade `check-key`: descifrado y correspondencia Ed25519 local, sin API/SMTP/código de emergencia; salida solo resultado y huella pública. Reutiliza contenedor/verificación existentes; no cambia el protocolo. Ocho unitarias CLI incluyen copia temporal legible y rechazo de contraseña/pública incorrectas. Salidas de error/interfaz no interactiva retornan código distinto de cero.
- `SuperAdminRecoveryCliBackendIT` ahora recupera usando una **copia cifrada de una clave temporal**, con Tomcat real y correo doble. Comprueba nuevo correo/contraseña, access/refresh anteriores rechazados, prueba consumida, dos presentaciones únicas de códigos distintos y pública inscrita conservada. No se inscribió ni custodió la clave real.
- `SuperAdminRecoveryBoundariesIT`: **3 IT** nuevas; alteración individual de instalación/cuenta/correo/nonce/vencimiento, frontera exacta de cinco minutos y concurrencia de prueba/finalización. Solo una prueba y finalización confirman, un envío simulado y una revocación de versión. No se aumentan intentos ni envíos reales para probar negativas.
- `ManualOtpValidationHarnessIT`: mantiene Tomcat/PostgreSQL reales y correo doble. Añade prueba del recibo idempotente Q-018 y rechazo de reenvío consumido sin otra entrega. Q-018 devuelve el mismo resultado mínimo al repetir su capacidad; esto **no es una segunda verificación OTP**. No cambiar ese contrato a un 4xx para simular uso único.
- Helper SMTP manual pide confirmación separada `RECIBIDO` antes de entrada oculta, informa emisión/transporte/persistencia/consumo por separado y marcas UTC. No se ejecutó con Gmail en esta interacción. Recepción anterior válida solo para transporte; **CI-L01-01 integral pendiente**. [Guía](validacion-otp-http-manual-2026-10-15.md).
- Titular confirmó solo PC Windows disponible; medio separado sin confirmar, clave/inscripción/copia/código aún pendientes. [Pasos personales y comprobación offline de copia](recuperacion-superadmin-protocolo.md). **CI-L01-07 pendiente**, sin atribuirle material temporal del agente.

### Ejecuciones y resolución de intentos fallidos

`l01-connected-first.log` falló por marcador de términos sintéticos incompleto; no se debilitó el CHECK. Los intentos siguientes (`second`…`ninth`) conservaron sus fallos y permitieron corregir el propio arnés: precedencia de CORS del test base, espera de opciones de formulario, cerrar sesión anterior antes de cambiar de actor, config `environment=test` solo para el servidor de test, selector de modalidad inicialmente nula, cierre de consulta en zona asíncrona Flutter y expectativas de códigos **400/409** que correspondían al contrato ya existente. Ninguna corrección desactivó el gate, amplió permisos o modificó las reglas de producto para aprobar el test.

`l01-connected-tenth.log`: **BUILD SUCCESS, 57 unitarias +6 IT**, cero fallos/errores/omitidas. Desglose IT: fronteras recuperación3 + CLI/backend1 + recorrido conectado1 + validadorOTP1. SHA **142646A4C25E4CC223803CC20355095264DF9ED964DA557CE68BDAAB83E99DAE**. El recorrido conectado contiene etapas CRM/Flutter; no sumarlas de nuevo a las 6 IT. `flutter analyze --no-pub` posterior: limpio (`l01-connected-final-analyze.log`).

Comando dirigido desde backend, tras importar Scope Test, fijar Continue y el opt-in:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify '-Dit.test=ConnectedL01ClientsIT,SuperAdminRecoveryBoundariesIT,SuperAdminRecoveryCliBackendIT,ManualOtpValidationHarnessIT' '-Djacoco.destFile=target/jacoco-connected-l01-10.exec' '-Djacoco.dataFile=target/jacoco-connected-l01-10.exec' *> target/l01-connected-tenth.log
```

Lanzador reproducible preparado: `scripts/Test-RutaFijaL01ConnectedClients.ps1`, build actual CRM y selección explícita de estas pruebas; su resultado posterior se registra debajo. No ejecutar este arnés simultáneamente con el validador OTP manual. No migra desarrollo/piloto, no usa correo real ni clave del titular, no despliega y no habilita offline.

### Reproducción final con lanzador y paquete local

- Se ejecutó **el lanzador completo** `scripts/Test-RutaFijaL01ConnectedClients.ps1`: build Angular actual correcto y **57 unitarias +6 IT**, cero fallos/errores/omitidas; `BUILD SUCCESS`. Log `backend/target/l01-connected-wrapper-final.log`, SHA **C9AED29D18338CC4333B9C9CFC73C0A2634B9D103B9A171893FDA18AE4B5FF60**. Repite la selección anterior; no suma casos únicos. Se justificó para comprobar el lanzador nuevo y el build servido por el recorrido, sin repetir toda la regresión 155 IT.
- `flutter analyze --no-pub` final limpio. Las 121 pruebas CRM y 74 Flutter anteriores se reutilizan sobre código de producto sin cambios; las etapas de UI con HTTP real son evidencia nueva y separada.
- Helper OTP: `-Prepare` de nuevo correcto sin DB/SMTP (`l01-manual-prepare-review.log`); ejecución predeterminada correcta (`l01-manual-static-review.log`): `STATIC_GUARDS=PASS`, `DB_CONNECTED=false`, `SMTP_ATTEMPTS=0`. **No `-Execute` ni envío externo en esta interacción**. No se da por corregido otro problema de credenciales ni se solicitó su valor.
- Paquete cliente actualizado `backend/target/ruta-fija-recovery-cli.jar`, SHA **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A**. El anterior 91b85744… se conserva localmente en target. Listado verifica solo las clases CLI; `jdeps -s` solo java.base/java.net.http; sin consola termina con código2 antes de leer una ruta/clave. El primer intento de empaquetar no encontró jar en PATH; se usó el ejecutable del JDK21 instalado, sin descargar ni cambiar PATH/configuración global.
- V26 y catálogo aprobado no cambiaron. final3 ZIP **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**, manifiesto **E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD**, nuevamente verificados iguales. Sin Git/GitHub, migración de desarrollo/piloto, despliegue o regeneración del snapshot.

Se solicitó al titular únicamente la entrada local indispensable para CI01 cuando ya terminaron los arneses que limpian la base. Hasta recibir su resultado saneado, CI01 integral permanece pendiente. La disponibilidad de custodia fue contestada y documentada: no existe todavía medio/copia/inscripción acreditados. La [matriz única](matriz-cierre-L01-revision-2026-10-14.md) concentra estado, implementación, intervención y siguiente acción: pendientes obligatorios **CI01 integral, CI03 y CI07**. L-01 EN_CURSO; L-02 no iniciada.
