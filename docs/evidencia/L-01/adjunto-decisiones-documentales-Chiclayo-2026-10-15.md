---
documento: "Ruta Fija — Decisiones documentales y de contrato para L-01"
version: "0.1.0"
fecha: "2026-10-15"
zona_horaria: "America/Lima"
estado: "DECISIONES CONFIRMADAS Y PROPUESTAS PARA APROBACIÓN"
lote: "L-01 EN_CURSO"
referencia_principal: "OPEN-REQUIRED-DOCUMENTS; Q-003; Q-018"
ambito_confirmado: "Provincia de Chiclayo, departamento de Lambayeque, Perú"
modalidades_confirmadas: ["TAXI", "COLECTIVO"]
contiene_credenciales: false
---

# Ruta Fija — Decisiones documentales y de contrato para L-01

Este archivo reúne las decisiones que deben entregarse a Codex para concretar el catálogo documental y completar la aprobación/rechazo del conductor dentro de L-01. Incorpora el ámbito y los tipos de unidad indicados por el titular y las dos decisiones de contrato que necesitan aclaración: identidad preliminar Q-018 y límites de cuentas ADMIN.

**No declara cerrado L-01, no inicia L-02 y no aprueba términos.** Las propuestas identificadas como pendientes no se convierten en decisiones aprobadas por adjuntar o leer este archivo. No contiene contraseñas, códigos de recuperación ni documentos personales.

## 1. Estados y procedencia

| Estado | Interpretación |
|---|---|
| CONFIRMADO POR EL TITULAR | Declaración expresa recibida en esta conversación. Puede incorporarse a la ficha y contrato como alcance del piloto. |
| REPORTADO EN EL PROYECTO | El agente local comunicó que existe o se decidió; debe contrastarse con código, contrato y evidencia actuales. |
| PROPUESTA CONCRETA | Recomendación lista para revisar y aprobar o corregir. No es una aprobación implícita del titular. |
| POR CONTRASTAR | Hace falta una fuente aplicable, comprobación o dato específico. No puede resolverse con un valor inventado. |

Fuentes de proyecto: registro V3.1; ficha factual de 2026-10-15; corte técnico Q-018 comunicado por el titular en esta conversación; y sus aclaraciones posteriores sobre taxis, colectivos, Chiclayo y unidades. El código adjunto anterior no prueba los cambios recientes V18/V19.

## 2. Alcance ya confirmado

| ID | Decisión | Estado |
|---|---|---|
| DD-01 | El piloto corresponde a empresas de taxi y empresas de colectivos. Cada organización mantiene sus datos y recursos separados. | CONFIRMADO POR EL TITULAR |
| DD-02 | Se ejecutará en la provincia de Chiclayo, departamento de Lambayeque, país Perú. Zona operativa America/Lima. | CONFIRMADO POR EL TITULAR |
| DD-03 | Se desea admitir vehículos sedán de cuatro ruedas o más, referidos por el titular como M1, y vehículos de tres filas. No se amplía a buses, motos, mototaxis, carga ni otras modalidades por inferencia. | CONFIRMADO POR EL TITULAR; clasificación de cada unidad POR CONTRASTAR |
| DD-04 | El catálogo será versionado y separará documentos del conductor, del vehículo y referencias de autorización de la empresa/servicio. | Método aprobado según el corte recibido |
| DD-05 | La decisión de aprobar o rechazar al conductor corresponde al ADMIN de su organización. Correo verificado e invitación aceptada no la sustituyen. | Regla V3.1; criterio de L-01 confirmado en el corte |
| DD-06 | Mientras falten requisitos o validaciones necesarias, no se habilita acceso operativo. La aprobación/rechazo no se traslada unilateralmente fuera de L-01. | Alcance de cierre confirmado en el corte |

DD-02 sustituye, para el lugar del piloto, la respuesta anterior de la ficha que decía Lima. **No confirma la ciudad física del PC**, que es una ubicación de infraestructura distinta. El alcance provincial no acredita por sí mismo que una empresa, unidad o ruta tenga autorización válida.

### 2.1 Precisión obligatoria: M1 y vehículos de tres filas

M1 no significa simplemente sedán ni vehículo de cuatro ruedas. El Reglamento Nacional de Vehículos describe la categoría M para transporte de pasajeros con cuatro ruedas o más y M1 con hasta ocho asientos para pasajeros, sin contar el del conductor. Tres filas pueden corresponder a M1 u otra categoría; no constituyen un criterio suficiente de clasificación. Véase referencia R1.

