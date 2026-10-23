# Auditoría del proyecto, IDE y ejecutables — 23/10/2026

**Resultado:** los **1633 errores y 83 advertencias de VS Code quedaron en 0/0**, comprobados en el IDE real antes y después. Pasaron **731 pruebas locales**: 257 Java, 241 Angular y 233 Flutter. Se corrigieron causas verificadas, conservando las reglas de negocio, el stack, las reglas de análisis del código móvil y la evidencia histórica.

**Límite operativo relevante:** la base nativa `solucion_ruta_fija_1` permanece en V12; el backend actual exige V50. El inicio normal ahora rechaza esa incompatibilidad antes de lanzar procesos o migraciones. Se probó el arranque, navegador y cierre sobre `ruta_fija_test` V50 **en modo explícito de solo lectura**. Este ensayo no acredita operación autenticada ni habilita el piloto real.

Workspace: `D:\UTP\CICLO X UTP\INTEGRADOR I\APF3\solucion_1_ruta_fija_3`. Coordinación y QA independiente: raíz, backend, móvil y operaciones. Sin Git/GitHub, servicios de pago, exposición pública ni intervenciones en el POCO.

## 1. Causa exacta del contador del IDE

El analizador Dart recorría copias históricas sin su contexto de paquete Flutter. Encontraba imports y símbolos sin resolver en archivos que son respaldos o evidencia, no fuentes de una aplicación independiente.

| Ubicación de las copias | Diagnósticos iniciales |
|---|---:|
| `docs/evidencia/` | 649 |
| `memory/backup/` | 707 |
| `backend/target/` | 360 |
| Total | **1716: 1633 errores y 83 advertencias** |
| Código móvil activo | **0** |

La reproducción `flutter analyze --no-pub` desde la raíz coincidió exactamente con el contador del IDE. Desde `mobile`, el análisis ya era correcto. Las exclusiones de búsqueda o vigilancia de archivos de VS Code no delimitaban el análisis Dart.

Se añadieron siete rutas de archivos históricos/generados a `dart.analysisExcludedFolders` en `.vscode/settings.json` y al nuevo `analysis_options.yaml` de la raíz: `backend/target`, `docs/evidencia`, `memory/backup`, `backups`, `artifacts`, `.runtime` y `.worktrees`. Se mantienen analizados `mobile/lib`, `mobile/test` y el código de terceros del paquete; `mobile/analysis_options.yaml` conserva exactamente su SHA-256. No se cambiaron severidades, reglas lint, comprobaciones estrictas ni diagnósticos del código activo. La configuración Java ligera se mantuvo: no era la causa del contador.

