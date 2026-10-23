# Recuperación independiente de SUPER_ADMIN — L-01

**Fecha de decisión:** 2026-10-14  
**Estado:** decisión aprobada por el usuario; flujo backend implementado y probado; cliente operativo pendiente  
**Evidencia de prueba:** `SuperAdminRecoveryFlowIT` y regresión `mvnw verify`, PostgreSQL de pruebas `ruta_fija_test` (V14)

## Decisión aprobada

La recuperación exige conjuntamente:

1. Un código de emergencia vigente.
2. Una firma Ed25519 del reto temporal producida con la clave privada del titular, cuya clave pública X.509 se inscribió antes del incidente durante una configuración inicial autorizada.
3. La verificación OTP del nuevo correo solicitado.

Al completar el flujo, el `SUPER_ADMIN` define una contraseña nueva. El sistema consume el reto y el código anterior, revoca las sesiones vigentes y rota el código de emergencia. Muestra el código nuevo una sola vez y conserva la clave pública inscrita. Un `ADMIN` subordinado no puede inscribir ni autorizar una recuperación, y la recuperación no ofrece una operación para sustituir la clave pública.

La clave privada queda fuera del repositorio, servidor y `backend/.local/ruta-fija-native.env`, y se custodia separadamente del código de emergencia. El sistema nunca recibe ni persiste la clave privada. El código de emergencia se conserva como hash BCrypt; las firmas y credenciales no se escriben en auditoría.

## Comportamiento implementado

- La inscripción requiere sesión válida de `SUPER_ADMIN`, reautenticación con su contraseña vigente, cuenta activa y correo verificado. Es de una sola vez por instalación.
- Se genera un identificador de instalación y un código aleatorio de 32 bytes codificado en Base64URL. La respuesta es `no-store`; el código se muestra una sola vez.
- La solicitud pública de reto devuelve la misma forma de respuesta aunque el correo o la cuenta no sean elegibles, para no confirmar la existencia de la cuenta. El reto elegible se liga a instalación, cuenta, nuevo correo, nonce de un solo uso y vencimiento de cinco minutos.
- El contenido firmado es UTF-8 y tiene siete líneas, separadas por LF y sin LF final: `ruta-fija-super-admin-recovery-v1`, `installationId`, `accountReference`, correo nuevo normalizado, `challengeId`, `nonce` y `expiresAt` en milisegundos Unix. La firma es Base64URL sin padding.
- Se aceptan hasta tres pruebas inválidas por reto; al tercer fallo se consume. La prueba válida habilita el envío del OTP al nuevo correo y conserva los límites aprobados: seis dígitos, cinco minutos, tres envíos por usuario en sesenta minutos, bloqueo tras dos códigos incorrectos por treinta minutos.
- La finalización verifica el OTP, cambia y marca verificado el correo, cambia la contraseña, consume el reto, incrementa `session_version`, revoca refresh tokens y rota el hash del código de emergencia en una transacción. El cambio de `session_version` invalida access tokens anteriores.
- `V14__super_admin_recovery.sql` añade de forma aditiva el estado de recuperación y los metadatos de propósito del OTP. La base de desarrollo no fue modificada.

## Comprobaciones ejecutadas

`SuperAdminRecoveryFlowIT` pasó 4/4 pruebas en PostgreSQL 16.15 aislado: inscripción única; recuperación válida con correo y contraseña nuevos, código rotado, clave pública conservada, refresh/access tokens revocados y rechazo de reutilización; tres firmas inválidas consumen el reto sin enviar OTP; una firma válida con código de emergencia incorrecto se rechaza; y `ADMIN` no puede inscribir la clave. `mvnw verify` pasó 29 pruebas unitarias y 40 de integración, sin fallos, errores ni omitidas. Las pruebas reemplazan el transporte SMTP por un mock; no hubo envío externo.

La primera ejecución detectó que el identificador devuelto difería del persistido; se corrigió y las pruebas volvieron a pasar. También se actualizaron fixtures de integración para que sus usuarios satisfagan la regla vigente de correo verificado.

## Pendientes para el cierre funcional

- Construir y probar el cliente de configuración inicial y el recorrido de recuperación que invoque estas rutas; el backend y las pruebas no sustituyen una validación de uso real. La custodia concreta del archivo/medio de clave privada debe definirse sin colocarla en el servidor o repositorio.
- No hay validación humana del flujo en una interfaz, ni prueba SMTP real de OTP en la recuperación. El envío real queda bloqueado hasta que el usuario confirme que corrigió localmente la contraseña de aplicación.
- El criterio backend cubierto aquí no cierra por sí solo L-01 ni autoriza iniciar L-02.
