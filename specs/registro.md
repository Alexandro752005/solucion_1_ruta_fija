# Ruta Fija — registro de descubrimiento v3.1

Corte: 2026-10-11, America/Lima. Base: registro v2.0 y las 53 respuestas entregadas en `ruta_fija_respuestas_2026-10-11-17-26-37.json`.

**53/53 respuestas integradas.** Las 17 propuestas consultadas están respondidas. Las decisiones previas que no cambiaste se conservan. Este documento y el JSON contienen la especificación vigente; las pruebas, la viabilidad técnica y la implementación no se declaran completadas.

## Lectura rápida

- Invitación: seis dígitos, destinatario identificado, un uso y máximo cinco días desde emisión.
- Oferta operativa: diez minutos o hasta el inicio previsto de la prestación, lo que ocurra primero; exige aceptación del conductor.
- Mantenimiento programado: bloquea el día completo en America/Lima y obliga a resolver todos los servicios que solapen ese día.
- Incidencias: no se permite reabrir una cerrada en el MVP.
- GPS: solo servicio iniciado, intento de envío cada treinta segundos, puntos detallados siete días; resumen en app y detalle mediante solicitud verificada.
- Corroboración de terminal: radio de 200 m, punto de máximo 2 minutos y precisión horizontal de 100 m o mejor; pendiente de calibración.
- Auditoría operativa: seis meses, acceso por rol/empresa, cambios por campo protegidos y ninguna contraseña, OTP o token.

## Acuerdos que permanecen vigentes

S/0 y sin nuevos desembolsos; sin RENIEC, MTC ni QR; solo SUPER_ADMIN, ADMIN y CONDUCTOR; una empresa y un rol activos por persona. No hay funcionamiento ni cola de acciones offline. Una oferta requiere unidad ya revisada, vinculada y vigente; el conductor debe aceptar. Finalizar servicio libera ocupación horaria, no el vínculo con la unidad. Placa por texto y conservación del identificador del vehículo y su historial. Los diseños de Stitch permanecen como referencia visual.

OTP por correo: cinco minutos de vigencia; tres envíos por persona en ventana móvil de sesenta minutos; dos fallos de validación y bloqueo de treinta minutos. La recuperación autorizada del correo del conductor queda como excepción confirmada al uso inicial, con esos mismos límites. El código de invitación tiene contador independiente.

## Parámetros confirmados y límites de la evidencia

| Parámetro | Decisión vigente | Referencia | Estado de cumplimiento |
|---|---|---|---|
| Invitación | Máximo 5 días; una persona, un uso | Q-102 / Q-103 | Especificado; no probado |
| Validación de invitación | 5 errores/15 min → bloqueo del flujo 30 min; origen 50/15 min → pausa 15 min en endpoint | POL-INV-SECURITY | Especificado; no probado |
| Oferta | Máximo 10 min; vencimiento anticipado al inicio de prestación | Q-053 | Especificado; no probado |
| Unidad empresarial | Vínculo con fin requerido; unidad propia puede tener fin abierto | POL-LINK-END | Especificado; no probado |
| Fechas de vínculo | Día final incluido en Lima; hora exacta prevalece | POL-LINK-DATES | Especificado; no probado |
| Mantenimiento | Día completo; retorno exige ADMIN | Q-036 / Q-037 / Q-038 | Especificado; no probado |
| GPS | 30 s durante servicio; retención 7 días | Q-075 / Q-077 / Q-079 | Objetivo/procedimiento por probar |
| Terminal | ≤200 m, ≤120 s de antigüedad, precisión ≤100 m | Q-106 | Objetivo por calibrar |
| Auditoría operativa | 6 meses | Q-086 | Política de purga por definir/probar |
| Concurrencia | 3 empresas, 30 conductores y 3 ADMIN en total, más 1 SUPER_ADMIN | Q-092 | Capacidad por probar |
| Latencia | p95 ≤2 s en servidor hasta confirmación durable | Q-093 | Rendimiento por probar |
| Interrupción ordinaria | Máximo objetivo 30 min en horario operativo | Q-094 | Horario y medición pendientes |
| RTO | 4 h tras pérdida total del servidor | Q-095 | Restauración por ensayar |
| RPO y respaldo | Pérdida máxima 1 h; respaldo horario fuera del servidor | Q-096 | Recursos y restauración por probar |
| Android | 10 o superior, provisional | Q-097 | Inventario/stack pendientes |
| CRM | Chrome y Edge estables del piloto; área útil desde 1280×720 | Q-098 | Versiones y compatibilidad por probar |

## Revisión de coherencia

No se detectó una contradicción principal entre estas respuestas y las restricciones confirmadas. Los valores distintos del formulario son elecciones válidas: sustituyen recomendaciones pendientes. Las recomendaciones anteriores de quince minutos de oferta, bloqueo solo por horas de taller y otros plazos de retención no se aplican como reglas activas.

El mantenimiento de día completo también afecta un servicio a las 17:00 aunque el taller esté programado de 09:00 a 11:00. Q-037 debe resolver ese conflicto antes de confirmar el preventivo. Una avería urgente bloquea de inmediato; no termina ficticiamente un servicio.

Descargar servicios propios (Q-082) no significa descargar directamente el trayecto GPS: Q-078 eligió resumen y solicitud verificada. Los siete días de puntos GPS no se amplían a seis meses por copiar coordenadas en auditoría. Una evidencia puntual retenida por incidencia necesita justificación y alcance acotado.

El indicador de cumplimiento usa cierres válidos de servicios iniciados; no cuenta una oferta aceptada, cancelación o interrupción como servicio completado. La declaración de cierre y la corroboración de llegada se muestran por separado. GPS compatible no acredita todo el recorrido ni determina fraude o culpa.

## Respuestas vigentes por módulo

Las respuestas son confirmadas por entrega expresa del archivo. Backend, CRM, app y criterios que acompañan las nuevas respuestas son derivaciones de especificación, con implementación no revisada. Se mantienen IDs y respuestas anteriores.

### MOD-ORG — Empresas, usuarios y permisos

#### Q-001 — Pertenencia múltiple

**Pregunta:** Si una persona trabaja con dos empresas, ¿debe poder pertenecer simultáneamente a ambas?

**Respuesta vigente:** Una persona no puede pertenecer simultáneamente a dos empresas de Ruta Fija. La cuenta conserva su identidad e historial; cualquier traslado sucesivo entre empresas requiere un proceso separado y no autoriza dos pertenencias activas.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-002 — Alta de empresa

**Pregunta:** ¿Quién tiene autoridad para crear y habilitar una nueva empresa en la plataforma?

**Respuesta vigente:** Solo SUPER_ADMIN crea las empresas y sus cuentas ADMIN. SUPER_ADMIN gestiona la plataforma y a los ADMIN; el ADMIN gestiona conductores, unidades, rutas, grupos y operaciones de su empresa. Crear la empresa no sustituye la activación descrita en Q-008.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-003 — Responsable de aprobación

**Pregunta:** ¿Qué rol debe tomar la decisión final de aprobar un conductor dentro de una empresa?

**Respuesta vigente:** El ADMIN activo de la empresa toma la decisión final de aprobar o rechazar la incorporación de sus conductores. Verificar el correo, introducir una invitación o asignar una unidad no equivale a aprobar al conductor.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-004 — Autorización sobre solicitud propia

**Pregunta:** ¿Puede un ADMIN crear o habilitar su propia cuenta administrativa?

**Respuesta vigente:** Un ADMIN no puede crear ni habilitar su propia cuenta ADMIN. Solo SUPER_ADMIN realiza ese alta. Esta respuesta trata la creación de cuentas ADMIN; la posibilidad de que un ADMIN también actúe como CONDUCTOR se consulta por separado en Q-101.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-005 — Revocación de permisos

**Pregunta:** Cuando se revoca un permiso a un usuario conectado, ¿desde qué momento debe dejar de poder ejecutar esa acción?

**Respuesta vigente:** La revocación tiene efecto desde su registro válido en el servidor. Toda autorización evaluada después de ese momento debe rechazar la acción revocada, aunque la sesión siga abierta o la pantalla conserve el botón. La revocación no deshace operaciones previamente completadas; las acciones en curso deben volver a validar el permiso antes de confirmar una modificación.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-006 — Aislamiento entre empresas

**Pregunta:** ¿Puede un administrador de empresa acceder a información operativa de otra empresa?

**Respuesta vigente:** Un ADMIN solo puede consultar y modificar la información operativa de su empresa. El servidor comprueba la empresa del usuario y del recurso en cada operación; cambiar un identificador o una URL no permite acceder a otra empresa.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-007 — Recuperación de administración

**Pregunta:** Si el único administrador deja la empresa, ¿cómo debe recuperarse la administración?

**Respuesta vigente:** SUPER_ADMIN crea y habilita un ADMIN sustituto, verifica la solicitud de recuperación administrativa y revoca los accesos del ADMIN saliente cuando corresponda. Se conserva la empresa y su historial; no se crea otra empresa para recuperar su administración.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-008 — Elegibilidad de empresa

**Pregunta:** ¿Cuál es el criterio que hace que una empresa quede habilitada para empezar a operar?

**Respuesta vigente:** La empresa queda habilitada para usar la plataforma cuando fue creada y autorizada por SUPER_ADMIN, dispone de un ADMIN activo y este completa la validación inicial y acepta expresamente los términos y condiciones. Se registra la versión aceptada, el ADMIN y la fecha de aceptación. Iniciar sesión por sí solo no demuestra aceptación ni habilita automáticamente conductores o unidades.

**Backend:** Autorizar rol, empresa y recurso; validar activación y aceptación de términos.

**CRM:** Mostrar altas y recuperación según SUPER_ADMIN o ADMIN.

**App:** Mantener cuenta y empresa vigentes sin selector entre pertenencias simultáneas.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-101 — Compatibilidad entre los tres roles

**Pregunta:** ¿Puede una misma persona ejercer ADMIN y CONDUCTOR en su empresa o cada cuenta debe tener un único rol?

**Respuesta vigente:** En el MVP cada persona tendrá un solo rol activo. Un cambio autorizado sustituye el anterior y queda auditado; crear otra cuenta no permitirá eludir esta regla.

**Backend:** Vincular la identidad estable de la persona con un único rol activo; un cambio autorizado sustituye el rol anterior, revoca permisos previos y se audita. Comprobar también cuentas duplicadas, sin inferir conexión a RENIEC.

**CRM:** SUPER_ADMIN gestiona cambios de rol autorizados; no mostrar un selector ADMIN/CONDUCTOR a la misma persona.

**App:** Mostrar la superficie CONDUCTOR solo cuando ese sea el rol vigente; refrescar autorización al cambiar de rol.

**Trazabilidad:** `D-V3-Q-101` → `BR-V3-Q-101` → `AC-V3-Q-101-01/02`. Elección: Un único rol por persona.

**Criterio esperado:** Un cambio autorizado deja un solo rol activo y registra el anterior.

**Caso que debe impedirse o tratarse:** Una segunda cuenta de la misma persona no consigue un segundo rol activo.

#### Q-105 — Traslado empresarial sucesivo

**Pregunta:** ¿Qué condiciones deben cumplirse para desvincular a un conductor de una empresa e incorporarlo posteriormente a otra?

**Respuesta vigente:** ADMIN autoriza la salida cuando no hay servicio en curso, las asignaciones futuras están canceladas o reasignadas y se resolvió la devolución o desvinculación de la unidad. Después, otra empresa puede invitar la misma cuenta y revisar una nueva solicitud; el historial anterior se conserva.

**Backend:** Cerrar la membresía de origen de manera autorizada solo tras resolver servicio en curso, futuros compromisos y unidad. La cuenta y el historial persisten; la nueva empresa necesita invitación y revisión propias. No copiar automáticamente aprobación o unidad.

**CRM:** Mostrar los bloqueos de salida, resolver compromisos y registrar devolución o desvinculación antes de autorizarla.

**App:** Solicitar o consultar salida y después incorporarse con la misma cuenta mediante nueva invitación.

**Trazabilidad:** `D-V3-Q-105` → `BR-V3-Q-105` → `AC-V3-Q-105-01/02`. Elección: Salida autorizada y nueva invitación.

**Criterio esperado:** Una salida resuelta permite nueva solicitud en otra empresa conservando historial.

**Caso que debe impedirse o tratarse:** Una solicitud de traslado no crea dos membresías activas ni omite revisión del destino.

### MOD-AUTH — Login e incorporación

#### Q-009 — Propósito OTP

**Pregunta:** ¿Para qué propósito concreto es indispensable usar OTP en la primera versión?

**Respuesta vigente:** El uso ordinario de OTP en el MVP es verificar el control del correo registrado antes del primer acceso efectivo. El OTP de correo no sustituye la revisión de identidad y documentos del conductor por el ADMIN. La recuperación asistida de Q-015 propone una excepción para verificar un correo nuevo, sin convertir OTP en un requisito de cada inicio de sesión.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-010 — Disparador OTP

**Pregunta:** Para ese propósito OTP, ¿qué hecho debe disparar el envío del código?

**Respuesta vigente:** El primer intento de acceso de una cuenta cuyo correo aún no está verificado dispara la verificación inicial. Los siguientes intentos mientras siga sin verificar reutilizan el proceso pendiente y respetan los límites de envío, vencimiento e intentos. Una cuenta ya verificada no recibe OTP en cada inicio de sesión ordinario.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-011 — Canal OTP

**Pregunta:** ¿Cuál debe ser el canal principal para recibir ese OTP?

**Respuesta vigente:** El canal principal de OTP es el correo electrónico registrado. El MVP no presupone SMS, WhatsApp de pago ni integraciones públicas. Debe comprobarse que el mecanismo de correo elegido funciona dentro de los recursos disponibles y sin nuevos desembolsos antes de depender de él en una demo o despliegue.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-012 — Vigencia OTP

**Pregunta:** ¿Después de cuánto tiempo debe vencer un OTP sin utilizar?

**Respuesta vigente:** Cada OTP vence cinco minutos después de su emisión, se utiliza una sola vez y se valida en el servidor. Un reenvío invalida el código anterior y no reinicia contadores de envíos ni elimina un bloqueo de validación.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-013 — Límite de envío OTP

**Pregunta:** ¿Cuántos códigos OTP puede solicitar una persona en una hora antes de limitar nuevos envíos?

**Respuesta vigente:** Se permiten como máximo tres envíos OTP por persona en una ventana móvil de sesenta minutos, incluyendo el inicial y los reenvíos. Al alcanzar el límite se informa cuándo podrá solicitar otro. Cambiar el correo propuesto o reenviar no debe crear un contador nuevo para la misma cuenta. Se mantiene el presupuesto de S/0.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-014 — Límite de validación OTP

**Pregunta:** ¿Cuántos intentos fallidos de un OTP deben permitirse antes de bloquear su uso?

**Respuesta vigente:** Se permiten dos intentos fallidos consecutivos de validación OTP por persona. Al segundo fallo se invalida el código y se bloquea la validación durante treinta minutos. Solicitar otro código o cambiar el correo no reinicia el contador ni levanta el bloqueo. Cumplida la espera podrá solicitar un nuevo código si dispone de cupo dentro del límite de tres envíos en sesenta minutos. El contador de envíos y el de validaciones fallidas son independientes.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-015 — Recuperación de canal

