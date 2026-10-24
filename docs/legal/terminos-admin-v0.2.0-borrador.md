# Términos de uso de ADMIN — Piloto académico de Ruta Fija

**Estado: BORRADOR PARA REVISIÓN — NO APROBADO — NO VIGENTE**  
**Versión de trabajo: 0.2.0**  
**Preparación: 2026-10-14**  
**Aplicación: Ruta Fija V3.1, piloto académico**

> Esta versión integra reglas de producto ya acordadas. No acredita que estén implementadas o probadas. La ficha siguiente debe completarse y el texto debe aprobarse antes de utilizarlo para registrar una aceptación real. No reemplaza el aviso de privacidad que corresponda al tratamiento efectivo de datos.

## 1. Ficha del piloto

| Dato necesario | Valor que debe confirmar el responsable |
|---|---|
| Responsable del piloto y del tratamiento de sus datos | [COMPLETAR: identidad real; no solo nombre del producto] |
| Identificación, dirección y representante, cuando corresponda | [COMPLETAR] |
| Persona que aprueba esta versión | [COMPLETAR: nombre y condición en que aprueba] |
| Contacto para cuentas, incidentes y consultas sobre datos | [COMPLETAR: canal atendido; puede ser el mismo para las tres funciones] |
| Periodo y organizaciones participantes | [COMPLETAR: inicio, término y participantes autorizados] |
| Entorno, almacenamiento y proveedores efectivos | [COMPLETAR: API, base de datos, registros, respaldos y servicios externos] |
| Datos autorizados para la prueba y funciones habilitadas | [COMPLETAR: reales/ficticios, categorías necesarias y funciones verificadas] |
| Conservación y retiro de cuentas, aceptaciones y evidencias de recuperación | [COMPLETAR: plazos, responsables, eliminación y copias] |

La ausencia de estos datos no se interpreta como una autorización para tratar información real o publicar términos incompletos. La condición académica no identifica por sí sola al responsable del tratamiento.

## 2. Objeto y límites del piloto

Ruta Fija permite evaluar la administración de organizaciones de transporte y la operación de sus conductores mediante un CRM web y una aplicación móvil. Este documento regula el uso de las cuentas ADMIN dentro del piloto identificado en la ficha.

Solo se habilitarán las funciones expresamente incluidas en la ficha y verificadas para la etapa correspondiente. Una función mencionada en el proyecto o prevista para un lote posterior no se entiende disponible por aparecer en este documento.

La versión vigente del alcance excluye funcionamiento offline y colas de acciones para sincronización posterior. Sin conexión no se declara completada una operación que requiera confirmación del servidor.

El piloto no establece contratación comercial, cobros ni una garantía de disponibilidad continua. Sus objetivos técnicos no constituyen por sí solos compromisos de servicio acreditados. No se introducirán nuevos desembolsos sin decisión expresa del responsable.

## 3. Roles, incorporación y atribuciones de ADMIN

Se contemplan SUPER_ADMIN, ADMIN y CONDUCTOR. Una persona mantiene una organización y un rol activos; cambiar de rol debe sustituir el anterior y conservar trazabilidad, sin duplicar cuentas para acumular permisos.

SUPER_ADMIN crea y autoriza las organizaciones y sus cuentas ADMIN. ADMIN no crea ni habilita otras cuentas ADMIN por su propia autoridad y no adquiere atribuciones de SUPER_ADMIN. Si se requiere reemplazar al único ADMIN, SUPER_ADMIN verifica la solicitud y gestiona el reemplazo preservando la organización y su historial.

La organización queda habilitada según el flujo autorizado después de su creación y autorización por SUPER_ADMIN y de que su ADMIN activo complete la validación inicial y acepte expresamente la versión vigente de estos términos. El inicio de sesión no equivale a esa aceptación.

ADMIN actúa sobre su organización y dentro de las funciones y permisos habilitados. Puede gestionar la incorporación de sus conductores, emitir invitaciones y revisar su expediente. La aprobación o rechazo corresponde al ADMIN activo de esa organización. Verificar un correo, aceptar una invitación o registrar un vehículo no sustituye la aprobación documental.

