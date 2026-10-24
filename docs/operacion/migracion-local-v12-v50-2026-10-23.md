# Migración segura local V12 → V50 y desbloqueo del runtime

Fecha: 23/10/2026, America/Lima. Resultado: **APROBADA**. Autorización: instrucción expresa del propietario para respaldar, ensayar, migrar desarrollo y dejar API/CRM funcionando. Esta operación no publica Git/GitHub, no modifica la fixture protegida y no habilita el piloto real.

## Causa y corrección

`iniciar_ruta_fija.bat` rechazaba correctamente `solucion_ruta_fija_1`: tenía V1–V12 y el código exigía V1–V50. La política `RutaFijaRuntimeSchemaPolicy.ps1` se conserva sin cambios. La corrección fue migrar expresamente la base después del respaldo y ensayo. El launcher sigue sin migrar ni ejecutar semillas al abrirse.

## Respaldo preventivo

- Ruta absoluta: `D:\UTP\CICLO X UTP\INTEGRADOR I\APF3\solucion_1_ruta_fija_3\backups\solucion_ruta_fija_1_pre_v50_20261023_171242.dump`.
- Tamaño: **81,446 bytes**; formato custom completo, incluidos objetos grandes.
- SHA-256: `dba3c114210a349f8dc7297cdab56805578cb0a76e1cb2298a7743dac758b467`.
- `pg_dump` y `pg_restore --list` terminaron con código 0. La restauración real del dump en la base aislada también pasó.
- ACL del dump: usuario local y SYSTEM. El dump contiene datos privados y permanece excluido de Git. Las contraseñas solo se importaron al entorno del proceso; no se pasaron como argumentos ni se exportaron a esta evidencia.

Comando de volcado ejecutado, con `PGPASSWORD` obtenido de `Import-RutaFijaNativeEnvironment.ps1 -Scope Runtime -PassThru`:

```powershell
& 'C:/Program Files/PostgreSQL/16/bin/pg_dump.exe' -w -h 127.0.0.1 -p 5432 -U rf_migrator -F c -b -v -f 'backups/solucion_ruta_fija_1_pre_v50_20261023_171242.dump' solucion_ruta_fija_1
& 'C:/Program Files/PostgreSQL/16/bin/pg_restore.exe' --list 'backups/solucion_ruta_fija_1_pre_v50_20261023_171242.dump'
```

## Secuencia ejecutada y resultados

Las fuentes de los ejecutores específicos y los resultados están en [la evidencia de esta operación](../evidencia/migracion-nativa-v50-2026-10-23/). Son herramientas de una migración controlada, no pasos cotidianos de arranque. Sus guardas rechazan una base que ya no esté exactamente en V12.

| Operación / comando desde la raíz | Resultado comprobado |
|---|---|
| `scripts/Get-RutaFijaReadOnlyFingerprint.ps1 -Execute -EvidenceFile <ruta nueva>` | Huellas iniciales por tabla de las dos bases, obtenidas con transacciones de solo lectura |
| `python .runtime/migracion-nativa-v50-20261023_1714/operate.py backup` | Dump de 81446 bytes y catálogo legible |
| `python .runtime/migracion-nativa-v50-20261023_1714/operate.py rehearse` | Restauración aislada como `rf_migrator`; V13–V50 aplicadas; 50 versionadas; conservación de las 15 tablas originales al proyectar sus columnas V12 |
| `python .runtime/migracion-nativa-v50-20261023_1714/operate.py migrate` | **38 migraciones nuevas**, **50 totales**, cero fallos y cero repetibles en desarrollo |
| `grant-rf-app-preserving-security.sql` dentro de la operación anterior | 87 tablas con DML, 16 tablas protegidas de solo lectura y 2 tablas de autoridad sin acceso; secuencias con USAGE/SELECT/UPDATE |
| `Assert-RuntimeSchemaObservation -Database solucion_ruta_fija_1 -Rows @($row) -ExitCode 0 -TestDatabase $false` | **COMPUERTA V50 APROBADA EXITOSAMENTE** |
| `python .runtime/migracion-nativa-v50-20261023_1714/operate.py retire-clone` | Se eliminó exclusivamente la base temporal identificada del ensayo, sin conexiones activas; dump conservado |
| `.\iniciar_ruta_fija.bat` | `F1_1B_START=PASS procesos=controlados docker=0`, modo **NORMAL_LOCAL** |
| `.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test` | `F1_1B_RUNTIME=PASS backend=UP frontend=UP ports=8080,4300` |
| `node .runtime/migracion-nativa-v50-20261023_1714/check_crm_browser.mjs` | Edge mostró `/login?returnUrl=%2Fdashboard`, correo/contraseña e inicio de sesión; cero excepciones JS y cero errores de carga; sin ingresar credenciales |
| `python .runtime/migracion-nativa-v50-20261023_1714/final_checks.py` | 9 comprobaciones HTTP, preservación de datos, historial Flyway, conexiones `rf_app` a desarrollo, inmutables y coincidencia de las 50 fuentes SQL con el JAR |

