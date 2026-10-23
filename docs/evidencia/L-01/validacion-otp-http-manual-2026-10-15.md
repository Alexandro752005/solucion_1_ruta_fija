# L-01 — Validación manual de OTP persistido y consumo HTTP

> **Corte vigente 2026-10-17: CI-L01-01 VERIFICADO.** El titular completó la continuación fa616262-500e-4490-9292-7ee1927d2257, contrastada en la base por RUN_ID/caso, consumo y limpieza. [Evidencia durable y reanudación de limpiezas](cierre-parcial-CI01-y-terminos-2026-10-17.md). No repetir el envío. Los estados pendientes y el fallo descritos debajo son históricos.

> **Actualización vigente 2026-10-16:** el titular ya ejecutó el caso `3e9c0bf5-237b-4370-a87f-cea684e0b1d8`: hubo emisión/recepción, pero Enter vacío detuvo el consumo. Limpieza e historial contrastados. Las declaraciones de «no ejecutada» siguientes son del corte histórico del 15. Continuación segura, pruebas sin SMTP y comandos actuales: [recuperación selectiva del caso](otp-recuperacion-selectiva-2026-10-16.md). No usar `-Execute` solo para repetir; usar el RUN_ID explícito después de inspección y prueba ficticia. CI01 integral sigue pendiente.

Fecha: 2026-10-15. **Preparada; entrega real de este recorrido NO EJECUTADA.** L-01 EN_CURSO. La entrega aislada anterior del adaptador y la confirmación de entrada se conservan en su evidencia; no se sustituyen por esta preparación.

## Qué falta acreditar

CI-L01-01 exige además de entrega/recepción la verificación mediante el flujo persistido. El mensaje anterior usó un OTP temporal del adaptador, sin desafío HTTP persistido: ya venció y no debe reutilizarse ni enviarse por chat.

El validador prepara una invitación y una organización inequívocamente sintéticas en `ruta_fija_test`, con ADMIN inactivo, para recorrer las rutas reales:

1. POST `/api/v1/onboarding/requests`: emisión mediante el adaptador SMTP, persistencia de hashes/ventana de cinco minutos y un solo envío.
2. El titular introduce el OTP recibido exclusivamente en su consola local oculta.
3. POST `/api/v1/onboarding/otp/verify`: consumo y solicitud `PENDING_REVIEW`; sin JWT, cookie, pertenencia operativa, AppUser o Driver del solicitante.
4. Se comprueban consumo, invitación usada y ausencia de duplicación; se deshabilita únicamente el fixture exacto, invalida sus credenciales y preserva el ledger histórico.

No acepta términos, no aprueba documentos, no realiza pruebas físicas ni inicia otro lote. El destinatario es exclusivamente `sandrito10xyz@gmail.com` y el remitente autorizado `rutafija.dev1@gmail.com`. El usuario SMTP y el remitente permitido se comparan por separado; no se modifica la lista permitida.

## Ejecución local del titular

Desde la raíz del proyecto, en una terminal interactiva con Java 21. No usar transcripción, capturas de secretos ni redirección de entrada/salida. No introducir OTP, contraseñas ni tokens en argumentos o en este chat.

```powershell
# Solo compila el helper y prepara sus dependencias; no inicia Spring/DB/SMTP:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -Prepare

# Modo predeterminado: valida configuración y destino sin conectar DB ni SMTP:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1

# Recorrido interactivo. Este comando sí inicia el backend de pruebas y,
# solo tras la confirmación local requerida, hace UNA emisión:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -Execute
```

La ejecución pide la frase local `ENVIAR_UN_OTP_L01` antes de crear el fixture y emitir. No es una contraseña. Después pide confirmar recepción escribiendo `RECIBIDO` (sin código) y el OTP por entrada oculta. Si hay fallo, espera agotada, dato inválido o pérdida de respuesta, se detiene; **no reenvía ni reintenta automáticamente**. No repetir `-Execute` por su cuenta para forzar un resultado.

## Guardas y coste del fixture

