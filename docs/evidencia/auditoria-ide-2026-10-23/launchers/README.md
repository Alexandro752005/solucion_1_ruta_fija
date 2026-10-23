# Auditoría de ejecutables y scripts — 2026-10-23

Este corte evoluciona los archivos vivos de operación después de un [respaldo exacto de 16 originales](respaldo-originales.json). No reescribe actas, evidencias, firmas ni manifiestos de L-00 a L-08. La [supersesión local](source-supersession.json) vincula cada original, su copia y el archivo vigente; los hashes históricos siguen describiendo sus bytes históricos.

El [manual principal vigente](<../../../Uso del Sistema.md>) y el [README backend](../../../../backend/README.md) incorporan las instrucciones actuales y conservan sus bytes anteriores dentro de una sección de antecedentes. Los comandos antiguos de bootstrap, limpieza o migración no son instrucciones del corte actual.

## Hallazgos y cambios

| Hallazgo comprobado | Corrección y evidencia |
|---|---|
| Los dos BAT de demo tenían un byte VT en la ruta PowerShell, separadores perdidos y un CR interno en la ruta de URL | Rutas absolutas entre comillas, CRLF válido y propagación de fallo. Los originales se preservan; los cinco BAT superan los controles de bytes y rutas de la suite nueva |
| `Invoke-RutaFijaNativeTest.ps1 -Action Verify` ejecutaba `TRUNCATE` masivo en finally y exigía V14 | Verify delega al runner unitario seguro antes de importar secretos. Clean se rechaza antes de conexión; modo por defecto Plan. No se ejecutó el flujo destructivo histórico |
| `Test-RutaFijaNativePreflight.ps1` invocaba Git | Se sustituye por política local: ruta bajo `backend/.local`, rechazo de reparse points, exclusión canónica declarada y ACL estricta. No se consultó Git ni su índice |
| Los dos archivos privados permitían `Authenticated Users: Modify` por herencia | ACL corregida solo en esos archivos: usuario actual y SYSTEM. [Descriptores previos](acl-descriptores/) y [resultado](acl-verificacion.json); ningún contenido secreto fue copiado a evidencia |
| El cierre detenía PostgreSQL global aun cuando ya estaba activo o tenía otros clientes | Nueva lease: solo se adquiere si el launcher inició el servicio detenido. StopOwned requiere instancia exacta y ausencia observable de otros clientes; incertidumbre conserva PostgreSQL. Parada global disponible solo como opción explícita separada |
| Un filtro por `backend_type` podía omitir sesiones ajenas cuyo tipo estuviera oculto | Se cuentan identidades de sesión visibles; NULL/observabilidad incompleta nunca prueba cero clientes. [Consulta real](lease-observabilidad-readonly.json): no hay observabilidad completa, por lo que se rechaza la parada sin conceder privilegios |
| El instante de creación del servicio puede no ser visible desde la cuenta normal | Si ya corría sin lease se permite reutilizarlo sin adquirir propiedad. Una parada con lease exige el instante observable; las identidades inciertas no se detienen |
| El runtime identificaba procesos solo mediante PID, nombre y marcador corto | Estado v2 registra ruta completa, inicio UTC, puerto y marcador absoluto; PID reutilizado o ejecutable distinto se rechazan. QuickDemo incorpora también el inicio UTC de cloudflared |
| Start podía ejecutar Flyway automáticamente | Se valida el historial V1 a V50 y 51 éxitos mediante SELECT; Flyway queda desactivado al iniciar. Migraciones solo por procedimientos separados autorizados |
| La base normal y la fixture no tienen el mismo esquema | [SELECT real](esquemas-readonly.json): desarrollo V12; prueba V50. El arranque normal se bloquea antes de Java/Angular. Smoke de lectura sobre la fixture requiere dos flags expresos; no cambia la configuración persistente ni las reglas del modo normal |
| Se confundió el total de 51 éxitos con V0 a V50 | [Aclaración nueva](migraciones-versionadas-y-repetibles.json): son 50 versionadas V1 a V50 y una repetible. El acta firmada previa no se modifica |
| QuickDemo podía instalar cloudflared automáticamente o publicar antes de comprobar el esquema | No hay instalación/descarga automática. Puertos nativos libres y esquema compatible se comprueban antes de iniciar el túnel; con desarrollo V12 se rechaza. Ningún túnel público se ejecutó en esta auditoría |
| Una modificación en tests forzaba recompilar el JAR pese a usar `maven.test.skip` | La vigencia del JAR considera `src/main`, POM y configuración Maven; los tests tienen su runner aislado |

