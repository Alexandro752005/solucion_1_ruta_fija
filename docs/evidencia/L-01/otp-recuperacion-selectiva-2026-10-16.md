# CI-L01-01 — Diagnóstico y continuación selectiva del caso manual

> **Histórico preservado. Actualización 2026-10-17:** la continuación fa616262-500e-4490-9292-7ee1927d2257 quedó acreditada y CI01 VERIFICADO. [Correspondencia, archivo y estado actual](cierre-parcial-CI01-y-terminos-2026-10-17.md). No volver a ejecutar las instrucciones de envío de este corte fallido.

Fecha: **2026-10-16, America/Lima**. L-01 **EN_CURSO**. CI-L01-01 integral **PENDIENTE**. Sin envíos externos por el agente durante este diagnóstico.

Caso exacto comunicado por el titular: `3e9c0bf5-237b-4370-a87f-cea684e0b1d8`. No se consultaron credenciales, OTP ni tokens para mostrarlos en la evidencia. Las consultas operativas usan únicamente `ruta_fija_test` y `rf_test`; la única lectura global de negocio es el agregado de cupos ADMIN solicitado.

## Registro humano y causa

El titular comunicó emisión HTTP, éxito SMTP, desafío persistido y recepción del correo. Pulsó Enter sin escribir en la entrada oculta y obtuvo `FAIL phase=CONSOLE_OTP_INPUT reason=OTP_INPUT_FORMAT_REJECTED`. Informó cierre `PASS_DISABLED_AND_TOKENS_REVOKED` y ledger preservado. Otros dos lanzamientos se detuvieron en `PREFLIGHT / RECIPIENT_ALREADY_PRELIMINARY_IDENTITY`.

El arnés original hacía una sola lectura `Console.readPassword`: una entrada vacía fallaba antes de llamar al endpoint de verificación. La limpieza conserva intencionadamente identidad e historial de envíos, invalida la capacidad/OTP, cambia la contraseña sintética y deshabilita organización/ADMIN. El preflight de un **caso nuevo** rechaza cualquier identidad preliminar existente. Por ello esos dos lanzamientos posteriores no crearon otro fixture ni emitieron otro correo. No se atribuye este fallo a Gmail, las credenciales ni al teclado.

No es posible continuar con el OTP/token de aquel proceso: la limpieza ya retiró el OTP e invalidó la capacidad, y no se conserva el token en claro. No se reconstruyen secretos ni se revierte esa invalidación.

## Estado contrastado de solo lectura

Inspecciones anterior y posterior a las pruebas: 16-10-2026 **21:32:33 y 21:40:23 Lima**. Sus marcas UTC son del 17-10 por la diferencia horaria; no son otro día operativo del piloto.

| Dato del caso | Resultado |
|---|---|
| Organización sintética / ADMIN | INACTIVE / active=false |
| Ledger propio ADMIN / total ADMIN | 1 / 1; quedan 3 altas históricas de prueba |
| Envío conservado | 16-10-2026 21:15:30.687057 Lima (`2026-10-17T02:15:30.687057Z`) |
| send_at_2 / send_at_3 | null / null |
| Presupuesto a las 21:40 Lima | 1 envío en ventana móvil de 60 minutos; **2 disponibles** |
| Verificaciones HTTP fallidas registradas | 0 |
| OTP almacenado / capacidad utilizable | No / no |
| Correo verificado / desafío consumido | false / false |
| Invitación anterior | Revocada, sin consumir |
| Solicitudes / cuenta operativa del destinatario / tokens de acceso activos | 0 / 0 / 0 |
| Recursos inesperados dentro del fixture | 0 |
| Elegibilidad de continuación selectiva | PASS_SAME_IDENTITY_NO_NEW_ADMIN |

El cupo es una observación temporal, no una reserva. Se vuelve a calcular antes de cualquier intento. No se borró ni cambió el caso real durante este diagnóstico; las inspecciones mantienen su envío, ledger y estados. No se consultaron filas personales ajenas para elaborar un inventario.

## Cambios del arnés

Archivos: `scripts/Invoke-RutaFijaL01OtpManual.ps1`, `ManualOtpDeliveryValidation.java`, nuevo `ManualOtpCaseRecovery.java` y pruebas dedicadas en `backend/src/test/java/pe/rutafija/support/`.

