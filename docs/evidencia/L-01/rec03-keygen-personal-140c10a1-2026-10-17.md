# REC-CI07-03 — Generación personal comunicada; lectura pendiente

Fecha local **2026-10-17, America/Lima**. RUN_ID **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. [Inspección de solo lectura](rec03-keygen-personal-140c10a1-2026-10-18T003658Z.json).

El operador comunicó «Par Ed25519 creado» y que dispone de la contraseña de cifrado nueva. Las rutas personales de su salida se omiten de este registro; no compartió el contenido de la clave ni su contraseña. El agente no generó claves.

Metadatos UTC **2026-10-18T00:36:57.8277111Z**: principal cifrado477 bytes, última escritura00:35:09.1156179Z; pública X.509 Ed25519 canónica de44 bytes con SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. La huella es distinta de la inscrita. Copia y pública de copia todavía ausentes, directorio restringido y JAR original91913FF7… conservados. Solo se consultó la pública y los metadatos; no se abrió ni calculó hash de la privada. Esto no acredita todavía su descifrado.

Inspección UTC **2026-10-18T00:36:58.847903400Z**, `ruta_fija_test` / `rf_test` / V26: marcador y lock activos, ledger1/2/0, cupos0/2/20, cuentas/organización, aceptación1 y términos publicados conservados. REC02 única y pública inscrita5b727e9a… intactas, cero auditorías de sustitución, sin reto vigente ni recuperación completa. El inspector separado no adquiere el lock del servidor. Cero escrituras y cero intentos SMTP en esta inspección.

## Continuación personal

[Guía1.0.3, apartado3](../../operacion/L-01-REC-CI07-03-ejecucion.md): crear la copia local con destinos desocupados y `File.Copy(..., false)`, que impide sobrescribir incluso si otro proceso crea el destino después del preflight. Conservar el estado ante error o copia parcial. Solo tras `CREATED_LOCAL_ONLY` y regreso a PowerShell ejecutar una vez `check-key` del **principal** con la contraseña de cifrado nueva. Comunicar resultado y huella; esperar antes de comprobar la copia o preparar la pública. No repetir `keygen`.

Esta corrección de la instrucción de copia refuerza la condición ya vigente de no sobrescribir; no modifica el cliente, protocolo, permisos o mecanismo de ensayo. El agente no ejecuta esa copia ni el descifrado. Dos archivos en la misma PC no acreditan respaldo independiente.

CI01 VERIFICADO, sin repetición de correo. CI03 PENDING por gate previo, aceptación preservada y otro ADMIN no autorizado. CI07 PENDING; SC03/07 sin aplicar y `TEST_CLEANUP_RELEASED=false`. L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. Sin suites, limpiezas, envíos externos, migraciones de desarrollo/piloto, Git/GitHub, despliegue o cambios de final3/términos.