Para el producto se registrarán por separado: categoría vehicular documentada, carrocería, cantidad de asientos/capacidad autorizada y número de filas cuando sea necesario para delimitar el piloto. No se deduce categoría por apariencia, nombre comercial o cantidad de ruedas.

**DD-07 — Propuesta de admisión a confirmar:** admitir sedanes M1 y vehículos de tres filas cuya categoría documentada también sea M1 y cuya habilitación permita el servicio concreto. Si la tarjeta o evidencia técnica indica M2 u otra categoría, mantener esa unidad sin habilitación para el piloto hasta que el titular decida expresamente ampliar el alcance y se contraste su normativa. No reclasificarla como M1 para hacerla pasar.

Esta propuesta interpreta de manera conservadora la intención de usar autos y tres filas; **el titular todavía no ha confirmado que todos los tres filas deban ser exclusivamente M1**. No rechazar permanentemente ni habilitar automáticamente una unidad por esa ambigüedad.

## 3. Catálogo técnico propuesto: conductor

**DD-08 — Propuesta:** aprobar la siguiente base de expediente personal. Los requisitos condicionales se activan con una regla documentada de modalidad/jurisdicción, no con una suposición del programador.

| ID de requisito | Documento/dato | Aplicación propuesta | Campos mínimos | Revisión y vigencia |
|---|---|---|---|---|
| CON-01 | Documento de identidad del conductor | Obligatorio para identificar al solicitante. Para el piloto previsto: DNI; otro documento admitido requiere una regla explícita. | Tipo, número y nombres/apellidos. Emisor y fin de vigencia cuando correspondan. | Misma persona del expediente y documento admisible. No inventar fecha de vencimiento ni desconocer excepciones aplicables. |
| CON-02 | Licencia de conducir | Obligatoria para habilitar conducción. | Número, clase, categoría, restricciones relevantes, emisor, fecha de vencimiento y resultado de revisión. | Vigente y compatible con unidad/modalidad; debe cubrir el periodo de conducción previsto. No basta que el número tenga un formato válido. |
| CON-03 | Credencial o habilitación de conductor para el servicio | Condicional: exigir si la autoridad competente la requiere para esa modalidad. | Tipo, número/referencia, autoridad emisora, modalidad/ámbito y fechas aplicables. | Confirmar exigibilidad local y revisar que no esté vencida o suspendida según la comprobación disponible. No importar automáticamente una credencial ATU de Lima/Callao al piloto de Chiclayo. |
| CON-04 | Otros requisitos de habilitación personal expresamente aplicables | Solo cuando se identifique su fuente y necesidad para la modalidad concreta. | Nombre del requisito, fundamento, resultado y referencia mínima. | Un requisito aún no contrastado se marca POR DEFINIR; no se considera cumplido ni se elimina silenciosamente del catálogo. |

No se proponen certificados médicos, antecedentes, SCTR, constancias de capacitación u otros archivos personales como exigencia universal sin identificar su aplicabilidad. Esto **no afirma que nunca puedan ser exigibles**: si la modalidad o autoridad establece un requisito, debe incorporarse antes de habilitar la operación correspondiente. No se recogen diagnósticos ni información adicional solo por estar disponible.

### 3.1 Compatibilidad de licencia

La referencia general del MTC vincula A-IIa con taxi M1; el reglamento de automóvil colectivo distingue A-IIa para M1 y A-IIb para M2 y contempla categorías superiores compatibles. Referencias R2 y R3. Esto orienta el catálogo; no reemplaza revisar las reglas aplicables al servicio local.

**DD-09 — Propuesta:** para unidades M1 admitidas, validar una categoría de licencia autorizada para esa conducción y servicio. No aceptar A-I por el solo hecho de que sea un automóvil. Las categorías compatibles se representan mediante una tabla explícita y versionada; no mediante una comparación textual del tipo `categoria >= A-IIa`.

No imponer una duración genérica de tres, cinco o diez años a todas las licencias. Se registra la fecha de vencimiento del documento y el resultado de la revisión disponible. Si cambia la categoría o se renueva, se conserva el historial de revisión pertinente.

### 3.2 Cuenta y contacto, separados de documentos

Correo verificado, teléfono cuando corresponda, identidad preliminar/cuenta y consentimiento o información aplicable son datos del proceso. No son una licencia ni una autorización de transporte. El OTP acredita control del canal dentro de sus límites, no aprobación documental. La contraseña no forma parte del catálogo ni del expediente visible al ADMIN.

## 4. Catálogo técnico propuesto: vehículo

