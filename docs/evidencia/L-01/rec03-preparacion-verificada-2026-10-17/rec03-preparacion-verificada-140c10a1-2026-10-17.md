# REC-CI07-03 — Sustitución pública ejecutada y contrastada

Fecha local **2026-10-17, America/Lima**. RUN_ID **140c10a1-be0f-4d08-a6d4-c796e2ed107a**, segmento **e140a035-8211-4037-82e6-388329f1cfc8**. [Inspección y estado público](rec03-preparacion-verificada-140c10a1-2026-10-18T011953Z.json).

El operador ejecutó `preparar-clave` y comunicó `PASS_PRECONDITION_ONLY`, sustitución1, el segmento previsto y pública SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. La preparación fue personal mediante el arnés autorizado; el agente no realizó su autenticación ni escritura.

## Correspondencia persistida

SELECT a **2026-10-18T01:19:53.372925200Z**, `ruta_fija_test` / `rf_test` / V26:

- Una auditoría **58b926a7-bf2d-4ff1-a978-feea67d1e9c4**, fecha del servidor **2026-10-18T01:18:58.039Z**.
- Coinciden instalación, cuenta, RUN, segmento, aprobación SHA434560CD…, propuesta SHA1B0886BA… y origen `AUTHORIZED_TEST_FIXTURE_MAINTENANCE_NOT_HTTP_ENROLLMENT`.
- Pública histórica5b727e9a… → nueva5569d8c8…; la pública actual corresponde a la nueva y la verificación del inspector `syntheticRecoveryFixtureKeyPreparationAuditCorrespondenceVerified` es **true**.
- Confirmación humana de huella, checks de principal/copia y disponibilidad del sustituto registrada antes de escribir.
- Inscripción HTTP original **1**, conservando la referencia de su pública anterior. Sustitución del fixture **1**, contabilizada por separado.
- REC02 **1**, auditoría **d791653a-6379-4f02-ac1d-1db020dde403**, sin otra reparación. El UPDATE de esta preparación afecta solo pública/timestamp y auditoría; no se consultó ni comparó el hash del código. Su uso efectivo se comprobará en el flujo normal siguiente.
- Ledger **1/2/0**, cupos **0/2/20**, dos cuentas y una organización del caso, aceptación ADMIN1 y términos publicados conservados.
- Marcador original y lock activos; limpieza estándar rechazada. Sin reto vigente ni recuperación completa. Estado público a01:19:52Z y listeners127.0.0.1:4311/8091, PID22648, corresponden al mismo caso/segmento; sessionVersion previa0.

Las guardas de preparación `noPriorKeyReplacement=false` y `credentialMatches=false` son esperables tras sustituir la pública antigua: impiden repetir esa preparación. No se intentan forzar. La correspondencia entre auditoría aprobada y pública actual se verifica por separado. El inspector separado no toma posesión ni libera el lock del servidor.

## Recorrido normal todavía pendiente

[Guía REC03, apartado5](../../operacion/L-01-REC-CI07-03-ejecucion.md): recuperación personal con **la copia nueva**, contraseña de cifrado nueva y sustituto REC02 vigente; reto, firma, OTP simulado, contraseña de acceso nueva y conservación local del código rotado mostrado una vez. Después login con correo nuevo y declaraciones `copia-ok`, `recuperacion-ok`, `verificar`, solo si las acciones se realizaron. Esperar archivo y contraste antes de acreditar el resultado.

El código fuente vigente genera `accountReference` aleatorio para cada reto: el operador contrasta su coherencia entre el resumen y los bytes mostrados, sin equipararlo al UUID interno de la cuenta. Instalación prevista **f0cd888b-57df-4def-9d08-7882ffadebae** y correo nuevo **sa.recovered.140c10a1@rutafija.test**. Leer toda la secuencia antes de crear el reto de cinco minutos. Ante fallo, conservar el caso sin reintentar ni reenviar por rutina.

## Estado y límites

La sustitución quedó acreditada; no acredita recuperación, código rotado recibido, cambio de contraseña o revocación. **CI07 PENDING**, sin `PASS_LOCAL_TEMPORARY_COPY_ONLY`. CI03 PENDING únicamente por gate previo; aceptación conservada y otro ADMIN no autorizado. SC03/07 condicionales sin aplicar; `TEST_CLEANUP_RELEASED=false`, L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. CI01 VERIFICADO sin repetir correo.

Se reutilizan las pruebas válidas de la implementación; no se repiten suites ni se ejecutan limpiezas. Cero escrituras y cero SMTP de la inspección. Sin secretos, hashes de códigos, rutas privadas o archivos cifrados leídos por el agente. Copia misma PC no acredita independencia de custodia; sin claves definitivas, despliegue, migraciones de desarrollo/piloto, Git/GitHub o cambios de final3/términos/protocolo normal.