Cuando estén habilitadas para el piloto, las funciones operativas permiten gestionar grupos, conductores, vehículos, asignaciones, incidencias y comunicados, y consultar resumen, reportes y auditoría autorizados. Este listado no concede acceso a organizaciones ajenas ni convierte cada módulo en una función ya validada en L-01.

## 4. Uso y seguridad de la cuenta

La cuenta se asigna a su titular; sus credenciales, códigos de verificación y medios de recuperación no se comparten. ADMIN no debe solicitar contraseñas de conductores ni incorporarlas a observaciones, archivos o mensajes de soporte.

Se prohíbe utilizar la cuenta para consultar o modificar información de otra organización, eludir límites o autorizaciones, suplantar a otra persona o exportar información fuera del alcance permitido.

La autorización se verifica en el servidor. Una sesión abierta no conserva permisos revocados: las solicitudes posteriores deben aplicar el nuevo estado, y una escritura pendiente debe volver a comprobar la autorización antes de confirmarse.

Ante sospecha de acceso indebido, el titular debe comunicarlo al canal de incidentes de la ficha. Se aplicará el procedimiento de contención documentado por el responsable, con suspensión o revocación cuando corresponda, registro del resultado y recuperación por el flujo autorizado.

La recuperación del conductor mantiene la misma cuenta. El ADMIN de su organización comprueba su identidad mediante el expediente y evidencia independiente obtenida por un contacto previo seguro o en persona. Conocer el DNI o escribir desde un correo nuevo no basta. El nuevo correo requiere verificación y, cuando se haya perdido también la contraseña, se utiliza un mecanismo temporal de un solo uso. Al completar la recuperación se revocan sesiones y se registra el cambio; ADMIN no conoce la contraseña del conductor.

Un ADMIN subordinado no puede autorizar la recuperación de SUPER_ADMIN. Esa recuperación utiliza un procedimiento independiente previamente inscrito y comprobado, separado de la gestión de conductores.

## 5. Invitaciones y verificación de correo

Las invitaciones de incorporación constan de seis dígitos, identifican un destinatario y tienen un único uso, con vigencia máxima de cinco días desde su emisión. Solo puede consumirlas la cuenta destinataria autenticada y con correo verificado. Un reintento de la misma aceptación no debe consumir otro cupo. La incorporación requiere una invitación válida; no se habilita postulación pública por búsqueda de organizaciones.

La invitación y el código OTP son mecanismos distintos. El OTP verifica el control del correo antes del primer acceso efectivo y en los procesos de recuperación que lo requieran; no se exige en cada inicio ordinario de una cuenta ya verificada.

El OTP vence a los cinco minutos y tiene un solo uso. Se permiten hasta tres envíos por persona en una ventana móvil de sesenta minutos. Un reenvío invalida el código anterior sin reiniciar contadores. Dos validaciones fallidas consecutivas invalidan el código y bloquean su validación durante treinta minutos. Cambiar el correo propuesto no elude esos límites.

La validación de invitaciones dispone de límites independientes: cinco códigos incorrectos en quince minutos bloquean ese flujo de la cuenta durante treinta minutos. Existe además un freno por origen para el endpoint de invitación, sin suspender por ello todas las cuentas de una red compartida.

Un rechazo del proveedor, agotamiento de cuota o fallo de entrega no verifica el correo ni habilita acceso por sí mismo. La aplicación debe informar el fallo o la espera aplicable.

## 6. Información utilizada y acceso

Se utilizarán únicamente los datos autorizados en la ficha y necesarios para las funciones habilitadas: datos de cuenta e identidad, organización, estado de incorporación y datos operativos de los servicios. Los documentos, contactos y ubicaciones requieren el alcance específico que corresponda; no se habilitan automáticamente por aceptar estos términos.

ADMIN consulta la operación y auditoría autorizadas de su organización. CONDUCTOR consulta sus propios resultados. SUPER_ADMIN administra la plataforma; ese rol no presume acceso irrestricto al detalle personal operativo de todas las organizaciones.

