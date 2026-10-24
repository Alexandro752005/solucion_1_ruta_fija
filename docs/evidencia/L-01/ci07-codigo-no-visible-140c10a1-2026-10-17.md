# CI-L01-07 — Entrega del código de emergencia no acreditada

Fecha local: 2026-10-17, America/Lima. **L-01 EN_CURSO. CI07 manual PENDING.** Este corte incorpora la aclaración del operador: el código no aparece en ninguna línea visible de su terminal. No se considera un secreto entregado ni se le atribuye pérdida después de una entrega acreditada.

## Qué se comprobó

1. **Inscripción persistida, entrega sin acreditar.** El caso sigue siendo `140c10a1-be0f-4d08-a6d4-c796e2ed107a`. Una respuesta enrollment exitosa observada, una credencial y auditoría son evidencia de inscripción; no demuestran visibilidad o recepción humana del código.
2. **Código fuente y binario canónico.** JAR SHA-256 `91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A`. Inspección estática mediante `javap` del JDK local, sin ejecutar el cliente: `enroll` exige el campo `emergencyCodeShownOnce`, comprueba su formato de43 caracteres y contiene las llamadas de impresión del encabezado y el valor. El adaptador `ConsoleTerminal.println` llama a `PrintWriter.println` y `flush`. Esto describe el binario; no acredita que su salida llegara a la terminal del operador.
3. **Una prueba dirigida existente PASS.** Ejecución Maven offline del único método `SuperAdminRecoveryCliTest#enrollsOnlyThePublicKeyAndShowsTheEmergencyCodeOnce`: **1 test, 0 fallos, 0 errores, 0 omitidos**. Usa claves/credenciales completamente ficticias, HTTP falso en loopback y `ScriptedTerminal`; no inicia Spring ni consulta PostgreSQL o SMTP. Comprueba que la salida simulada contiene el código ficticio y excluye token/contraseña. Su aserción actual es de presencia; no se presenta como comprobación de visibilidad o recepción única en consola Windows. No sustituye el ensayo humano ni acredita el código del caso.
4. **Proceso del cliente.** La consulta de metadatos no encontró un proceso `java.exe -jar … recover …` en el momento inspeccionado. No se capturaron argumentos, rutas privadas ni secretos. Al no estar ese proceso disponible, no se pudo comprobar su JAR/runtime efectivo ni la causa de su terminación; el resultado de cancelación personal sigue sin confirmar.
5. **Estado posterior a la prueba.** SELECT de solo lectura a **2026-10-17T10:05:31.649392900Z**, `ruta_fija_test`/`rf_test`/V26: una inscripción y un reto histórico vencido, cero intentos fallidos, cero recuperaciones completadas, sessionVersion0 y una aceptación ADMIN preservada. Ledger1/2/0 y cupos0/2/20, pública coincidente con la observación de enrollment y protección de limpiezas activa. No hubo escritura de base, petición de recuperación o envío de correo por el diagnóstico.

**No hay causa exacta demostrada.** No se atribuye automáticamente al teclado, al usuario, a otra versión del cliente o a un fallo de Java. La terminal simulada y el análisis estático no verifican el renderizado real de aquella sesión.

## Evidencias archivadas

- [Hechos saneados del binario](ci07-codigo-no-visible-140c10a1-2026-10-17/inspeccion-binario-saneada.json).
- [Bytecode de enrollment](ci07-codigo-no-visible-140c10a1-2026-10-17/enroll-bytecode.txt) y [adaptador de consola](ci07-codigo-no-visible-140c10a1-2026-10-17/console-terminal-bytecode.txt). Solo código estático, no datos de ejecución.
- [Prueba dirigida](ci07-codigo-no-visible-140c10a1-2026-10-17/cli-enroll-unit-original.log) y [fuente del test](ci07-codigo-no-visible-140c10a1-2026-10-17/SuperAdminRecoveryCliTest-source.java), con datos ficticios identificados como tales.
- [SELECT posterior](ci07-codigo-no-visible-140c10a1-2026-10-17/estado-post-unit-solo-lectura.json), SHA-256 **CB98F33EB195BB43EBB2C07C7662A5E8367F3A717C13C4FC217D04764F1862DD**. Reutiliza la [consulta ya archivada](ci07-reto-sin-codigo-140c10a1-2026-10-17/ManualL01EnrollmentReadOnly.java), sin seleccionar códigos/hashes de emergencia ni claves privadas.
- [Manifiesto de este corte](ci07-codigo-no-visible-140c10a1-2026-10-17/manifest.sha256).

## Siguiente comprobación solicitada al operador

No repetir la búsqueda del secreto ni ejecutar enrollment/recovery. Una vez que B muestre `PS …>`, consultar únicamente la huella del JAR referenciado por su variable y la versión de Java de esa misma terminal:

```powershell
(Get-FileHash -LiteralPath $trialJar -Algorithm SHA256).Hash
java -version
```

Si todavía estuviera esperando la confirmación de firma, responder N antes de esas órdenes. Comunicar solo huella y versión; sin contraseñas, token, código, archivos de claves ni capturas completas. La comprobación no conecta a API/base/correo. Todavía no se recibieron esos dos resultados en este corte.

El protocolo no ofrece lectura del código guardado como hash ni reinscripción sobre la credencial existente. No se rotan factores, no se extrae el secreto de la memoria del arnés y no se cambia SQL para continuar. Si el código no quedó disponible, cualquier alternativa debe resolverse expresamente dentro de las autorizaciones; el ensayo permanece pendiente mientras tanto. Tampoco se cambia el cliente por una causa aún no probada.

CI03 conserva su aceptación sintética, pero el gate previo sigue pendiente y otro ADMIN no está autorizado. SC-03/SC-07 siguen aprobados condicionalmente **sin aplicar**. TEST_CLEANUP_RELEASED=false; mantener A/marcador/lock. CI01 no se repite. Términos publicados, final3, historial y cupos preservados; sin Git/GitHub, despliegue, migración de desarrollo/piloto o L-02.
