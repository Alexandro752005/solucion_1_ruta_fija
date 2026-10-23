# Diagnóstico seguro de configuración SMTP — 2026-10-14

## Resultado

La prueba real permanece detenida. El preflight local no abrió conexión SMTP ni generó un mensaje.

- Archivo activo: `backend/.local/ruta-fija-native.env`.
- `SPRING_MAIL_USERNAME`: una definición, línea 16. El valor configurado **no coincide** con el remitente permitido.
- `SPRING_MAIL_PASSWORD`: una definición, línea 17. Está presente y su formato local coincide con el patrón esperado de contraseña de aplicación; esto no demuestra autenticación válida.
- No se encontraron definiciones duplicadas de esas variables dentro del archivo activo. La plantilla `backend/ruta-fija-native.env.example` no es una fuente activa. El `.env` de la raíz no define estas variables.
- El remitente permitido procede de `backend/src/main/resources/application-local.properties:24` (`app.otp.email.allowed-sender`). El destinatario permitido está en la línea 25. No se modificaron.
- `SmtpOtpEmailSender` inyecta usuario, remitente y destinatarios desde la configuración Spring y rechaza el envío antes de SMTP cuando el usuario no coincide con el remitente permitido (`backend/src/main/java/pe/rutafija/identity/infrastructure/SmtpOtpEmailSender.java:37-63`).

## Carga y precedencia verificadas

Spring no carga automáticamente `ruta-fija-native.env`. `scripts/Import-RutaFijaNativeEnvironment.ps1` lee el archivo indicado, rechaza nombres no permitidos y duplicados, y coloca las variables en el entorno del proceso (`:12-18,44-48,52-76,94-106,174-178`). `scripts/Invoke-RutaFijaNativeRuntime.ps1` valida esa configuración, la instala temporalmente en su entorno y arranca Java con el perfil `local` (`:288-306,506-538`). Java recibe esas variables heredadas; `application-local.properties:16-17` resuelve `spring.mail.username/password` desde ellas. En un arranque directo desde el IDE, ese archivo no se importa por sí solo.

El remitente autorizado es una propiedad distinta y fija del perfil local; el adaptador compara ambos valores y no modifica la lista permitida para acomodar credenciales.

## Fuente efectiva del proceso observado

En este corte no se encontró un proceso backend de Ruta Fija escuchando en `127.0.0.1:8080`. El único `java.exe` observado no correspondía a `RutaFijaApplication` y no tenía puertos en escucha. Las variables SMTP no están definidas en el entorno Process/User/Machine de esta sesión. Por tanto, no existe una instancia activa a la que atribuir ahora una configuración efectiva y no se puede inferir la fuente usada en el ensayo anterior. Si se inicia mediante el lanzador nativo, la fuente sería el archivo privado importado al entorno heredado por Java.

## Acción manual pendiente

Revisar únicamente `SPRING_MAIL_USERNAME` en `backend/.local/ruta-fija-native.env` y dejarlo igual al remitente que ya está permitido. No cambiar `app.otp.email.allowed-sender`. Avisar cuando esté corregido; hasta entonces no se hará la prueba real autorizada.

No se muestran valores SMTP ni se registraron contraseñas, OTP, tokens o cuerpos de correo.
