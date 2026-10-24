# Prueba SMTP controlada L-01 — 2026-10-14

## Alcance y resguardo

- Se usó únicamente la configuración local de Gmail aprobada para el piloto: remitente y destinatario de la lista permitida del perfil `local`.
- `backend/.local/ruta-fija-native.env` existe. El importador lo cargó en el entorno del proceso; no se modificó el archivo ni se imprimió el valor de ninguna credencial.
- Se comprobó que el usuario SMTP coincide con el remitente autorizado y que la variable de contraseña está presente. Un indicador local de formato esperado devolvió falso; no se registraron ni la longitud ni los caracteres.
- No se ejecutó el flujo HTTP contra la base de desarrollo y no se envió a otra dirección.

## Procedimiento observado

1. Se verificaron los valores de `application-local.properties`: `smtp.gmail.com:587`, autenticación, STARTTLS habilitado y obligatorio, y timeouts de conexión, lectura y escritura de 5000 ms.
2. Un auxiliar temporal fuera del árbol fuente invocó el adaptador real `SmtpOtpEmailSender` con un código de seis dígitos generado en memoria y el único destinatario autorizado. El auxiliar no imprimió el código.
3. La llamada falló con `ExternalServiceUnavailableException`, la respuesta genérica intencional del adaptador.
4. Se hizo una conexión diagnóstica separada que exigía STARTTLS y autenticaba, sin crear ni enviar un mensaje. Gmail respondió `AuthenticationFailedException`.

## Resultado

**No verificada: autenticación rechazada; ninguna entrega confirmada.** El diagnóstico indica que el fallo ocurrió al autenticar. El mensaje no obtuvo aceptación SMTP y no hay evidencia de recepción. No se reintentó el envío para evitar duplicados.

La variable local no coincide con el formato esperado que se comprobó sin mostrarla. Revisar localmente que `SPRING_MAIL_PASSWORD` contenga una contraseña de aplicación válida de la cuenta `rutafija.dev@gmail.com`, sin comillas ni espacios de agrupación, y que la cuenta permita contraseñas de aplicación. No compartir el valor por chat. Cuando la configuración esté corregida, se podrá repetir la prueba controlada ya autorizada.

La prueba solo ejercitó el adaptador y la autenticación SMTP. No verifica recepción en la bandeja, cuota S/0, el flujo de login completo con Gmail ni la elegibilidad de la cuenta en sus ajustes de Google. Las pruebas automáticas anteriores siguen usando dobles para no enviar correo.

## Otros criterios de L-01

La revisión estática local encontró que la gestión existente de organizaciones está limitada a `SUPER_ADMIN` y la gestión de usuarios a `ADMIN`, pero no se encontró provisión de cuentas `ADMIN` por `SUPER_ADMIN`, aceptación versionada de términos, flujo de invitación ni flujo de recuperación del conductor o de `SUPER_ADMIN`. Esos criterios siguen abiertos; `OPEN-SUPERADMIN-RECOVERY` requiere definir la comprobación independiente de control del proyecto.

No se cambió el snapshot `final3`, no se ejecutaron comandos Git y no se inició L-02.

## Preflight local posterior — 2026-10-14

Se volvió a comprobar `backend/.local/ruta-fija-native.env` sin imprimir ni guardar sus valores. El archivo existe; `SPRING_MAIL_USERNAME` no coincide con el remitente permitido; la variable de contraseña está presente y sus caracteres, omitiendo espacios de agrupación solo para el indicador local, cumplen el patrón de 16 caracteres alfanuméricos. Esto no acredita que Google acepte la contraseña.

No se abrió conexión SMTP ni se envió mensaje durante este preflight. Como el nombre de usuario no coincide con la lista permitida, se detuvo antes de la prueba controlada previamente autorizada. Corregir localmente `SPRING_MAIL_USERNAME` para que sea exactamente la cuenta emisora aprobada y volver a avisar; no enviar credenciales por chat. Tras corregirlo se hará una sola prueba al destinatario autorizado, verificando por separado aceptación SMTP, recepción y consumo del OTP.