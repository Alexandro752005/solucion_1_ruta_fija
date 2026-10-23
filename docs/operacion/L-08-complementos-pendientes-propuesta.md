# L-08 — Complementos concretos para completar la validación

Versión 0.1.0, 2026-10-22 America/Lima. **PROPUESTA; NO AUTORIZACIÓN NI EVIDENCIA DE EJECUCIÓN.** Los casos físicos y CAP25 cerrados permanecen retirados. No modifica requisitos ni presupone medios independientes disponibles.

## L08-FIS-02 — Batería, escala y lector operativos

1. Caso nuevo exclusivamente en `ruta_fija_test@127.0.0.1:5432`; reutiliza SUPER_ADMIN/ADMIN/organización sintéticos elegibles. Máximo **un CONDUCTOR** nuevo, por los flujos normales. Ledger CONDUCTOR 7→8/20; sin recuperar cupos, reabrir cuentas o mezclar evidencias anteriores.
2. Misma aplicación separada `pe.rutafija.conductor.l08`. Credenciales solo mediante consola protegida; correo simulado, sin envíos externos. Expediente, recursos y servicio ficticios identificados, conservando permisos, documentos, estados e historial.
3. El usuario confirma primero disponibilidad de una misma red Wi-Fi privada para PC y Poco, carga suficiente y una sesión de unos 15 minutos. No se presupone esa red. USB alimentando el teléfono no acredita consumo de batería. `adb shell dumpsys battery unplug` tampoco equivale a cortar la alimentación: **no se utilizará para simular descarga**.
4. Si hay red privada y autorización concreta, acceso temporal a la API de pruebas únicamente desde el Poco y durante el ensayo, con autenticación normal y regla de firewall limitada al destino/puerto/perfil privado; sin túnel público, despliegue, acceso desde otras redes ni modificación permanente. Antes de ejecutar se identifican interfaz y restricciones reales. Si no se puede limitar, no se expone.
5. Intervención humana: lector TalkBack en inicio/asignaciones/diálogos; escala grande real del dispositivo; consentimiento/permiso de ubicación personales y servicio GPS en primer plano. Sin movimiento obligatorio, capturas de mapa o coordenadas en evidencia pública.
6. Registrar batería inicial/final, duración real, cargador desconectado, pantalla/estado de red, permiso y cadencia GPS; termal/otras cargas si disponibles sin datos personales. **Propuesta de criterio académico**, limitada a ese Poco y sesión: caída de batería de como máximo 5 puntos porcentuales en 15 minutos, sin cierre, bloqueo térmico o pérdida persistente de captura. Requiere aprobación expresa; no se extrapola autonomía diaria ni otros equipos y se registra la resolución del indicador de porcentaje.
7. Archivar observaciones humanas y técnicas antes de retirar solo el nuevo caso. Deshabilitar cuenta/recursos y revocar sesiones/periodos; conservar ledger, servicio e historial y retiro GPS de siete días. Ningún PASS automático sustituye TalkBack o las decisiones humanas.

**Decisiones indispensables:** autorizar L08-FIS-02 con máximo una alta, confirmar disponibilidad de red/sesión y aprobar o corregir el criterio de batería. Esta propuesta no acredita esos hechos.

## L08-REST-03 — Recuperación empresarial completa V50

1. Conservar las dos bases V48 de `55438` y el dataset cerrado `55439`; no sobrescribirlos, migrarlos ni reiniciar sus cronómetros.
2. Destino propuesto nuevo: PostgreSQL nativo **solo `127.0.0.1:55440`, base `ruta_fija_test`**, directorio propio `backend/.local/l08-restore-v50-<nuevo RUN_ID>`. Sin servicio Windows ni escritura en desarrollo/piloto. Requiere autorización identificada antes de crear el destino.
3. Nuevo respaldo consistente de V50 del test compartido de `5432`, lectura únicamente; cifrado GCM, secretos DPAPI/ACL y separación de roles. Conservar manifiesto original íntegro y comparar filas, esquema y permisos, sin normalización que omita categorías.
4. Diseñar y registrar de antemano el inicio/detección del incidente controlado, la edad del último respaldo y el punto final de recuperación. Medir el RTO total del nuevo ensayo; conservar como antecedentes los tiempos/fallos anteriores. No presentar solo el tiempo de arranque de API como RTO integral.
5. JAR V50 trazado, migraciones deshabilitadas sobre el destino restaurado. Verificar salud y operación empresarial real mediante ADMIN sintético existente: lectura de recursos y alta/reconsulta/retiro normal de **un grupo exclusivamente sintético nuevo en la copia aislada**, sin cuentas o cupos nuevos. Correo y GPS no se envían. Verificar aislamiento y controles antes/después; no falsificar respuestas ni forzar SQL.
6. Archivar resultado y detener exclusivamente la instancia propia, conservando datos, contadores e historia. Ninguna copia de recuperación se convierte en piloto.

**Límite explícito:** aunque el ensayo pase, una restauración dentro de la misma PC no acredita medio independiente, RPO horario, verificación diaria, cobertura24/7 o alertas externas. Esos recursos siguen confirmados pendientes y no se sustituyen por aprobación documental.

## Recursos operativos conocidos como pendientes

Medio de respaldo fuera de la PC/servidor; equipo independiente para el monitor; atención y escalamiento humanos24/7; condiciones de privacidad, participantes/títulos, aceptación ADMIN legítima y custodia/ensayo del titular real. No se solicitan de nuevo como si ya existieran. Cuando haya disponibilidad real se identificará la alternativa S/0 antes de configurar o enviar información fuera de la PC.
