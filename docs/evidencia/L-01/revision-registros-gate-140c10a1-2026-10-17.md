# CI-L01-03 — Revisión del historial del mismo ensayo, sin otra cuenta

Fecha local: 2026-10-17, America/Lima. **L-01 EN_CURSO. CI03 local PARCIAL/PENDING. SC-03/SC-07 no aplicados.**

## Instrucción vigente y resultado

El titular **no autoriza por ahora REC-CI03-01**. Se conservan la cuenta, la aceptación existente, la identidad, el historial, los cupos y la protección contra limpiezas. No se crea ni prepara otro ADMIN. No se modifica SC-03 ni se reconstruye retrospectivamente un PASS.

**No se encontró evidencia suficiente del gate previo por términos pendientes en los registros inspeccionados de este caso.** Sí existen dos rechazos backend anteriores, pero su motivo persistido es `INVALID_CREDENTIALS`. La aceptación y el login posterior permanecen acreditados; estos hechos no convierten los rechazos genéricos en una prueba del gate de términos.

## Fuentes contrastadas y alcance

- RUN_ID: `140c10a1-be0f-4d08-a6d4-c796e2ed107a`; ADMIN sintético `92ebc07c-1319-4850-a734-f792cb299979`, organización sintética `1a759298-7831-4aaf-9299-c3920a9cc28a`.
- [Archivo humano original](ensayos-humanos-140c10a1-be0f-4d08-a6d4-c796e2ed107a/observacion-1792227077200.json), SHA-256 **9319F045F65FD4EE61B9A07EDEC3000E5EA6AC0E75618D99E2C4896843B8A719**: permanece intacto, único archivo de observación del caso; declaración personal, aceptación HTTP y login verdaderos, sin `preAcceptanceGate`.
- PostgreSQL local **ruta_fija_test / rf_test / V26**, dos inspecciones transaccionales **solo lectura** del mismo caso, terminadas con rollback. Una contrasta la secuencia; la segunda añade exclusivamente la extracción de motivos de login mediante una lista de valores permitidos, sin volcar metadata libre ni secretos.
- [Historia backend y motivos saneados](revision-registros-gate-140c10a1-2026-10-17/historia-backend-solo-lectura-v2.json), observación **2026-10-17T09:00:13.190769400Z**, SHA-256 **6906E24A75C1B5C222AEFDBB0D17E272EB1E12AD76175A37D6FEAD121F7DD7EF**. La [primera consulta](revision-registros-gate-140c10a1-2026-10-17/historia-backend-solo-lectura.json) se conserva como antecedente, sin sobrescribirla.
- Auditoría del mismo SUPER_ADMIN/ADMIN/organización/activación: **21 eventos** en `audit_event` y **6 eventos** en `admin_activation_event`; son los mismos registros en ambas consultas, no se suman como ejecuciones de pruebas.
- [Inventario de archivos y huellas de fuentes revisadas](revision-registros-gate-140c10a1-2026-10-17/inventario-fuentes.json): estado público, observación, inspecciones y logs del caso en `backend/target`, `backend/.local`, `docs/evidencia/L-01` y `frontend`, excluyendo secretos y dependencias. No se hallaron HAR, trazas o access/request logs en ese ámbito. Los logs de bootstrap encontrados preceden a esta activación y no contienen el rechazo requerido.
- Se revisaron el observador del arnés, los servicios de autenticación/activación, el gate backend, el manejador de errores y el cliente de activación. No se inspeccionaron perfiles privados del navegador, historial de terminal, `.env`, JWT privado o claves. No se afirma ausencia de registros fuera de las fuentes revisadas.

## Qué ocurrió antes y después

Todos los tiempos siguientes son UTC del servidor.

| Registro | Momento | Interpretación admisible |
|---|---|---|
| `LOGIN_FAILED`, ADMIN exacto, `reason=INVALID_CREDENTIALS` | 08:20:11.878Z | Rechazo backend real anterior a aceptar; no identifica términos pendientes como causa. |
| `LOGIN_FAILED`, ADMIN exacto, `reason=INVALID_CREDENTIALS` | 08:34:52.873Z | Mismo límite. No deducir si el operador escribió mal la contraseña: el servicio usa el mismo motivo para contraseña, inactividad o estado de organización. |
| Credencial reclamada y contraseña establecida | 08:36:45.327Z | Inicio persistido de la activación parametrizada, posterior a ambos rechazos. |
| Correo verificado | 08:38:55.179Z | Precondición cumplida. |
| Aceptación persistida | **08:39:10.296Z** | Aceptación `c496b63c-735f-48cc-be56-90f7ec1464e1`, ADMIN_TERMS1.0.0, documento `f590e228-5d2e-47e4-b144-b0df1198bd49`, SHA aprobado `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417`. |
| Auditoría `ADMIN_ACTIVATION_TERMS_ACCEPTED` | 08:39:10.300Z | Evidencia backend de aceptación expresa, ligada al documento/cuenta/organización correctos. |
| Activación completada | 08:39:15.743Z | ADMIN activo/COMPLETED, después de aceptar. |
| `LOGIN_SUCCESS`, ADMIN exacto | **08:39:54.982Z** | Sesión posterior permitida; coincide con `activatedAdminLoginObserved=true`. No registra por sí mismo un GET protegido exitoso ni el rechazo previo por términos. |