**DD-10 — Propuesta:** revisar la unidad en un expediente propio. El conductor con unidad empresarial no tiene que aportar documentos de propiedad a su nombre; el ADMIN gestiona la unidad de flota y su vínculo autorizado.

| ID de requisito | Documento/dato | Aplicación propuesta | Campos mínimos | Revisión y vigencia |
|---|---|---|---|---|
| VEH-01 | Tarjeta de identificación vehicular o documento de identificación admitido | Base obligatoria de identificación. | Referencia, placa, categoría, carrocería, marca/modelo, año y asientos/capacidad documentada. Número de filas como dato de alcance cuando corresponda. | Coincidencia con la unidad. No exigir renovación anual de un documento sin ese vencimiento ni confundir tarjeta con permiso de servicio. |
| VEH-02 | Seguro obligatorio aplicable al servicio | Necesario antes de habilitar operación real. | Tipo de seguro, entidad emisora, número/referencia, placa/unidad cubierta, uso/cobertura aplicable, inicio y fin. | Vigente y apto para servicio y ámbito. La aceptación de CAT en sustitución de SOAT no se activa sin confirmar su aplicabilidad. |
| VEH-03 | Certificado de inspección técnica vehicular | Exigible según servicio, categoría y reglas aplicables. | Número, centro emisor, placa, resultado y fechas. | Resultado apto y vigente cuando sea obligatorio. No fijar un plazo universal de seis o doce meses por inferencia. |
| VEH-04 | Habilitación, tarjeta de circulación o autorización vehicular aplicable | Exigir el título correspondiente al servicio local, con su denominación real. | Tipo, número, autoridad, empresa/unidad vinculada, modalidad, ámbito/ruta y fechas aplicables. | Válido para esa unidad y servicio. No asumir que todos los permisos se llaman TUC ni copiar plazos de una resolución individual a toda la flota. |
| VEH-05 | Autorización interna de uso/vínculo | Necesaria para ofertar un servicio a un conductor. | Unidad, conductor, organización, modalidad de unidad propia/empresarial, inicio/fin, ADMIN autorizante y resultado. | No superposición de vínculos incompatibles; fin requerido para unidad empresarial según el registro vigente. Es una regla de Ruta Fija, no una habilitación municipal. |

La falta de una unidad propia no impide que ADMIN apruebe el expediente personal cuando cumpla sus requisitos. Puede quedar **APROBADO, PENDIENTE DE UNIDAD**, sin conducción ni ofertas operativas hasta disponer de una unidad revisada y vinculada. No duplicar los documentos de flota dentro de cada expediente personal.

## 5. Empresa, autorización y rutas

**DD-11 — Propuesta:** registrar las referencias de habilitación de empresa/servicio que correspondan y comprobar su relación con las unidades y rutas. No pedir al conductor que asuma el trámite de toda la empresa.

| Apartado | Información mínima propuesta | Comprobación |
|---|---|---|
| Empresa operadora | Identidad empresarial declarada y referencia de autorización cuando corresponda. | Que la organización que pretende operar sea la titular o esté válidamente vinculada. Crear una organización en la plataforma no acredita autorización externa. |
| Servicio de taxi | Modalidad, autoridad, autorización/títulos aplicables y ámbito autorizado. | Revisar la exigencia provincial aplicable; no extrapolar automáticamente reglas ATU. |
| Colectivo | Modalidad específica, autoridad, autorización, código o referencia de ruta y recorrido/paradas autorizados cuando corresponda. | El catálogo de rutas de Ruta Fija debe respetar el servicio autorizado. No basta escribir origen/destino. |
| Ámbito del piloto | Provincia de Chiclayo, Lambayeque, Perú. | Una ruta que sale de esa provincia requiere decisión adicional y revisión del ámbito/autoridad, aunque siga dentro de Lambayeque. |

Las fuentes municipales consultadas muestran trámites de taxi y resoluciones para auto colectivo en Chiclayo, pero **no se obtuvo una lista normativa consolidada y completa de requisitos locales de todas las variantes**. No se declara aquí que el catálogo satisfaga por sí solo cada requisito municipal. Antes de cerrar la aplicabilidad se debe contrastar el TUPA/ordenanza/condiciones vigentes con la modalidad y autorización reales del participante. R4 y R5 son referencias para esa comprobación, no una lista universal de permisos.

### 5.1 Rutas de taxi y colectivos