**Pregunta:** Si el conductor pierde acceso al canal donde recibe el OTP, ¿qué vía debe permitirle recuperar el acceso?

**Respuesta vigente:** El ADMIN de la empresa verifica al conductor con su expediente y evidencia independiente obtenida por un contacto previo seguro o en persona. Autoriza recuperar la misma cuenta; el correo nuevo se verifica por OTP con los límites vigentes. Si también se perdió la contraseña, se usa un enlace temporal de un solo uso. Se revocan sesiones y se audita el cambio; ADMIN no conoce la contraseña.

**Backend:** Autorizar solo al ADMIN de la empresa para recuperar la misma cuenta CONDUCTOR tras verificación interna independiente. Mantener el correo nuevo pendiente hasta OTP limitado; emitir token temporal de un uso si falta contraseña y revocar sesiones al completar. No registrar secretos ni devolver contraseñas.

**CRM:** Registrar comprobación, evidencia, motivo y autorización; no aprobar solo con DNI escrito o un correo nuevo.

**App:** Verificar el correo nuevo y restablecer contraseña cuando corresponda; el cambio incompleto no sustituye el canal anterior.

**Trazabilidad:** `D-V3-Q-015` → `BR-V3-Q-015` → `AC-V3-Q-015-01/02`. Elección: Recuperación asistida por ADMIN.

**Criterio esperado:** Una recuperación autorizada verifica el nuevo correo y conserva el identificador de cuenta.

**Caso que debe impedirse o tratarse:** DNI conocido o mensaje desde correo nuevo, por sí solos, no permiten sustituir correo ni ver la contraseña.

#### Q-016 — Entrada a incorporación

**Pregunta:** ¿La incorporación a una empresa debe permitirse por búsqueda pública o sólo mediante invitación?

**Respuesta vigente:** La incorporación a una empresa requiere una invitación válida emitida por el ADMIN de esa empresa. No se habilita la búsqueda pública para postular libremente. La invitación permite comenzar la incorporación, pero no reemplaza la revisión documental ni la aprobación.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-017 — Medio de invitación

**Pregunta:** ¿Qué formato de código debe usar el conductor para aceptar una invitación?

**Respuesta vigente:** El ADMIN genera y comparte un código de invitación de exactamente seis dígitos numéricos mediante un canal ya disponible sin gasto adicional. El conductor lo introduce como texto; se conservan los ceros iniciales. Es un código de invitación, no un descuento ni un OTP. No se requiere QR ni enlace de invitación. La modalidad personal o compartida y su vigencia se precisan en Q-102 y Q-103.

**Backend:** Validar invitación, OTP, límites, cupos y recuperación en operaciones controladas.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-018 — Consumo del cupo

**Pregunta:** ¿En qué momento del proceso debe consumirse un cupo de invitación?

**Respuesta vigente:** Se consume un cupo cuando la identidad preliminar de un conductor, con correo verificado, acepta un código válido y el servidor crea correctamente una solicitud PENDING_REVIEW. Escribir, consultar o validar el código sin completar esa aceptación no consume cupo. Aceptación, comprobación de cupos y creación de la solicitud forman una sola operación; repetir la misma operación devuelve su solicitud sin consumir otro cupo. La identidad y la solicitud de incorporación permanecen separadas de AppUser y Driver: la verificación OTP y la contraseña preliminar no crean acceso operativo. ADMIN revisa el expediente y decide aprobar o rechazar; después de aprobar la documentación, ADMIN elige un grupo activo y el sistema crea AppUser y Driver vinculados. Si no se elige grupo, la solicitud puede quedar aprobada sin acceso operativo hasta completar esa asignación. Se respetan la pertenencia única y los periodos de espera por rechazo.

**Backend:** Validar invitación, OTP, límites y cupos en operaciones controladas. Consumir la invitación y crear la solicitud pendiente en una transacción idempotente. La aprobación y la creación de cuenta/driver ocurren después de la revisión del ADMIN; requieren grupo activo y no habilitan operaciones mientras falten requisitos documentales.

**CRM:** ADMIN emite códigos de invitación y tramita recuperación autorizada.

**App:** Mostrar código de seis dígitos, OTP, vencimiento, bloqueo y recuperación sin QR.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-102 — Vigencia de invitación

**Pregunta:** ¿Cuánto tiempo debe permanecer válido un código de invitación de seis dígitos sin aceptar?

**Respuesta vigente:** Como máximo creo que debe permanecer válido durante 5 días, no más, pues es un código y se supone que el uso del mismo debe ser inmediato.

**Backend:** Calcular vencimiento con reloj del servidor y una duración máxima de 5 días continuos desde emisión. No aceptar en o después del vencimiento ni extender por consultar el código. Conservar revocación y uso único.

**CRM:** Mostrar creación, vencimiento y revocación; una nueva invitación no prolonga silenciosamente el código anterior.

**App:** Mostrar invitación vencida y solicitar una nueva a ADMIN.

**Trazabilidad:** `D-V3-Q-102` → `BR-V3-Q-102` → `AC-V3-Q-102-01/02`. Elección: Opción propia.

**Criterio esperado:** Una invitación aún vigente puede consumirse antes de cumplir su plazo máximo de 120 horas.

**Caso que debe impedirse o tratarse:** Una invitación con 5 días cumplidos o revocada no permite incorporar al conductor.

#### Q-103 — Invitación personal o compartida

**Pregunta:** ¿Cada código de invitación corresponde a una persona y un solo uso, o un código puede admitir varios conductores hasta agotar sus cupos?

**Respuesta vigente:** ADMIN emite una invitación para el correo identificado de un conductor. Solo esa cuenta con correo verificado puede consumirla una vez. Una aceptación repetida de la misma operación no consume otro cupo.

**Backend:** Vincular invitación con correo de un destinatario y consumirla atómicamente una vez, solo por su cuenta autenticada y verificada. Un reintento de la misma aceptación devuelve el resultado existente sin otro consumo.

**CRM:** Exigir destinatario identificado al crear la invitación y mostrar su uso único.

**App:** Usar cuenta con el correo destinatario verificado; aceptar invitación no equivale a aprobar documentación.

**Trazabilidad:** `D-V3-Q-103` → `BR-V3-Q-103` → `AC-V3-Q-103-01/02`. Elección: Una persona y un único uso.

**Criterio esperado:** El destinatario correcto consume una invitación una sola vez y genera su solicitud.

**Caso que debe impedirse o tratarse:** Otra cuenta o dos consumos concurrentes no reciben el mismo cupo.

#### OPEN-SUPERADMIN-RECOVERY — Recuperación del titular SUPER_ADMIN

**Pregunta:** ¿Cómo debe recuperar su cuenta el titular SUPER_ADMIN si pierde su correo y su contraseña?

**Respuesta vigente:** El titular guarda un código de emergencia robusto, protegido y de un solo uso. La recuperación exige además una comprobación independiente previamente definida de su control del proyecto. Se verifica el nuevo canal, se revocan sesiones y se audita. Los ADMIN subordinados no autorizan el cambio.

**Backend:** Preparar recuperación del titular con código de emergencia protegido de un uso y comprobación independiente de control del proyecto. Invalidar el código consumido, sesiones y credenciales temporales; impedir autorización por ADMIN subordinado. La evidencia y la custodia exactas siguen pendientes de definir.

**CRM:** Ofrecer al titular una vía de recuperación separada de la gestión de conductores; no mostrar autorización de SUPER_ADMIN a ADMIN.

**App:** No añade una función del CONDUCTOR.

**Trazabilidad:** `D-V3-OPEN-SUPERADMIN-RECOVERY` → `BR-V3-OPEN-SUPERADMIN-RECOVERY` → `AC-V3-OPEN-SUPERADMIN-RECOVERY-01/02`. Elección: Código de emergencia y control del titular.

**Criterio esperado:** El procedimiento diseñado exige código de emergencia y evidencia independiente, con auditoría.

**Caso que debe impedirse o tratarse:** Un ADMIN subordinado o un código de emergencia aislado no autorizan la recuperación.

#### OPEN-OTP-PROVIDER — Viabilidad del correo sin desembolsos

**Pregunta:** ¿Qué estrategia usaremos para comprobar un canal de correo OTP viable con S/0?

**Respuesta vigente:** Evaluaremos un servicio de correo cuya cuota sin pago permita el piloto, sin depender de créditos temporales ni compras de dominio. Antes de habilitar registro se probarán envío, recepción, límites y fallos. Al agotarse la cuota se pausarán nuevos envíos y se mostrará un error claro; no se simulará verificación.

**Backend:** Seleccionar y probar un canal transaccional sin nuevos pagos, créditos temporales ni compra de dominio. Controlar cuotas y fallos sin simular envío o verificación; conservar los límites OTP confirmados.

**CRM:** Mostrar fallos y suspensión de envío en administración; no certificar entrega si el proveedor la rechazó.

**App:** Informar envío fallido, límite o espera con un mensaje accionable; nunca aprobar el correo sin verificación real.

**Trazabilidad:** `D-V3-OPEN-OTP-PROVIDER` → `BR-V3-OPEN-OTP-PROVIDER` → `AC-V3-OPEN-OTP-PROVIDER-01/02`. Elección: Correo transaccional sin pago, verificado.

**Criterio esperado:** La prueba de canal registra envío y recepción reales dentro de cuota sin pago.

**Caso que debe impedirse o tratarse:** Cuota agotada o fallo de entrega no dejan el correo como verificado.

#### POL-INV-SECURITY — Protección de invitaciones

**Pregunta:** ¿Qué límite de intentos fallidos protegerá el código de invitación de seis dígitos?

**Respuesta vigente:** Se valida desde una cuenta autenticada: 5 códigos incorrectos en 15 minutos bloquean la validación de invitaciones de esa cuenta durante 30 minutos. El origen tiene un freno adicional de 50 fallos en 15 minutos para ese endpoint, con pausa de 15 minutos; no se bloquean otras acciones ni todas las cuentas de una IP compartida.

**Backend:** Aplicar contador de códigos incorrectos por cuenta: 5 en 15 minutos, bloqueo de ese flujo 30 minutos. Añadir freno del endpoint por origen: 50 fallos en 15 minutos, pausa de 15 minutos. No mezclar OTP ni bloquear globalmente cuentas por IP; no revelar datos personales.

**CRM:** Mostrar el límite de invitación y la espera, sin bloquear la operación ajena a ese flujo.

**App:** Distinguir código incorrecto, vencido y límite de validación; no confundirlo con el OTP.

**Trazabilidad:** `D-V3-POL-INV-SECURITY` → `BR-V3-POL-INV-SECURITY` → `AC-V3-POL-INV-SECURITY-01/02`. Elección: 5 errores en 15 minutos.

**Criterio esperado:** El quinto código incorrecto dentro de 15 minutos activa el bloqueo de validación de invitaciones por 30 minutos.

**Caso que debe impedirse o tratarse:** El bloqueo de invitación no reinicia ni consume intentos OTP y no suspende globalmente todas las cuentas de una IP compartida.

### MOD-DRV — Conductores y membresías

#### Q-019 — Vencimiento durante operación

**Pregunta:** Si una licencia vence durante un servicio en curso, ¿qué efecto debe tener sobre la posibilidad de seguir operando?

**Respuesta vigente:** La licencia debe permanecer vigente durante toda la conducción. Si pierde vigencia durante un servicio, el conductor pierde su habilitación operativa y no puede continuar conduciendo con esa licencia. Se registra la incidencia, se avisa al ADMIN y se gestiona un relevo seguro. Se conserva la aprobación histórica y se bloquean nuevas operaciones hasta validar la renovación. Antes de reservar, aceptar e iniciar se comprueba que la vigencia documental cubra el periodo previsto. La revisión es interna, sin conexión a MTC.

**Backend:** Separar incorporación, revisión y elegibilidad; conservar versiones y motivos.

**CRM:** ADMIN revisa, observa, rechaza o aprueba su empresa.

**App:** Mostrar observaciones, plazo, reenvío y habilitación sin aprobación automática.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-020 — Repostulación tras rechazo

**Pregunta:** Si se rechaza una solicitud, ¿bajo qué condición puede el conductor volver a postular a esa empresa?

**Respuesta vigente:** Cada devolución del ADMIN después de revisar cuenta una ronda, aunque observe varios documentos. Si ADMIN rechaza por reiteración después de 4 o más rondas, se esperan 7 días desde el rechazo. Reenviar no suma una ronda. Luego se exige nueva invitación, misma cuenta, historial conservado y una sola solicitud pendiente por empresa.

**Backend:** Contar devoluciones de ADMIN, no campos ni reenvíos. Solo un rechazo expreso por reiteración tras al menos 4 rondas activa 7 días desde rejectedAt. Luego exigir nueva invitación, misma cuenta, historial y una sola solicitud pendiente por empresa.

**CRM:** Mostrar ronda, observaciones y motivo de rechazo; no rechazar automáticamente al llegar a cuatro.

**App:** Corregir la solicitud abierta; si fue rechazada por reiteración, mostrar fecha de nueva postulación y necesidad de invitación.

**Trazabilidad:** `D-V3-Q-020` → `BR-V3-Q-020` → `AC-V3-Q-020-01/02`. Elección: Contar rondas de devolución.

**Criterio esperado:** Cuatro devoluciones más rechazo por reiteración producen siete días de espera desde el rechazo.

**Caso que debe impedirse o tratarse:** Varios documentos en una devolución o un reenvío no aumentan el contador de rondas por sí solos.

#### Q-021 — Plazo de subsanación

**Pregunta:** Cuando una solicitud recibe observaciones, ¿cuánto tiempo tiene el conductor para subsanarlas?

**Respuesta vigente:** El conductor dispone de un plazo objetivo de cuarenta y ocho horas corridas desde que las observaciones quedan disponibles en la app. El ADMIN puede ampliarlo registrando el cambio. Vencido el plazo, la solicitud se marca fuera de plazo, sin rechazo automático ni pérdida de documentos. Se puede corregir mientras siga abierta. El reenvío conserva la misma solicitud y las versiones documentales, y la devuelve a revisión sin aprobar automáticamente al conductor.

**Backend:** Separar incorporación, revisión y elegibilidad; conservar versiones y motivos.

**CRM:** ADMIN revisa, observa, rechaza o aprueba su empresa.

**App:** Mostrar observaciones, plazo, reenvío y habilitación sin aprobación automática.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-022 — Causa de suspensión

**Pregunta:** ¿Qué situación debe permitir suspender a un conductor ya aprobado?

**Respuesta vigente:** La pérdida de vigencia de la licencia bloquea automáticamente la habilitación operativa, conservando la aprobación histórica. El ADMIN puede suspender operativamente al conductor cuando compruebe documentos falsos o faltas graves al reglamento interno, registrando motivo y evidencia. La suspensión y la habilitación operativa se representan separadas del estado principal de incorporación; no se añade por esta razón un sexto estado principal ni se altera la cuenta para impedirle consultar y subsanar su expediente.

**Backend:** Separar incorporación, revisión y elegibilidad; conservar versiones y motivos.

**CRM:** ADMIN revisa, observa, rechaza o aprueba su empresa.

**App:** Mostrar observaciones, plazo, reenvío y habilitación sin aprobación automática.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-023 — Efecto de suspensión

**Pregunta:** Si se suspende a un conductor con asignaciones futuras, ¿qué debe suceder con ellas?

