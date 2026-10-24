# Recuperación asistida CONDUCTOR — incremento Q-015

**Estado:** implementación fuente preparada para revisión; todavía no validada en ejecución. La entrega al titular y cualquier uso de correo real siguen fuera de este incremento.

## Contrato implementado

- Un `ADMIN` verificado solo puede iniciar, consultar, reenviar el OTP o cancelar recuperación de un `CONDUCTOR` activo, verificado y vinculado a un registro `driver` de su misma organización.
- El inicio exige confirmación explícita de revisión del expediente y evidencia independiente. Registra método (`PRIOR_SECURE_CONTACT` o `IN_PERSON`), referencia corta, motivo, cuenta, administrador, organización y marcas de tiempo. No se recibe ni guarda un expediente o documento.
- El correo solicitado queda pendiente hasta que se consume el OTP de seis dígitos. Se conserva el mismo identificador de cuenta. El envío usa `OtpEmailSender` y conserva sus límites y hashes existentes: cinco minutos, máximo tres envíos en sesenta minutos, uso único y bloqueo de treinta minutos luego de dos códigos incorrectos.
- Si no se perdió la contraseña, consumir el OTP cambia el correo y revoca sesiones en la misma transacción. Si también se perdió la contraseña, el OTP solo prueba control del correo nuevo y emite un token aleatorio de un solo uso. El correo, la contraseña y la revocación de sesiones permanecen sin cambio hasta consumir ese token; entonces se aplican juntos en una única transacción.
- Un token vencido o una recuperación cancelada conserva el correo y las sesiones anteriores. La solicitud pendiente puede cancelarse desde el CRM; la retención de su metadata se mantiene pendiente de decisión.
- El administrador nunca recibe ni puede consultar la contraseña. Los endpoints de prueba automática sustituyen `OtpEmailSender` por un mock; no está autorizada una entrega SMTP por esta implementación.

## Rutas fuente

| Operación | Ruta | Acceso |
|---|---|---|
| Autorizar y comenzar | `POST /api/v1/users/{userId}/conductor-recovery` | `ADMIN` de la organización |
| Consultar datos y estado | `GET /api/v1/users/{userId}/conductor-recovery/{recoveryId}` | `ADMIN` de la organización |
| Reenviar OTP | `POST /api/v1/users/{userId}/conductor-recovery/{recoveryId}/otp/resend` | `ADMIN` de la organización |
| Cancelar | `POST /api/v1/users/{userId}/conductor-recovery/{recoveryId}/cancel` | `ADMIN` de la organización |
| Verificar correo solicitado | `POST /api/v1/auth/conductor-recovery/otp/verify` | Token de verificación y OTP |
| Restablecer contraseña | `POST /api/v1/auth/conductor-recovery/{recoveryId}/password-reset` | Token temporal posterior al OTP |

Las respuestas contienen `Cache-Control: no-store`. El token de verificación se devuelve al administrador que autorizó el flujo para su entrega segura al conductor; el OTP llega únicamente al nuevo correo. El token de restablecimiento se muestra una sola vez en la respuesta posterior a verificar el OTP.

## Decisiones y pendientes

- La retención de metadatos de expediente revisado, método y referencia de evidencia y motivo no está especificada en Q-015. Esta implementación los persiste únicamente en `conductor_recovery_request`, sin adjuntos, DNI ni duplicación del texto libre en el registro de auditoría; la duración de retención y el mecanismo de eliminación quedan pendientes de decisión.
- Q-015 no fija la vigencia del token temporal de contraseña. Se implementó provisionalmente una vigencia de 30 minutos. Confirmar esta duración antes del cierre del criterio.
- No existe todavía cliente CRM/app que orqueste la entrega del token de verificación al conductor y que recoja el OTP y el token temporal con protección adecuada. El contrato de backend no declara completa la recuperación de extremo a extremo.
- `ConductorRecoveryFlowIT` se añadió con `OtpEmailSender` como doble. Incluye que, al exigir cambio de contraseña, correo/sesión permanezcan iguales tras OTP, y que solo cambien al consumir el token; también cubre expiración y cancelación. `ConductorRecoveryRequestTest` fija la igualdad exacta del instante de expiración y el consumo único. Ambas pruebas siguen sin ejecutar hasta que se abra una ventana serial de pruebas backend. No se ejecutó Maven, no se accedió a PostgreSQL y no se envió correo.

## Validación ejecutada — 2026-10-14

La afirmación anterior de que las pruebas no se habían ejecutado corresponde a su fecha de redacción y queda actualizada por el [corte técnico de continuación 2](corte-incremento-2-2026-10-14.md): `ConductorRecoveryRequestTest` 1/1 y `ConductorRecoveryFlowIT` 5/5 pasaron dentro de `mvn verify`, con `OtpEmailSender` simulado y solo `ruta_fija_test` V17. No hubo envío externo. El TTL de reset de 30 minutos y la retención siguen pendientes; no se declara completo el recorrido de cliente.