- `-InspectRunId`: conexión JDBC de solo lectura/repetible, sin arrancar Spring, Flyway, HTTP ni SMTP. Valida nombre sintético exacto, ADMIN propio, destinatario, desafío, revocación, ausencia de solicitud/operación/recursos inesperados y cupos. No imprime hashes de contraseña/OTP/capacidad.
- `-InputSelfTest`: `Console.readPassword` con datos ficticios; no lee la configuración privada ni conecta a DB/HTTP/SMTP. El lanzador conserva UTF-8 con BOM para que Windows PowerShell lea sus mensajes correctamente.
- `-Execute -ResumeRunId`: rama explícita y cerrada para el fixture sintético exacto. No se afloja el preflight de un caso nuevo. Exige caso deshabilitado, identidad no verificada, cero solicitudes, cero errores/bloqueos OTP pendientes y presupuesto disponible; un caso distinto, modificado o consumido se rechaza.
- La preparación de continuación bloquea/revalida organización, ADMIN, identidad y desafío. Reutiliza ADMIN, identidad y ledger; **no consume otra alta ADMIN**. Habilita temporalmente solo la organización sintética y crea una invitación nueva de 120 horas con secreto efímero. El ADMIN permanece inactivo y la invitación anterior permanece revocada. No escribe en el desafío/identidad ni reinicia sus contadores durante esa preparación.
- El endpoint normal POST `/api/v1/onboarding/requests` emite una vez y conserva el presupuesto previo de la misma identidad. Se comprueba que los tiempos anteriores aún dentro de la ventana sigan presentes y solo se haya añadido el nuevo envío. Los tiempos que vencen salen de la ventana por la regla normal, sin borrar datos para forzar cupo.
- Se imprime un **RUN_ID nuevo del intento** y `MANUAL_OTP_CASE_RUN_ID` con el RUN_ID raíz anterior. El nuevo intento utiliza su propia clave de idempotencia. Se declara `MANUAL_OTP_ADMIN_HISTORICAL_CREATIONS_CONSUMED=0` en continuación.
- La captura explica que **no aparecerán caracteres ni asteriscos**. Se permiten hasta tres lecturas locales en total, solo si las anteriores están vacías. Vacío no llama al servidor. Se conserva el vencimiento recibido del primer HTTP; se comprueba antes y después de cada lectura. No se renueva el plazo. Cancelación, formato no vacío incorrecto o plazo vencido detienen el caso.
- Tras obtener seis cifras ASCII, el modo real permite **una sola verificación HTTP**. Un rechazo no provoca otra lectura, verificación o envío. No se ejecutan las negativas automáticas del modo simulado en Gmail.
- La limpieza sigue restringida al fixture: invalida capacidad/OTP, revoca accesos, cambia contraseña sintética, deshabilita organización/ADMIN y revoca únicamente invitación propia sin usar. Conserva una invitación consumida como consumida. Ahora contrasta retiro de OTP/capacidad, ausencia de accesos activos, ledger propio y conservación exacta de tiempos/errores/bloqueos.
- No arranca Flyway en el proceso manual; utiliza el esquema V26 ya existente con validación JPA. Se mantienen seed/offline desactivados y tareas programadas retiradas del proceso aislado. Un bloqueo asesor no bloqueante coordina con la limpieza estándar de test; si está ocupado, se detiene. Esto no autoriza ejecutar otras pruebas durante el paso humano.

No se elimina identidad, desafío, invitación o ledger para resolver el preflight; no hay TRUNCATE, cambio de proveedor, edición privada ni modificación de límites de negocio.

## Comprobaciones ejecutadas y límites

1. **Consola interactiva real (ConPTY):** se lanzó `-InputSelfTest`, se pulsó Enter vacío y se introdujeron después seis cifras ficticias. La consola volvió a pedir entrada sin mostrar las cifras ni asteriscos. Resultado `MANUAL_OTP_INPUT_SELF_TEST=PASS; DB_CONNECTED=false; SMTP_ATTEMPTS=0; HTTP_VERIFICATIONS=0`. No se redirigió la entrada ni se usó un OTP real.
2. **12 unitarias correctas:** ocho para captura (vacío, cancelación, formato, borrado de buffer y caducidad exacta/original) y cuatro para cupos/alcance (incluye ventana exactamente a 60 minutos y continuidad incluso sin un nuevo cupo ADMIN).
3. **4 integraciones correctas en PostgreSQL real, correo doble:** continuación con la misma identidad, preservación de tiempos/ledger/invitación revocada y organización sintética ajena; emisión/consumo por servicio y limpieza; rechazo de ventana agotada, RUN_ID desconocido y caso con error HTTP previo. **No son un ensayo nuevo de HTTP remoto ni Gmail.** Cada caso usa una transacción marcada para rollback desde el inicio; tampoco se confirma su alta temporal en el ledger.
4. Esta clase **no hereda `NativePostgresIntegrationTest`**, no llama a `cleanBusinessData`, no aplica Flyway ni deja fixtures confirmados. Comprueba después del rollback que desaparecieron sus propios registros nuevos y el agregado del ledger coincide con el anterior. No se ejecutaron los arneses históricos que limpian toda la base.
5. Sintaxis PowerShell y modo estático correctos; modo estático declara DB=false/SMTP=0. El wrapper ejecutó realmente la inspección final del RUN_ID indicado. El caso real mantiene el estado de la tabla anterior. También se ejecutó `-Prepare` del wrapper final: BUILD SUCCESS, clases al día/classpath preparado, sin pruebas/DB/SMTP; log separado identificado abajo.
6. `final3` ZIP/manifiesto y candidato de términos conservan sus huellas anteriores. No hay Git/GitHub, despliegue, migración de desarrollo/piloto, claves reales o términos publicados.

