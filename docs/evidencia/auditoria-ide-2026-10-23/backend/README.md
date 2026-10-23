# Auditoría Java y Maven — 2026-10-23

Resultado actual: **compilación fresca de 406 fuentes principales y 185 fuentes de prueba, sin errores ni avisos de javac con `-Xlint:all`; 257 pruebas aprobadas en 50 clases, sin fallos, errores ni omisiones**. La revisión independiente está en [QA Java del agente móvil](../mobile/qa-java-warning-patch.json).

Los 1633 errores y 83 avisos originales del IDE fueron atribuidos por la auditoría del coordinador a Dart y copias históricas. No eran 1633 fallos de Java. Esta superficie se comprobó con Maven real, Java 21.0.10 y Spring Boot 3.5.16, sin cambiar el stack, el POM vivo o sus dependencias.

## Verificaciones ejecutadas

| Orden o procedimiento real | Resultado y evidencia |
|---|---|
| `powershell.exe -NoProfile -File scripts/Test-RutaFijaBackendUnitSafe.ps1 -Execute` inicial | 406 fuentes principales y 181 de prueba compiladas; 242 pruebas de 47 clases aprobadas. [Corrida inicial](run-e5619078-de50-419f-94a3-e51291f4c535/result.json). |
| Mismo script después de las correcciones | 406 + 185 fuentes; `-Xlint:all`, `showWarnings=true`, `showDeprecation=true`; 0 errores y 0 avisos javac. 257 pruebas de 50 clases aprobadas. [Resultado final](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/result.json), [órdenes y salidas](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/commands.json). |
| Inspección de `ObjectStreamClass` antes y después | Los 28 `serialVersionUID` originales se conservan exactamente. [Antes](correcciones-9cd90f87-411f-4492-bc84-1e087bb670d0/serial-uids-before.json), [después](correcciones-9cd90f87-411f-4492-bc84-1e087bb670d0/serial-uids-after.json). |
| `javac --release 21 -Xlint:all` para `scripts/L08ContinuityTools.java` y `scripts/support/L08ClosureSigner.java` | Ambos helpers actuales compilan; hashes coinciden con el manifiesto L-08. No se ejecutaron. [Comprobante](run-e5619078-de50-419f-94a3-e51291f4c535/standalone-manifested-helpers.json). |
| `mvn.cmd --batch-mode --no-transfer-progress --offline -f AUDIT_POM -DskipTests package` | Empaquetado aislado aprobado en 4,508 s. En esta orden solo se omite repetir las pruebas ya aprobadas; no se usa como prueba funcional. [Log](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/03-package-isolated.log). |
| Comparación del JAR nuevo con las clases y recursos auditados | 672 entradas coinciden. JAR anterior respaldado por hash antes de actualizar únicamente el JAR local del iniciador. [Constancia](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/runtime-jar-refresh/refresh.json). |
| `powershell.exe -NoProfile -File scripts/Invoke-RutaFijaNativeTest.ps1 -Action Verify` | Delegación real al nuevo arnés seguro: 257 pruebas, 50 clases, exit0. [Comprobante de la orden](native-wrapper-final/verify-command.json), [resultado delegado](run-8cbb9b20-4419-461b-a961-2ee2f46dfde7/result.json). |
| Revisión independiente de iniciadores y sus mocks | 42 comprobaciones de mocks y estática, más 3 órdenes Plan y 2 negativas de guardas, aprobadas. No iniciaron servicios ni tocaron la base. [QA independiente](../launchers/qa-independent-backend.json). |

La receta segura selecciona explícitamente las clases de pruebas revisadas. No se ejecutó `mvn test` genérico, ya que los nombres `*IntegrationTest` también pueden entrar en Surefire y varios de ellos abren PostgreSQL y limpian datos. El script crea una copia derivada del POM para cambiar únicamente rutas de fuentes/recursos/salidas, mostrar todos los avisos y limitar memoria; conserva las versiones y el filtrado de recursos de Spring Boot. [Alcance del POM derivado](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/pom-scope.json).

## Correcciones y preservación

