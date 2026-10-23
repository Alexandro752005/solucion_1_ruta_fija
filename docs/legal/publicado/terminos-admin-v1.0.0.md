# Términos de uso de ADMIN — Piloto académico de Ruta Fija

**Versión del documento: 1.0.0**  
**Actualización factual: 2026-10-15**  
**Aplicación: Ruta Fija V3.1, piloto académico**

> Este documento establece las reglas de uso del piloto. Solo puede presentarse para aceptación después de aprobarse expresamente y publicarse su contenido identificado. La información de privacidad e identificación se preparará por separado antes del uso de datos reales; estas reglas no la sustituyen ni acreditan por sí solas cumplimiento legal o validación técnica de cada módulo.

## 1. Ficha del piloto

| Dato del piloto | Declaración para esta versión |
|---|---|
| Función y canal técnico | Titular, desarrollador y responsable técnico del proyecto Ruta Fija; contacto del proyecto rutafija.dev1@gmail.com. No se presume por esta función una clasificación jurídica del tratamiento. |
| Información de privacidad e identificación | Se prepara por separado y se entrega por el mecanismo que corresponda antes del uso de datos reales del piloto. No se inventan en estas reglas identidad, dirección o representación no proporcionadas. |
| Función del aprobador | Titular, desarrollador y responsable técnico del proyecto Ruta Fija. La aprobación se registra por separado identificando esta versión y la huella exacta de su contenido. |
| Contacto del proyecto | rutafija.dev1@gmail.com; revisión declarada cada dos o tres días, sin disponibilidad permanente. El soporte operativo CONDUCTOR–ADMIN utilizará el número que ADMIN registre en incidencias de su organización; todavía no se proporcionó el número. |
| Periodo y organizaciones participantes | La idea comenzó aproximadamente el 2026-08-20; esa fecha no inicia plazos del piloto. El inicio efectivo se registrará al habilitarlo y su fin/cierre se registrará en un mismo acto siete días después. 2026-10-26 permanece como estimación; no hay inicio o despliegue efectivo acreditado. Se prevén cinco organizaciones ficticias y hasta 25 personas reales: límites históricos de 1 SUPER_ADMIN, 4 ADMIN y 20 CONDUCTOR. Solo podrán operar las organizaciones con ADMIN activo y condiciones cumplidas; al menos una quedará sin operación con los cupos vigentes. |
| Entorno, almacenamiento y proveedores efectivos | Desarrollo local; PC declarado en Lima, Perú. Base y túnel HTTPS del piloto no activos; no hay copia cifrada ni restauración comprobada. Gmail SMTP provisional S/0 desde rutafija.dev1@gmail.com. No se atribuyen respaldos activos a Drive ni despliegue por esta ficha. |
| Datos y funciones | Piloto previsto de taxi/colectivo M1 documentado por modalidad y título en la provincia de Chiclayo, Lambayeque. Catálogo L01-CHICLAYO 0.1.0: datos estructurados y revisión visual, sin copias. Pruebas actuales sintéticas; no acredita títulos reales ni autoriza recopilar datos reales sin las condiciones operativas documentadas. Mapas/GPS y dispositivo físico tienen sus lotes propios. |
| Conservación declarada | Cuentas ADMIN/CONDUCTOR y expediente se deshabilitan al cierre. A los 30 días del fin se retiran los datos personales, expediente, HMAC DNI y vínculos personales ADMIN/CONDUCTOR previstos en el procedimiento, conservando IDs, historial mínimo y cupos. ADMIN identifica y comunica los expedientes; el responsable técnico ejecuta su retiro restringido y auditado sin reactivar permisos operativos. Excepciones solo por categoría/caso con finalidad y vencimiento. Sin copias documentales. Términos/aceptaciones: 90 días del fin; evidencia mínima de recuperación: 30 días desde terminar/cancelar; auditoría operativa: 6 meses. Ejecución no acreditada. SUPER_ADMIN y su custodia no se retiran por inferencia. |