## Comandos y resultados ejecutados

| Comando o inspección | Resultado |
|---|---|
| Lectura de cinco BAT y sus destinos NativeRuntime, NativeTest, QuickDemo, PostgreSQL, preflight/importadores, auditoría F5.2 y proxy Angular | Dependencias/rutas/puertos/efectos revisados; no se ejecutó instalación, demo pública, migración ni limpieza |
| Parser Windows PowerShell5.1 de todos los `scripts/**/*.ps1` vivos | [79 scripts, cero errores](parser-todos-ps1-final.json); solo parse, no ejecución de sus cuerpos |
| `Test-RutaFijaLauncherSafety.ps1 -Execute` | [42 comprobaciones aprobadas](qa-mocks-lease-demo-final.json): ownership y observabilidad simulados, PID reutilizado, bytes/rutas BAT, guardas demo y parsers. No inició/detuvo servicio ni accedió a DB |
| `Invoke-RutaFijaNativeRuntime.ps1 -Action Start` sobre destino normal | [Rechazo real con código1](guardas-ejecutadas.json) por V12 antes de Java/Angular y sin migraciones |
| `... -Action Start -SmokeTestDatabase` sin `-SmokeReadOnly` | Código1 antes de importar configuración o conectar |
| `Invoke-RutaFijaNativeTest.ps1 -Action Clean` | Código1 antes de importar configuración o conectar; la limpieza global es inaccesible |
| `Get-RutaFijaReadOnlyFingerprint.ps1 -Execute ...` | [Snapshot previo](fingerprint-antes-smoke.json): hashes SHA-256 de contenido por tabla para 121 tablas entre ambas bases, ledger/aceptaciones e historial Flyway; sin exportar filas personales |
| Runner unitario backend del agente Java | 50 clases, 257 pruebas aprobadas; compile/test-compile y reportes aislados. Esta evidencia pertenece a la [superficie backend](../backend/) y no equivale a integración DB |

El smoke real de API/CRM y los BAT se coordina desde la auditoría raíz después de disponer del JAR actual y revisión independiente. Este documento no lo presenta como aprobado antes de ejecutarlo. El cierre final debe comparar el fingerprint posterior con el anterior y conservar los intentos/fallos. Los procesos normales de negocio no se validan mediante smoke de lectura.

## Límites y preservación

- La base normal V12 sigue sin migrarse: el inicio funcional normal del backend V50 requiere una migración planificada, respaldada y autorizada fuera de la auditoría del IDE.
- El modo smoke de lectura desactiva las dos tareas de mantenimiento solo para ese proceso y configura las conexiones JDBC/Hikari para rechazar escritura; no es un modo de operación para ADMIN/CONDUCTOR.
- En esta cuenta, el inicio UTC de PostgreSQL y algunos tipos de sesión no son observables. El resultado seguro es conservar el servicio; no se concedieron privilegios para poder apagarlo.
- La revisión del demo valida guardas y proceso de cierre; no acredita conectividad externa, disponibilidad de cloudflared ni autorización de publicar datos reales. El candidato local conserva sus gates del piloto.
- Los archivos originales bajo `originales/` contienen fuentes/documentos públicos. Los archivos privados no fueron copiados; solo se archivaron descriptores ACL.
- No se ejecutó Git/GitHub, instalación de herramientas, TRUNCATE, migración, reapertura de casos ni parada de procesos desconocidos. Los estados y documentos firmados históricos permanecen preservados; los cambios vivos tienen trazabilidad nueva.