Se respaldaron por bytes y SHA-256 45 fuentes Java vivas y el nuevo script de prueba antes de modificarlos. Se agregaron un helper tipado de estado JSON y tres clases de prueba. [Índice final de cambios](correcciones-9cd90f87-411f-4492-bc84-1e087bb670d0/live-source-changes-final.json), [respaldo exacto](correcciones-9cd90f87-411f-4492-bc84-1e087bb670d0/backup-before.json).

El inventario inicial contenía 85 avisos reales de javac en 44 archivos: 28 de serialización, 51 conversiones sin comprobar, 3 de obsolescencia y 3 del cierre demasiado amplio de un recurso. [Inventario inicial corregido](run-e5619078-de50-419f-94a3-e51291f4c535/javac-warning-inventory-v2.json). Se sustituyeron las lecturas JSON sin tipos por límites tipados y validación de claves; las listas reanudables siguen siendo mutables y permanecen enlazadas a su journal. Los dos gestores de autorización conservan `check()` como puente de compatibilidad obsoleto y trasladan su mismo cuerpo a `authorize()`, con pruebas de equivalencia y rechazo. No se relajan roles, rutas, aceptación de términos o estados de sesión.

POI 5.5.1 ya invoca `dispose()` desde `close()`, comprobado en su bytecode local. Se retiró la segunda llamada redundante y una prueba nueva exporta 250 filas, abre el XLSX generado y comprueba la retirada de archivos temporales. [Bytecode examinado](run-e5619078-de50-419f-94a3-e51291f4c535/poi-5.5.1-bytecode.txt).

Se preservaron fuera de `target` los 50 XML finales, los hashes y un ZIP de los 650 archivos de entrada de compilación, y una copia del JAR validado. Cada entrada del ZIP y cada XML se cotejaron por SHA-256. [Archivo final comprobado](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/archive-check-final.json).

JAR local actual: SHA-256 `bf17109f3986c6176c1461389cbc9382902fba4c6a609cf66dd48a3561a5c7b8`, 91 348 713 bytes. El JAR de entrega L-08 sellado, SHA-256 `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`, permanece intacto; esta auditoría crea un corte posterior y no reescribe su acta, firma, APK o manifiesto.

## Avisos de ejecución e incidencias preservadas

Los avisos de la JVM y bibliotecas no se ocultaron: ByteBuddy/Mockito carga su agente durante las pruebas, la JVM informa del límite de CDS y Spring JCL advierte sobre `commons-logging` transitivo. Dos mensajes WARN del manejador HTTP corresponden a pruebas negativas con dobles JDBC. No son errores de compilación ni conexiones reales a una base. [Clasificación y líneas originales](run-ed21be06-39e5-4575-8126-d9bac34ff3f4/warnings-classified.json).

La primera corrida después de ampliar las pruebas falló en un test nuevo porque una segunda configuración `when()` de Mockito reevaluaba un stub que ya lanzaba una excepción. Se corrigió ese test con `doThrow()`; no se cambió el comportamiento del producto para hacerlo pasar. [Log fallido conservado](run-2ef394c3-1cc1-4885-90bf-93e40035dbcc/02-safe-unit-tests.log) y [XML fallido conservado](correcciones-9cd90f87-411f-4492-bc84-1e087bb670d0/Mockito-restub-failed-report-preserved.xml).

También se conservan un borrador inicial del inventario cuyo parser agrupó mal los registros y una constancia inicial nula del archivado ZIP antes de cargar su ensamblado .NET. Sus versiones posteriores indican la causa, la sustitución y las comprobaciones efectivas; no se presentan como pruebas aprobadas. Una expectativa inicial incorrecta de Plan para QuickDemo se corrigió tras comprobar su `ValidateSet`; el rechazo ocurrió antes de ejecutar acciones. [Pruebas de planes y guardas](../launchers/qa-independent-backend-plans.json).

En esta superficie no se inició Spring Boot, una API, un túnel o un servicio PostgreSQL, no se envió correo, no se accedió a una base ni se ejecutó Git. El smoke integrado de solo lectura y sus huellas de tablas corresponden a la evidencia separada del coordinador y A-OPS.