Las funciones previstas y las condiciones todavía no comprobadas no se presentan como servicios activos. La publicación de reglas de uso no autoriza el uso de datos reales antes de preparar la información de privacidad, controles y condiciones operativas aplicables. La condición académica no identifica por sí sola al responsable del tratamiento.

## 2. Objeto y límites del piloto

Ruta Fija permite evaluar la administración de organizaciones de transporte y la operación de sus conductores mediante un CRM web y una aplicación móvil. Este documento regula el uso de las cuentas ADMIN dentro del piloto identificado en la ficha.

Solo se habilitarán las funciones expresamente incluidas en la ficha y verificadas para la etapa correspondiente. Una función mencionada en el proyecto o prevista para un lote posterior no se entiende disponible por aparecer en este documento.

La versión vigente del alcance excluye funcionamiento offline y colas de acciones para sincronización posterior. Sin conexión no se declara completada una operación que requiera confirmación del servidor.

El piloto no establece contratación comercial, cobros ni una garantía de disponibilidad continua. Sus objetivos técnicos no constituyen por sí solos compromisos de servicio acreditados. No se introducirán nuevos desembolsos sin decisión expresa del responsable.

## 3. Roles, incorporación y atribuciones de ADMIN

Se contemplan SUPER_ADMIN, ADMIN y CONDUCTOR. Una persona mantiene una organización y un rol activos; cambiar de rol debe sustituir el anterior y conservar trazabilidad, sin duplicar cuentas para acumular permisos.

La identificación utiliza un registro común de persona para los tres roles: comprobación manual autorizada y una huella criptográfica vinculada al DNI, sin almacenar copias originales de identidad administrativa ni mostrar número o huella en vistas ordinarias. El titular SUPER_ADMIN realiza su configuración inicial con reautenticación y declaración expresa; luego revisa identidad administrativa de ADMIN. El expediente operativo CONDUCTOR continúa restringido al ADMIN de su organización. El correo verificado no reemplaza la comprobación personal; recuperar una cuenta conserva su identidad y cupo. Una huella vinculable sigue requiriendo protección y no se presenta como dato anónimo irreversible.

SUPER_ADMIN crea y autoriza las organizaciones y sus cuentas ADMIN. El alta ADMIN permanece inactiva hasta establecer contraseña propia mediante credencial de un uso y 24 horas, verificar correo y aceptar términos vigentes. La cuenta pendiente consume cupo histórico. ADMIN no crea ni habilita otras cuentas ADMIN por su propia autoridad y no adquiere atribuciones de SUPER_ADMIN.

SUPER_ADMIN verifica y gestiona las solicitudes de reemplazo de ADMIN. Cuando el reemplazo implique crear una cuenta nueva, requiere cupo histórico disponible y completar su activación segura. Agotadas las cuatro creaciones ADMIN del piloto, no se crea otra cuenta de reemplazo ni se reinicia el contador mediante desactivación o eliminación. La recuperación de la misma cuenta conserva su identidad y no constituye un alta nueva. Los cambios preservan la organización, su historial y la revocación auditada que corresponda. La sustitución y revocación se confirman según el flujo autorizado; se permite la revocación preventiva y no se impone un ADMIN único por organización.

Se prevén cinco organizaciones ficticias. Solo se habilitarán operativamente aquellas que dispongan de ADMIN activo y cumplan las condiciones del piloto. Con el cupo vigente de cuatro creaciones ADMIN, al menos una organización permanecerá sin habilitación operativa. Su existencia como registro de prueba no autoriza compartir cuentas, ampliar permisos o eludir el límite.

La organización queda habilitada según el flujo autorizado después de su creación y autorización por SUPER_ADMIN y de que su ADMIN activo complete la validación inicial y acepte expresamente la versión vigente de estos términos. El inicio de sesión no equivale a esa aceptación.

ADMIN actúa sobre su organización y dentro de las funciones y permisos habilitados. Puede gestionar la incorporación de sus conductores, emitir invitaciones y revisar su expediente. La aprobación o rechazo corresponde al ADMIN activo de esa organización. Verificar un correo, aceptar una invitación o registrar un vehículo no sustituye la aprobación documental.