**Respuesta vigente:** Se cancelan las ofertas pendientes y se dejan sin efecto las asignaciones futuras, liberando ocupaciones horarias. Los servicios quedan programados, pendientes de reasignación e inicio bloqueado. ADMIN elige un reemplazo habilitado y obtiene su aceptación. El servicio en curso se atiende como incidencia con relevo o interrupción segura.

**Backend:** Al suspender, cancelar ofertas pendientes e invalidar asignaciones futuras de forma consistente. Liberar ocupaciones, mantener servicios programados con reasignación e inicio bloqueado y conservar vínculo de unidad. Servicio iniciado requiere incidencia y manejo seguro, sin falso éxito.

**CRM:** Listar servicios afectados y obtener nueva aceptación de un reemplazo elegible; distinguir suspensión de desvinculación.

**App:** Retirar acciones de inicio de asignaciones invalidadas y explicar el motivo; conservar historial.

**Trazabilidad:** `D-V3-Q-023` → `BR-V3-Q-023` → `AC-V3-Q-023-01/02`. Elección: Invalidar las asignaciones futuras.

**Criterio esperado:** Suspender invalida futuros compromisos y deja los servicios pendientes de reasignación.

**Caso que debe impedirse o tratarse:** Suspender no elimina el vínculo de unidad ni marca como completado un servicio en curso.

#### Q-024 — Corrección de identidad

**Pregunta:** ¿Quién puede corregir la identidad de un conductor después de que fue aprobada?

**Respuesta vigente:** El ADMIN de la empresa puede tramitar una corrección de identidad mediante revisión interna y justificación documentada. Se conserva el identificador de la cuenta y del conductor, el dato anterior, el corregido, la evidencia, el autor y la fecha. Se distinguen una corrección material y el intento de sustituir a una persona por otra; este último no se permite mediante edición. Corregir un dato no exige un nuevo estado principal; si afecta documentos aprobados, se vuelve a revisar la parte afectada y se evalúa su habilitación operativa.

**Backend:** Separar incorporación, revisión y elegibilidad; conservar versiones y motivos.

**CRM:** ADMIN revisa, observa, rechaza o aprueba su empresa.

**App:** Mostrar observaciones, plazo, reenvío y habilitación sin aprobación automática.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### OPEN-REQUIRED-DOCUMENTS — Catálogo documental de incorporación

**Pregunta:** ¿Cómo cerraremos el catálogo concreto de documentos exigidos para conductor y unidad?

**Respuesta vigente:** Primero identificaremos la modalidad real del piloto y cerraremos una lista única de documentos del conductor y de la unidad, con campos, vigencia, excepciones justificadas y revisión interna. No se habilita operación mientras falte un documento obligatorio de esa lista.

**Backend:** Versionar un catálogo único de conductor y unidad para la modalidad real del piloto. El catálogo concreto aún no está cerrado: identificar documentos, campos, vigencias y excepciones; no habilitar operación con obligatorios faltantes.

**CRM:** No presentar un expediente como completo sin conocer y aplicar la lista obligatoria de la modalidad del piloto.

**App:** Mostrar requisitos concretos del catálogo aprobado; no inventar campos ni pedir documentación sin finalidad definida.

**Trazabilidad:** `D-V3-OPEN-REQUIRED-DOCUMENTS` → `BR-V3-OPEN-REQUIRED-DOCUMENTS` → `AC-V3-OPEN-REQUIRED-DOCUMENTS-01/02`. Elección: Catálogo único para una modalidad piloto.

**Criterio esperado:** Cuando exista el catálogo aprobado, todos sus obligatorios son revisados antes de habilitar.

**Caso que debe impedirse o tratarse:** Seleccionar el método de catálogo no se considera aprobación de una lista documental que todavía no existe.

### MOD-GRP — Grupos

#### Q-025 — Composición de grupo

**Pregunta:** ¿Qué recursos debe representar un grupo para la operación: conductores, vehículos o ambos?

**Respuesta vigente:** Un grupo organiza conductores, unidades o ambos dentro de la misma empresa. Pertenecer al grupo no crea un vínculo conductor-unidad, no habilita documentos y no evita las validaciones individuales de disponibilidad, horarios y permisos.

**Backend:** Validar pertenencia empresarial, membresías y compromisos antes de eliminar.

**CRM:** Mostrar recursos agrupados y motivos que bloquean eliminar.

**App:** El grupo no concede nuevos permisos.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-026 — Cardinalidad de grupos

**Pregunta:** ¿Un conductor puede pertenecer a más de un grupo de la misma empresa?

**Respuesta vigente:** El conductor puede integrar varios grupos de su empresa. Se impiden membresías duplicadas y se comprueban sus conflictos y los de su unidad entre todos los grupos.

**Backend:** Permitir varias membresías de grupo de la misma empresa con unicidad conductor-grupo. Comprobar conflictos de recursos entre todos sus grupos, independientemente del grupo de origen de la oferta.

**CRM:** Gestionar varias pertenencias y mostrar grupos sin duplicar al conductor en la flota.

**App:** Consultar los comunicados y servicios autorizados por sus grupos de una única empresa.

**Trazabilidad:** `D-V3-Q-026` → `BR-V3-Q-026` → `AC-V3-Q-026-01/02`. Elección: Varios grupos de la misma empresa.

**Criterio esperado:** El conductor pertenece a dos grupos de su empresa sin duplicar la persona.

**Caso que debe impedirse o tratarse:** Pertenecer a grupos distintos no permite dos servicios superpuestos para la misma persona o unidad.

#### Q-027 — Efecto de grupo en permisos

**Pregunta:** ¿Pertenecer a un grupo debe cambiar los permisos del conductor?

**Respuesta vigente:** Pertenecer a un grupo no cambia los permisos del conductor. Los permisos dependen del rol autorizado y su vigencia; los únicos roles son SUPER_ADMIN, ADMIN y CONDUCTOR.

**Backend:** Validar pertenencia empresarial, membresías y compromisos antes de eliminar.

**CRM:** Mostrar recursos agrupados y motivos que bloquean eliminar.

**App:** El grupo no concede nuevos permisos.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-028 — Eliminación de grupo en uso

**Pregunta:** ¿Cuándo puede eliminarse un grupo que tiene asignaciones pendientes?

**Respuesta vigente:** Se impide eliminar un grupo mientras tenga asignaciones o servicios vinculados pendientes o en ejecución, incluidos servicios que quedaron pendientes de reasignación. Deben completarse los compromisos pendientes antes de permitir su eliminación. El CRM muestra los compromisos que la bloquean y el servidor vuelve a comprobarlos al confirmar. Al eliminarlo se conserva la referencia histórica de los servicios y se impide su uso nuevo, sin producir estados genéricos de error.

**Backend:** Validar pertenencia empresarial, membresías y compromisos antes de eliminar.

**CRM:** Mostrar recursos agrupados y motivos que bloquean eliminar.

**App:** El grupo no concede nuevos permisos.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

### MOD-VEH — Vehículos y flota

#### Q-029 — Exclusividad conductor/unidad

**Pregunta:** ¿Puede una unidad estar vinculada simultáneamente a dos conductores dentro de la empresa?

**Respuesta vigente:** Una unidad no puede mantener vínculos autorizados con dos conductores para periodos superpuestos. Puede tener vínculos sucesivos con distintos conductores si no se solapan sus vigencias ni existe una operación en curso incompatible. Se comprueba la exclusividad en el servidor, incluso ante solicitudes simultáneas.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-030 — Unidad entre empresas

**Pregunta:** ¿Puede una misma unidad operar para más de una empresa de la plataforma?

**Respuesta vigente:** Una unidad no puede operar simultáneamente para dos empresas. Su identidad interna y el historial se conservan ante un eventual traslado; el proceso de transferencia no habilita pertenencias operativas superpuestas.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-031 — Elegibilidad documental de unidad

**Pregunta:** ¿Qué condiciones habilitan documental y operativamente una unidad y cuáles solo afectan su disponibilidad horaria?

**Respuesta vigente:** ADMIN revisa documentos y el servidor deriva la elegibilidad con su vigencia y los bloqueos. Para cada oferta se comprueban además vínculo y horario. La ocupación de un servicio solo afecta disponibilidad, sin invalidar la revisión documental.

**Backend:** Separar conformidad documental, bloqueo operativo y ocupación. Derivar elegibilidad por vigencia y bloqueos; revalidar vínculo y horario al emitir y aceptar oferta.

**CRM:** Mostrar razones de ineligibilidad o de ocupación sin cambiar documentos por estar en servicio.

**App:** Consultar unidad y habilitación sin confundir ocupación horaria con rechazo de documentos.

**Trazabilidad:** `D-V3-Q-031` → `BR-V3-Q-031` → `AC-V3-Q-031-01/02`. Elección: Documentos y disponibilidad separados.

**Criterio esperado:** Una unidad ocupada conserva conformidad documental y resulta no disponible para otro intervalo incompatible.

**Caso que debe impedirse o tratarse:** Documento vencido o bloqueo de mantenimiento impiden una oferta aunque el calendario de servicios esté vacío.

#### Q-032 — Aprobación de unidad propia

**Pregunta:** Si una unidad propia se incorpora a una empresa, ¿quién debe validar esa incorporación?

**Respuesta vigente:** El ADMIN de la empresa revisa y valida la incorporación de una unidad propia, sus datos y la documentación exigida. La validación no asigna automáticamente rutas ni servicios y debe respetar la pertenencia empresarial y la exclusividad de los vínculos.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-033 — Permiso de sustitución de unidad

**Pregunta:** Si una unidad asignada deja de estar disponible, ¿quién puede sustituirla?

**Respuesta vigente:** El ADMIN autoriza la sustitución de unidad y la desvinculación. Debe registrar motivo, vigencias y las asignaciones afectadas. La nueva unidad debe quedar revisada y vinculada antes de ofrecer el servicio al conductor. Una sustitución que cambie los datos de una asignación pendiente o aceptada exige anular su versión anterior y solicitar aceptación de la nueva; no se modifica silenciosamente lo que el conductor aceptó.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-034 — Duración del vínculo de unidad

**Pregunta:** ¿Qué vigencia tiene el vínculo conductor-unidad y qué ocurre con él cuando termina un servicio?

**Respuesta vigente:** El vínculo conductor-unidad tiene una vigencia autorizada por el ADMIN; puede fijarse un periodo de inicio y fin, por ejemplo del 10/10/2026 al 10/11/2026. Finalizar o cancelar un servicio libera únicamente su ocupación horaria y no rompe ese vínculo. Dentro de su vigencia el conductor puede recibir nuevos servicios compatibles con la misma unidad. El vínculo finaliza por su vencimiento registrado, cuando tenga fin programado, o por desvinculación autorizada; no se reserva un servicio cuya conducción prevista quede fuera de su vigencia.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-035 — Corrección de identificador de unidad

**Pregunta:** ¿Cómo se ingresa una placa y cómo se conserva la identidad e historial del vehículo si cambia?

**Respuesta vigente:** La unidad conserva un identificador interno estable aunque cambie su placa. El ADMIN ingresa y corrige la placa mediante texto, conservando placas anteriores, vigencias, motivo y autor del cambio. Los servicios anteriores conservan la identificación histórica que les correspondía. No se crea una nueva unidad por cambiar la placa y no se exige fotografía, cámara ni OCR para capturarla.

**Backend:** Conservar ID y placas; validar vigencias, exclusividad y vínculo.

**CRM:** ADMIN ingresa placa como texto y administra periodos de vínculo.

**App:** Mostrar unidad y vigencia; finalizar servicio no desvincula.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-104 — Vencimiento de vínculo durante ejecución

**Pregunta:** Si el servicio se retrasa y vence la vinculación conductor-unidad durante su ejecución, ¿qué procedimiento debe seguir ADMIN para cerrar, renovar o relevar ese vínculo?

**Respuesta vigente:** Se alerta antes del vencimiento. ADMIN puede renovar la misma vinculación si no hay conflicto y siguen vigentes los documentos; registra la nueva autorización sin alterar el periodo histórico. Si no puede renovarla, coordina interrupción segura o relevo con nueva aceptación. Un vencimiento inesperado genera incidencia, bloquea nuevas ofertas y exige resolver el tramo afectado.

**Backend:** Alertar vencimiento próximo; renovación de ADMIN requiere documentos vigentes y ausencia de conflictos, conserva autorización anterior y no extiende por retraso automáticamente. Vencimiento inesperado genera incidencia, bloquea nuevas ofertas y exige resolver el tramo, sin liberar físicamente la unidad.

**CRM:** Renovar con controles o coordinar relevo/interrupción seguros y aceptación del reemplazo.

**App:** Mostrar aviso y decisión de ADMIN; no renovar su unidad por finalizar ni asumir un relevo sin aceptación.

**Trazabilidad:** `D-V3-Q-104` → `BR-V3-Q-104` → `AC-V3-Q-104-01/02`. Elección: Renovación de ADMIN o relevo seguro.

**Criterio esperado:** Una renovación válida conserva periodo anterior y registra nueva autorización antes de continuar bajo esa vigencia.

**Caso que debe impedirse o tratarse:** El retraso por sí solo no prorroga el vínculo ni concede la unidad a otro conductor mientras sigue físicamente en servicio.

#### POL-LINK-DATES — Límite temporal de vínculos

**Pregunta:** ¿Cómo se interpreta el último día de una vinculación de unidad cuando ADMIN no indica una hora?

**Respuesta vigente:** Con fechas sin hora, el inicio incluye su día y el vínculo sigue vigente durante todo el día final en America/Lima. Se almacena un fin exclusivo al comienzo del día siguiente. Si se indica una hora exacta, se respeta ese instante.

**Backend:** Usar día inicial y final incluidos para fechas sin hora en America/Lima; representar fin exclusivo al comienzo del día siguiente. Para hora exacta, respetar el instante indicado y conservar su precisión.

**CRM:** Mostrar fecha final incluida o la hora exacta, sin interpretar ambas como el mismo límite.

**App:** Consultar vigencia de unidad con fecha final clara.

**Trazabilidad:** `D-V3-POL-LINK-DATES` → `BR-V3-POL-LINK-DATES` → `AC-V3-POL-LINK-DATES-01/02`. Elección: Incluir todo el día final.

**Criterio esperado:** Un vínculo hasta 10/11/2026 sin hora incluye ese día completo y termina al comenzar 11/11/2026 en Lima.

**Caso que debe impedirse o tratarse:** Una hora exacta de fin no se extiende automáticamente hasta medianoche.

#### POL-LINK-END — Fin programado de vinculación

**Pregunta:** ¿En qué casos puede existir una vinculación de unidad sin fecha de fin programada?

**Respuesta vigente:** La vinculación de una unidad empresarial exige fecha final. Una unidad propia puede quedar vinculada sin fin programado hasta desvinculación autorizada. Toda oferta debe caber en la vigencia aplicable y pasar los controles de elegibilidad.

**Backend:** Exigir fin programado para unidad empresarial; permitir fin nulo en unidad propia hasta desvinculación autorizada. Toda prestación debe caber en la vigencia y pasar controles; completar servicio no rompe vínculo.

**CRM:** Validar fin según propiedad de la unidad y facilitar revisión de vínculos propios abiertos.

**App:** Mostrar periodo vigente o vinculación propia sin fin programado, sin prometer disponibilidad si hay otros bloqueos.