Cuando se habiliten exportaciones, ADMIN obtendrá la información operativa mínima permitida de su empresa. No se incluirán por defecto DNI completo, contactos, imágenes documentales ni puntos GPS. Las solicitudes de información adicional se atenderán mediante comprobación de identidad y alcance.

Gmail SMTP es el canal aprobado provisionalmente para los correos de verificación del piloto. La cuenta emisora se controla por el responsable; el dominio gmail.com pertenece a Google. El envío transfiere al servicio de correo los datos necesarios del destinatario y del mensaje. Esto no determina dónde se alojan la base de datos, sus copias ni los demás componentes del proyecto; esos destinos deben identificarse en la ficha y en la información de privacidad correspondiente.

## 7. Ubicación, cuando se habilite en el lote correspondiente

La especificación vigente limita el seguimiento a servicios iniciados. Con permiso del dispositivo y conexión, se intenta transmitir ubicación cada treinta segundos; la captura se detiene al finalizar y no existe cola offline. No se promete una posición exacta ni un punto recibido en cada intervalo. El CRM debe indicar la hora y calidad disponibles para no presentar una lectura antigua como ubicación actual.

La posición procede del dispositivo del conductor. No acredita por sí sola la ubicación de un vehículo si este no cuenta con una fuente propia vinculada y validada.

El conductor dispone de resumen propio de inicio, terminal, horarios y resultado; los puntos detallados se solicitan mediante acceso verificado. La regla de producto conserva puntos detallados siete días, tras los cuales se eliminan; solo se prevén evidencias puntuales de incidencias con finalidad y excepción justificadas y acotadas.

Estas reglas deben comprobarse antes de habilitar captura real. Este documento no certifica su implementación en L-01 ni autoriza seguimiento fuera de un servicio iniciado. La información específica al conductor y los permisos del sistema operativo se tratan en su propio flujo; la aceptación de ADMIN no los sustituye.

## 8. Conservación, consultas y retiro de datos

La auditoría operativa tiene una retención de producto de seis meses, con tratamiento acotado de casos abiertos. Ese plazo no se aplica automáticamente a cuentas, términos aceptados, recuperación, documentos o ubicaciones. La auditoría no debe almacenar contraseñas, OTP, tokens ni una copia permanente del trayecto GPS.

La ficha y el procedimiento de conservación deben precisar plazos por categoría, responsables y tratamiento de respaldos y registros antes de usarlos con datos reales. El vencimiento de una cuenta o la eliminación en la base activa no acreditan por sí solos la eliminación de todas las copias.

Las consultas o solicitudes sobre acceso, corrección, retiro y ubicación se presentan al contacto de datos de la ficha, mediante verificación adecuada de identidad. El responsable atenderá el alcance y los derechos aplicables sin revelar información de terceros. Este borrador no inventa plazos legales de respuesta ni atribuye al ADMIN la titularidad de los datos de los conductores.

## 9. Aceptación y cambios

Solo una versión aprobada y publicada puede aceptarse. Se mostrará su contenido completo y la acción será expresa, sin casilla marcada previamente. El usuario podrá consultarlo antes de decidir. Iniciar sesión o seguir usando otra función no sustituye la aceptación.

El registro vinculará cuenta ADMIN, organización, identificador y versión exacta del documento, huella criptográfica de su contenido y fecha/hora del servidor. La fuente publicada se conservará sin sobrescritura. La huella confirma qué contenido se aceptó; no demuestra que el responsable lo haya aprobado ni que la persona lo haya leído.

La primera versión aprobada se publicará como 1.0.0. **Propuesta de política para aprobar junto con el texto:** toda modificación de una versión publicada produce una nueva versión inmutable. Los cambios sustantivos de alcance, obligaciones o tratamiento se presentan para una nueva aceptación antes de habilitar las funciones afectadas. Las versiones y aceptaciones anteriores se conservan según la política específica aprobada; no se reescribe su historia.

La versión 0.2.0 continúa sin vigencia y no admite una aceptación real. La aprobación debe identificar al responsable y el archivo final concreto, después de completar la ficha y verificar su correspondencia con el piloto.