La distinción de producto debe quedar registrada: taxi directo puede necesitar solo origen y destino; colectivos usan la ruta y paradas correspondientes a su operación. La regla previa Q-040 exige parada intermedia para una ruta utilizable: su variante para taxi directo necesita quedar aprobada y versionada antes de cambiarla.

Este documento no autoriza implementar todo el módulo de rutas dentro de L-01 ni altera por sí solo Q-040. Codex debe registrar la dependencia y trabajarla en su lote, sin presentar una ruta interna como permiso de transporte.

## 6. Campos comunes, vigencias y excepciones

**DD-12 — Propuesta de contrato de revisión:** cada requisito contiene, al menos:

| Campo lógico | Regla propuesta |
|---|---|
| Identificación | ID del requisito, entidad propietaria —persona, vehículo o empresa— y versión del catálogo. |
| Aplicabilidad | Modalidad, categoría y ámbito; condición de obligatoriedad; fuente o justificación. |
| Documento | Tipo, referencia/número, emisor y datos estrictamente necesarios para comprobarlo. |
| Tiempo | Emisión/inicio y vencimiento cuando existan; ausencia de vencimiento diferenciada de dato desconocido. |
| Resultado | PENDIENTE, REVISADO_CONFORME, OBSERVADO o NO_APLICA_JUSTIFICADO como etiquetas propuestas, sin imponer que ya existan estos enums. |
| Revisor | ADMIN, organización, fecha/hora del servidor y método de revisión. |
| Evidencia mínima | Referencia de comprobación y observación; sin secretos ni copias indiscriminadas en auditoría. |
| Renovación | Versión anterior/reemplazo y motivo; no sobrescribir la historia necesaria. |

Reglas:

1. Un vencimiento desconocido no se transforma en «sin vencimiento» ni en una fecha lejana ficticia.
2. La licencia y los documentos operativos exigibles deben cubrir el periodo del servicio. Se verifican en los puntos de control del contrato vigente, sin borrar la aprobación histórica al vencer.
3. **NO_APLICA** requiere condición y motivo verificables; no es una casilla genérica para omitir requisitos obligatorios.
4. No se permite una excepción interna que contradiga un requisito externo obligatorio. Un caso dudoso queda en revisión hasta resolverlo.
5. La revisión visual documentada no equivale a certificación oficial automática. No se introduce integración RENIEC/MTC.
6. Los errores materiales y cambios de identidad/contacto usan los flujos autorizados. No se sustituye a una persona editando el expediente ni se cambia correo por una ruta genérica.

## 7. Datos estructurados, archivos y conservación

### 7.1 Modalidad recomendada de evidencia

**DD-13 — Propuesta recomendada para el piloto:** guardar datos estructurados y el resultado de una revisión visual por ADMIN, **sin almacenar fotografías, escaneos o PDF de DNI/licencia por defecto**. Presentación del documento por un procedimiento controlado, sin exigir copias permanentes en chats o correo.

Ventajas: menor exposición de datos, almacenamiento y trabajo de protección de archivos. No elimina la necesidad de revisión ni garantiza que sea suficiente para cada obligación aplicable. Si una norma o necesidad probada exige conservar copia, definir primero el caso concreto y su protección.

Esta propuesta sigue pendiente de aprobación. La ficha anterior contemplaba documentos reales e imágenes; si se aprueba el modelo sin archivos, se actualizan sus respuestas, aviso de datos y términos para que no describan una carga que no ocurrirá.

### 7.2 Si el titular decide conservar copias

La decisión alternativa debe completar **antes de habilitar la carga real**:

- Lista exacta de documentos y páginas/partes que se conservan, finalidad y necesidad.
- Ubicación privada real, fuera del repositorio y del directorio público del CRM; acceso mediante API autenticada y autorizada.
- Acceso del titular y del ADMIN autorizado de su empresa; acceso técnico excepcional justificado. Sin enlaces públicos ni acceso personal irrestricto de SUPER_ADMIN.
- Formatos, tamaño máximo, controles de contenido y descarga, permisos, protección en tránsito/reposo y tratamiento de errores.
- Plazo de conservación de cada categoría, eliminación en datos activos/copias controladas y tratamiento antes de restaurar.
- Evidencia de configuración y pruebas; una ruta escrita en un documento no demuestra que exista almacenamiento seguro.

No se almacenan documentos físicos originales en la plataforma: se trata de **copias digitales** cuando esa opción sea elegida. No guardar un PDF simplemente porque el formulario admita adjuntarlo.

### 7.3 Conservación propuesta y decisiones ya declaradas