**Trazabilidad:** `D-V3-POL-LINK-END` → `BR-V3-POL-LINK-END` → `AC-V3-POL-LINK-END-01/02`. Elección: Fin en unidad empresarial; propio opcional.

**Criterio esperado:** Una unidad propia puede tener vínculo sin fin programado; una empresarial debe indicar fin.

**Caso que debe impedirse o tratarse:** Una unidad empresarial sin fecha final o una oferta fuera de vigencia no se autoriza.

### MOD-MNT — Mantenimiento

#### Q-036 — Bloqueo por mantenimiento

**Pregunta:** ¿Qué condición de mantenimiento debe impedir reservar una unidad para un servicio?

**Respuesta vigente:** Cada mantenimiento programado bloquea todo el día correspondiente en America/Lima. Si la unidad no vuelve a operación, permanece bloqueada hasta confirmación de ADMIN. Es más conservador y reduce capacidad de servicios.

**Backend:** Convertir mantenimiento programado en bloqueo del día calendario completo correspondiente en America/Lima. Los conflictos se calculan sobre ese día, no solo sobre horas de taller. Si no existe retorno confirmado, continúa el bloqueo operativo; no habilitar por hora estimada.

**CRM:** Mostrar calendario completo bloqueado y advertir asignaciones de cualquier hora afectada.

**App:** Mostrar unidad no disponible por mantenimiento y conservar el vínculo autorizado.

**Trazabilidad:** `D-V3-Q-036` → `BR-V3-Q-036` → `AC-V3-Q-036-01/02`. Elección: Bloquear el día completo.

**Criterio esperado:** Un mantenimiento programado de 09:00 a 11:00 impide reservar servicio a las 17:00 del mismo día.

**Caso que debe impedirse o tratarse:** El fin previsto o la cancelación de una orden no devuelve por sí solo una unidad averiada a operación.

#### Q-037 — Mantenimiento sobre reserva

**Pregunta:** Si se programa mantenimiento sobre una unidad ya reservada, ¿qué debe suceder con esa reserva?

**Respuesta vigente:** El mantenimiento preventivo no se confirma mientras solape asignaciones. ADMIN cambia el mantenimiento o cancela, reprograma o reemplaza las asignaciones afectadas con nueva aceptación. Una avería urgente aplica bloqueo inmediato y deja los servicios afectados pendientes de reasignación.

**Backend:** Antes de confirmar preventivo, resolver ofertas y asignaciones que solapen el bloqueo efectivo de día completo de Q-036, o cambiar su fecha. Para avería urgente bloquear inmediatamente y dejar futuros servicios incompatibles pendientes de reasignación; cambios materiales necesitan nueva aceptación.

**CRM:** Presentar impacto de todo el día y guiar cancelación, reprogramación o reemplazo antes de confirmar preventivo.

**App:** Informar modificación o cancelación de asignación; cualquier nueva oferta debe aceptarse.

**Trazabilidad:** `D-V3-Q-037` → `BR-V3-Q-037` → `AC-V3-Q-037-01/02`. Elección: Resolver antes de confirmar.

**Criterio esperado:** Un preventivo se confirma solo después de resolver compromisos del día bloqueado.

**Caso que debe impedirse o tratarse:** Una avería urgente no espera esa resolución administrativa para bloquear la unidad ni completa automáticamente el servicio iniciado.

#### Q-038 — Retorno a operación

**Pregunta:** ¿Quién debe autorizar que una unidad salga de mantenimiento y vuelva a estar disponible?

**Respuesta vigente:** El ADMIN confirma que el mantenimiento terminó y autoriza el retorno de la unidad, registrando responsable, fecha y evidencia disponible del trabajo. El servidor comprueba que no subsistan otros bloqueos ni documentos vencidos. Llegar a la hora estimada de salida no habilita automáticamente una unidad que sigue en mantenimiento.

**Backend:** Bloquear intervalos y operaciones incompatibles; retorno requiere autorización.

**CRM:** ADMIN resuelve conflictos y confirma retorno.

**App:** Avisar indisponibilidad y cambios de oferta; no exigir foto de placa.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-039 — Disparador preventivo

**Pregunta:** ¿Cuál debe ser el criterio principal para generar un mantenimiento preventivo?

**Respuesta vigente:** En el MVP, el ADMIN puede programar mantenimiento preventivo manualmente cuando lo considere necesario. Debe registrar unidad, motivo e intervalo previsto. No se presupone automatización por kilometraje, sensores o integraciones; la programación respeta los conflictos de Q-036 y Q-037.

**Backend:** Bloquear intervalos y operaciones incompatibles; retorno requiere autorización.

**CRM:** ADMIN resuelve conflictos y confirma retorno.

**App:** Avisar indisponibilidad y cambios de oferta; no exigir foto de placa.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

### MOD-RTE — Rutas

#### Q-040 — Ruta habilitada

**Pregunta:** ¿Qué información mínima debe tener una ruta para poder usarse en un servicio?

**Respuesta vigente:** La definición de una ruta utilizable contiene una parada de inicio, una parada de fin y al menos una parada intermedia, identificadas y ordenadas. Los datos mínimos se validan antes de programar un servicio. Esta validación del catálogo se distingue de la ejecución: la expresión recorrido incompleto se usa para un servicio cuyo recorrido no fue finalizado por el conductor.

**Backend:** Validar paradas mínimas y guardar la versión asignada a cada servicio.

**CRM:** ADMIN gestiona versiones sin reescribir servicios anteriores.

**App:** Mostrar recorrido asignado y acción Ruta finalizada en ejecución.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-041 — Reutilización de ruta

**Pregunta:** ¿Una misma ruta debe poder reutilizarse en varios horarios y servicios?

**Respuesta vigente:** Una ruta puede reutilizarse en varios horarios y servicios. Cada servicio mantiene su programación, asignación y versión de ruta; reutilizarla no permite solapar conductor o unidad ni copiar el estado de ejecución de otro servicio.

**Backend:** Validar paradas mínimas y guardar la versión asignada a cada servicio.

**CRM:** ADMIN gestiona versiones sin reescribir servicios anteriores.

**App:** Mostrar recorrido asignado y acción Ruta finalizada en ejecución.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-042 — Versionado de ruta

**Pregunta:** Si se cambia una ruta que ya tiene servicios programados, ¿qué versión deben usar esos servicios?

**Respuesta vigente:** El servicio conserva la versión de la ruta que estaba vigente al crear su asignación o reserva. Editar la ruta genera una versión nueva para usos posteriores y no cambia silenciosamente servicios programados, aceptados, iniciados o finalizados. Si el ADMIN cambia expresamente la ruta de una asignación todavía no iniciada, se conserva el cambio y se requiere aceptación de la nueva versión.

**Backend:** Validar paradas mínimas y guardar la versión asignada a cada servicio.

**CRM:** ADMIN gestiona versiones sin reescribir servicios anteriores.

**App:** Mostrar recorrido asignado y acción Ruta finalizada en ejecución.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-043 — Desvío autorizado

**Pregunta:** ¿Puede el conductor modificar el recorrido durante la ejecución de un servicio?

**Respuesta vigente:** El conductor no puede modificar manualmente el recorrido asignado. Si necesita desviarse por una incidencia, debe comunicarla al ADMIN; registrar lo que ocurrió no equivale a editar la definición de la ruta. La política de interrupciones y gravedad se precisa en el módulo de incidencias.

**Backend:** Validar paradas mínimas y guardar la versión asignada a cada servicio.

**CRM:** ADMIN gestiona versiones sin reescribir servicios anteriores.

**App:** Mostrar recorrido asignado y acción Ruta finalizada en ejecución.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-044 — Validación de paradas

**Pregunta:** ¿Cómo se registra que el conductor completó el recorrido de un servicio?

**Respuesta vigente:** Un recorrido se registra como completado cuando el conductor responsable pulsa Ruta finalizada y el servidor valida el cierre de un servicio iniciado, su asignación aceptada y su participación vigente. Sin un cierre válido el servicio no queda finalizado correctamente. La comprobación de ubicación de Q-060 aporta evidencia adicional y puede generar observaciones; el botón registra la declaración de cumplimiento, no demuestra por sí solo todo el recorrido. No se declara una parada fallida únicamente porque pasó un tiempo estimado.

**Backend:** Validar paradas mínimas y guardar la versión asignada a cada servicio.

**CRM:** ADMIN gestiona versiones sin reescribir servicios anteriores.

**App:** Mostrar recorrido asignado y acción Ruta finalizada en ejecución.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

### MOD-AVL — Disponibilidad del conductor

#### Q-045 — Autor de disponibilidad

**Pregunta:** ¿Quién debe declarar la disponibilidad de un conductor para recibir asignaciones?

**Respuesta vigente:** CONDUCTOR declara su disponibilidad en la app. El sistema la distingue de su aprobación, habilitación documental, vinculación de unidad y ocupaciones horarias. Declararse disponible no confirma una asignación ni obliga a aceptarla.

**Backend:** Distinguir declaración, elegibilidad, reserva y compromiso aceptado.

**CRM:** Mostrar disponibilidad efectiva y ofertas pendientes.

**App:** CONDUCTOR declara disponibilidad y acepta o rechaza con conexión.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-046 — Vigencia de disponibilidad

**Pregunta:** ¿La disponibilidad debe declararse para un periodo concreto o mantenerse hasta que se cambie?

**Respuesta vigente:** La disponibilidad declarada se mantiene hasta que el conductor la cambie válidamente. No obstante, la posibilidad de recibir una oferta se recalcula para cada horario según documentos, unidad vinculada, mantenimiento, descansos y asignaciones o reservas existentes. La declaración no elimina esos bloqueos.

**Backend:** Distinguir declaración, elegibilidad, reserva y compromiso aceptado.

**CRM:** Mostrar disponibilidad efectiva y ofertas pendientes.

**App:** CONDUCTOR declara disponibilidad y acepta o rechaza con conexión.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-047 — Desconexión y disponibilidad

**Pregunta:** Si el conductor se desconecta de la app, ¿cómo debe interpretarse su disponibilidad declarada?

**Respuesta vigente:** Cerrar la app o perder la conexión no cambia automáticamente la disponibilidad declarada. El ADMIN puede emitir una oferta elegible y se intenta notificar por los canales habilitados. El conductor debe abrir la app con conexión y aceptar antes de que venza la reserva; recibir una notificación no equivale a aceptar y no se garantiza su entrega inmediata. La aplicación no opera offline.

**Backend:** Distinguir declaración, elegibilidad, reserva y compromiso aceptado.

**CRM:** Mostrar disponibilidad efectiva y ofertas pendientes.

**App:** CONDUCTOR declara disponibilidad y acepta o rechaza con conexión.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-048 — Cambio de disponibilidad con compromiso

**Pregunta:** Si el conductor marca que no está disponible mientras tiene una asignación aceptada, ¿qué debe prevalecer?

**Respuesta vigente:** Una asignación aceptada constituye un compromiso confirmado. Mientras ese compromiso siga vigente, el conductor no puede cambiar unilateralmente su disponibilidad para invalidarlo. Si surge una imposibilidad real, la comunica al ADMIN, quien decide y registra cancelación, reprogramación o reemplazo. Cambiar la disponibilidad no cancela ni libera automáticamente una asignación aceptada.

**Backend:** Distinguir declaración, elegibilidad, reserva y compromiso aceptado.

**CRM:** Mostrar disponibilidad efectiva y ofertas pendientes.

**App:** CONDUCTOR declara disponibilidad y acepta o rechaza con conexión.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-049 — Elegibilidad para disponibilidad

**Pregunta:** ¿Cómo debe mostrarse y qué puede recibir un conductor aprobado que todavía espera una unidad?

**Respuesta vigente:** Se muestra Aprobado, pendiente de asignación de unidad. El conductor puede solicitar unidad de la empresa y consultar el avance. ADMIN resuelve esa cola; no se permite disponibilidad operativa ni ofertas mientras falte una unidad revisada y vinculada.

**Backend:** Mantener aprobación del conductor y estado de unidad separados; permitir solicitud empresarial de unidad, sin elegibilidad operativa hasta revisión y vínculo válido.

**CRM:** Mostrar cola de aprobados pendientes de unidad y gestionar solicitudes.

**App:** Mostrar Aprobado, pendiente de asignación de unidad y permitir solicitar y consultar avance.

**Trazabilidad:** `D-V3-Q-049` → `BR-V3-Q-049` → `AC-V3-Q-049-01/02`. Elección: Pendiente de unidad, con solicitud.

**Criterio esperado:** Un aprobado sin unidad puede solicitarla y consultar su avance.

**Caso que debe impedirse o tratarse:** No aparece como disponible para servicios ni recibe oferta operativa hasta tener unidad válida.

### MOD-ASG — Asignaciones operativas

#### Q-050 — Significado de asignación

**Pregunta:** ¿Qué compromiso representa una asignación operativa antes de que empiece un servicio?

**Respuesta vigente:** Al emitir una oferta, el ADMIN solicita un servicio concreto y reserva provisionalmente el horario del conductor y de su unidad vinculada. El compromiso confirmado nace cuando el conductor acepta la versión vigente y el servidor vuelve a comprobar su elegibilidad. Ese compromiso existe antes de comenzar el servicio. La aceptación no inicia la conducción; el inicio requiere la acción de Q-059.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-051 — Autor de asignación

**Pregunta:** ¿Quién tiene autoridad para crear una asignación operativa?

**Respuesta vigente:** El ADMIN de la empresa crea las ofertas y gestiona las asignaciones operativas de sus conductores y unidades. Administrador de Flotas u operador son descripciones del mismo rol ADMIN, no roles adicionales. CONDUCTOR acepta o rechaza su oferta; SUPER_ADMIN no sustituye al ADMIN en la gestión ordinaria de estos servicios.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-052 — Aceptación de asignación

**Pregunta:** ¿El conductor debe aceptar una asignación antes de que quede confirmada?

**Respuesta vigente:** Sí. Una asignación solo queda CONFIRMADA después de que el conductor destinatario acepte su versión vigente desde la app con conexión y el servidor valide las condiciones. Estar disponible o tener unidad vinculada no sustituye la aceptación. El ADMIN crea la oferta y no puede marcarla aceptada por el conductor. Un cambio sustancial de conductor, unidad, ruta u horario antes del inicio requiere nueva aceptación.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-053 — Vigencia de reserva

**Pregunta:** ¿Qué plazo debe tener la reserva horaria de una oferta mientras el conductor la acepta?

**Respuesta vigente:** 10 minutos.
La oferta vence a los 10 minutos o al inicio de la prestación ofertada, lo que ocurra primero. La liberación y validación siguen las mismas reglas. Favorece despacho rápido y exige que el conductor revise la app con mediana frecuencia.

**Backend:** Usar reserva de oferta por 10 minutos desde creación en servidor, con deadline menor o igual al inicio previsto de la prestación ofertada. Relevo usa inicio del nuevo tramo. Validar vigencia en cada aceptación y reserva; liberar ocupación al vencer, sin romper vínculo ni reasignar automáticamente.

**CRM:** Mostrar cuenta regresiva de 10 minutos y servicios que requieren nueva oferta al vencer.

**App:** Mostrar plazo real y aceptar o rechazar conectado; no aceptar localmente una oferta ya vencida.

**Trazabilidad:** `D-V3-Q-053` → `BR-V3-Q-053` → `AC-V3-Q-053-01/02`. Elección: Opción propia.

