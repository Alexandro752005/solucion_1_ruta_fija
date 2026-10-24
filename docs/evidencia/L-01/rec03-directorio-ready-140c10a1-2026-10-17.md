# REC-CI07-03 — Directorio preparado; generación personal pendiente

Fecha local **2026-10-17, America/Lima**. RUN_ID **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. [Evidencia de solo lectura](rec03-directorio-ready-140c10a1-2026-10-18T003222Z.json).

El operador comunicó `L01_TEMPORARY_KEY_WORKSPACE=READY_NO_KEYS_CREATED`. El corte de metadatos **2026-10-18T00:32:21.0145903Z** confirma el directorio nuevo, herencia deshabilitada, propietario coincidente y una única regla Allow con FullControl para ese usuario. Principal, pública y copia todavía no existen. No se abrieron privadas ni se registraron rutas personales o identificadores del usuario Windows. El JAR conserva SHA **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A**.

La inspección existente `-InspectRunId` a **2026-10-18T00:32:22.285091900Z** confirma `ruta_fija_test` / `rf_test` / V26: ledger1/2/0, cupos0/2/20, cuentas y organización del caso conservadas, aceptación1 y documento publicado intactos. Marcador original SHA83A91866… y lock activos; la limpieza estándar continúa rechazada. REC02 única conservada, pública original5b727e9a… y cero auditorías de sustitución; sin reto vigente ni recuperación completa. El inspector separado no toma posesión del lock que mantiene el servidor. Listeners127.0.0.1:4311/8091, PID22648.

## Siguiente paso humano

Mantener A abierta. En la misma B, en `PS ...>`, ejecutar una vez `java -jar $trialJar keygen $trialNewPrimary`. El operador elige, confirma y conserva personalmente una contraseña de cifrado de prueba de al menos12 caracteres ASCII. Se usará para abrir el archivo nuevo en `check-key` y `recover`; no se guarda en evidencia, argumentos, `.env` ni chat. Entrada invisible sin caracteres ni asteriscos.

Detenerse después de comunicar si apareció «Par Ed25519 creado» y si conservó la contraseña, o el error saneado. No copiar, comprobar, preparar pública, inscribir ni recuperar todavía. No repetir el bloque de preparación: el destino ya existe. Ante fallo, conservarlo para diagnóstico.

## Límites

Este corte verifica directorio y permisos en ejecución; no acredita generación de claves, lectura de copia, recuperación o independencia de custodia. El agente no generó claves ni ejecutó la intervención humana. Se reutilizan las pruebas válidas de implementación; no se ejecutaron suites, limpiezas o correos nuevos. El fallo de pegado anterior y sus evidencias permanecen como historial.

CI01 VERIFICADO sin repetir. CI03 PENDING solo por gate previo, aceptación preservada y otro ADMIN no autorizado. CI07 PENDING, sustitución todavía0; SC03/07 condicionales sin aplicar, `TEST_CLEANUP_RELEASED=false`. L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. Sin cambios de términos, final3, protocolo, cuentas o cupos; sin Git/GitHub, despliegue ni migraciones de desarrollo/piloto.
