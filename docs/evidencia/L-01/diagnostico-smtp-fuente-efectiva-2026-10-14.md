# Diagnóstico SMTP y fuente de configuración — 2026-10-14

## Resultado

La discrepancia se reproduce comparando valores en memoria; este registro no contiene el correo ni la contraseña. La variable configurada para autenticación SMTP no coincide con el remitente permitido. No se abrió conexión SMTP ni se intentó autenticar o entregar mensajes. La allowlist permanece intacta.

## Archivos y definiciones

- Fuente privada de ejecución: `backend/.local/ruta-fija-native.env`. Tiene una definición de `SPRING_MAIL_USERNAME` (línea 16) y una de `SPRING_MAIL_PASSWORD` (línea 17). El importador también rechaza claves duplicadas dentro de este archivo.
- Remitente aprobado por la configuración actual: `backend/src/main/resources/application-local.properties`, propiedad `app.otp.email.allowed-sender` (línea 24). El destinatario autorizado se mantiene en la línea 25.
- `SPRING_MAIL_USERNAME` y `SPRING_MAIL_PASSWORD` aparecen como referencias de entorno en `application-local.properties` (líneas 16–17), no como credenciales literales.
- `backend/ruta-fija-native.env.example` contiene valores de ejemplo; no es la fuente activa. No se encontraron estas variables en el `.env` de la raíz ni en la configuración `.vscode` inspeccionada.
- La comparación actual sigue siendo desigual. No se editaron ni el archivo privado ni la allowlist.

## Carga y fuente efectiva

Spring Boot no importa archivos `*.env` por sí solo. `scripts/Import-RutaFijaNativeEnvironment.ps1` lee por defecto `backend/.local/ruta-fija-native.env`, valida claves y duplicados y asigna las variables al entorno del proceso. Con alcance `Runtime` incluye las variables SMTP. `scripts/Invoke-RutaFijaNativeRuntime.ps1` invoca ese importador antes de lanzar el backend local; Java hereda el entorno y Spring resuelve los placeholders del perfil local. Un inicio directo desde el IDE no ejecuta este importador automáticamente y depende del entorno que el IDE entregue.

Durante esta revisión no había listener en el puerto 8080 ni proceso `RutaFijaApplication`; el único proceso Java era el servidor de lenguaje Java de VS Code. Tampoco estaban definidas las variables SMTP en los ámbitos Process, User o Machine del diagnóstico. Por ello no existe ahora un proceso backend cuya fuente efectiva se pueda leer, y no puede atribuirse retrospectivamente la configuración del intento de autenticación anterior. La corrida de `verify` descrita en otra evidencia usó el alcance `Test`, perfil `test`, sin variables SMTP en el proceso y con `OtpEmailSender` de prueba; no es evidencia de autenticación SMTP.

## Siguiente paso permitido

Si se requiere corrección manual, revisar únicamente `SPRING_MAIL_USERNAME` en `backend/.local/ruta-fija-native.env` y alinearla con el remitente que ya está autorizado en `application-local.properties`. No cambiar la allowlist ni compartir el valor por chat. Después se necesita una nueva comprobación segura de coincidencia. La entrega real seguirá detenida hasta resolver y verificar esa discrepancia.

No se registraron secretos, códigos OTP, tokens ni contenido de mensajes.