**Criterio esperado:** Una oferta con inicio dentro de 6 minutos vence al inicio previsto, sin completar los 10 minutos.

**Caso que debe impedirse o tratarse:** A los 10 minutos o al inicio de prestación, lo que ocurra primero, no se acepta la misma oferta ni se rompe el vínculo de unidad.

#### Q-054 — Conflicto de reserva

**Pregunta:** Si dos ADMIN intentan reservar conductor o unidad para horarios incompatibles, ¿qué obtiene el segundo?

**Respuesta vigente:** Tiene prioridad la primera reserva o asignación incompatible registrada válidamente por el servidor, aunque todavía espere aceptación. El segundo intento se rechaza por conflicto y conserva los datos del formulario. Se comprueban conductor y unidad, sus vigencias y márgenes. No se sobrescribe la primera oferta. Al expirar, rechazarse o cancelarse esa reserva, su intervalo puede volver a ofrecerse, siempre que no exista otro bloqueo.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-055 — Separación entre compromisos

**Pregunta:** ¿Qué margen entre servicios debe dejarse para poder reservar al mismo conductor de nuevo?

**Respuesta vigente:** Se aplica un margen configurable según el tipo de servicio, suficiente para traslado, preparación y descansos exigibles. Las reservas y las aceptaciones deben respetarlo tanto para el conductor como para la unidad. Si un servicio se retrasa, se recalcula la disponibilidad y se avisa al ADMIN para resolver las asignaciones posteriores. No se asume un margen universal; la configuración del tipo de servicio debe estar definida antes de usarlo.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-056 — Liberación por vencimiento

**Pregunta:** Si vence una reserva sin aceptación, ¿qué debe ocurrir con los recursos reservados?

**Respuesta vigente:** Al vencer el plazo sin aceptación, la oferta pasa a EXPIRADA y se liberan sus reservas horarias del conductor y la unidad. Se conserva el vínculo conductor-unidad dentro de su periodo autorizado. El servicio permanece programado y pendiente de una nueva oferta o reasignación; su inicio sigue bloqueado hasta obtener una aceptación válida. Se avisa al ADMIN y se conserva el historial. No se reofrece automáticamente sin una decisión del ADMIN.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-057 — Gestión de rechazo de asignación

**Pregunta:** Si el conductor rechaza una oferta o comunica una imposibilidad después de aceptarla, ¿cómo se gestiona el reemplazo?

**Respuesta vigente:** Si el conductor rechaza una oferta, se registra el rechazo, se libera la reserva horaria y el servicio queda pendiente de nueva oferta o reasignación. El CRM muestra candidatos habilitados y disponibles con unidad vinculada, pero el ADMIN decide el reemplazo; el nuevo conductor también debe aceptar. Si la asignación ya fue aceptada, se registra una imposibilidad sobrevenida y el ADMIN gestiona su cancelación o sustitución; no se trata como un rechazo retroactivo ni se borra la aceptación original.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-058 — Cancelación y servicio

**Pregunta:** Si se cancela una asignación confirmada antes del inicio, ¿qué debe ocurrir con el servicio vinculado?

**Respuesta vigente:** Si el ADMIN cancela una asignación confirmada antes del inicio, el servicio mantiene su programación y queda pendiente de reasignación, con inicio bloqueado. Se libera la ocupación del horario, conservando el vínculo conductor-unidad dentro de su vigencia. Cualquier reemplazo requiere una nueva oferta y aceptación del conductor. Cancelar también el servicio exige una decisión expresa con motivo. Se conservan la aceptación anterior, cancelación y notificaciones.

**Backend:** Crear reserva, validar aceptación y vencimiento, serializar conflictos y preservar versiones.

**CRM:** ADMIN emite oferta y atiende rechazo, vencimiento o reasignación.

**App:** CONDUCTOR acepta la oferta vigente, ve plazo y motivo de bloqueo.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

### MOD-SRV — Servicios y ejecución

#### Q-059 — Inicio de servicio

**Pregunta:** ¿Qué hecho debe hacer que un servicio pase de programado a iniciado?

**Respuesta vigente:** El conductor asignado y que aceptó confirma Iniciar servicio al comenzar realmente la prestación. El servidor valida asignación confirmada y vigente, conductor y unidad habilitados, vínculo válido, márgenes y ausencia de operaciones incompatibles. Se registran responsable, fecha y hora de ejecución y de registro. La hora programada, la creación de la oferta y la aceptación no inician automáticamente el servicio.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-060 — Finalización de servicio

**Pregunta:** ¿Qué condición debe demostrar que un servicio terminó correctamente?

**Respuesta vigente:** El servicio debe haber sido iniciado y el conductor responsable de su tramo vigente confirma que completó la prestación mediante Ruta finalizada. El servidor registra el cierre y comprueba automáticamente una ubicación reciente y suficientemente precisa respecto del terminal de la versión de ruta asignada, sin exigir otro botón. Se conservan horas reales, participantes, unidades, resultado, incidencias y evidencia de ubicación. Una discrepancia genera observación y aviso al ADMIN; la falta de evidencia válida queda como llegada no corroborada, sin atribuir fraude automáticamente. Un servicio cancelado o interrumpido no cuenta como cumplimiento correcto. La ubicación aporta evidencia de llegada, sin certificar por sí sola todas las paradas; las incidencias no impeditivas conservan su seguimiento independiente.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-061 — Guardia de inicio

**Pregunta:** ¿Se permite iniciar un servicio sin una asignación confirmada?

**Respuesta vigente:** No se permite iniciar un servicio sin asignación CONFIRMADA mediante aceptación válida del conductor. El servidor comprueba que corresponde a ese servicio, conductor, unidad vinculada, versión de oferta y horario, además de la habilitación y los conflictos operativos. Una oferta pendiente, vencida, rechazada, cancelada o pendiente de reemplazo bloquea el inicio y muestra el motivo. El ADMIN no puede omitir la aceptación para forzar el inicio.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-062 — Tratamiento de demora

**Pregunta:** Si el conductor llega tarde, ¿qué debe suceder con el estado del servicio?

**Respuesta vigente:** Si se supera la hora prevista sin un inicio válido, el servicio permanece PROGRAMADO con alerta de retraso. No se inicia ni cancela automáticamente. El ADMIN decide si espera, reprograma, ofrece un reemplazo o cancela. La reprogramación o sustitución requiere aceptación de la nueva oferta. Al empezar se conserva la hora programada y se registra la real; una alerta de retraso no hace válida una oferta vencida.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-063 — Inicio sin conexión

**Pregunta:** ¿Qué ocurre si se pierde la conexión al aceptar, iniciar o finalizar un servicio?

**Respuesta vigente:** La plataforma no acepta acciones offline ni su sincronización posterior. Sin conexión no se confirma la aceptación, el inicio ni el cierre de un servicio. Al volver la conexión se consulta el estado vigente. Si el servidor completó una acción antes de perderse la respuesta, se recupera ese registro sin duplicarlo. Un inicio o final físico que nunca se registró debe comunicarse como incidencia y tratarse mediante corrección autorizada; no se fabrica una confirmación previa del servidor.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-064 — Corrección de ejecución

**Pregunta:** ¿Quién puede corregir los tiempos de un servicio ya finalizado?

**Respuesta vigente:** Solo el ADMIN de la empresa con permiso vigente puede corregir los tiempos de un servicio finalizado. CONDUCTOR puede solicitar la corrección, pero no editar directamente. Se exige motivo y se conservan valores originales y corregidos, autor y fecha. Se valida el orden temporal y la coherencia de los tramos. No se alteran ubicaciones ni evidencias originales; cualquier efecto de la corrección sobre la verificación del cierre queda registrado. No existe un rol operador separado.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

#### Q-065 — Cambio de conductor durante ejecución

**Pregunta:** Si se reemplaza al conductor durante un servicio, ¿cómo debe conservarse la responsabilidad de cada tramo?

**Respuesta vigente:** Se conserva un historial de tramos con conductor, unidad vinculada, horas reales y puntos de relevo, sin sobrescribir participaciones anteriores. El ADMIN gestiona una oferta para un reemplazo habilitado y con unidad válida; el nuevo conductor debe aceptarla antes de asumir el tramo. Se registra el momento efectivo del relevo, el motivo, autor, pausas y transferencias de pasajeros cuando correspondan. Las incidencias se asocian al tramo de su ocurrencia, aunque se reporten después. El cambio no reinicia la hora inicial del servicio ni convierte participación operativa en atribución automática de culpa.

**Backend:** Autorizar inicio y cierre; conservar resultado, evidencia y tramos.

**CRM:** ADMIN atiende retrasos, correcciones y relevo aceptado.

**App:** Iniciar servicio y Ruta finalizada requieren conexión y estado vigente.

**Origen:** decisión anterior conservada; no se vuelve a preguntar.

### MOD-INC — Incidencias

#### Q-066 — Catálogo inicial de incidencias

**Pregunta:** ¿Qué catálogo mínimo de incidencias necesita el MVP, además de vencimiento de licencia, avería, retraso, imposibilidad de cumplir y discrepancia de ubicación ya contemplados?

**Respuesta vigente:** A los tipos existentes se añaden accidente o riesgo de seguridad, problema de acceso a la parada y fallo técnico de la plataforma. Otros exige descripción. La gravedad y el efecto operativo se registran por separado del tipo.

**Backend:** Definir catálogo mínimo: licencia vencida, avería, retraso, imposibilidad de cumplir, discrepancia de ubicación, accidente/riesgo, acceso a parada, fallo de plataforma y Otros con descripción. Separar tipo, gravedad y efecto.

**CRM:** Filtrar y atender por tipo, gravedad y afectación; no imponer sanción por el tipo solo.

**App:** Seleccionar un tipo y describir Otros; aportar información disponible con conexión.

**Trazabilidad:** `D-V3-Q-066` → `BR-V3-Q-066` → `AC-V3-Q-066-01/02`. Elección: Catálogo mínimo y Otros.

**Criterio esperado:** Un reporte Otros requiere descripción y guarda gravedad separada.

**Caso que debe impedirse o tratarse:** Clasificar retraso o discrepancia GPS no produce automáticamente sanción o interrupción.

#### Q-067 — Incidencia bloqueante

**Pregunta:** ¿Qué condiciones de una incidencia obligan a interrumpir un servicio, además de la pérdida de habilitación ya definida?

**Respuesta vigente:** Se interrumpe ante accidente con riesgo, avería que impide circulación segura o imposibilidad real de prestar el recorrido. El conductor prioriza una detención segura y comunica la incidencia; ADMIN coordina relevo o cancelación. Un retraso o un GPS no corroborado no interrumpen por sí solos.

**Backend:** Registrar interrupción segura ante riesgo o imposibilidad real, preservar tramos y resultado. Pérdida de habilitación sigue sus bloqueos ya definidos. Retraso y GPS no corroborado aislados no interrumpen automáticamente.

**CRM:** Coordinar relevo o cancelación; conservar lo ejecutado y el motivo.

**App:** Priorizar detención segura ante riesgo y comunicar la incidencia; registrar solo con conexión, sin simular cola offline.

**Trazabilidad:** `D-V3-Q-067` → `BR-V3-Q-067` → `AC-V3-Q-067-01/02`. Elección: Seguridad o imposibilidad de continuar.

**Criterio esperado:** Una avería que impide circulación segura genera incidencia y gestión de interrupción o relevo.

**Caso que debe impedirse o tratarse:** La mera falta de corroboración GPS o retraso no convierte por sí solo el servicio en interrumpido.

#### Q-069 — Condición de cierre de incidencia

**Pregunta:** ¿Qué información y evidencia mínima debe aportar el ADMIN para cerrar una incidencia?

**Respuesta vigente:** ADMIN registra diagnóstico o motivo, acciones tomadas, resultado y fecha. Adjunta o referencia evidencia cuando la naturaleza del caso la requiere; si no puede obtenerla, explica la razón. Si implica reparación o habilitación, debe completar la comprobación correspondiente antes de devolver a operación.

**Backend:** Exigir diagnóstico/motivo, acciones, resultado y fecha al cierre; evidencia según naturaleza o razón de no obtención. Si resuelve reparación o habilitación, comprobar retorno real y vigencia antes de operar. No cambiar el resultado del servicio por cerrar incidencia.

**CRM:** Completar datos de resolución y comprobaciones de habilitación pertinentes.

**App:** Consultar resultado del caso y conservar la relación con el tramo afectado.

**Trazabilidad:** `D-V3-Q-069` → `BR-V3-Q-069` → `AC-V3-Q-069-01/02`. Elección: Resultado y evidencia según el caso.

**Criterio esperado:** Un cierre registra acción, resultado, motivo y evidencia pertinente o razón justificada de falta.

**Caso que debe impedirse o tratarse:** Cerrar incidencia de avería no elimina otros bloqueos de la unidad ni marca servicio interrumpido como exitoso.

#### Q-070 — Reapertura de incidencia

**Pregunta:** ¿Qué hecho permite reabrir una incidencia cerrada y quién puede solicitarlo?

**Respuesta vigente:** No se puede reabrir una incidencia, esto no está permitido en el MVP.

**Backend:** Rechazar toda transición de incidencia cerrada a abierta en el MVP, incluso desde API directa. Conservar el cierre y auditoría; un caso posterior no debe alterar el anterior.

**CRM:** No mostrar acción Reabrir ni permitirla desde accesos alternativos.

**App:** No ofrecer reapertura de una incidencia cerrada.

**Trazabilidad:** `D-V3-Q-070` → `BR-V3-Q-070` → `AC-V3-Q-070-01/02`. Elección: Opción propia.

**Criterio esperado:** Una incidencia cerrada permanece cerrada tras una solicitud de reapertura.

**Caso que debe impedirse o tratarse:** Ningún ADMIN, SUPER_ADMIN o CONDUCTOR reabre la misma incidencia en el MVP.

#### Q-068 — Responsable de atención

**Pregunta:** ¿Quién debe hacerse responsable de atender una incidencia reportada?

**Respuesta vigente:** Cada incidencia tiene un ADMIN responsable de su empresa, designado al atenderla. El conductor aporta información y ADMIN coordina resolución y cierre. Un cambio de responsable queda auditado.

**Backend:** Asignar un ADMIN responsable de la misma empresa al atender la incidencia; registrar reasignación de responsable y permitir colaboración sin nuevos roles.

**CRM:** Identificar quién atiende cada caso y auditar el cambio de responsable.

**App:** Consultar seguimiento y aportar información propia sin asumir funciones de cierre administrativo.

**Trazabilidad:** `D-V3-Q-068` → `BR-V3-Q-068` → `AC-V3-Q-068-01/02`. Elección: Un ADMIN responsable por incidencia.

**Criterio esperado:** Cada incidencia atendida tiene ADMIN responsable identificado.

**Caso que debe impedirse o tratarse:** Un ADMIN de otra empresa no asume ni cierra la incidencia.

### MOD-COM — Comunicados y notificaciones

#### Q-072 — Acuse de comunicado

**Pregunta:** ¿Puede el ADMIN exigir confirmación de lectura en un comunicado y qué efecto tiene que el conductor no la registre?

**Respuesta vigente:** ADMIN puede exigir pulsar Leído en comunicados marcados importantes. La falta de acuse genera un pendiente y aviso a ADMIN, sin bloquear automáticamente servicios ni aceptar ofertas en nombre del conductor.

