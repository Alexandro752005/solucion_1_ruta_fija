# Decisión del proveedor OTP para el piloto (2026-10-14)

**Lote:** L-00  
**Estado:** decisión aprobada para el piloto, de carácter provisional  
**Condición económica:** S/0; no se autoriza contratar Google Workspace ni otro plan o servicio de pago.

## Proveedor y criterio de dominio

Se adopta Gmail SMTP usando la cuenta emisora rutafija.dev@gmail.com, cuya administración está bajo control de la organización según confirmación del usuario. El dominio gmail.com pertenece a Google; la organización no afirma ser dueña de ese dominio ni dispone de dominio propio.

Para este piloto se acepta como criterio que la organización controle la cuenta emisora. No se exige dominio personalizado ni control organizacional de gmail.com. Esta es una excepción explícita y provisional; se podrá cambiar de proveedor después del piloto, manteniendo S/0 salvo aprobación futura.

Google publica una opción Gmail personal sin costo y documenta el servidor smtp.gmail.com, puerto 587, autenticación y TLS/STARTTLS. La configuración aprobada para L-01 es únicamente esa: autenticación obligatoria y STARTTLS obligatorio.

**Destinatario autorizado solo para la prueba controlada de L-01:** sandrito10xyz@gmail.com. No se autoriza el envío a otros destinatarios bajo esta decisión.

## Límites de L-00 y evidencia

No se envió ningún correo en L-00. Tampoco se integró SMTP ni se guardaron credenciales. La selección del proveedor y la excepción de dominio satisfacen la evaluación/decisión del recurso de correo de L-00; la configuración de la aplicación, la elegibilidad de la contraseña de aplicación y la entrega real quedan para L-01.

La cuenta de Google y la disponibilidad de contraseña de aplicación no fueron inspeccionadas desde este entorno. Google exige tener activada la verificación en dos pasos para crear una contraseña de aplicación; la opción puede no estar disponible bajo ciertos ajustes de seguridad o Protección Avanzada. Confirmar en L-01. Si la cuenta no permite autenticación SMTP de forma compatible con S/0, detener la entrega real y registrar el bloqueo; no cambiar a un servicio de pago.

La revisión estática local encontró que backend/pom.xml no declara spring-boot-starter-mail, no hay configuración SMTP de Spring y el importador actual no permite variables SPRING_MAIL_*. La configuración privada existente es backend/.local/ruta-fija-native.env, excluida mediante .gitignore; no se leyeron ni mostraron sus valores y no se modificó. En L-01 se actualizarán el importador y la plantilla privada antes de añadir credenciales. Luego, el usuario ingresará localmente la contraseña de aplicación en esa configuración privada mediante SPRING_MAIL_PASSWORD=<contraseña de aplicación> y el usuario emisor mediante SPRING_MAIL_USERNAME=<cuenta emisora>. No poner la contraseña en el chat, argumentos de comandos ni registros. No añadir todavía estas claves: el importador vigente las rechaza.

## Requisitos autorizados para el traspaso a L-01

- Usar smtp.gmail.com, puerto 587, autenticación y STARTTLS obligatorio mediante un adaptador SMTP separado de la lógica de OTP.
- Verificar spring-boot-starter-mail y definir límites de conexión, lectura y escritura.
- Mantener OTP de seis dígitos, vigencia de cinco minutos, hasta tres envíos por persona en ventana móvil de sesenta minutos, dos errores de verificación y bloqueo de treinta minutos. El OTP debe almacenarse de forma segura, ser de un solo uso y respetar esos límites. El contador de invitación es independiente.
- Usar dobles de prueba en automatización. Reservar el envío real para una validación controlada de L-01 exclusivamente desde la cuenta y hacia el destinatario autorizados.
- La validación con dispositivo físico permanece en L-08.

Estos puntos son traspaso documentado; no se inició L-01 ni se afirma que estén implementados o probados.

## Fuentes oficiales consultadas el 2026-10-14

- [Gmail sin costo y planes](https://workspace.google.com/products/gmail/)
- [Configuración SMTP de Gmail: smtp.gmail.com, autenticación y puerto TLS/STARTTLS 587](https://support.google.com/mail/answer/7104828?hl=en-GB)
- [Requisitos y disponibilidad de contraseñas de aplicación](https://support.google.com/accounts/answer/185833?hl=en)
- [Límites de envío de Gmail](https://support.google.com/mail/answer/22839/limits-for-sending-emails?hl=en-GB)