| ID | Categoría | Plazo/acción | Estado |
|---|---|---|---|
| DD-14A | Datos estructurados del expediente y revisiones del piloto | Propuesta: eliminación a los 30 días de terminar el piloto, con excepciones justificadas por finalidad/obligación aplicable; deshabilitación al cierre. | PROPUESTA para concretar el expediente; no plazo legal universal |
| DD-14B | Copias documentales | Si se aprueba DD-13, no se conservarán; si se eligen copias, fijar plazo específico antes de recogerlas. | DEPENDE DE DD-13 |
| DD-14C | Términos y aceptaciones | 90 días después del piloto. | Declarado en ficha; su aplicación y el texto final se contrastan |
| DD-14D | Metadatos mínimos de recuperación | 30 días desde terminar el trámite. | Declarado en ficha; no prolonga validez de tokens |
| DD-14E | Auditoría operativa | Seis meses, con datos personales mínimos y acceso restringido. | Regla V3.1; no copiar documentos/GPS para extender retención |
| DD-14F | Detalle GPS | Siete días; corresponde al lote de ubicación. | Regla V3.1; no se implementa seguimiento por este archivo |

El responsable del piloto ejecuta o encarga autorizadamente deshabilitación, eliminación e inventario de copias. Se registran resultados y fallos. Los respaldos no se presumen creados ni restaurados por haber elegido Drive. La eliminación de datos operativos y la conservación mínima de auditoría se explican por separado, sin prometer «borrar todo» a los 30 días.

El contacto para solicitudes de datos será el que figure confirmado en la ficha. Se verifica identidad mediante un medio previamente establecido y se entrega únicamente el alcance autorizado. No se solicitan contraseñas ni documentos innecesarios para atender una consulta. Estos acuerdos no equivalen a aprobación jurídica ni reemplazan información previa al participante.

## 8. Aprobación, rechazo y habilitación dentro de L-01

**DD-15 — Propuesta de recorrido para concretar Q-003 y OPEN-REQUIRED-DOCUMENTS:**

1. La solicitud llega a PENDING_REVIEW con identidad y empresa destinataria identificadas. Eso no crea por sí solo acceso operativo.
2. ADMIN autenticado, verificado y de esa organización consulta el expediente restringido. Se audita el acceso.
3. Se evalúan los requisitos aplicables de la versión del catálogo. El listado general no expone números de documentos, correos ni teléfonos innecesarios.
4. ADMIN aprueba el expediente personal conforme o registra observaciones/rechazo con motivo. No aprueba por el solo hecho de que se verificó correo o invitación.
5. Las observaciones, devoluciones, subsanación y reenvío conservan solicitud e historial y respetan Q-020/Q-021. No contabilizar un reintento HTTP como nueva ronda.
6. La creación o vinculación posterior de AppUser/Driver se ejecuta según la decisión de identidad de la sección 9, sin duplicar personas/cuentas ni introducir una membresía activa incompatible.
7. La aprobación personal, selección de grupo, vinculación de unidad y habilitación operativa quedan diferenciadas. El corte reporta como condición un grupo activo: Codex debe contrastar su autorización/contrato y no añadir facultades por pertenencia a grupo. La falta de unidad se muestra expresamente, sin fingir aprobación vehicular.
8. Antes de ofertar o iniciar, se exige organización/ADMIN habilitados, documentos operativos, unidad/vínculo, aceptación y demás condiciones del contrato. Aprobar la incorporación no omite esas guardas.

La ausencia de documentos de una unidad empresarial todavía no elegida no se trata como falsedad del conductor ni obliga a que sea propietario. Lo que no puede habilitarse es conducir una unidad sin los controles aplicables.

La implementación de aprobación/rechazo pertenece a L-01 según el corte recibido. No se mueve a otro lote para poder marcar este como VERIFICADO. Mapa, navegación y validación física de L-08 conservan su alcance propio.

## 9. Decisión de contrato DC-01: identidad preliminar Q-018

### 9.1 Diferencia que debe quedar explícita

| Aspecto | Propuesta anterior conversada | Implementación comunicada en V18/V19 |
|---|---|---|
| Identidad al aceptar | CONDUCTOR autenticado y con correo verificado. | Identidad preliminar separada de AppUser/Driver. |
| Sesión | Acceso restringido de una cuenta verificada. | No hay sesión web/móvil operativa; login rechaza esa identidad preliminar. |
| Invitación y solicitud | Consumo y solicitud en una transacción idempotente. | Se reporta consumo al verificar OTP y solicitud PENDING_REVIEW en una transacción idempotente. |
| Alta de cuenta/conductor | La cuenta ya existe; falta pertenencia y operación autorizadas. | AppUser/Driver se crearían o vincularían después de aprobar. |