**Backend:** Permitir acuse explícito Leído en mensajes importantes; registrar actor y hora y crear pendientes de acuse. No usar ese acuse como aceptación de servicio ni bloquear por sí solo inicio.

**CRM:** Identificar pendientes de lectura y avisar sin cancelar servicios automáticamente.

**App:** Confirmar Leído y distinguir esa acción de aceptar oferta.

**Trazabilidad:** `D-V3-Q-072` → `BR-V3-Q-072` → `AC-V3-Q-072-01/02`. Elección: Acuse en comunicados importantes.

**Criterio esperado:** Un comunicado importante conserva acuse explícito o pendiente de lectura.

**Caso que debe impedirse o tratarse:** No abrir el comunicado no acepta ni cancela una asignación y no bloquea por sí solo un servicio.

#### Q-073 — Destinatarios y cambios de grupo

**Pregunta:** Si un conductor deja un grupo, ¿conserva acceso a los comunicados que recibió cuando pertenecía a él?

**Respuesta vigente:** Mientras mantenga su membresía en la empresa, el conductor puede consultar los comunicados que recibió cuando pertenecía al grupo. No recibe nuevos mensajes del grupo. Las retiradas por seguridad se respetan y el acceso termina al salir de la empresa.

**Backend:** Conservar conjunto de comunicados efectivamente recibidos al salir de grupo, mientras siga activa membresía de empresa. No entregar futuros mensajes de ese grupo; respetar retiradas y revocar al salir de empresa.

**CRM:** Mostrar historial de entrega, membresías y retiradas autorizadas.

**App:** Consultar mensajes recibidos del grupo anterior solo mientras siga en empresa y el contenido no se haya retirado.

**Trazabilidad:** `D-V3-Q-073` → `BR-V3-Q-073` → `AC-V3-Q-073-01/02`. Elección: Conservar los recibidos mientras siga en empresa.

**Criterio esperado:** Salir del grupo conserva acceso a un mensaje anterior recibido si permanece en la empresa.

**Caso que debe impedirse o tratarse:** No recibe mensajes posteriores del grupo ni accede a contenido interno tras salir de la empresa.

#### Q-074 — Avisos externos

**Pregunta:** ¿Qué avisos deben intentar notificarse fuera de la app y por cuáles canales viables sin gasto adicional?

**Respuesta vigente:** Todos los avisos quedan en la bandeja. Se intenta push para oferta, cancelación, reasignación, suspensión e incidencia crítica solo tras comprobar una vía sin gasto y sus límites. Correo se reserva para seguridad y recuperación con un canal probado; ADMIN ve los envíos fallidos.

**Backend:** Mantener bandeja como fuente consultable. Intentar push de oferta/cancelación/reasignación/suspensión/incidencia crítica solo con canal viable sin gasto verificado; reservar correo a seguridad y recuperación. Registrar fallo de entrega sin cambiar estados por recepción.

**CRM:** Consultar fallos de aviso y estado operativo real, sin dar por aceptada una oferta por push.

**App:** Leer bandeja y consultar estado vigente al tocar aviso; una notificación antigua no habilita acciones ya vencidas.

**Trazabilidad:** `D-V3-Q-074` → `BR-V3-Q-074` → `AC-V3-Q-074-01/02`. Elección: Bandeja y push para eventos operativos.

**Criterio esperado:** Un aviso fallido queda identificado y el evento sigue consultable en bandeja.

**Caso que debe impedirse o tratarse:** Recibir una notificación no acepta oferta ni convierte el canal sin probar en una dependencia garantizada.

#### Q-071 — Permiso de publicación

**Pregunta:** ¿Quién debe poder publicar un comunicado a todos los conductores de la empresa?

**Respuesta vigente:** ADMIN puede publicar comunicados dirigidos a todos sus conductores o a grupos de su empresa. CONDUCTOR consulta y confirma lectura cuando corresponda, sin publicar comunicados generales.

**Backend:** Autorizar publicación a ADMIN dentro de su empresa y sus grupos; impedir publicación general por CONDUCTOR y distribución entre empresas. Registrar autor y alcance.

**CRM:** Crear comunicados para empresa o grupos propios y revisar destinatarios antes de publicar.

**App:** Consultar comunicaciones recibidas; no mostrar editor de comunicados generales.

**Trazabilidad:** `D-V3-Q-071` → `BR-V3-Q-071` → `AC-V3-Q-071-01/02`. Elección: Publica cualquier ADMIN de su empresa.

**Criterio esperado:** ADMIN publica a un grupo de su empresa y los destinatarios correspondientes lo reciben.

**Caso que debe impedirse o tratarse:** CONDUCTOR o un ADMIN de otra empresa no publica ni distribuye ese comunicado.

### MOD-GPS — Monitoreo y ubicación

#### Q-075 — Ventana de captura de ubicación

**Pregunta:** ¿Se permite capturar ubicación solo durante el servicio iniciado o también antes y después, y con qué límite?

**Respuesta vigente:** La captura comienza después de que el servidor registra Iniciar servicio y termina al finalizar, interrumpir o cancelar. Puede obtenerse una lectura puntual al pulsar inicio para validar permiso; no se conserva seguimiento previo ni posterior.

**Backend:** Aceptar puntos de seguimiento solo para servicio iniciado autorizado; parar captura al finalizar/interrumpir/cancelar. Una lectura puntual para comprobar permiso al inicio no constituye historial previo. Obtener evidencia de cierre en el flujo de cierre, no seguir después.

**CRM:** Mostrar ubicación de servicios iniciados, con última hora y sin trayectos personales anteriores o posteriores.

**App:** Activar seguimiento durante servicio y detenerlo al registrar cierre; mostrar que la captura está activa.

**Trazabilidad:** `D-V3-Q-075` → `BR-V3-Q-075` → `AC-V3-Q-075-01/02`. Elección: Solo durante un servicio iniciado.

**Criterio esperado:** Un servicio iniciado habilita captura y el cierre válido detiene seguimiento.

**Caso que debe impedirse o tratarse:** No se guardan puntos de seguimiento anteriores al inicio ni posteriores al cierre.

#### Q-076 — Permiso de ubicación denegado

**Pregunta:** Si el conductor deniega ubicación, ¿se le permite iniciar el servicio o solo conservar consulta, incorporación y acceso a su cuenta? El cierre sin evidencia ya queda no corroborado.

**Respuesta vigente:** Si el conductor deniega ubicación, conserva acceso a cuenta, incorporación y consulta, pero no inicia un servicio que requiere seguimiento. La app explica cómo habilitar el permiso. Una pérdida posterior de GPS genera falta de evidencia e incidencia según gravedad, sin fingir ubicación.

**Backend:** Para un servicio que requiera seguimiento, validar permiso de ubicación antes de registrar inicio. Mantener cuenta, incorporación y consulta si se deniega; pérdida posterior se registra como falta de evidencia según gravedad.

**CRM:** Mostrar causa de inicio no disponible; no atribuir fraude automático por denegación o pérdida GPS.

**App:** Explicar cómo habilitar permiso y conservar acceso a cuenta e incorporación.

**Trazabilidad:** `D-V3-Q-076` → `BR-V3-Q-076` → `AC-V3-Q-076-01/02`. Elección: Bloquear inicio, mantener consulta y cuenta.

**Criterio esperado:** Denegar permiso impide iniciar un servicio que requiere seguimiento, sin borrar la cuenta.

**Caso que debe impedirse o tratarse:** Perder GPS tras iniciar no inventa puntos ni acredita por sí solo fraude.

#### Q-077 — Frecuencia de ubicación

**Pregunta:** ¿Con qué frecuencia necesita el ADMIN recibir ubicación durante un servicio activo, considerando batería, conexión y presupuesto S/0?

**Respuesta vigente:** Durante un servicio activo se intenta enviar un punto cada 30 segundos con conexión. Se señala retraso de actualización y se solicita una lectura puntual al cierre para corroboración. Se detiene la captura al terminar; no hay cola offline.

**Backend:** Intentar envío de ubicación cada 30 segundos durante servicio activo y con conexión; no encolar offline. Guardar hora y calidad para mostrar antigüedad. Obtener lectura puntual al cierre dentro de la captura autorizada.

**CRM:** Mostrar último punto y su hora; no presentar actualización antigua como posición actual.

**App:** Enviar con permiso y conexión durante el servicio, detener al terminar y obtener evidencia puntual en cierre.

**Trazabilidad:** `D-V3-Q-077` → `BR-V3-Q-077` → `AC-V3-Q-077-01/02`. Elección: Cada 30 segundos.

**Criterio esperado:** Bajo conexión, el objetivo de muestreo es 30 segundos durante el servicio.

**Caso que debe impedirse o tratarse:** Sin conexión no se guardan acciones offline para sincronizar después ni se garantiza un punto exacto cada 30 segundos.

#### Q-078 — Acceso al historial de ubicación

**Pregunta:** Además del acceso operativo del ADMIN a su empresa, ¿debe CONDUCTOR consultar o descargar su propio historial de ubicación? No se presume acceso irrestricto de SUPER_ADMIN.

**Respuesta vigente:** La app muestra solo inicio, terminal, horarios y resultado de sus servicios. Los puntos detallados se entregan por una solicitud de acceso verificada, sin impedir los derechos que resulten aplicables.

**Backend:** Exponer al conductor solo resumen propio en app: inicio, terminal, horarios y resultado. Tratar puntos detallados mediante solicitud de acceso verificada, limitada a datos propios y a retención; no presumir acceso irrestricto de SUPER_ADMIN.

**CRM:** ADMIN consulta operación propia y el procedimiento autorizado de entrega de detalle; no exporta trayectos personales indiscriminadamente.

**App:** Mostrar resumen y vía de solicitud del detalle, sin mapa histórico completo ni descarga directa general de puntos.

**Trazabilidad:** `D-V3-Q-078` → `BR-V3-Q-078` → `AC-V3-Q-078-01/02`. Elección: Resumen en app y solicitud del detalle.

**Criterio esperado:** El conductor ve resumen de su servicio y puede solicitar su detalle por proceso verificado.

**Caso que debe impedirse o tratarse:** Una solicitud no revela ubicaciones de otro conductor ni recrea puntos ya eliminados.

#### Q-079 — Retención de ubicación

**Pregunta:** ¿Durante cuánto tiempo se conservarán las ubicaciones y qué datos permanecerán después de ese plazo?

**Respuesta vigente:** Se conservan puntos detallados 7 días y luego se eliminan. Se mantienen resúmenes sin trayecto detallado y evidencias puntuales justificadas de incidencias. Reduce almacenamiento y el tiempo disponible para revisar recorridos.

**Backend:** Eliminar puntos detallados tras 7 días; mantener resúmenes sin trayecto detallado. Evidencia puntual de incidencia necesita finalidad, alcance y excepción justificados. Definir expiración también en copias; auditoría no debe duplicar permanentemente coordenadas.

**CRM:** Mostrar periodo de disponibilidad y evidencia puntual restringida cuando exista excepción justificada.

**App:** Informar que el detalle está sujeto a retención; el resumen propio sigue disponible según su política.

**Trazabilidad:** `D-V3-Q-079` → `BR-V3-Q-079` → `AC-V3-Q-079-01/02`. Elección: Puntos durante 7 días.

**Criterio esperado:** Tras siete días, el trayecto detallado deja de estar consultable sin excepción justificada y acotada.

**Caso que debe impedirse o tratarse:** La retención de auditoría de seis meses no extiende automáticamente la de puntos GPS.

#### Q-106 — Criterios de corroboración de llegada

**Pregunta:** ¿Qué criterios deben definir una ubicación válida para corroborar el terminal: radio permitido, antigüedad máxima y precisión mínima?

**Respuesta vigente:** Al cerrar se corrobora terminal solo con un punto a no más de 200 m del terminal configurado, con antigüedad máxima de 2 minutos y precisión horizontal reportada de 100 m o mejor. Sin cumplimiento queda no corroborado o con discrepancia para revisión. Se calibra en el piloto.

**Backend:** En el cierre comparar con terminal de la versión de ruta del servicio: distancia <=200 m, antigüedad <=120 s y precisión horizontal reportada <=100 m. Mantener punto y resultado dentro de retención. Sin evidencia válida diferenciar no corroborado de discrepancia, sin acusación ni cierre automático por llegada.

**CRM:** Mostrar declarado/corroborado/discrepancia/no corroborado por separado y atender observaciones.

**App:** Ruta finalizada solicita automáticamente comprobación de terminal; no exigir otro botón ni ocultar falta de evidencia.

**Trazabilidad:** `D-V3-Q-106` → `BR-V3-Q-106` → `AC-V3-Q-106-01/02`. Elección: 200 m, 2 minutos y precisión de 100 m.

**Criterio esperado:** Un punto reciente dentro de 200 m y precisión adecuada puede corroborar el terminal al cerrar.

**Caso que debe impedirse o tratarse:** Un punto antiguo, impreciso o ausente no corrobora; un punto en terminal no acredita todas las paradas.

### MOD-REP — Reportes

#### Q-080 — Indicador prioritario

**Pregunta:** ¿Qué indicador principal debe mostrar el CRM para evaluar la operación diaria: cumplimiento, puntualidad, disponibilidad u otro?

**Respuesta vigente:** El indicador principal es el porcentaje de servicios completados respecto a los programados del periodo. Cancelados, interrumpidos y pendientes se muestran separados; se distingue cierre declarado de corroboración de terminal. No se elimina del denominador un servicio para mejorar el resultado.

**Backend:** Calcular porcentaje de completados respecto a programados del periodo con denominador identificable y resultados separados. Usar cierre válido de servicio iniciado; distinguir declarado y corroborado y no excluir cancelados para mejorar KPI.

**CRM:** Priorizar cumplimiento y mostrar cancelados/interrumpidos/pendientes con periodo y denominador.

**App:** Consultar resultados propios sin métricas de otros conductores no autorizadas.

**Trazabilidad:** `D-V3-Q-080` → `BR-V3-Q-080` → `AC-V3-Q-080-01/02`. Elección: Cumplimiento de servicios.

**Criterio esperado:** El KPI identifica periodo, completados y total programado, con estado de corroboración separado.

**Caso que debe impedirse o tratarse:** Una oferta aceptada, cancelación o interrupción no se cuenta como servicio completado.

#### Q-082 — Exportación de datos personales

**Pregunta:** ¿Qué campos personales puede exportar ADMIN de su empresa y debe permitirse algún tipo de exportación propia al CONDUCTOR?

**Respuesta vigente:** ADMIN exporta identificador interno, nombre, estado, grupo y datos de servicios necesarios de su empresa. Se excluyen DNI completo, contactos, imágenes de documentos y puntos GPS por defecto. CONDUCTOR puede descargar sus propios servicios y resultados; los datos adicionales se atienden mediante acceso verificado.

**Backend:** Autorizar exportación operativa por empresa: ID interno, nombre, estado, grupo y servicio necesario; excluir DNI completo, contactos, documentos y puntos GPS por defecto. Permitir servicios/resultados propios al conductor; datos extra vía acceso verificado. Auditar descarga y excluir secretos.

**CRM:** Exportar CSV/listado operativo mínimo y explicar alcance; no añadir expediente o GPS completo por defecto.

**App:** Descargar servicios y resultados propios, sin acceso directo general a puntos GPS de Q-078.

**Trazabilidad:** `D-V3-Q-082` → `BR-V3-Q-082` → `AC-V3-Q-082-01/02`. Elección: Exportación operativa mínima.