Flyway 11.7.2/Java21 se ejecutó con las dependencias locales ya disponibles, usuario `rf_migrator`, destino V50, `group=true` transaccional, `cleanDisabled=true`, sin baseline/repair/clean y sin la ubicación de migraciones de pruebas. Se comprobaron V1–V12 antes y exactamente 38 pendientes V13–V50. La validación final estricta comprobó los 50 checksums sin ignorar estados.

En el primer intento del ensayo, `validate()` rechazó las migraciones pendientes antes de ejecutar SQL de migración. Se ajustó el ejecutor para admitir exclusivamente el estado pendiente durante esa validación previa, conservando el control exacto de las 38 pendientes y la validación final estricta. La base real no se alteró en ese intento. También se corrigieron problemas de comillas y precisión temporal al preparar el puente, y aserciones de las herramientas de verificación; los resultados finales aprobados se publican aquí. Las trazas de preparación permanecen en `.runtime/`.

Observación exacta de la compuerta:

```text
solucion_ruta_fija_1|1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50|50|0|
```

## Preservación y permisos

Las 15 tablas de aplicación anteriores contenían **142 filas**; además existían las 12 filas de Flyway. Inmediatamente después de migrar y conceder permisos, las 142 filas conservaban los hashes de sus columnas originales, tanto en el ensayo como en desarrollo. V13–V50 añaden las estructuras y datos de catálogo previstos por sus fuentes inalteradas.

Después del arranque normal, 14 tablas originales siguen idénticas. Los 91 registros originales de `audit_event` también conservan su hash; al corte final hay **18 eventos nuevos `SECURITY_ACCESS_DENIED`**, generados por comprobaciones de seguridad sin credenciales. Son inserciones del runtime, no pérdida de datos. Las cuentas y contraseñas no fueron creadas, reemplazadas ni restablecidas.

`ruta_fija_test` conserva exactamente las huellas iniciales de sus **105 tablas**, ledger **1/3/8**, **2 aceptaciones**, V1–V50 y su repetible de pruebas. No se ejecutaron migraciones, limpiezas ni operaciones de aplicación contra ella. El snapshot L-00 y los términos publicados mantienen sus SHA-256 originales.

Se ejecutaron los GRANT DML y de secuencias solicitados dentro de una transacción que repuso los REVOKE explícitos de V22/V46/V49 y protegió `flyway_schema_history`. No se concedió DDL a `rf_app`. Un GRANT global sin esos REVOKE habría debilitado las reglas de auditoría/retención. Las funciones autorizadas por las migraciones conservan sus permisos.

## Runtime abierto y conexión del POCO