- Configuración privada existente, sin cambios ni valores impresos. SMTP Gmail587/auth/STARTTLS obligatorio; conexión/lectura/escritura5000ms, debug de correo desactivado.
- Destino validado por la configuración nativa: exclusivamente `ruta_fija_test`, usuario/migrador de pruebas. Configuración del proceso prioritaria y perfil test; no utiliza la base de desarrollo. Test es loopback y puerto aleatorio, sin túnel.
- Antes de emitir comprueba cupo ADMIN y que no haya cuenta/identidad preliminar/invitación activa del destinatario. Si existe un conflicto, se detiene sin envío; no borra datos ni reinicia límites para superarlo.
- El fixture consume **una creación ADMIN histórica de la base de pruebas** aunque se desactive. No afecta los cupos de una base piloto inexistente. Los registros finales permanecen identificados; no se ejecuta TRUNCATE, limpieza global o borrado del ledger.
- Tareas programadas se retiran solo del proceso manual para no caducar o limpiar casos ajenos mientras espera al titular. No cambia la configuración de producto ni habilita offline.
- El cierre conserva una invitación ya usada como usada; solo revoca invitaciones propias aún sin consumir. No fuerza estados incompatibles del contrato.

## Evidencia que se puede comunicar

Solo resultado saneado, fecha, `MANUAL_OTP_RUN_ID`, banderas de persistencia/consumo/ausencia de sesión y resultado de limpieza; si falla, fase y código de resultado. Nunca OTP, token, contraseña, cuerpos HTTP o credenciales. El resultado global satisfactorio se imprime después de confirmar el cierre del fixture.

La automatización usa **`ManualOtpValidationHarnessIT` con Tomcat y PostgreSQL reales, destinatario sintético y `OtpEmailSender` simulado**. Comprueba la misma lógica HTTP/SQL y el cierre de sus registros sin alterar una organización ajena. Su resultado se registra en el [incremento vigente](incremento-Chiclayo-2026-10-15.md); no prueba entrega externa. El helper manual no se llama desde Surefire/Failsafe ni por un test automático con SMTP real.

Hasta que el titular ejecute este recorrido y exista resultado satisfactorio/recepción, el consumo integral real sigue pendiente. No se declara verificado por haber preparado el script o por la recepción del mensaje anterior.

## Precisión de evidencia y reutilización — continuación de revisión

No se reabrió la discrepancia SMTP anterior: quedó resuelta y la recepción del primer correo ya está confirmada. Falta una ejecución integral nueva del desafío persistido, no una nueva corrección inferida de credenciales. El script usa únicamente su valor privado vigente y se niega a emitir ante una discrepancia actual.

El resultado distingue solicitud HTTP confirmada, éxito devuelto por el transporte, desafío hasheado/TTL persistido, recepción confirmada por el titular en consola y consumo HTTP/estado final. Las marcas `ISSUED_AT_UTC` y `CONSUMED_AT_UTC` son momentos observados de cada paso; las condiciones temporales exactas se contrastan con las filas del desafío. No son capturas del mensaje del proveedor ni se conserva el código. El éxito global aparece solo después de limpiar exclusivamente su fixture.

**Q-018 tiene recibo idempotente:** tras consumir la prueba, repetir el mismo token de incorporación puede devolver el mismo requestId/estado mínimo sin validar de nuevo un OTP, sin otro envío, sesión o solicitud. No sería correcto exigir que ese recibo sea un 4xx ni modificar el contrato para conseguirlo. Las comprobaciones de repetición con código diferente y rechazo de reenvío del desafío consumido se ejecutan **solo con correo simulado** en `ManualOtpValidationHarnessIT`; verifican una sola solicitud y un solo envío. Los endpoints OTP de autenticación/recuperación conservan sus propios rechazos de prueba consumida. No se presenta el recibo como una segunda verificación exitosa.

En el recorrido con Gmail se conserva una única introducción/verificación del OTP; no se añaden pruebas negativas ni reintentos automáticos. Estado consumido, hash OTP retirado y ausencia de otra solicitud se comprueban en SQL. Su acreditación se combina con las negativas simuladas, identificando las dos fuentes.

Ejecutar el recorrido manual solo cuando hayan terminado los procesos de pruebas automatizadas que limpian `ruta_fija_test`. La matriz mantiene la intervención del titular pendiente y no marca CI-L01-01 integral como verificado por preparar o compilar este helper.

El titular respondió «Lo ejecutaré localmente y comunicaré solo el resultado saneado». Es una actuación anunciada, **no un resultado ejecutado**. Hasta recibir RUN_ID, RESULT y cierre saneados, no marcar CI01 integral verificado. No ejecutar más arneses con limpieza de la base durante esa actuación; el agente no hizo un envío externo adicional.