Las dos variantes pueden separar incorporación de operación; **no son el mismo contrato de autenticación**. Pasar las pruebas de la variante implementada no aprueba por sí mismo el cambio respecto de la anterior.

### 9.2 Recomendación para decidir sin rehacer trabajo innecesario

**DC-01A — PROPUESTA RECOMENDADA:** aceptar expresamente la variante de identidad preliminar ya construida, siempre que el contrato la describa como postulante que acredita control del correo en un flujo limitado a incorporación, no como CONDUCTOR operativo autenticado. Crear/vincular la misma persona en la aprobación, sin duplicar cuentas.

Condiciones de aceptación:

- Código dirigido al destinatario y OTP con sus límites; el token/reto tiene propósito y alcance restringidos.
- Consumo de invitación/cupo y solicitud atómicos e idempotentes; reintento devuelve la misma solicitud.
- Un ID de solicitud o una clave de idempotencia **no son credenciales para leer el expediente**.
- Definir el reingreso del postulante para consultar estado/subsanar mediante un acceso de incorporación autorizado. No habilitar sesión operativa ni pedir OTP en cada consulta por comodidad de diseño.
- Al aprobar, asociación única de identidad, organización, cuenta y conductor; no crear segunda cuenta para personas existentes ni permitir dos roles/empresas activos.
- Mantener rechazo de login operativo antes de completar sus condiciones, con pruebas del acceso permitido y denegado.
- Actualizar contrato/criterios y memoria para que no sigan prometiendo una sesión CONDUCTOR anterior al alta que esta variante no proporciona.

**Alternativa DC-01B:** mantener el contrato anterior de cuenta verificada con acceso restringido antes de solicitud y ajustar la implementación. Implica revisar el trabajo existente; no se elige automáticamente.

**Estado de decisión del titular:** PENDIENTE DE CONFIRMACIÓN entre DC-01A y DC-01B. Si Codex encuentra aprobación explícita posterior en el proyecto, debe citarla y comprobar su alcance, no volver a preguntar ni atribuirla a este archivo.

## 10. Decisión de contrato DC-02: límites 1 / 4 / 20 y reemplazos ADMIN

El corte comunica límites de creación del piloto: 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR, y bloqueo de un quinto alta ADMIN incluso si es reemplazo. La ficha anterior también utilizó esos valores como distribución de cuentas. Debe aclararse qué se contó: activas, reservadas, personas distintas o altas históricas.

### 10.1 Opciones y recomendación

| Opción | Interpretación | Efecto de reemplazar un ADMIN |
|---|---|---|
| DC-02A — recomendada | Máximo simultáneo de cuentas activas/habilitadas del piloto: 1 / 4 / 20 en total, con historia preservada. | Se permite reemplazo autorizado sin superar el máximo activo. No se borra al saliente. |
| DC-02B | Máximo acumulado de creaciones históricas. | Un reemplazo puede quedar bloqueado aunque se revoque al saliente; requiere asumir expresamente esa restricción y su procedimiento. |

**DC-02A es una propuesta, no una corrección ya aprobada.** No se debe eliminar silenciosamente un límite acumulado si el titular lo autorizó expresamente en otra interacción.

### 10.2 Reglas técnicas si se elige DC-02A

- Los cupos son totales del piloto, no cuatro ADMIN o veinte conductores por cada empresa, salvo nueva decisión expresa.
- SUPER_ADMIN realiza el reemplazo tras verificación independiente por contacto previo seguro o presencial. No se obtiene un quinto ADMIN operativo por usar PENDING_ACTIVATION.
- El candidato puede completar activación limitada; el relevo efectivo revoca al saliente y habilita al reemplazo de manera consistente, sin exceder el cupo ni dejar un cambio parcial.
- Conservar IDs, historial, aceptaciones y auditoría del saliente; no borrar registros para liberar cupos.
- Definir cómo se reserva cupo en activaciones pendientes y cómo expira/cancela esa reserva, sin permitir reservas ilimitadas.
- En recuperación de la misma cuenta no se cuenta una persona/alta nueva por restablecer credenciales.
- Probar dos altas o reemplazos concurrentes y límites exactos. No afirmar capacidad de carga solo porque existe un cupo de negocio.

**Estado de decisión del titular:** PENDIENTE DE CONFIRMACIÓN de DC-02A o DC-02B y de qué estados consumen/reservan cupo. La cantidad de personas reales que participarán continúa siendo una decisión separada de capacidad y altas.