**Criterio esperado:** El archivo de ADMIN solo contiene datos operativos permitidos de su empresa.

**Caso que debe impedirse o tratarse:** La exportación propia no revela otros conductores ni equivale a descargar puntos GPS detallados.

#### Q-083 — Revisión de indicadores históricos

**Pregunta:** Si ADMIN corrige un servicio de un periodo ya reportado, ¿se recalcula el reporte o se emite una versión corregida conservando el original?

**Respuesta vigente:** La vista del periodo se recalcula con la corrección. Un reporte previamente emitido conserva su versión original y la siguiente emisión crea una versión corregida con fecha y referencia al cambio, sin sobrescribir el archivo anterior.

**Backend:** Recalcular vista vigente al corregir y versionar emisiones de reporte con fecha, referencia a corrección y snapshot consistente. Conservar la versión original; no modificar archivos previamente descargados.

**CRM:** Distinguir reporte vigente de emitido y ofrecer versión corregida con causa.

**App:** Mostrar el dato vigente propio y preservar referencia al histórico cuando corresponda.

**Trazabilidad:** `D-V3-Q-083` → `BR-V3-Q-083` → `AC-V3-Q-083-01/02`. Elección: Versionar los reportes emitidos.

**Criterio esperado:** Una corrección genera nueva emisión distinguible y conserva la emisión anterior.

**Caso que debe impedirse o tratarse:** No se sobrescribe el reporte original ni se oculta el cambio de tiempos.

### MOD-AUD — Auditoría

#### Q-086 — Retención de auditoría

**Pregunta:** ¿Durante cuánto tiempo debe conservarse la auditoría y qué parte puede archivarse o anonimizarse después?

**Respuesta vigente:** Se conserva auditoría operativa 6 meses, con la misma revisión de eliminación, anonimización y evidencia de casos abiertos. Reduce almacenamiento y el periodo disponible para reconstruir decisiones.

**Backend:** Retener auditoría operativa 6 meses; aplicar eliminación/anonimización por finalidad y excepciones justificadas de casos abiertos. No trasladar ese plazo automáticamente a términos, recuperación, documentos o GPS. Definir calendario y copias antes de activar purga.

**CRM:** Mostrar registros operativos dentro de retención y excepciones acotadas con acceso restringido.

**App:** El historial propio visible no debe confundirse con retención universal de todos los datos de cuenta.

**Trazabilidad:** `D-V3-Q-086` → `BR-V3-Q-086` → `AC-V3-Q-086-01/02`. Elección: 6 meses de auditoría operativa.

**Criterio esperado:** Se aplica objetivo de seis meses a auditoría operativa, con excepción justificada de caso abierto.

**Caso que debe impedirse o tratarse:** No se conserva todo dato personal indefinidamente ni se prolongan puntos GPS a seis meses por estar referenciados en auditoría.

#### Q-084 — Eventos auditables

**Pregunta:** ¿Qué decisiones del proceso deben quedar registradas de manera obligatoria en auditoría?

**Respuesta vigente:** Se auditan todos los eventos del núcleo definido y los intentos denegados relevantes de seguridad, con actor, empresa, resultado y hora. No se registran secretos ni cada refresco de pantalla; acceso y retención se deciden por separado.

**Backend:** Auditar núcleo de decisiones y cambios críticos y denegaciones relevantes, con empresa, actor, resultado y hora; no guardar secretos ni cada refresco. Aplicar retención y permisos propios, con resultados originales preservados.

**CRM:** Consultar eventos obligatorios según empresa y permiso; no simular evidencia de implementación.

**App:** Mostrar resultado e historial propios relevantes; no el log administrativo completo.

**Trazabilidad:** `D-V3-Q-084` → `BR-V3-Q-084` → `AC-V3-Q-084-01/02`. Elección: Núcleo de cambios y decisiones.

**Criterio esperado:** Una aceptación, rechazo, vencimiento, cambio crítico o denegación relevante genera evento auditable diseñado.

**Caso que debe impedirse o tratarse:** Un refresco corriente no crea automáticamente un evento obligatorio ni se guardan OTP o contraseñas.

#### Q-085 — Acceso a auditoría

**Pregunta:** ¿Qué rol debe poder consultar quién aprobó, rechazó o reasignó un recurso?

**Respuesta vigente:** ADMIN consulta la auditoría operativa de su empresa. SUPER_ADMIN consulta los eventos de administración de plataforma; no tiene detalle operativo personal irrestricto. CONDUCTOR ve resultados e historial de sus propias solicitudes y servicios, sin el registro administrativo completo.

**Backend:** Autorizar lectura de auditoría por objeto y empresa: ADMIN operativo propio, SUPER_ADMIN administración de plataforma, CONDUCTOR resultados propios. No conceder lectura personal transversal a SUPER_ADMIN por su nombre de rol.

**CRM:** Separar vistas operativas y de plataforma según rol.

**App:** Mostrar historial propio de solicitudes y servicios sin log administrativo completo.

**Trazabilidad:** `D-V3-Q-085` → `BR-V3-Q-085` → `AC-V3-Q-085-01/02`. Elección: ADMIN de empresa y SUPER_ADMIN de plataforma.

**Criterio esperado:** ADMIN consulta eventos de aprobación/reasignación de su empresa.

**Caso que debe impedirse o tratarse:** SUPER_ADMIN no obtiene detalle operativo personal de todas las empresas por defecto.

#### Q-087 — Contenido de cambio auditado

**Pregunta:** Si se corrige un dato sensible, ¿qué información del cambio debe conservar el registro?

**Respuesta vigente:** Se registra entidad, empresa, campo, valor anterior y nuevo, actor, hora, motivo y referencia de evidencia cuando corresponda. Los valores personales se muestran enmascarados salvo permiso específico y tienen retención definida; nunca se registran secretos.

**Backend:** Guardar diferencias por campo con entidad, empresa, anterior/nuevo, autor, hora, motivo y evidencia pertinente; proteger/enmascarar valores personales y limitar revelado por permiso. No registrar contraseñas, OTP ni tokens.

**CRM:** Revisar cambios enmascarados y revelar datos personales solo con autorización pertinente.

**App:** Solicitar corrección y consultar resultado propio; no editar evidencia ni acceder a cambios ajenos.

**Trazabilidad:** `D-V3-Q-087` → `BR-V3-Q-087` → `AC-V3-Q-087-01/02`. Elección: Cambios por campo con valores protegidos.

**Criterio esperado:** Una corrección conserva valor original y nuevo con autor y motivo, bajo permiso de consulta.

**Caso que debe impedirse o tratarse:** Un token o contraseña no aparece en valores históricos; auditar no elude retención GPS.

### MOD-UX — Dashboard, inicio y perfil

#### Q-088 — Objetivo del dashboard

**Pregunta:** ¿Qué decisión operativa debe facilitar primero el dashboard del ADMIN: resolver incidencias, gestionar ofertas pendientes, reasignar servicios o revisar incorporación?

**Respuesta vigente:** Tras las alertas críticas, la prioridad es resolver servicios próximos sin aceptación confirmada o que necesitan reasignación. Luego se muestran ofertas por vencer, incidencias e incorporación pendiente.

**Backend:** Entregar al dashboard colas por criticidad, proximidad de inicio y asignación válida; mantener alertas de seguridad por encima de la prioridad ordinaria. No usar disponibilidad declarada como aceptación.

**CRM:** Tras alertas críticas, priorizar próximos servicios sin asignación válida/reasignación, luego ofertas por vencer, incidencias e incorporación.

**App:** No altera el inicio móvil; mantiene correspondencia con estados del servidor.

**Trazabilidad:** `D-V3-Q-088` → `BR-V3-Q-088` → `AC-V3-Q-088-01/02`. Elección: Servicios próximos sin asignación válida.

**Criterio esperado:** Un servicio próximo sin aceptación aparece antes que incorporación ordinaria, después de alertas críticas.

**Caso que debe impedirse o tratarse:** Un contador de ofertas pendientes no se presenta como servicios ya cubiertos.

#### Q-089 — Prioridad de inicio móvil

**Pregunta:** En el inicio móvil, ¿qué tiene prioridad cuando conviven una oferta pendiente de aceptación y un servicio ya aceptado próximo a comenzar?

**Respuesta vigente:** El servicio aceptado próximo a iniciar ocupa la tarjeta principal con su acción de inicio. La oferta pendiente aparece claramente con vencimiento y acciones de aceptar o rechazar, sin ocultar el compromiso existente.

**Backend:** Exponer servicio aceptado próximo y oferta pendiente con sus estados y deadlines independientes; volver a comprobar conflictos al aceptar.

**CRM:** Conservar la misma información operativa que utiliza la app sin fusionar oferta y servicio.

**App:** Fijar servicio aceptado próximo en tarjeta principal con Iniciar; destacar oferta y vencimiento separados con aceptar/rechazar.

**Trazabilidad:** `D-V3-Q-089` → `BR-V3-Q-089` → `AC-V3-Q-089-01/02`. Elección: Servicio aceptado próximo, oferta destacada.

**Criterio esperado:** La oferta visible no oculta el servicio aceptado próximo ni su acceso a inicio.

**Caso que debe impedirse o tratarse:** Aceptar una oferta no inicia servicio ni permite solapamiento con uno ya confirmado.

#### Q-090 — Edición de perfil

**Pregunta:** ¿Qué campos del perfil puede cambiar CONDUCTOR directamente, separándolos de identidad, correo de recuperación y documentos que requieren revisión?

**Respuesta vigente:** CONDUCTOR puede cambiar preferencias de avisos, tamaño de texto y una foto de perfil opcional. Identidad, contacto verificado, correo y documentos usan solicitudes o flujos específicos con revisión o verificación; una foto nunca sustituye un documento.

**Backend:** Permitir edición directa de preferencias de avisos/texto y foto de perfil opcional. Identidad, contacto verificado, correo y documentos usan sus procesos específicos; una foto no verifica documentos ni se convierte en foto de placa.

**CRM:** Gestionar solicitudes de cambios que requieren revisión; no tratar preferencias como nueva revisión documental.

**App:** Editar preferencias y foto opcional; guiar cambios de identidad/correo/documentos al flujo correspondiente.

**Trazabilidad:** `D-V3-Q-090` → `BR-V3-Q-090` → `AC-V3-Q-090-01/02`. Elección: Preferencias y foto opcional.

**Criterio esperado:** Cambiar tamaño de texto o foto opcional no cambia estado principal del conductor.

**Caso que debe impedirse o tratarse:** Editar perfil no cambia libremente correo verificado, identidad o documentación aprobada.

### MOD-NFR — Requisitos transversales no funcionales

#### Q-092 — Carga objetivo inicial

**Pregunta:** ¿Cuántas empresas y conductores conectados simultáneamente debe soportar el MVP inicial?

**Respuesta vigente:** Objetivo inicial: hasta 3 empresas, 30 CONDUCTOR y 3 ADMIN conectados simultáneamente en total, además de 1 SUPER_ADMIN. No se promete esta capacidad antes de una prueba con servicios y GPS según los intervalos elegidos.

**Backend:** Objetivo de carga: 3 empresas, 30 conductores y 3 ADMIN concurrentes en total más 1 SUPER_ADMIN. Medir con ofertas, concurrencia y GPS elegidos sobre recursos reales sin gasto; no declarar capacidad acreditada.

**CRM:** Validar funcionamiento de flujos críticos bajo la carga sin mostrar métricas internas innecesarias en el flujo operativo.

**App:** Probar aceptación e inicio bajo carga junto con ubicación cada 30 segundos.

**Trazabilidad:** `D-V3-Q-092` → `BR-V3-Q-092` → `AC-V3-Q-092-01/02`. Elección: 3 empresas, 30 conductores y 3 ADMIN.

**Criterio esperado:** La prueba futura representa 3 empresas y la concurrencia total acordada, con aislamiento.

**Caso que debe impedirse o tratarse:** Elegir el objetivo no se registra como una prueba pasada ni permite contratar infraestructura de pago.

#### Q-093 — Latencia de asignación

**Pregunta:** Bajo esa carga, ¿qué tiempo de respuesta debe tener la creación de una oferta y el registro de su aceptación en el servidor?

**Respuesta vigente:** Bajo la carga elegida, al menos el 95 % de creaciones de oferta y aceptaciones válidas se confirma en servidor en 2 segundos o menos. Se comprueban conflictos y no se declara éxito antes de guardar; los tiempos de red del usuario se informan aparte.

**Backend:** Objetivo p95 <=2 s desde recepción en servidor hasta confirmación durable de creación de oferta/aceptación bajo carga Q-092. Medir red y notificaciones aparte; no omitir transacciones, conflictos o auditoría.

**CRM:** Mostrar progreso, respuesta y recuperación de resultado sin duplicados si se pierde respuesta.

**App:** Esperar confirmación del servidor antes de mostrar aceptación exitosa; consultar estado tras respuesta perdida.

**Trazabilidad:** `D-V3-Q-093` → `BR-V3-Q-093` → `AC-V3-Q-093-01/02`. Elección: 95 % en 2 segundos o menos.

**Criterio esperado:** La prueba futura mide percentil 95 del procesamiento durable en servidor.

**Caso que debe impedirse o tratarse:** Un acuse previo al guardado o un envío push no se usa para cumplir el tiempo de aceptación.

#### Q-094 — Disponibilidad objetivo

**Pregunta:** ¿Cuál es la duración máxima aceptable de una interrupción de plataforma durante el horario operativo, sin modo offline?

**Respuesta vigente:** El objetivo es recuperar una interrupción ordinaria en máximo 30 minutos dentro del horario operativo. Al detectarla se comunica estado y se suspenden acciones nuevas; no se acumulan inicios o aceptaciones offline. Superar el umbral exige decisión operativa de ADMIN.

**Backend:** Objetivo de interrupción ordinaria <=30 min durante horario operativo del piloto, aún por concretar. Detectar y comunicar fallo; no aceptar ni encolar acciones offline.

**CRM:** Mostrar indisponibilidad y coordinar decisión operativa si se supera el umbral.

**App:** Mantener mensaje de desconexión y bloquear acciones que requieren servidor sin simular inicios offline.

**Trazabilidad:** `D-V3-Q-094` → `BR-V3-Q-094` → `AC-V3-Q-094-01/02`. Elección: Hasta 30 minutos por interrupción.

**Criterio esperado:** El objetivo de interrupción ordinaria es 30 minutos y la medición distingue pérdida total.

**Caso que debe impedirse o tratarse:** Durante caída no se confirma ni se encola offline una aceptación o inicio.

#### Q-095 — Objetivo de recuperación RTO

**Pregunta:** Si se pierde el servidor, ¿cuál es el tiempo máximo objetivo para recuperar la operación?

**Respuesta vigente:** Tras pérdida total del servidor, el objetivo es restaurar datos y servicio en 4 horas desde la detección. Se prepara un procedimiento y se prueba con respaldo real en recursos sin gasto; si no se alcanza, no se promete ese plazo.

**Backend:** Objetivo RTO <=4 h desde detectar pérdida total hasta operación restaurada y comprobada. Preparar y probar respaldo y destino dentro de recursos sin gasto.

**CRM:** Comunicar recuperación de pérdida total y verificar operación antes de reabrir acciones.

**App:** Reconectar a operación comprobada y consultar compromisos vigentes sin reproducir acciones offline.

