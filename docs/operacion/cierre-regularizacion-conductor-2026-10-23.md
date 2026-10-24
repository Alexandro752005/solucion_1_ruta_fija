# Cierre técnico local — Regularización del conductor heredado

Fecha: 23/10/2026, America/Lima. Decisión: **IMPLEMENTACIÓN VERIFICADA LOCALMENTE Y DISPONIBLE EN EL RUNTIME**. Esta decisión se refiere al código y al flujo técnico; la aprobación personal del expediente real corresponde al ADMIN.

## Problema y comportamiento resultante

El conductor existente conservaba usuario y ficha, pero carecía de expediente aprobado/provisionado. El login móvil rechazaba correctamente su acceso; el alta ordinaria tampoco podía adoptar una cuenta ya existente.

El nuevo flujo permite que ADMIN abra un expediente pendiente, complete el cotejo y su aprobación personal, y lo vincule expresamente a la cuenta existente. Conserva identificadores, contraseña, correo, organización, grupo e historial. La verificación de correo sigue ocurriendo mediante el OTP real del titular después de la vinculación. La habilitación operativa sigue separada.

Se agregó subsanación administrativa para expedientes devueltos y recuperación del estado pendiente después de recargar el CRM. No se requiere acceso móvil previo para revisar documentos desde el CRM. La consulta ordinaria utiliza el vínculo explícito y el evento inmutable de apertura; no adopta identidades por coincidencia de correo.

Contrato añadido: `POST /drivers/{driverId}/regularization`, `POST /onboarding/requests/{id}/provision-existing` y `POST /onboarding/requests/{id}/resubmit-existing`, bajo `/api/v1`. El esquema permanece V1–V50; no se cambiaron migraciones ni se desactivaron compuertas de autorización.

## Comandos y resultados comprobados

| Comprobación | Comando / fuente | Resultado |
|---|---|---|
| Compilación y pruebas unitarias aisladas | `scripts/Test-RutaFijaBackendUnitSafe.ps1 -Execute` | 257 pruebas, 50 clases; cero fallos, errores u omitidas |
| Integración PostgreSQL aislada | Maven offline `test-compile failsafe:integration-test failsafe:verify`, selección explícita `LegacyConductorReconciliationIT,LegacyConductorRegularizationConcurrencyIT` | 13 pruebas aprobadas; incluye dos carreras reales en el caso concurrente |
| Estado pendiente tras recarga | Repetición selectiva de `LegacyConductorReconciliationIT` después del cambio de consulta | 12/12 aprobadas; estado correcto, sin PII y 404 para otra organización |
| Tipos CRM | `npm.cmd run typecheck` | Exit 0 |
| Drivers y Solicitudes | `npm.cmd run test:ci -- --include=src/app/features/drivers/drivers.page.spec.ts --include=src/app/features/onboarding/onboarding-requests.page.spec.ts` | 42/42 aprobadas |
| Build CRM | `npm.cmd run build` | Exit 0, 11.193 s; sin advertencias ni errores |
| Empaquetado backend | Maven offline `-Dmaven.test.skip=true package`, después de ejecutar las pruebas | BUILD SUCCESS, 4.587 s |
| Cierre y arranque | `finalizar_ruta_fija.bat` / `iniciar_ruta_fija.bat` | Procesos propios detenidos/reiniciados; PostgreSQL compartido preservado |
| Runtime | `Invoke-RutaFijaNativeRuntime.ps1 -Action Test` | API UP, CRM UP, puertos 8080/4300 |
| Puente | `Invoke-RutaFijaMobileLocalBridge.ps1 -Action Test -Execute` | Instancia propia verificada, 4311→8080 |
| POCO real por ADB/curl, sin login | Salud / móvil anónimo / ruta administrativa | 200 / 401 / 404 |
| Confirmación expresa | Petición autenticada ADMIN con confirmación falsa | 400; no crea expediente ni aprobación |
| Cierre del puente ante carrera de salida | `scripts/Test-RutaFijaMobileBridgeStopRace.ps1` | 9 comprobaciones aisladas aprobadas; ningún proceso real terminado por el ensayo |