La aprobación del expediente del conductor, su pertenencia a un grupo y la habilitación o asignación de un vehículo son decisiones distintas. Aprobar el expediente no asigna automáticamente un grupo o una unidad ni habilita por sí solo todas las operaciones. Cada acción requiere las condiciones previstas en su contrato funcional y comprobadas por el servidor. Mientras falte una condición, se mantiene el estado pendiente correspondiente y se informa qué operación continúa restringida. La aprobación personal puede quedar registrada sin grupo; la creación o vinculación posterior de cuenta y conductor exige un grupo activo elegido por ADMIN. Esa provisión mantiene al conductor sin habilitación para conducir mientras falten las condiciones operativas aplicables, incluida la unidad revisada. La aprobación de una persona tampoco acredita por sí sola la documentación o autorización de un vehículo. Estas condiciones no exigen vehículo para toda consulta autorizada.

Cuando estén habilitadas para el piloto, las funciones operativas permiten gestionar grupos, conductores, vehículos, asignaciones, incidencias y comunicados, y consultar resumen, reportes y auditoría autorizados. Este listado no concede acceso a organizaciones ajenas ni convierte cada módulo en una función ya validada en L-01.

## 4. Uso y seguridad de la cuenta

La cuenta se asigna a su titular; sus credenciales, códigos de verificación y medios de recuperación no se comparten. ADMIN no debe solicitar contraseñas de conductores ni incorporarlas a observaciones, archivos o mensajes de soporte.

Se prohíbe utilizar la cuenta para consultar o modificar información de otra organización, eludir límites o autorizaciones, suplantar a otra persona o exportar información fuera del alcance permitido.

La autorización se verifica en el servidor. Una sesión abierta no conserva permisos revocados: las solicitudes posteriores deben aplicar el nuevo estado, y una escritura pendiente debe volver a comprobar la autorización antes de confirmarse.

Ante sospecha de acceso indebido, el titular debe comunicarlo al canal de incidentes de la ficha. Se aplicará el procedimiento de contención documentado por el responsable, con suspensión o revocación cuando corresponda, registro del resultado y recuperación por el flujo autorizado.

La recuperación del conductor mantiene la misma cuenta. El ADMIN de su organización comprueba su identidad mediante el expediente y evidencia independiente obtenida por un contacto previo seguro o en persona. Conocer el DNI o escribir desde un correo nuevo no basta. El nuevo correo requiere verificación y, cuando se haya perdido también la contraseña, se utiliza un mecanismo temporal de un solo uso. Al completar la recuperación se revocan sesiones y se registra el cambio; ADMIN no conoce la contraseña del conductor.

Un ADMIN subordinado no puede autorizar la recuperación de SUPER_ADMIN. Esa recuperación utiliza un procedimiento independiente previamente inscrito y comprobado, separado de la gestión de conductores.

## 5. Invitaciones y verificación de correo

Las invitaciones de incorporación constan de seis dígitos, identifican un destinatario y tienen un único uso, con vigencia máxima de cinco días desde su emisión. La identidad preliminar destinataria verifica su correo y acepta por el acceso limitado de incorporación, sin crear una cuenta o perfil operativos ni iniciar sesión operativa. Puede consultar y subsanar su solicitud mediante ese acceso separado. Un reintento de la misma aceptación no debe consumir otro cupo. La incorporación requiere una invitación válida; no se habilita postulación pública por búsqueda de organizaciones.

La invitación y el código OTP son mecanismos distintos. El OTP verifica el control del correo antes del primer acceso efectivo y en los procesos de recuperación que lo requieran; no se exige en cada inicio ordinario de una cuenta ya verificada.

El OTP vence a los cinco minutos y tiene un solo uso. Se permiten hasta tres envíos por persona en una ventana móvil de sesenta minutos. Un reenvío invalida el código anterior sin reiniciar contadores. Dos validaciones fallidas consecutivas invalidan el código y bloquean su validación durante treinta minutos. Cambiar el correo propuesto no elude esos límites.

