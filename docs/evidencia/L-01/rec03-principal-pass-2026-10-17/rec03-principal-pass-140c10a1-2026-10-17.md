# REC-CI07-03 — Principal PASS reportado; lectura de copia pendiente

Fecha local **2026-10-17, America/Lima**. RUN_ID **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. [Resultados y contraste](rec03-principal-pass-140c10a1-2026-10-18T004155Z.json).

El operador respondió al comando solicitado para el principal con `LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE` y pública SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. Se registra como resultado humano del cliente vigente; el agente no introdujo la contraseña ni descifró el archivo. La huella coincide con el archivo público nuevo consultado.

El operador no remitió la línea `L01_TEMPORARY_COPY`. En lugar de reconstruirla, se comprobaron metadatos a **2026-10-18T00:41:54.0403279Z**: principal y copia existen,477 bytes cada uno; ambas públicas son Ed25519 canónicas e iguales en huella. El directorio restringido, marcador y JAR original se conservan. No se abrieron ni calcularon hashes de privadas; tamaño y pública iguales no acreditan igualdad del contenido cifrado o lectura de la copia.

Inspección SELECT **2026-10-18T00:41:55.013857500Z** en `ruta_fija_test` / `rf_test` / V26: ledger1/2/0, cupos0/2/20, cuentas/organización, aceptación1 y términos publicados intactos; marcador y lock activos, limpieza estándar rechazada, REC02 única y pública inscrita original5b727e9a… conservadas; sustitución0, sin reto vigente ni recuperación completa. El inspector separado no adquiere el lock del servidor. Sin escrituras ni SMTP.

## Siguiente intervención

Mantener A abierta. En la misma B, en PowerShell, una sola vez `java -jar $trialJar check-key $trialNewCopy`; introducir oculto el mismo cifrado nuevo que pasó con el principal y comunicar únicamente resultado/huella. Esperar antes de preparar la pública. Si falla, detenerse sin repetir ni borrar archivos. [Guía vigente1.0.4](../../operacion/L-01-REC-CI07-03-ejecucion.md). No volver a copiar, generar, inscribir o comprobar el principal.

CI01 VERIFICADO sin repetir correo; CI03 PENDING por gate previo, aceptación conservada y otro ADMIN no autorizado; CI07 PENDING, lectura/uso de copia y recuperación todavía pendientes. SC03/07 condicionales sin aplicar, `TEST_CLEANUP_RELEASED=false`, L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. Una copia en la misma PC no acredita independencia de custodia. Sin nuevas suites, limpiezas, envíos externos, cuentas, migraciones de desarrollo/piloto, despliegue, Git/GitHub o cambios de términos/final3/protocolo.
