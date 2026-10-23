# CI-L01-07 — Reto vencido antes de la prueba; código de emergencia no identificado

Fecha local: 2026-10-17, America/Lima. Corte SELECT: **2026-10-17T09:56:36.782756800Z**. **CI07 manual PENDING, L-01 EN_CURSO.**

## Interrupción reportada y estado verificado

El operador indicó que está en «¿Firmar y continuar?» del cliente `recover` y no tiene identificado el código mostrado al inscribir. Esto no acredita pérdida definitiva del código: todavía está pendiente comprobar personalmente la salida local de `enroll`. No se inspeccionó su terminal ni se solicitó el secreto.

| Dato | Resultado de solo lectura |
|---|---|
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| Base / rol / esquema | `ruta_fija_test` / `rf_test` / V26 |
| Reto | `b4429e57-7a6c-409b-a2da-d6380685b0e8` |
| Creado / vencimiento UTC | `2026-10-17T09:50:04.922Z` / `2026-10-17T09:55:04.922Z` |
| Estado al corte | Vencido por su plazo original; `failed_proof_attempts=0`, `proof_verified_at=null`, `consumed_at=null` |
| Vínculo | SUPER_ADMIN sintético e instalación originales; correo solicitado coincide con el nuevo correo sintético del caso |
| Recuperaciones completadas | Cero eventos `SUPER_ADMIN_RECOVERY_COMPLETED` |
| Inscripción | Una respuesta exitosa, credencial y huella pública sin cambios respecto al corte previo |
| Sesión / aceptación ADMIN | Versión de sesión0; aceptación sintética anterior preservada |
| Ledger / cupos | Altas históricas1/2/0; restantes0/2/20 |
| Protección | Marcador del mismo RUN_ID y lock activos; proceso25932 en loopback4311/8091 |

[JSON de inspección](ci07-reto-sin-codigo-140c10a1-2026-10-17/estado-reto-solo-lectura.json), SHA-256 **06F0EAA79C85B6102086E4AB9EA52BFAFC434E11E1D6FF76137548A9EF292A4D**. [Fuente SELECT](ci07-reto-sin-codigo-140c10a1-2026-10-17/ManualL01EnrollmentReadOnly.java), [salida original saneada](ci07-reto-sin-codigo-140c10a1-2026-10-17/stdout-original.log) y [manifiesto](ci07-reto-sin-codigo-140c10a1-2026-10-17/manifest.sha256). Reutiliza la utilidad de inspección con campos públicos del reto; no cambia el mecanismo del ensayo. Compilación y ejecución de consulta con transacción read-only/rollback: cero escrituras de base, HTTP o SMTP del agente. No selecciona nonces, firmas o hashes de códigos, ni lee claves privadas.

## Confirmación del protocolo vigente

El [cliente](../../../backend/src/main/java/pe/rutafija/identity/recoverycli/SuperAdminRecoveryCli.java) imprime durante `enroll`, tras el éxito y la huella pública, el encabezado «Código de emergencia (mostrar una sola vez; guardar aparte de la clave privada):» seguido del valor de43 caracteres. No es la contraseña de cifrado, un token de sesión o un OTP. El código no se incorpora al archivo de clave ni se publica en evidencias.

En `recover`, una respuesta distinta de `S` a «¿Firmar y continuar?» cancela **antes de firmar y antes de enviar `/proof`**. No hay endpoint de cancelación ni eliminación del reto; el reto queda registrado y vence por su plazo original. Ya está vencido al corte. No se prolongó la vigencia ni se consumió o reinició su contador.

El [servicio](../../../backend/src/main/java/pe/rutafija/identity/application/SuperAdminRecoveryService.java) almacena el código codificado mediante `PasswordEncoder` y exige conjuntamente código y firma. La inscripción repetida se rechaza si ya existe la credencial; no ofrece consulta del código anterior ni reemisión. La rotación ocurre al completar una recuperación válida. Esta inspección de fuente no autoriza una nueva vía de rescate, lectura de memoria del arnés, borrado de credencial o sustitución de factores.

## Continuación personal solicitada

1. En el prompt actual de B escribir `N` y Enter. Mantener A abierta, sin reiniciar el ensayo. La salida puede informar cancelación/error genérico; no es un PASS y no confirma una escritura de cancelación en la base.
2. Consultar solo localmente el historial visible de esa misma terminal B y localizar el encabezado de `enroll` y la línea siguiente. No transcribirla, fotografiarla, buscarla en registros del servidor ni enviarla al chat.
3. Comunicar únicamente `Localizado` o `No disponible` y si B volvió a `PS ...>`. **La cancelación y la disponibilidad siguen sin confirmar en este corte.**
4. Si está localizado, preparar nuevamente los pasos completos del cliente existente, usando el mismo caso/clave/copia y un reto nuevo del flujo normal; el reto vencido permanece histórico y no se reutiliza ni renueva. No iniciar otro `recover` mientras la disponibilidad y la salida del intento actual no estén confirmadas. Cero reintentos automáticos y ningún cambio de límites.
5. Si no está disponible en la salida original ni fue custodiado por el operador, mantener CI07 pendiente. El protocolo actual no permite reconstruirlo desde el hash ni omitir ese factor. Cualquier alternativa que modifique el procedimiento requiere una decisión explícita; no se implementa ni ejecuta por inferencia.

**No se repite `enroll` o `keygen`, no se rota una credencial, no se solicita OTP ni se envía correo.** El estado continúa sintético; custodia independiente y titular real no acreditados. CI03 mantiene pendiente su gate previo y REC-CI03-01 no está autorizado. SC-03/SC-07 no se aplican; **TEST_CLEANUP_RELEASED=false**, L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. CI01, términos publicados y final3 se conservan.
