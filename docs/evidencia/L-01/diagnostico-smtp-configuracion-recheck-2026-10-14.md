# Revisión segura de SMTP tras el cambio local — 2026-10-14

## Resultado actual

La discrepancia **sigue presente** y la entrega real permanece detenida. No se abrió una conexión SMTP ni se intentó autenticar o enviar un mensaje.

- Archivo privado examinado en memoria, sin imprimir valores: `backend/.local/ruta-fija-native.env`.
- Allí hay una definición de `SPRING_MAIL_USERNAME` en la línea 16 y una de `SPRING_MAIL_PASSWORD` en la línea 17. La contraseña está presente; este examen no la autentica con Google.
- El usuario SMTP configurado no coincide con el remitente permitido.
- El remitente permitido procede de `backend/src/main/resources/application-local.properties:24`, propiedad `app.otp.email.allowed-sender`. El destinatario de la prueba sigue configurado en la línea 25. Ninguna de las dos propiedades se modificó.
- `backend/ruta-fija-native.env.example` contiene una definición de ejemplo para cada variable (líneas 28–29); no es una fuente activa. El `.env` de la raíz no define estas variables. Las definiciones de usuario/contraseña del perfil local son placeholders de entorno en `application-local.properties:16–17`, no valores duplicados.

## Cómo llega la configuración al proceso

Spring Boot no carga `ruta-fija-native.env` automáticamente. `scripts/Import-RutaFijaNativeEnvironment.ps1` lee el archivo, rechaza claves no permitidas o repetidas y coloca los valores permitidos en el entorno del proceso. `scripts/Invoke-RutaFijaNativeRuntime.ps1` importa el alcance Runtime antes de lanzar Java con el perfil local; el proceso Java hereda ese entorno. Las variables de entorno resuelven entonces los placeholders de `spring.mail.username` y `spring.mail.password`. Un lanzamiento directo desde IDE no importa ese archivo por sí solo: depende del entorno/configuración que el IDE entregue al proceso.

En esta revisión no había un proceso `RutaFijaApplication` activo ni variables SMTP de ámbito Process, User o Machine en la sesión de diagnóstico. Por eso no hay una instancia activa cuya fuente efectiva pueda inspeccionarse; tampoco se puede atribuir retrospectivamente la configuración del intento de autenticación anterior. La corrida backend reciente usó el perfil `test`, eliminó del proceso las variables SMTP y empleó dobles de correo; no representa la configuración real de Gmail.

## Corrección manual requerida

Revisar solamente `SPRING_MAIL_USERNAME` en `backend/.local/ruta-fija-native.env` y hacer que coincida con el remitente que ya está permitido. No cambiar `app.otp.email.allowed-sender` ni agregar destinatarios. No hace falta compartir el valor por chat. Avisar cuando el archivo local esté corregido; hasta comprobar de nuevo la coincidencia, no se ejecutará el ensayo real.

No se registró ningún valor SMTP, contraseña, OTP, token ni cuerpo de correo.