**Trazabilidad:** `D-V3-Q-095` → `BR-V3-Q-095` → `AC-V3-Q-095-01/02`. Elección: Recuperar en 4 horas.

**Criterio esperado:** El ensayo futuro cronometra recuperación completa desde la detección hasta comprobación.

**Caso que debe impedirse o tratarse:** Un servidor levantado sin datos íntegros no se considera recuperación cumplida.

#### Q-096 — Objetivo de recuperación RPO

**Pregunta:** Ante un fallo, ¿cuánto historial reciente como máximo sería aceptable perder y con qué frecuencia deben existir respaldos?

**Respuesta vigente:** Objetivo de pérdida máxima: 1 hora. Se generan respaldos o incrementos al menos cada hora, cifrados y recuperables fuera del servidor original, con comprobación diaria y prueba de restauración. Se verifica cuota y espacio sin nuevos gastos antes de afirmar cumplimiento.

**Backend:** Objetivo RPO <=1 h con respaldos/incrementos al menos horarios, cifrados y recuperables fuera del servidor, comprobación diaria y restauración probada. Definir cuotas, retención y acceso sin pagos.

**CRM:** Administración supervisa respaldo y fallos; no añadir al CONDUCTOR detalles de infraestructura.

**App:** No añade acciones del conductor; conservar coherencia de estados recuperados.

**Trazabilidad:** `D-V3-Q-096` → `BR-V3-Q-096` → `AC-V3-Q-096-01/02`. Elección: Máximo 1 hora de datos.

**Criterio esperado:** Un respaldo recuperable fuera del servidor limita pérdida objetivo a una hora en el ensayo.

**Caso que debe impedirse o tratarse:** Una copia en el mismo servidor o un archivo ilegible no cumple el respaldo acordado.

#### Q-097 — Compatibilidad móvil

**Pregunta:** ¿Cuál es la versión más antigua de Android que usan realmente los conductores del piloto?

**Respuesta vigente:** Se toma Android 10 como mínimo provisional de producto, condicionado a confirmar que todos los conductores del piloto cumplen y que el stack funciona. Si hay equipos inferiores se vuelve a evaluar; no se promete soporte hasta probar.

**Backend:** Mantener Android 10 como mínimo provisional de producto. Inventariar equipos y verificar stack/permisos/seguridad antes de declararlo soportado.

**CRM:** Registrar equipos del piloto y casos incompatibles para planificación, no como estado de rechazo del conductor.

**App:** Probar flujos críticos, permisos y GPS en Android 10 y versiones superiores presentes en el piloto.

**Trazabilidad:** `D-V3-Q-097` → `BR-V3-Q-097` → `AC-V3-Q-097-01/02`. Elección: Objetivo provisional Android 10 o superior.

**Criterio esperado:** Android 10 queda registrado como objetivo provisional pendiente de pruebas.

**Caso que debe impedirse o tratarse:** Elegirlo no acredita soporte del stack ni justifica afirmar que todos los equipos del piloto lo cumplen.

#### Q-098 — Compatibilidad CRM

**Pregunta:** ¿Qué navegadores y resoluciones usa el ADMIN para el CRM?

**Respuesta vigente:** Objetivo inicial: Chrome y Edge en sus versiones estables vigentes al piloto, resolución útil desde 1280 × 720. Se registra la versión y se prueban tablas, zoom y formularios. El móvil usa la app del conductor; CRM completo en teléfono queda fuera del piloto.

**Backend:** Objetivo CRM: Chrome/Edge estables al piloto, área útil >=1280×720. Registrar versiones y probar compatibilidad; CRM completo en teléfono queda fuera del piloto.

**CRM:** Validar tablas, zoom, foco y formularios en navegadores y área útil elegidos.

**App:** La app CONDUCTOR conserva su alcance separado; no asumir CRM completo en móvil.

**Trazabilidad:** `D-V3-Q-098` → `BR-V3-Q-098` → `AC-V3-Q-098-01/02`. Elección: Chrome y Edge de escritorio, desde 1280 × 720.

**Criterio esperado:** La prueba futura usa versiones concretas de Chrome/Edge y área útil mínima acordada.

**Caso que debe impedirse o tratarse:** No se promete Safari, tablet o CRM completo en teléfono por esta elección.

#### Q-099 — Accesibilidad objetivo

**Pregunta:** ¿Qué necesidades de accesibilidad deben cubrirse en el MVP, incluida lectura, contraste, tamaño de texto y navegación por teclado en el CRM?

**Respuesta vigente:** El MVP exige contraste legible, texto escalable, acciones que no dependan solo de color, etiquetas y errores claros, foco visible y navegación por teclado en CRM. La app respeta tamaño de texto y ofrece controles identificables; se prueban los flujos críticos con lector de pantalla, sin declarar certificación.

**Backend:** Definir pruebas de accesibilidad de flujos críticos sin afirmar certificación; datos/errores deben permitir etiquetas y orden comprensibles.

**CRM:** Contraste legible, texto escalable, foco visible, teclado, etiquetas/errores claros y lector de pantalla en flujos críticos; respetar Stitch con correcciones justificadas.

**App:** Respetar tamaño de texto, controles identificables y lectura/contraste en flujos críticos; no depender solo de color.

**Trazabilidad:** `D-V3-Q-099` → `BR-V3-Q-099` → `AC-V3-Q-099-01/02`. Elección: Accesibilidad base verificable.

**Criterio esperado:** Las pruebas futuras cubren teclado, escala, lector de pantalla y contraste de flujos críticos.

**Caso que debe impedirse o tratarse:** Una paleta observada en Stitch o pruebas automáticas aisladas no acreditan accesibilidad completa.

## Detalles que aún requieren cierre

Estos pendientes no deshacen las respuestas elegidas ni justifican repetirlas. Son detalles o evidencias concretos que faltan para terminar los contratos.

| ID | Qué falta |
|---|---|
| OPEN-OTP-PROVIDER | Comprobar correo sin nuevos desembolsos y tratamiento de cuotas, fallos y envío inicial. |
| OPEN-SUPERADMIN-RECOVERY | Recuperación segura del titular SUPER_ADMIN sin depender de un ADMIN subordinado ni de servicios de pago. |
| OPEN-REQUIRED-DOCUMENTS | Contrastar el catálogo documental reutilizable de v3 con modalidad de transporte, excepciones y validación interna. No cerrar all documents sin lista concreta. |
| OPEN-RETENTION-PURPOSE | Definir retención por finalidad de documentos, términos, seguridad y evidencia, incluyendo respaldos y excepciones; siete días GPS y seis meses de auditoría operativa ya están elegidos. |
| OPEN-PILOT-RESOURCES | Comprobar recursos sin gasto, canal push, horario operativo, almacenamiento, respaldos y objetivos de carga/latencia/RTO/RPO. |
| OPEN-DEVICE-INVENTORY | Inventariar Android y dispositivos/navegadores reales; el mínimo Android 10 y Chrome/Edge son objetivos elegidos pendientes de pruebas. |
| OPEN-UNIT-CARDINALITY | Precisar si un conductor puede tener más de una unidad vinculada a la vez. La exclusividad de cada unidad entre conductores y la ausencia de servicios superpuestos ya están confirmadas; no deducir la cardinalidad inversa. |
| OPEN-TRACKED-SERVICES | Precisar si todos los servicios de la modalidad piloto requieren seguimiento GPS o si existen clases autorizadas sin seguimiento. Q-076 ya bloquea inicio cuando el servicio lo requiere. |
| OPEN-CLOSED-INCIDENT-PROCEDURE | Definir el tratamiento de recurrencia o cierre erróneo con una incidencia posterior y eventual vínculo histórico, manteniendo prohibida la reapertura. |
| OPEN-MODULE-CONTRACTS | Completar contratos de módulo, excepciones y mapeo de pantallas/variantes con Stitch a partir de las decisiones consolidadas; no confundir entrevista respondida con producto implementado. |

## Comprobaciones y aceptación pendientes

Los criterios se encuentran especificados y **no ejecutados** contra la plataforma. Validar el JSON o el formulario no demuestra rendimiento, coste cero, restauración, accesibilidad, fidelidad visual o cumplimiento normativo.

| ID | Comprobación | Referencias |
|---|---|---|
| VT-V3-OPEN-SUPERADMIN-RECOVERY-01 | Definir y probar la evidencia independiente, custodia y procedimiento de emergencia antes del piloto. | OPEN-SUPERADMIN-RECOVERY |
| VT-V3-OPEN-OTP-PROVIDER-01 | Seleccionar proveedor o recurso existente y verificar condiciones vigentes, cuotas y entrega real; esta elección no confirma viabilidad técnica. | OPEN-OTP-PROVIDER |
| VT-V3-OPEN-REQUIRED-DOCUMENTS-01 | Identificar modalidad real y aprobar la lista exacta, vigencias y excepciones con contraste normativo; este formulario solo decide cómo organizarla. | OPEN-REQUIRED-DOCUMENTS |
| VT-V3-Q-074-01 | Comprobar cuotas y disponibilidad del canal elegido antes de implementarlo. | Q-074 |
| VT-V3-Q-075-01 | Definir aviso de privacidad, finalidad y base aplicable del tratamiento antes del piloto. | Q-075 |
| VT-V3-Q-077-01 | Medir consumo, batería y capacidad con la carga acordada; ninguna cifra acredita viabilidad con S/0. | Q-077 |
| VT-V3-Q-079-01 | Contrastar finalidad, obligaciones de la modalidad y gestión de respaldos antes de fijar la política definitiva. | Q-079 |
| VT-V3-Q-106-01 | Probar geometría del terminal y precisión de dispositivos. La corroboración no demuestra todo el recorrido ni evita falsificación por sí sola. | Q-106 |
| VT-V3-Q-086-01 | Definir retención específica de términos, seguridad, documentos y evidencia según finalidad y obligaciones aplicables; probar volumen y expiración de respaldos. | Q-086 |
| VT-V3-Q-092-01 | Ejecutar prueba representativa de concurrencia, aislamiento, ofertas y GPS con los recursos reales. | Q-092 |
| VT-V3-Q-093-01 | Medir el percentil bajo la carga de Q-092; elegir el objetivo no acredita rendimiento. | Q-093 |
| VT-V3-Q-094-01 | Definir horario operativo del piloto y comprobar el objetivo. La recuperación de pérdida total se decide en Q-095. | Q-094 |
| VT-V3-Q-095-01 | Definir quién ejecuta la restauración y realizar un ensayo completo con tiempos registrados. | Q-095 |
| VT-V3-Q-096-01 | Definir retención de respaldos compatible con borrado de datos personales y comprobar restauración e integridad. | Q-096 |
| VT-V3-Q-097-01 | Recoger el inventario real y verificar las versiones vigentes de Flutter, librerías y requisitos de seguridad. | Q-097 |
| VT-V3-Q-098-01 | Recoger navegador, versión, tamaño útil y zoom de los ADMIN del piloto y probar las combinaciones acordadas. | Q-098 |
| VT-V3-Q-099-01 | Probar los flujos críticos con teclado, escalado y lector de pantalla; verificar los criterios del alcance elegido. | Q-099 |
| VT-V3-MAINTENANCE-DAYS | Especificar validación para mantenimiento que abarca varias fechas, bloqueo de días afectados y retorno real, preservando la decisión de día completo. | Q-036, Q-037 |
| VT-V3-GPS-ACCESS | Concretar quién verifica y atiende solicitudes de puntos detallados, plazos y alcance por finalidad, respetando retención de siete días. | Q-078, Q-079 |
| VT-V3-AUDIT-RETENTION | Definir cómputo de seis meses calendario, eventos sujetos al plazo, excepciones justificadas y gestión de copias; no aplicar a toda prueba contractual o documento automáticamente. | Q-086, Q-087 |
| VT-V3-INCIDENT-CLOSED | Precisar tratamiento de cierre erróneo o recurrencia mediante caso posterior, sin habilitar reapertura en el MVP. | Q-070 |

## Orden de trabajo recomendado

1. Cerrar modalidad piloto y lista exacta de documentos, márgenes por tipo de servicio y alcance GPS; concretar custodia/evidencia para la recuperación del titular.
2. Probar correo y push sin nuevos gastos, inventario de equipos, almacenamiento y respaldo fuera del servidor. No comprometer cuotas o capacidades todavía no medidas.
3. Completar contratos de incorporación y unidad, mantenimiento, ofertas/aceptación y ejecución por tramos, con transacciones, permisos y variantes de error.
4. Completar incidencias sin reapertura, comunicados, reportes versionados y auditoría con retención por finalidad.
5. Mapear esos contratos a componentes y variantes existentes de Stitch y ejecutar aceptación, carga, restauración y accesibilidad. Registrar evidencia real; no generar pantallas adicionales por cada regla si una variante del componente resuelve el caso.

## Historial y alcance

La v3 conserva respuestas anteriores, snapshots v2, decisiones y reglas sustituidas y el archivo de respuestas como procedencia. Las cinco preguntas archivadas cuya propuesta se confirmó vuelven al registro vigente como RESPONDIDA; su archivo original sigue siendo histórico. No se renumeraron las preguntas. Los IDs POL-* y OPEN-* del formulario se conservaron.

Los 17 módulos conservan cobertura parcial donde falta contrato o evidencia. No se modificaron los proyectos Stitch ni se inspeccionó el código de la plataforma. La operación confirma decisiones de negocio y objetivos; no certifica que todas las reglas posibles estén descubiertas.

Fuente de respuestas SHA-256: `3fd57da3dbe871ec8d84e53f051d8a442bdf896307b82d679df9d8b2d87c77c6`.

Base v2 SHA-256: `b0f99998f4d8ce48da63976df9fb6bc1378e3fb8f584733690cb28e5a534adb3`.


## Memoria actualizada: roles y módulos declarados

La tabla del usuario se registra como inventario declarado. No demuestra que cada módulo esté implementado o probado. La aclaración posterior establece: **«No existe soporte offline para la plataforma»**. Queda descartada la frase anterior de sincronización de pendientes con soporte offline básico.

| Rol / superficie | Módulos declarados |
|---|---|
| SUPER_ADMIN / Web | Resumen global; gestión de organizaciones |
| ADMIN / Web | Resumen; usuarios; organización; grupos; conductores; vehículos; asignaciones; incidencias; comunicados; reportes; auditoría |
| CONDUCTOR / Android | Inicio; perfil; disponibilidad; asignaciones; incidencias; comunicados; ubicación con consentimiento en primer plano |

Rutas, servicios y mantenimiento conservan sus reglas de dominio aunque aún deba mapearse dónde aparecen en la navegación. La ubicación se limita al primer plano con consentimiento y al servicio iniciado: no se promete captura con la aplicación cerrada ni seguimiento de fondo. El objetivo de treinta segundos se prueba dentro de ese alcance.

Arquitectura reportada: CRM Angular/TypeScript, Android Flutter/Dart, API compartida Java/Spring Boot y PostgreSQL/Flyway. Las versiones históricas quedan en el JSON para contrastarlas con los manifiestos y archivos de dependencias del repositorio; no se propone actualizarlas durante la semana.

La estrategia de seis perfiles, trabajo por procesos, contratos compartidos y calendario de siete días se documenta como propuesta en `ruta_fija_plan_agentes_7_dias.md`. No cambia decisiones de negocio ni acredita un despliegue.