La validación de invitaciones dispone de límites independientes: cinco códigos incorrectos en quince minutos bloquean ese flujo de la cuenta durante treinta minutos. Existe además un freno por origen para el endpoint de invitación, sin suspender por ello todas las cuentas de una red compartida.

Un rechazo del proveedor, agotamiento de cuota o fallo de entrega no verifica el correo ni habilita acceso por sí mismo. La aplicación debe informar el fallo o la espera aplicable.

## 6. Información utilizada y acceso

Se utilizarán únicamente los datos autorizados en la ficha y necesarios para las funciones habilitadas: datos de cuenta e identidad, organización, estado de incorporación y datos operativos de los servicios. Los documentos, contactos y ubicaciones requieren el alcance específico que corresponda; no se habilitan automáticamente por aceptar estos términos.

Los datos personales no se muestran en las vistas ordinarias de CRM/app: se usan referencias. ADMIN de la misma organización puede revisar datos personales únicamente en el expediente restringido, con auditoría. El teléfono operativo que ADMIN configure para el contacto de sus conductores es la excepción concreta autorizada, sin habilitar un directorio personal general. ADMIN consulta la operación y auditoría autorizadas de su organización. CONDUCTOR consulta sus propios resultados. SUPER_ADMIN administra la plataforma; ese rol no presume acceso irrestricto al detalle personal operativo de todas las organizaciones.

Cuando se habiliten exportaciones, ADMIN obtendrá la información operativa mínima permitida de su empresa. No se incluirán por defecto DNI completo, contactos, imágenes documentales ni puntos GPS. Las solicitudes de información adicional se atenderán mediante comprobación de identidad y alcance.

Gmail SMTP es el canal aprobado provisionalmente para los correos de verificación del piloto. La cuenta emisora se controla por el responsable; el dominio gmail.com pertenece a Google. El envío transfiere al servicio de correo los datos necesarios del destinatario y del mensaje. Esto no determina dónde se alojan la base de datos, sus copias ni los demás componentes del proyecto; esos destinos deben identificarse en la ficha y en la información de privacidad correspondiente.

## 7. Ubicación, cuando se habilite en el lote correspondiente

La especificación vigente limita el seguimiento a servicios iniciados. Con permiso del dispositivo y conexión, se intenta transmitir ubicación cada treinta segundos; la captura se detiene al finalizar y no existe cola offline. No se promete una posición exacta ni un punto recibido en cada intervalo. El CRM debe indicar la hora y calidad disponibles para no presentar una lectura antigua como ubicación actual.

La posición procede del dispositivo del conductor. No acredita por sí sola la ubicación de un vehículo si este no cuenta con una fuente propia vinculada y validada.

El conductor dispone de resumen propio de inicio, terminal, horarios y resultado; los puntos detallados se solicitan mediante acceso verificado. La regla de producto conserva puntos detallados siete días, tras los cuales se eliminan; solo se prevén evidencias puntuales de incidencias con finalidad y excepción justificadas y acotadas.

Estas reglas deben comprobarse antes de habilitar captura real. Este documento no certifica su implementación en L-01 ni autoriza seguimiento fuera de un servicio iniciado. La información específica al conductor y los permisos del sistema operativo se tratan en su propio flujo; la aceptación de ADMIN no los sustituye.

## 8. Conservación, consultas y retiro de datos

La auditoría operativa tiene una retención de producto de seis meses, con tratamiento acotado de casos abiertos. Ese plazo no se aplica automáticamente a cuentas, términos aceptados, recuperación, documentos o ubicaciones. La auditoría no debe almacenar contraseñas, OTP, tokens ni una copia permanente del trayecto GPS.

