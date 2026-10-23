# SMTP: configuración efectiva, entrega y recepción — 2026-10-15

L-01 EN_CURSO. Evidencia posterior al recheck de configuración; los diagnósticos anteriores se conservan como cortes históricos.

## Discrepancia resuelta

El usuario autorizó expresamente `rutafija.dev1@gmail.com` como remitente y mantuvo `sandrito10xyz@gmail.com` como destinatario de la única prueba controlada. La comparación se realizó por separado:

| Fuente | Observación anterior a corregir configuración permitida |
|---|---|
| `backend/.local/ruta-fija-native.env`, `SPRING_MAIL_USERNAME` | Una definición; coincidía con el nuevo remitente autorizado. |
| Mismo archivo, `SPRING_MAIL_PASSWORD` | Una definición; se cargó únicamente la credencial privada vigente, sin imprimirla. |
| `backend/src/main/resources/application-local.properties`, `app.otp.email.allowed-sender` | Conservaba el remitente anterior. Era el origen de esta discrepancia; no requería atribuir otra corrección al archivo privado. |
| Configuración de destinatario permitido | Coincidía con el destinatario ya autorizado. |

Se actualizó exclusivamente el remitente permitido al valor autorizado; no se amplió la lista de destinatarios. La constante de la prueba del adaptador se actualizó al mismo valor. No se modificaron credenciales privadas.

## Ejecución controlada

Agente A-OPS/SMTP: importación `scripts/Import-RutaFijaNativeEnvironment.ps1 -Scope Runtime` y adaptador real `SmtpOtpEmailSender`, con transporte Gmail. Las comprobaciones booleanas independientes de usuario SMTP, remitente permitido y destinatario resultaron verdaderas antes del envío.

- `smtp.gmail.com:587`; autenticación, STARTTLS habilitado y obligatorio.
- Tiempos máximos de conexión, lectura y escritura: 5000 ms; debug deshabilitado.
- `SMTP_ATTEMPTS=1`; resultado observado `SMTP_ACCEPTED`; ningún reintento automático.
- Código aleatorio de seis dígitos, vencimiento de cinco minutos, solo en memoria. No se conservaron OTP, contraseña, MIME ni token en la evidencia.
- El destinatario confirmó expresamente: «Sí, llegó a entrada»; luego reiteró haber recibido el correo y autorizó continuar. No se solicitó ni compartió el código por chat.

El auxiliar temporal de diagnóstico se retiró mediante eliminación individual no recursiva de sus cuatro archivos y del directorio vacío. No contenía credenciales ni el OTP. No se dejó servicio Spring Boot, conexión de aplicación o migración por esta ejecución.

## Alcance probado y pendiente

Quedan acreditadas la configuración efectiva del adaptador, aceptación por Gmail y recepción en bandeja de entrada según el destinatario autorizado. No se ejecutó este envío a través de un desafío OTP persistido ni de login HTTP; por tanto, **no prueba consumo real del OTP, creación de sesión ni recorrido completo de aplicación con SMTP real**. Las reglas negativas, uso único y límites se validan separadamente con dobles y PostgreSQL de pruebas.

No se ejecutarán nuevos envíos automáticamente para convertir esta evidencia de transporte en una prueba integral. L-01 no se cierra por la recepción del correo.