En la auditoría inspeccionada no aparece una operación de activación/protegida rechazada específicamente por términos pendientes antes de **08:39:10.296Z**. Los éxitos SUPER_ADMIN anteriores pertenecen a otra identidad/rol; no acreditan acceso previo del ADMIN.

## Backend frente a bloqueo visual

1. **Control backend existente:** `AdminLifecycleService.complete()` comprueba la aceptación vigente antes de activar y lanza HTTP409 con el motivo «Falta la aceptacion expresa…». El authorization manager de términos aplica el control a ADMIN en operaciones protegidas. Revisar ese código y conservar sus pruebas técnicas anteriores no acredita que esta persona haya observado el rechazo en este ensayo.
2. **Cliente:** `admin-activation.page.ts`, método `complete()`, retorna sin enviar HTTP si falta token, correo verificado o aceptación. Un botón ausente/inhabilitado, o no poder avanzar visualmente, no es evidencia de rechazo del servidor. Tampoco se deduce del código que el operador haya intentado el botón.
3. **Captura del ensayo:** `ManualL01HumanTrialServer.observe()` retorna para respuestas fuera de2xx. El observador existente no conserva un historial de HTTP4xx. El control explícito `gate` habría archivado los409/401, pero se ejecutó tarde y rechazó su precondición antes de enviar esas solicitudes.
4. **Auditoría y logs:** el manejador403 y los rechazos por `ApplicationException` no escriben esos eventos en `audit_event`; la configuración del ensayo no habilita un archivo de tráfico HTTP. Por ello la ausencia en esas fuentes no demuestra que nunca hubiera un rechazo; **impide acreditarlo** con los datos disponibles.
5. **Acceso posterior:** sesión ADMIN/login200 acreditados. No hay en el archivo una captura persistida del status de una operación protegida posterior. La pantalla mostrada no se convierte por inferencia en una traza HTTP200 de su API.

## Faltante único para decidir cómo completar CI03

Permanece pendiente **la comprobación del gate previo del mismo recorrido**. Para darla por acreditada hacen falta referencias verificables de una operación normal rechazada por términos pendientes antes de la aceptación, con método/ruta/estado y motivo inequívoco del backend, vinculadas a esta cuenta/organización/documento y su tiempo; y el acceso protegido permitido después. No basta un401 por falta de sesión, un403 de otro rol, `INVALID_CREDENTIALS`, un bloqueo visual o las pruebas automáticas de otro caso.

Si el operador conserva una captura de red original de ese momento, puede revisarse mostrando solo método/ruta/estado/motivo/tiempo/IDs no secretos. No se solicita exportar HAR, cabeceras, cookies, cuerpos o tokens, ni repetir acciones para simular una observación pasada. No se presume que esa captura exista. Si no existe, este punto sigue pendiente hasta una decisión posterior; **no se solicita otra cuenta ni cambio de SC-03 en este corte**.

## CI07 y controles conservados

CI07 puede continuar de manera independiente con el **mismo** SUPER_ADMIN sintético, JAR/protocolo Ed25519 vigentes, clave temporal generada personalmente por el operador, copia cifrada en la misma PC y correo simulado. Se pidió únicamente el resultado saneado de `LOCAL_ENCRYPTED_KEY_CHECK` de principal/copia y si coinciden las huellas públicas; **no se presume que esa preparación ya haya terminado**. Tras ello corresponderá inscripción con autenticación/reautenticación, recuperación usando la copia, OTP simulado, contraseña nueva y comprobaciones de rotación/revocación.

Actualización durante el acompañamiento: el operador confirmó estar en `Read-Host 'L01_HUMAN_RUN_ID mostrado en terminal A'` del apartado4. Se indicó introducir el identificador público `140c10a1-be0f-4d08-a6d4-c796e2ed107a` y continuar las entradas protegidas locales. Todavía no se reportaron resultados de `check-key`; no se registra generación/lectura/inscripción como verificada por alcanzar ese prompt.

La inspección09:00:13Z mantiene ledger **1 SUPER_ADMIN / 2 ADMIN / 0 CONDUCTOR**, cupos **0 / 2 / 20**, una aceptación, clave pública de recuperación aún no inscrita y advisory lock activo. Marcador del mismo RUN_ID conservado: **TEST_CLEANUP_RELEASED=false**. No se reinicia el servidor, modifica el observador para reescribir pasado ni ejecutan limpiezas.

Las consultas no realizaron HTTP, SMTP, escrituras de datos, alta/baja de cuentas, cambios de contraseña, generación de claves, migraciones o tests. Fuentes/outputs originales y saneados se archivan junto a este informe. CI01 continúa VERIFICADO sin repetirlo; términos publicados y snapshot final3 se conservan; piloto bloqueado y L-02 sin iniciar.
