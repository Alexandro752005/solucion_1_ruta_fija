# CI-L01-07 — Inscripción temporal verificada; recuperación manual pendiente

Fecha local: 2026-10-17, America/Lima. **L-01 EN_CURSO.** Corte de inspección: **2026-10-17T09:44:17.040007200Z**. No se declara todavía `PASS_LOCAL_TEMPORARY_COPY_ONLY`.

## Caso y correspondencia

El operador informó que la clave pública quedó inscrita y que su huella coincide con las comprobaciones de principal y copia. Esa comparación de los archivos es una declaración humana; el agente no leyó las claves privadas ni sus ubicaciones.

| Dato | Resultado contrastado |
|---|---|
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| Entorno | `ruta_fija_test`, usuario `rf_test`, esquema existente V26 |
| SUPER_ADMIN sintético | `088518c1-821c-4316-9f87-090f51ac44e0` |
| Instalación | `f0cd888b-57df-4def-9d08-7882ffadebae` |
| Huella pública SHA-256 | `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c` |
| Inscripción persistida | `2026-10-17T09:40:47.738Z` |
| Auditoría | Un `SUPER_ADMIN_RECOVERY_ENROLLED`, ID `88fa64c4-73b9-4094-8972-577748afa3a8`, `2026-10-17T09:40:48.051Z` |
| Respuestas exitosas observadas por el arnés | `enrollmentResponseCount=1` |
| Contraste independiente de persistencia | SHA-256 de la pública X.509 persistida igual al registrado por el observador HTTP |
| Versión de sesión antes de recuperar | 0, igual al valor observado al inscribir |
| Recuperación al corte | Cero retos de esta cuenta y cero `SUPER_ADMIN_RECOVERY_COMPLETED` |
| Ledger histórico | SUPER_ADMIN=1, ADMIN=2, CONDUCTOR=0; cupos restantes 0/2/20 |
| Protección | Marcador del mismo RUN_ID, lock de limpieza activo y proceso25932 escuchando solo en 127.0.0.1:4311/8091 |

La inscripción se realizó personalmente con el cliente vigente, autenticación y reautenticación del flujo normal; no se ejecutó enrollment mediante SQL o por el agente. El cliente revisado conserva SHA-256 `91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A`. No se sustituyó su protocolo Ed25519.

## Evidencia y límites de la comprobación

- [Inspección SELECT de inscripción](ci07-inscripcion-verificada-140c10a1-2026-10-17/inscripcion-solo-lectura.json), SHA-256 **08789DFBA7CEE65B5653994AA8520CF68911222CEEF6FD3B90E3AD908BF6FCBB**.
- [Fuente de inspección](ci07-inscripcion-verificada-140c10a1-2026-10-17/ManualL01EnrollmentReadOnly.java) y [salida original saneada](ci07-inscripcion-verificada-140c10a1-2026-10-17/stdout-original.log). Es una utilidad de consulta, no un mecanismo de ensayo o recuperación nuevo. Compilada con `javac`; transacción JDBC de solo lectura, guardas de caso/base/rol/V26 y rollback. Cero escrituras de base, peticiones HTTP o intentos SMTP del diagnóstico. No selecciona hashes de códigos de emergencia ni lee archivos privados.
- [Manifiesto de este corte](ci07-inscripcion-verificada-140c10a1-2026-10-17/manifest.sha256).

El [diagnóstico401 anterior](ci07-enrollment-401-140c10a1-2026-10-17.md) y su manifiesto permanecen intactos como antecedentes. Su observación de cero inscripciones era válida a su corte; esta evidencia posterior acredita la inscripción exitosa. La causa exacta del401 inicial no se ha reconstruido.

La aceptación ADMIN sintética existente sigue preservada y hay una sola aceptación del caso; documento publicado ADMIN1.0.0 y huella `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417` intactos. **CI03 continúa PENDING exclusivamente por el gate previo no acreditado.** No se creó otro ADMIN, no se alteró la aceptación y REC-CI03-01 sigue sin autorización.

## Siguiente intervención humana

Se entregó la secuencia completa del apartado5 de la [guía vigente](../../operacion/L-01-ensayos-humanos-sinteticos.md): ejecutar `recover` con `$trialCopy` en la terminal B existente, revisar y firmar el reto, presentar el código de emergencia, consultar el OTP **simulado** mediante `correo` en A sin reenvío, establecer personalmente una contraseña nueva, recibir el código rotado y comprobar el login con el correo nuevo. El reto dura cinco minutos; no se divide su ejecución con pausas de chat.

Solo después del uso efectivo de la copia y del login nuevo corresponde declarar `copia-ok`, `recuperacion-ok` y ejecutar `verificar`. Falta contrastar su archivo nuevo y las comprobaciones de consumo, pública conservada, rotación y revocación. No se presume un resultado satisfactorio a partir de la inscripción.

**TEST_CLEANUP_RELEASED=false.** Mantener la terminal A abierta y no ejecutar `salir` ni retirar el marcador mientras el gate de CI03 siga pendiente. SC-03/SC-07 todavía no se aplican; ambos PASS exigidos no están acreditados. Solo está confirmada una PC: lectura/uso de la copia temporal en ella no acredita custodia independiente ni preparación del titular real. CI01 sigue VERIFICADO sin repetir correo, piloto bloqueado y L-02 sin iniciar. Sin Git/GitHub, despliegue, migración o regeneración de final3.