Una extensión local temporal leyó [`vscode.languages.getDiagnostics()`](https://code.visualstudio.com/api/references/vscode-api#languages.getDiagnostics), sin crear, borrar ni modificar colecciones de diagnósticos. Conservó las capturas inicial y final y fue desinstalada al terminar. Su código, paquete y uso quedan auditables. No se atribuye el resultado a borrar la terminal ni a ocultar fallos.

Evidencias: [captura inicial](docs/evidencia/auditoria-ide-2026-10-23/ide/diagnostics.initial.json), [captura final 0/0 después del smoke](docs/evidencia/auditoria-ide-2026-10-23/ide/diagnostics.final.json), [recomprobación 0/0 al reanudar, 15:04 Lima](docs/evidencia/auditoria-ide-2026-10-23/ide/diagnostics.after-resume.json), [retiro de la herramienta temporal](docs/evidencia/auditoria-ide-2026-10-23/ide/temporary-tools-retired.json), [reproducción y pruebas móviles](docs/evidencia/auditoria-ide-2026-10-23/mobile/resultado-mobile-ide.json), [QA independiente del IDE y frontend](docs/evidencia/auditoria-ide-2026-10-23/mobile/qa-root-config.json).

## 2. Correcciones de código y contratos

### Flutter y contrato de ubicación

La suite completa detectó una expectativa antigua en `mobile_driver_repository_test.dart`: no incluía `locationServiceEnabled: true` y `foreground: true`, que el cliente ya envía y el backend exige. Se actualizó únicamente ese mapa esperado. Se conserva la comprobación exacta del payload y la prohibición de enviar `driverId`; no cambió código móvil de producción. Después pasaron 233 pruebas y el caso dirigido 3/3. Las siete pruebas conectadas quedaron explícitamente omitidas para proteger la fixture compartida.

### Angular y configuración de pruebas

Vite advertía que la configuración ESM estaba en un archivo `.ts` interpretado como CommonJS. Se renombró `vitest-base.config.ts` a `vitest-base.config.mts` manteniendo sus bytes y se actualizó su referencia en `angular.json`. No se usó la variable de supresión del aviso ni se cambiaron dependencias, presupuestos de compilación o reglas TypeScript. Typecheck, las 241 pruebas y la compilación de producción terminaron correctamente; el aviso desapareció.

### Java y compatibilidad

Una compilación fresca con `-Xlint:all` identificó 85 advertencias adicionales, independientes del contador Dart. Se corrigieron casts JSON sin tipos, declaraciones de serialización, el alcance de excepciones de cierre y una limpieza redundante de POI. Los 28 `serialVersionUID` calculados originalmente se conservaron explícitamente. Los gestores de autorización mantienen el método legado y delegan a `authorize()` con la misma política; se comprobaron ambas entradas, los rechazos y los permisos.

La verificación final compiló **406 fuentes principales y 185 de pruebas, con cero errores y cero advertencias javac**. Pasaron 257 pruebas en 50 clases, incluidas pruebas de JSON, autorización y exportación XLSX/limpieza temporal. El POM vivo, las versiones y las dependencias no cambiaron. Se preservaron respaldos exactos, los XML finales de Surefire y un ZIP de 650 fuentes/recursos comprobados.

El JAR de uso local en `backend/target` se actualizó después de QA y coincide en 672 entradas con las clases/recursos compilados. SHA-256 nuevo:

```text
bf17109f3986c6176c1461389cbc9382902fba4c6a609cf66dd48a3561a5c7b8
```

El JAR anterior quedó respaldado. Los binarios de `artifacts/L-08-entrega-final/` siguen intactos y representan el corte histórico sellado, no esta compilación posterior.

Evidencias: [índice backend](docs/evidencia/auditoria-ide-2026-10-23/backend/README.md), [resumen de compilación y regresión](docs/evidencia/auditoria-ide-2026-10-23/backend/summary.json), [QA independiente de compatibilidad](docs/evidencia/auditoria-ide-2026-10-23/mobile/qa-java-warning-patch.json), [respaldo y renovación del JAR](docs/evidencia/auditoria-ide-2026-10-23/backend/run-ed21be06-39e5-4575-8126-d9bac34ff3f4/runtime-jar-refresh/refresh.json).

## 3. Ejecutables de inicio, cierre y verificación

El documento solicitado quedó actualizado: **[Uso del Sistema — manual vigente de los BAT](<docs/Uso del Sistema.md>)**. También se actualizó [backend/README.md](backend/README.md). Ambos conservan el texto anterior como antecedente y su respaldo exacto por separado.

| Hallazgo | Corrección comprobada |
|---|---|
| Dos BAT de demo contenían bytes de control y rutas dañadas | Rutas absolutas correctas de Windows PowerShell y de los scripts; propagación de fallos |
| El verificador antiguo exigía V14 y ejecutaba TRUNCATE masivo sobre `ruta_fija_test` | `Verify` delega antes de importar secretos al runner unitario seguro; `Clean` rechaza antes de conectar |
| El preflight llamaba Git para comprobar secretos | Comprobación local de exclusiones y ACL, sin invocar Git ni inspeccionar su índice |
| Dos archivos privados heredaban permiso para otras identidades | ACL limitada al usuario actual y SYSTEM; respaldo solo del descriptor, sin exportar secretos |
| El cierre podía detener PostgreSQL compartido | Lease de propiedad e identidad de instancia, comprobación conservadora de clientes; sin propiedad u observabilidad, conserva el servicio |
| PID reutilizado o estado de proceso insuficiente | PID, ejecutable, inicio UTC y marcador de comando completo; incluye el túnel de demo |
| Backend V50 podía iniciarse contra una base V12 y migrar al arrancar | Gate SELECT del historial antes de lanzar; Flyway desactivado para el arranque normal |
| La demo podía descargar herramientas o abrir túnel antes de validar el entorno | Rechazo si falta herramienta; puertos y esquema comprobados antes del túnel; sin instalación automática |

Las **42 comprobaciones de seguridad** y el análisis sintáctico de **79 scripts PowerShell** pasaron. Los negativos de esquema incompatible, `Clean` prohibido y base de prueba sin modo lectura se comprobaron realmente. Las políticas de negocio del modo normal se mantienen; solo el smoke desactiva en su proceso los trabajos programados y restringe las conexiones a lectura.

Comandos vigentes desde la raíz:

```powershell
# Verificación unitaria segura del backend, sin PostgreSQL:
.\verificar_ruta_fija.bat

# Ensayo de UI/salud sobre el esquema V50, exclusivamente lectura:
.\iniciar_ruta_fija.bat --smoke-read-only --test-database

# Cierre de los procesos administrados; conserva PostgreSQL preexistente:
.\finalizar_ruta_fija.bat
```

`iniciar_ruta_fija.bat` sin parámetros es la entrada normal, pero **en el estado actual bloquea por V12**. Resolver esa diferencia requiere una migración planificada de la base nativa, con respaldo y ensayo de conservación. No se alteró esa base ni se cambió silenciosamente el destino hacia la fixture de prueba. El modo de solo lectura no permite iniciar sesión ni operar servicios como sustituto de un entorno normal. No se ejecutó demo pública.

Evidencias: [auditoría de lanzadores](docs/evidencia/auditoria-ide-2026-10-23/launchers/README.md), [42 comprobaciones](docs/evidencia/auditoria-ide-2026-10-23/launchers/qa-mocks-lease-demo-final.json), [QA independiente de guardas](docs/evidencia/auditoria-ide-2026-10-23/mobile/qa-launcher-observability-final.json), [BAT de verificación real](docs/evidencia/auditoria-ide-2026-10-23/launchers/verificar-bat-real.json).

## 4. Comprobación real entre funciones y componentes

Se ejecutó el BAT de inicio con los dos parámetros de solo lectura; reutilizó el nuevo JAR y arrancó API8080 y Angular4300. Se comprobaron `health` y `readiness` con HTTP200 y `UP`. Angular cargó su configuración `/api/v1` y la pantalla `/login` en Microsoft Edge con un perfil temporal dedicado.

El navegador renderizó los campos de correo y contraseña y los controles de acceso sin excepciones JavaScript, errores de consola ni peticiones fallidas por transporte. El refresco automático inicial, sin cookie/sesión, recibió el **401 esperado** a través del proxy Angular→API; no se enviaron credenciales ni se solicitaron operaciones de negocio. Este resultado no se presenta como una sesión autenticada ni una prueba de todos los flujos end-to-end.

El BAT de cierre retiró los procesos registrados de API y CRM y conservó PostgreSQL, que ya estaba activo. Los BAT de inicio y cierre terminaron con exit0. En esta invocación desde la captura de PowerShell, el proceso exterior de inicio terminó de liberar sus handles al cerrar los hijos; la disponibilidad se comprobó mientras estaban activos mediante estado, HTTP y navegador.

Se compararon antes y después **121 tablas**: 16 de la base nativa y 105 de la fixture. Coinciden nombres, cantidades de filas y hashes SHA-256 de contenido; también Flyway, ledger **1 SUPER_ADMIN / 3 ADMIN / 8 CONDUCTOR** y las **2 aceptaciones**. No se ejecutaron migraciones, limpieza global ni escrituras de negocio.

Corrección factual respecto de una formulación anterior: los 51 éxitos de `ruta_fija_test` corresponden a **V1…V50 más una migración repetible**, no a V0…V50 ni a V51. La aclaración se registra aquí y no reescribe el acta firmada.

Evidencias: [inicio](docs/evidencia/auditoria-ide-2026-10-23/runtime/start-bat.json), [HTTP](docs/evidencia/auditoria-ide-2026-10-23/runtime/http-checks.json), [navegador](docs/evidencia/auditoria-ide-2026-10-23/browser-smoke.json), [captura visual](docs/evidencia/auditoria-ide-2026-10-23/browser-login.png), [cierre](docs/evidencia/auditoria-ide-2026-10-23/runtime/stop-bat.json), [huellas antes](docs/evidencia/auditoria-ide-2026-10-23/launchers/fingerprint-antes-smoke.json), [huellas después](docs/evidencia/auditoria-ide-2026-10-23/runtime/fingerprint-after-smoke.json), [puertos finales](docs/evidencia/auditoria-ide-2026-10-23/runtime/ports-after.json).

## 5. Comandos y resultados

| Comando / comprobación | Resultado final |
|---|---|
| API de diagnósticos del IDE real | 0 errores, 0 advertencias |
| `flutter analyze --no-pub`, raíz y `mobile` | exit0, cero incidencias |
| `flutter test --no-pub --reporter expanded`, `mobile` | 233 PASS, 7 conectadas SKIP, 0 fallos |
| `npm.cmd run typecheck`, `frontend` | exit0; app y tests tipados |
| `npm.cmd run test:ci`, `frontend` | 241 pruebas, 47 archivos, exit0 |
| `npm.cmd run build`, `frontend` | exit0; producción sin aviso de presupuesto |
| `Test-RutaFijaBackendUnitSafe.ps1 -Execute` | compile/test-compile frescos; `-Xlint:all` 0 avisos; 257 PASS |
| `verificar_ruta_fija.bat` | exit0; delegación segura, 257 PASS |
| `Test-RutaFijaLauncherSafety.ps1 -Execute` | 42 comprobaciones PASS |
| Parser de Windows PowerShell sobre `scripts/**/*.ps1` | 79 archivos, cero errores |
| AST Python y JSON de configuración | 3 scripts Python y 11 JSON válidos |
| Inicio, UI/salud y cierre del smoke | PASS; exit0 de ambos BAT; datos idénticos |
| Archivo sellado L-08 | 427 entradas intactas; manifiesto original sin alterar |

Los logs contienen también los intentos fallidos y negativos que llevaron a las correcciones: test móvil antiguo, un stub Mockito de una prueba nueva y guardas rechazadas intencionalmente. No se borraron para aparentar éxito. El primer lanzamiento de typecheck tuvo un error de ruta en el arnés del coordinador y se corrigió; un primer comando PowerShell de tests confundía stderr de Vite con fallo del proceso, por lo que la comprobación definitiva captura el exit code nativo.

Persisten mensajes informativos de herramientas/dependencias, clasificados en el índice backend: carga dinámica de Mockito/ByteBuddy/CDS, detección de Commons Logging junto a spring-jcl y logs de pruebas negativas esperadas. Edge headless dejó mensajes de su plataforma Windows en un log separado; la consola JavaScript de la aplicación quedó limpia. No se suprimieron mensajes ni cambiaron dependencias para ocultarlos. **Cero diagnósticos del proyecto/IDE no significa que todos los logs históricos o de terceros carezcan de la palabra “warning” o “error”.**

## 6. Preservación, alcance y entrega

El snapshot final3, los términos publicados, el acta firmada, los APK/JAR de entrega L-08 y sus manifiestos permanecen intactos. Los cambios en fuentes vivas referenciadas por inventarios antiguos tienen respaldos exactos y registros de sustitución en esta auditoría; los inventarios históricos no se reescribieron. [QA de preservación y revisión móvil del coordinador](docs/evidencia/auditoria-ide-2026-10-23/qa-root-static.json).

Esta entrega corrige el IDE, el código diagnosticado y las entradas de operación inseguras. No acredita una migración de la base nativa V12, pruebas conectadas omitidas, recepción de alertas 24/7, custodia independiente, APK de producción, HTTPS de campo ni los demás requisitos del piloto. Sigue vigente el **GO para candidato local / NO_GO para piloto real** de L-08. La app instalada del POCO no se modificó.

El estado final queda respaldado por [el manifiesto de esta auditoría](docs/evidencia/auditoria-ide-2026-10-23/manifest-auditoria.sha256) y [su verificación](docs/evidencia/auditoria-ide-2026-10-23/verificacion-final.json). El manual de BAT enlazado arriba explica las entradas disponibles, el bloqueo V12 y cómo cerrar sin interrumpir procesos ajenos.

La revisión automática bloqueó una eliminación opcional del perfil temporal `.runtime/qa-edge-1792772227178` con el único motivo «blocked by policy». No se reintentó por otra vía; el perfil permanece conservado y el proceso de navegador está cerrado. Las capturas y pruebas de la aplicación siguen disponibles. Este residuo de la herramienta de QA no es un error del proyecto ni una sesión de usuario activa.