Comando dirigido final (no usar mientras el titular realiza su intento manual):

```powershell
# Desde backend; SOLO estas pruebas, sin limpieza global:
.\mvnw.cmd --batch-mode --no-transfer-progress verify `
  '-Dtest=ManualOtpConsoleInputTest,ManualOtpCaseRecoveryTest' `
  '-Dit.test=ManualOtpCaseRecoveryIT' `
  '-Djacoco.destFile=target/l01-otp-recovery-final.exec' `
  '-Djacoco.dataFile=target/l01-otp-recovery-final.exec'
```

Resultado final: **12 unitarias + 4 IT, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**. No se suma a los totales de ejecuciones anteriores ni se afirma una regresión completa nueva. La primera dirigida también pasó; produjo avisos JaCoCo por datos acumulados de otras compilaciones. La final separa su archivo de ejecución y no produjo esos avisos. No se presenta un porcentaje global de cobertura.

| Artefacto | SHA-256 |
|---|---|
| `backend/target/l01-otp-recovery-final-2026-10-16.log` | `7E226CF798D347EB1127FCBB00DE3446A7B4D09B44CE328401D8E90780D8DCBB` |
| `backend/target/l01-otp-case-inspection-initial-2026-10-16.log` | `D49C963AF8F39FCF5E2B5A651514329903AE09A8AF73E18DCD6D5E6B0F743541` |
| `backend/target/l01-otp-case-inspection-final-2026-10-16.log` | `06AD02F4E8172DC36CB7530323CB77FAC5CC15BD146DEABA6EDF99EF0A0311FD` |
| `backend/target/l01-otp-static-final-2026-10-16.log` | `498F949FD1FA959B520B44F33594C49BC18AE91CB9044A8F36BA297A04A31254` |
| `backend/target/l01-otp-prepare-final-2026-10-16.log` | `7B66469AC63AF654B030A677C3F0CE042AB306A74879D099FEC0F81B6340E6C6` |
| `scripts/Invoke-RutaFijaL01OtpManual.ps1` | `AF6961340DA6259382EB1540DEBF7C7D18BF03ADE05889C8901FC5049A52B1C0` |
| `ManualOtpDeliveryValidation.java` | `0B4B61B62870BC8FE163CF2476CCFBDB74502D44386C37D6AD6C7422223B4259` |
| `ManualOtpCaseRecovery.java` | `F1845A66BB65C524D7FF7FF95E61FFAD7FCED7281300F894E301AF5B5DE92956` |

La prueba ConPTY está observada en las llamadas de herramientas de esta interacción; no se inventa una transcripción automática en disco. Los logs de Maven/inspección sí son archivos generados por sus comandos y se identifican arriba.

## Siguiente intento exacto del titular

No se necesita un nuevo destinatario ni proveedor. Se mantienen `rutafija.dev1@gmail.com` y `sandrito10xyz@gmail.com`, Gmail587/auth/STARTTLS obligatorio y credencial privada vigente, sin mostrarla ni modificar listas.

Desde la raíz, en terminal interactiva, sin transcripción/redirecciones/capturas y sin otras pruebas ejecutándose:

```powershell
# Preparación sin pruebas, DB o SMTP (opcional si ya se usan los artefactos de este incremento):
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -Prepare

# Solo seis cifras FICTICIAS: pulsa primero Enter vacío y luego escribe el valor ficticio:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -InputSelfTest

# Solo lectura: debe indicar PASS_SAME_IDENTITY_NO_NEW_ADMIN y al menos un envío disponible:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -InspectRunId '3e9c0bf5-237b-4370-a87f-cea684e0b1d8'

# UN intento real selectivo; no ejecutar -Execute solo ni repetir automáticamente:
.\scripts\Invoke-RutaFijaL01OtpManual.ps1 -Execute -ResumeRunId '3e9c0bf5-237b-4370-a87f-cea684e0b1d8'
```

En el último comando: confirmar `ENVIAR_UN_OTP_L01`; al recibir el correo nuevo, escribir `RECIBIDO`; introducir **solo en la entrada oculta** sus seis cifras y pulsar Enter. **Es normal que no aparezcan caracteres ni asteriscos.** Un Enter vacío se puede corregir localmente hasta agotar las tres lecturas o la vigencia original. No reutilizar el correo/OTP del intento anterior. Si falla cualquier fase, detener y comunicar únicamente fase/código y cierre saneados.

Comunicar solamente RUN_ID nuevo, CASE_RUN_ID raíz, RESULT, HTTP_CONSUMPTION, EXACT_FIXTURE_CLEANUP y LEDGER_CREATION_PRESERVED. No enviar OTP, tokens, credenciales ni cuerpos HTTP. Un resultado satisfactorio debe incluir `PASS_PERSISTED_AND_CONSUMED_PENDING_REVIEW` y cierre correcto. Hasta obtenerlo, **CI-L01-01 integral sigue pendiente**; esta preparación no lo cierra ni autoriza L-02.