## 11. Matriz de decisiones que Codex debe registrar

| ID | Tema | Estado al emitir este archivo | Acción inmediata |
|---|---|---|---|
| DD-01 / DD-02 | Taxi/colectivo y ámbito Chiclayo–Lambayeque–Perú | CONFIRMADOS | Actualizar ficha, contrato y memoria; no volver a pedir la modalidad general o provincia. |
| DD-03 | Sedán y tres filas como alcance físico deseado | CONFIRMADO | Registrar intención; clasificar cada unidad con evidencia documental. |
| DD-07 | Tres filas solo si es M1, sin ampliación automática M2 | PROPUESTA | Confirmar la frontera de admisión; no confundir filas con categoría. |
| DD-08 / DD-09 | Catálogo personal y compatibilidad de licencia | PROPUESTA + contraste de aplicabilidad | Aprobar lista por variante; verificar requisitos locales condicionales. |
| DD-10 / DD-11 | Catálogo vehicular y autorización de empresa/ruta | PROPUESTA + contraste local | Identificar títulos reales, condiciones y rutas del participante. |
| DD-12 | Metadatos, revisión, vigencia y excepciones | PROPUESTA | Incorporar a contrato con resultados y transiciones explícitas. |
| DD-13 | Solo estructurados/revisión visual, sin copias por defecto | PROPUESTA | Aprobar o sustituir por política concreta de copias; actualizar la ficha. |
| DD-14A / DD-14B | Conservación del expediente/documentos | PROPUESTA | Aprobar plazo, excepciones, responsable y procedimiento de copias. |
| DD-14C a DD-14F | Plazos ya declarados/especificados para otras categorías | DECISIONES ANTERIORES | Mantener separados; verificar procedimiento efectivo. |
| DD-15 | Revisión y aprobación/rechazo dentro de L-01 | RECORRIDO PROPUESTO sobre criterio obligatorio | Completar contrato y CRM/backend/app según dependencias; no habilitar antes de cumplirlo. |
| DC-01 | Identidad preliminar versus cuenta previa autenticada | ACLARACIÓN NECESARIA | Seleccionar variante y actualizar contrato/pruebas. |
| DC-02 | Cupo activo versus altas históricas | ACLARACIÓN NECESARIA | Seleccionar criterio y estados contabilizables; probar reemplazo. |

### 11.1 Confirmaciones que realmente faltan

La emisión de este archivo no pide repetir todo el formulario. La ronda final puede concentrarse en:

1. Aceptar DD-07 —sedán y tres filas documentados como M1— o precisar la ampliación deseada.
2. Aprobar el catálogo propuesto DD-08 a DD-12, tras identificar y contrastar los requisitos locales condicionales. Confirmar rutas concretas de colectivos y referencias de autorización de participantes.
3. Elegir DD-13 y aprobar DD-14A/B, o definir alternativa de archivos con sus condiciones completas.
4. Elegir DC-01A o DC-01B y DC-02A o DC-02B, salvo aprobación explícita previa acreditada por Codex.

No se necesita subir un DNI ni una licencia real a esta conversación para decidir el esquema. Los datos personales/documentos de participantes se revisan por el procedimiento del piloto, no como anexos de esta memoria.

## 12. Entregables y comprobaciones para completar L-01

Con las decisiones aprobadas, Codex debe preparar o actualizar los artefactos existentes del proyecto:

1. **Contrato documental versionado:** modalidad/ámbito, unidades admitidas, requisitos personales/vehiculares/empresa, obligatoriedad/excepciones, campos, fuentes, vigencias y método de evidencia.
2. **Contrato Q-018 reconciliado:** identidad, prueba de control, estado pendiente, consulta/subsanación restringidas, idempotencia y alta posterior.
3. **Regla de cupos/reemplazo:** ámbito de cómputo, estados, reserva, expiración, concurrencia e historia.
4. **Implementación del recorrido ADMIN:** listado, expediente restringido, revisión, observación, rechazo/aprobación y resultado móvil; autorización server-side por organización.
5. **Pruebas pertinentes:** documentos incompletos/vencidos/incompatibles, no-aplica injustificado, otra empresa, actor sin permiso, cuenta duplicada, reintentos, aprobación concurrente y reemplazo al límite.
6. **Datos y términos coherentes:** ficha factual corregida y candidato final de términos; la aprobación del texto sigue siendo expresa y separada. No publicar un borrador como vigente.
7. **Matriz de evidencia:** distinguir regla aprobada, implementación, test simulado y ensayo real. Mantener L-01 EN_CURSO hasta satisfacer todos sus criterios, incluidos los que no son documentales.