La suite histórica de integración que limpia `ruta_fija_test` no se utilizó. Se prepararon dos bases QA nuevas, con privilegios por defecto antes de Flyway y datos sintéticos. Las pruebas de flujo utilizan rollback; la de concurrencia utiliza transacciones reales y dos hilos. Ambas bases temporales se retiraron después de verificar su nombre y contenido sintético, sin limpieza global de bases compartidas ni terminación forzada de clientes.

Las advertencias de instrumentación dinámica de Mockito/Byte Buddy que aparecen en los logs corresponden a la JVM de pruebas. No se modificaron dependencias ni se ocultaron diagnósticos para cambiar los resultados.

## Respaldo y preservación

Antes de la intervención se generó `backend/.local/accesos/regularizacion-conductor-20261023_215206/pre-regularizacion.dump`: **657.360 bytes**, SHA-256 `4794aa3cb5ff7757563e1816f0cdb600744b3dab1d5118b0c1a8b7fb95e99a37`. Su índice custom se validó mediante `pg_restore --list`; no se declara una nueva restauración de ese archivo.

Las copias de fuentes y del JAR anterior permanecen en carpetas privadas excluidas. Los originales individuales anteriores a cada edición se conservan en `backend/.local/accesos/regularizacion-conductor-fuentes/`, incluyendo la reconstrucción de los tres primeros cambios del CRM. No se publicaron credenciales, volcados ni almacenes privados.

La comparación de huellas de las 105 tablas de `ruta_fija_test` fue idéntica. En la base nativa se conservaron ledger, términos, aceptación personal ADMIN, fichas, identidad y registro Flyway. Las tres cuentas conservaron exactamente su UUID, correo, contraseña, rol, organización y datos de perfil comprobados contra el respaldo.

Las únicas tablas nativas con cambios fueron `assignment`, `operation_notice`, `app_user`, `refresh_token` y `audit_event`: las dos cancelaciones autorizadas conservaron ambas asignaciones y generaron cuatro avisos internos de cancelación; los accesos y cierres de sesión registraron sus efectos normales. No se creó ni aprobó el expediente real del conductor mediante el ensayo técnico.

Los bytes del documento ADMIN aprobado y del snapshot base L-00 conservaron sus SHA-256. Las migraciones del JAR coinciden con las fuentes y el arranque validó V50.

## Corrección incidental del cierre del puente

Durante el reinicio se detectó que el puente podía terminar por sí mismo mientras Windows consultaba el dueño del puerto. El helper trataba esa salida ya ocurrida como un conflicto de propiedad. Se corrigió para comprobar la salida del mismo objeto `Process` después de consultar el puerto. Un proceso vivo con dueño ajeno continúa rechazándose; la terminación recibe el objeto previamente verificado. Nueve comprobaciones aisladas cubren salida concurrente, dueño ajeno, ausencia de escucha y terminación del objeto correcto.

## Handoff

El runtime quedó activo con el código actualizado y el puente comprobado desde el POCO. La sesión técnica ADMIN quedó cerrada. La revisión real de identidad, cotejo, aprobación y OTP del conductor siguen pendientes de sus titulares; [algoritmo completo](regularizacion-conductor-heredado.md).

[Evidencia local](../evidencia/regularizacion-conductor-2026-10-23/): resultados QA, comprobación runtime/POCO, cancelaciones autorizadas, respaldo, limpieza QA, logs y manifiesto SHA-256. Los logs detallados de integración y sus primeros cortes se preservan en `.runtime/regularizacion-conductor-20261023_215206/`; la regresión unitaria tiene su evidencia independiente en `docs/evidencia/auditoria-ide-2026-10-23/backend/run-aa229598-2ab8-4103-9034-7462b7514172/`.
