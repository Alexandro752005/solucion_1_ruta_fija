# L-01 — Alta y reemplazo ADMIN

Estado del lote: EN_CURSO. Decisiones de diseño aprobadas por el titular el 2026-10-15; la evidencia técnica se registra aparte.

## OP-ADMIN-01 — Alta

SUPER_ADMIN crea una cuenta ADMIN inactiva, pendiente de activación, para una organización existente. La creación consume uno de los cuatro cupos históricos aunque la activación no se complete. SUPER_ADMIN no define ni conoce la contraseña de esa persona.

Una credencial temporal de un uso y veinticuatro horas permite establecer la contraseña propia. La continuación del alta solo recibe un acceso opaco de activación, sin sesión CRM, JWT ni refresh. Debe verificar el correo mediante OTP y aceptar expresamente el documento de términos vigente e identificado antes de habilitar la cuenta. No basta conocer la credencial, iniciar sesión o marcar una casilla por defecto.

Una credencial expirada se puede rotar por SUPER_ADMIN para la misma cuenta todavía pendiente e inactiva, con auditoría e invalidación de los medios temporales anteriores. No crea otra cuenta ni devuelve cupo; no sirve para recuperar arbitrariamente un ADMIN activo.

## OP-ADMIN-02 — Reemplazo

SUPER_ADMIN comprueba la solicitud mediante contacto empresarial previamente establecido o presencialmente y registra método, referencia y motivo. No basta un correo nuevo o el conocimiento de un dato personal. Prepara al sustituto sin privilegios, en la misma organización; la preparación ya consume una creación.

Una vez cumplidas las validaciones, se habilita al sustituto y se revocan cuenta y sesiones del ADMIN saliente en una transacción auditada. Se conserva organización e historial. SUPER_ADMIN puede revocar previamente al saliente por seguridad. El reemplazo no impone que solo exista un ADMIN por organización ni autoriza revocar a otros ADMIN ajenos al caso.

Agotadas las cuatro creaciones ADMIN del piloto, no se crea un sustituto adicional. Recuperar la misma cuenta/ID no equivale a crear otra identidad; la recuperación no debe hacerse mediante un reemplazo para evadir el límite.

## Reglas transversales

- Solo SUPER_ADMIN realiza altas o reemplazos; ADMIN no se autoautoriza ni gestiona otros ADMIN.
- El servidor comprueba rol, cuenta, organización, estado de activación y versión de términos antes de confirmar escrituras.
- OTP conserva seis dígitos, cinco minutos, uso único, tres envíos en sesenta minutos y bloqueo de treinta minutos tras dos verificaciones incorrectas; reenvío y rotación no reinician contadores.
- Credenciales y tokens se guardan como hashes; contraseña con BCrypt; sin secretos en auditoría o listas.
- ADMIN_TERMS1.0.0 está aprobado y publicado con SHA-256 `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417`; los borradores previos no son la versión vigente. Pruebas y aceptaciones de cuentas sintéticas no acreditan aceptación operativa de un ADMIN legítimo. No volver a solicitar aprobación del texto publicado.
- Los cupos no reconstruyen personas físicas ni eliminaciones históricas sin evidencia previa. No se declara unicidad personal transversal por comprobar únicamente un correo.

## Contactos y conservación aclarados

El titular confirmó como función «Titular, desarrollador y responsable técnico del proyecto Ruta Fija» y como canal del proyecto `rutafija.dev1@gmail.com`, con revisión cada dos o tres días y sin disponibilidad permanente.

Para soporte operativo, CONDUCTOR se comunicará con el número que ADMIN registre en el apartado de incidencias de su organización. No se proporcionó aún un número ni se implementa por esta declaración la pantalla de otro lote. Es una autorización concreta de contacto operativo, no una autorización general para mostrar datos personales.

ADMIN de la organización es responsable de ejecutar la eliminación del expediente a los treinta días de terminar el piloto conforme a DD-14A/B, registrando ejecución, fallos y excepciones justificadas. La aprobación del responsable y del plazo no acredita que el mecanismo de eliminación ya esté implementado o haya corrido.

Estas aclaraciones no publican términos ni resuelven por inferencia la identificación legal del responsable, sus obligaciones o las condiciones reales de tratamiento.