Las cuentas ADMIN y CONDUCTOR se deshabilitan al cierre del piloto. A los treinta días de su fin se ejecutará el retiro de datos personales previsto en el procedimiento, identificando por campo qué información se elimina o transforma y qué registros se conservan para una finalidad y un plazo específicos. Se conservan IDs, historial mínimo y cupos consumidos; los HMAC DNI y vínculos personales ADMIN/CONDUCTOR se retiran también a los treinta días, salvo excepción concreta con finalidad y vencimiento documentados. Los identificadores, relaciones o huellas que sigan permitiendo vincular información a una persona mantienen su carácter protegido; su conservación no se presenta como anonimización irreversible. SUPER_ADMIN y su custodia quedan fuera de este retiro por inferencia.

Expediente y revisiones estructuradas se eliminan a los treinta días del fin, con las excepciones concretas justificadas que correspondan. ADMIN identifica y comunica los expedientes de su organización sujetos al retiro previsto antes de deshabilitar su cuenta. La ejecución posterior al cierre se realiza mediante un procedimiento técnico autorizado, de alcance limitado y con registro de resultado, bajo responsabilidad del responsable técnico del piloto. Deshabilitar la cuenta ADMIN no suspende los plazos ni requiere reactivar sus permisos operativos para ejecutar la eliminación; tampoco amplía el acceso ordinario de SUPER_ADMIN al expediente personal. El mecanismo de retiro completo sigue pendiente de implementación y comprobación antes del uso de datos reales.

Términos y aceptaciones se conservan noventa días después del fin; metadatos mínimos de recuperación, treinta días desde la finalización o cancelación del trámite. La vigencia del token final de recuperación del CONDUCTOR es de treinta minutos y un uso; la conservación de metadatos no mantiene utilizable ese token. Los plazos de treinta y noventa días ligados al fin parten del fin/cierre efectivo registrado siete días después del inicio del piloto, no del inicio de la idea ni de la fecha estimada. No se extiende una excepción a otras categorías o a toda la base.

El procedimiento debe precisar la selección de registros, responsables por categoría y tratamiento de respaldos y registros antes de usarlos con datos reales. Conservar una huella HMAC DNI vinculable no acredita anonimización irreversible: deberán identificarse campos y vínculos que se retiren o sigan bajo una finalidad y plazo específicos. No se ejecuta borrado de SUPER_ADMIN o de sus medios de custodia por analogía. El vencimiento de una cuenta o la eliminación en la base activa no acreditan por sí solos la eliminación de todas las copias. Véase el procedimiento local de conservación de L-01; las declaraciones de esta ficha no certifican su ejecución.

Las consultas o solicitudes sobre acceso, corrección, retiro y ubicación se presentan al contacto de datos de la ficha, mediante verificación adecuada de identidad. El responsable revisará la solicitud y su alcance sin revelar información de terceros. Los plazos y condiciones de atención se precisan en la información de privacidad correspondiente al piloto.

## 9. Aceptación y cambios

Solo una versión aprobada y publicada puede aceptarse. Se mostrará su contenido completo y la acción será expresa, sin casilla marcada previamente. El usuario podrá consultarlo antes de decidir. Iniciar sesión o seguir usando otra función no sustituye la aceptación.

El registro vinculará cuenta ADMIN, organización, identificador y versión exacta del documento, huella criptográfica de su contenido y fecha/hora del servidor. La fuente publicada se conservará sin sobrescritura. La huella confirma qué contenido se aceptó; no demuestra que el responsable lo haya aprobado ni que la persona lo haya leído.

La primera versión aprobada se publicará como 1.0.0. Toda modificación de una versión publicada produce una nueva versión inmutable. Los cambios sustantivos de alcance, obligaciones o tratamiento se presentan para una nueva aceptación antes de habilitar las funciones afectadas. Las versiones y aceptaciones anteriores se conservan según la política específica aprobada; no se reescribe su historia.

La aprobación y publicación deben identificar esta versión 1.0.0 y la huella exacta de su contenido. La aceptación expresa de ADMIN registra ese mismo documento; una decisión factual o el inicio de sesión no equivalen a aprobarlo ni a aceptarlo.
