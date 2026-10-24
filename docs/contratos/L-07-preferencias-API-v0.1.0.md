# L07.3 — Preferencias propias v0.1.0

2026-10-21. Implementación pendiente de verificación por A-COORD. Solo CONDUCTOR con sesión MOBILE y pertenencia aprobada; continúa disponible para consulta si pierde habilitación operativa. No selector de usuario/empresa. GET y PUT `/api/v1/mobile/preferences`, `no-store`.

GET sin fila devuelve `{version:0,optionalNoticeAlerts:true,textSize:"NORMAL",mandatoryNoticesAlwaysVisible:true}` sin escribir. PUT exige `{version,optionalNoticeAlerts,textSize}`; tamaños `NORMAL`, `LARGE`, `EXTRA_LARGE`. El servidor resuelve cuenta y organización. Conflicto de versión409 requiere consultar antes de volver a enviar; sin cola offline, sin reenvío automático ni éxito optimista.

La preferencia de avisos afecta únicamente sugerencias visuales opcionales. Nunca oculta la bandeja persistida, requerimientos de respuesta, confirmaciones de servicio, alertas de seguridad, OTP ni los flujos de GPS/consentimiento. Desactivarla no cancela entregas ni borra mensajes. La app muestra siempre la bandeja y las acciones obligatorias.

El tamaño multiplica la escala accesible del sistema por1/1.15/1.3, sin reducir ni limitar la ampliación configurada por el usuario. Las preferencias se conservan en servidor, se cargan al iniciar sesión y se retiran de memoria al cerrarla. No se almacena cola o copia persistente local. Identidad, correo y documentos conservan sus flujos; sin edición directa ni foto/carga de PII.

V47 es aditiva y solo puede aplicarse por el coordinador tras preflight en ruta_fija_test. La relación de cuenta debe incluirse en el retiro personal aprobado a30d del fin efectivo; las preferencias no autorizan ampliar ese plazo. La auditoría registra antes/después de campos de presentación, no identidad ni secretos. Este documento no declara prueba física ejecutada.

## Contraste local posterior — 2026-10-21

[Acta L07](../actas/L-07-cierre-funcional-local-2026-10-21.md) y [matriz final](../evidencia/L-07/matriz-L07.md):verificación funcional local con sus límites. Las notas iniciales de implementación pendiente son históricas. No implica ensayo físico,habilitación del piloto o ejecución de purgas/copia reales.