Se conservan S/0, ausencia de integración RENIEC/MTC y QR, CONDUCTOR solo en app, aislamiento por organización y exclusión offline. No aplicar migraciones a desarrollo/piloto ni ejecutar Git, publicar servicios o realizar envíos externos por el solo hecho de adjuntar este archivo. Conservar el snapshot final3 y las restricciones de ejecución vigentes.

Este paquete documental **no resuelve por sí solo** SMTP/OTP real, aprobación/publicación de términos, gate backend, alta/reemplazo ADMIN, recuperación CONDUCTOR ni custodia/ensayo SUPER_ADMIN pendientes. Sus criterios y evidencias conservan seguimiento separado. Dispositivo físico permanece en L-08.

## 13. Referencias oficiales y alcance de la consulta

Consulta de referencias: 2026-10-15. Los enlaces son fuentes de contraste para el catálogo y no una certificación de cumplimiento local.

- **R1 — MTC, Reglamento Nacional de Vehículos, Anexo I, categorías M/M1/M2/M3.** Copia publicada por MTC, actualización identificada en el archivo al 23.08.2016. Se usa para la definición de categoría; no se presenta como texto consolidado integral a 2026. [Documento oficial](https://portal.mtc.gob.pe/transportes/terrestre/documentos/REGLAMENTO%20NACIONAL%20DE%20VEHICULOS%20%20actualizado%20al%2023.08.2016.pdf).
- **R2 — MTC, clasificación de licencias de conducir.** Referencia general de compatibilidad de categoría, no una autorización para un vehículo/ruta concretos. [Página oficial](https://portal.mtc.gob.pe/transportes/terrestre/licencias/info_general_clasificacion_licencias.html).
- **R3 — Reglamento del Servicio Temporal de Transporte Terrestre de Pasajeros en Automóvil Colectivo, publicación de 05.04.2022.** Copia oficial en el archivo del Congreso, artículo 8 para referencia de licencia y categoría. Su aplicabilidad y modificaciones se contrastan con la modalidad local antes de cerrar requisitos. [Publicación oficial](https://www2.congreso.gob.pe/sicr/cendocbib/con6_uibd.nsf/22FE798FCFD72CEE05258CEC005359EA/$FILE/2055629-2.pdf).
- **R4 — Municipalidad Provincial de Chiclayo, renovación de permanencia de taxi y otros servicios.** Referencia de trámite publicada en Gob.pe. La consulta de página completa no estuvo disponible; no se extrajeron de ella requisitos no leídos. [Referencia oficial](https://www.gob.pe/55738-solicitar-renovacion-de-permanencia-para-servicio-de-taxi-transporte-turistico-de-estudiantes-o-trabajadores).
- **R5 — Municipalidad Provincial de Chiclayo, Resolución Gerencial 0715-2026-MPCH-GDVT.** El índice oficial la describe como autorización de permanencia para auto colectivo M1 en ruta interurbana. Es un acto particular; no define un plazo o lista universal para todos los participantes. No se pudo revisar el texto íntegro en esta consulta. [Referencia oficial](https://www.gob.pe/institucion/munichiclayo/normas-legales/8489929-0715-2026-mpch-gdvt).

Los trámites de otras regiones y los requisitos ATU no se trasladan sin comprobar competencia y aplicabilidad. Tampoco se presume autorización de una ruta o empresa por su presencia en este documento.

## 14. Registro de aprobación para completar después

| Campo | Estado al generar el documento |
|---|---|
| Hechos confirmados del titular | DD-01, DD-02 y alcance físico DD-03; método de catálogo DD-04 según corte recibido. |
| Propuestas aceptadas en esta edición | Ninguna aprobación nueva se presume por la generación del archivo. |
| Decisiones que seleccionará el titular | IDs y opciones que confirme expresamente. |
| Aprobador y fecha | Se completan con la respuesta efectiva; no usar una firma inventada. |
| Versión final del contrato | Pendiente de aprobación de sus decisiones y contraste de requisitos. |
| Aprobación de términos | Separada y pendiente del texto final identificado. |
| Implementación y pruebas | Deben enlazar evidencia actual del repositorio. |
| Estado de L-01 | EN_CURSO. |

Una revisión posterior conservará este archivo como antecedente, identificará qué IDs fueron aprobados/cambiados y actualizará contrato, ficha, matriz y memoria sin sobrescribir la historia.