| Servicio | Dirección | Estado al cierre del trabajo |
|---|---|---|
| PostgreSQL | `127.0.0.1:5432` / `::1:5432` | Activo, PID7584, compartido y sin lease de propiedad del proyecto |
| API | `http://127.0.0.1:8080/actuator/health` | **UP / HTTP200**, PID8068, Java21, perfil local, base `solucion_ruta_fija_1` |
| CRM | `http://127.0.0.1:4300` | **HTTP200**, PID14536; formulario real verificado en Edge |
| Proxy Angular | `http://127.0.0.1:4300/api/v1/mobile/profile` | **401 AUTH_TOKEN_INVALID** sin credenciales; petición llegó al backend |
| USB POCO | `adb -s d9538efb reverse tcp:8080 tcp:8080` | Desde el teléfono: salud200/UP y perfil móvil401 sin sesión |
| Puente LAN del POCO | `192.168.18.4:4311 → 127.0.0.1:8080` | PID7596; salud200/UP y perfil móvil401 desde `192.168.18.9` |

La APK instalada sigue siendo `pe.rutafija.conductor` v4, SHA-256 `3b5533d3bb70fdd7bd043ed9fa62b002fadcc86c4ceee2554a4dc9cc02a6ae3c`; se comprobó su hash instalado y el launcher real antes de abrirla. Android informó `Status: ok`. Su URL compilada es `http://192.168.18.4:4311/api/v1`: por ello se preparó un puente temporal específico para este teléfono, con el servidor normal en8080 como destino. No se reinstaló la APK ni se reabrió el antiguo arnés FIS02.

El puente solo acepta ese par de direcciones privadas, `/api/v1/mobile/` y GET de salud. Rechazó otro peer con403 y una ruta administrativa con404. No registra cuerpos, contraseñas ni tokens. Se cierra automáticamente al terminar el proceso exacto del backend registrado. No se modificaron reglas del firewall, no se abrió un túnel público y no se inició otro backend ni otra base para el teléfono. Es una conexión local de desarrollo, no HTTPS productivo.

Se ejecutó además un cierre y reinicio normales: `F1_1B_STOP=PASS`, puertos8080/4300/4311 liberados, retirada automática comprobada del puente y mismo PostgreSQL PID7584 preservado sin lease propia. El arranque final se hizo desde un proceso sin las variables privadas del bootstrap usadas para restaurar el ensayo; el launcher importó su configuración de runtime habitual. La evidencia distingue la primera sesión y el reinicio final.

**Alcance:** conectividad, UI pública y arranque comprobados. No se ejecutó un flujo autenticado ADMIN→CONDUCTOR completo ni se aceptaron términos por una persona. El401 sin sesión es el resultado esperado de una prueba de transporte protegida. Para la prueba manual use cuentas vigentes de **desarrollo**; las identidades FIS02 pertenecen a la fixture protegida y no fueron copiadas a esta base.

## Uso cotidiano después de esta operación

```powershell
.\iniciar_ruta_fija.bat
.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test
# Al terminar la sesión:
.\finalizar_ruta_fija.bat
```

Los servicios se dejan **encendidos** para la prueba del propietario. No es necesario repetir las migraciones. El BAT normal arranca API/CRM; no incluye automáticamente el puente temporal móvil. Si se cierra y vuelve a abrir esta sesión con las mismas direcciones, ejecutar una sola vez, con4311 libre:

```powershell
python .runtime/migracion-nativa-v50-20261023_1714/start_mobile_bridge.py
```

El puente depende de los archivos locales específicos de esta operación y del SDK/JDK ya instalados; sus fuentes se archivan en la evidencia. Si cambian las IP o la URL compilada de la APK, se debe preparar de nuevo la conexión local. El acceso USB a8080 por sí solo no cambia la URL LAN compilada de la APK v4. El cierre normal detiene el backend y el puente termina automáticamente; PostgreSQL ya estaba activo y se conserva.

Sigue vigente **GO PARA CANDIDATO LOCAL DE ENTREGA / NO_GO_PILOTO_REAL**. La migración resuelve el bloqueo nativo V12; las condiciones del piloto real y la API HTTPS de distribución Android permanecen separadas.
