# L08.2 — Regresiones, monitor, retiro y continuación de los casos autorizados

2026-10-22 America/Lima. **L-08 EN_CURSO; piloto y despliegue bloqueados.** Este archivo no es un acta de cierre.

## Corte vigente posterior a CAP25

[Revisión integral con once apartados](../revision-integral-2026-10-22.md), [matriz](../matriz-L08.md).

- **CAP25 acreditado y retirado**: [caso](../cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/README.md),25 actores sintéticos en cuatro organizaciones,100 creaciones y100 aceptaciones sin errores. p95 durable343,0684ms y461,8702ms; pico medido25HTTP, conexión PostgreSQL observadora independiente. No25 personas/equipos ni latencia exclusiva de red o servidor.
- **Retiro concurrente acreditado en una finalidad**: inventario55P03, segunda conexión espera, una ejecución/auditoría/tombstone y replay. No se extiende a todas las carreras de hijos por finalidad. Dataset retirado, sesiones revocadas,100 servicios/ofertas y cuatro aceptaciones conservados; ledger propio1/4/20, marcador ausente/lock libre y cluster55439 detenido. Sin cambios al histórico1/3/7 de5432.
- **Candidato backend actualizado**: JAR SHA-256 `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`,672 entradas iguales a clases/recursos compilados. [API V50 empaquetada readonly](candidato-v50-solo-lectura.json):DB/readiness/liveness200UP, sin correo/migración/escrituras, huellas completas antes/después iguales, contexto y puerto cerrados. No versión liberada.
- **Restauración V48**: integridad SQL y arranque readonly de su binario preservado acreditados; cluster55438 detenido, ambas bases conservadas. RTO empresarial completo y RPO siguen pendientes.
- **Seguridad y preservación**: auditoría npm actual0, lock intacto; [144L07+36incremento1+451casofísico+12protegidos](preservacion-post-cap25.json) coincidentes. final3 y términos publicados intactos. No SMTP, Git/GitHub o despliegue.

Las menciones inferiores a CAP25 sin ejecutar, integridad pendiente o casos físicos abiertos corresponden a cortes históricos. Las evidencias originales permanecen conservadas.

## Estado vigente después del retiro físico

- [Caso físico cerrado y archivado](../conectado-a999ef52-d87f-498d-9251-1581cbb72562/cierre-caso.json): `PASS_PHYSICAL_FOREGROUND_CASE_ONLY`,14 puntos GPS, servicio completado y logout contrastados. Cuenta y recursos deshabilitados, todas las sesiones revocadas; contadores1/3/7 y dos aceptaciones conservados. `TEST_CLEANUP_RELEASED=true`, marcador ausente y lock libre. Se pueden reanudar pruebas dirigidas con rollback; la limpieza global sigue prohibida. No reabrir el caso retirado.
- [Integridad del snapshot V48 restaurado](restauracion/reconciliacion-integridad.md): comprobación completa contra las huellas originales, de solo lectura. Diferencias de representación SQL, permisos implícitos y colación explicadas y verificadas. **No acredita todavía recuperación empresarial, RTO completo o RPO.** No se repitió la restauración ni se migró el destino.
- Batería, TalkBack operativo, carga25, navegación operativa de escritorio, entrega final y condiciones operativas reales siguen con sus faltantes en la [matriz](../matriz-L08.md).

Las secciones siguientes conservan cortes anteriores. Las referencias a caso abierto, bloqueo de pruebas o diferencias de integridad no resueltas son históricas y quedan actualizadas por el estado anterior.

### Continuación técnica posterior

- [Arranque V48 sobre restauración](restauracion/arranque-v48-solo-lectura.json): API y PostgreSQL reales, readiness con BD y liveness HTTP200; clases del JAR preservado, sesiones de solo lectura, sin migraciones o correo y huellas completas iguales antes/después. No acredita operación empresarial completa o RTO/RPO.
- [Serialización de retiros](pruebas/retiro-serializacion/resultado.json):92 comprobaciones dirigidas correctas:9 unitarias,2 PostgreSQL de solo lectura entre conexiones y81 regresiones PostgreSQL con rollback. Se corrige la lectura de `replay` anterior al bloqueo: la capa Java ahora toma un advisory lock transaccional por plan antes de consultarla y auditar. No se concede UPDATE sobre los planes inmutables. Las dos pruebas reales demuestran espera, liberación por rollback y planes distintos; todavía no prueban el retiro completo con commit/tombstone.
- [L08-CAP25-01 autorizada](../CAP25-01-autorizacion.json): instancia nueva solo127.0.0.1:55439 y dataset máximo1/4/20 en cuatro organizaciones, correo/GPS simulados. Implementación en preparación; no hay aún medición de carga o carrera integral. Se conservan intactos5432,55438 y los casos retirados. [Propuesta exacta aprobada](../../../operacion/L-08-CAP25-01-validacion-aislada-propuesta.md).

## Cambios y comprobaciones

- API: error canónico503 para fallo de conexión/dependencia, separado de401 y500; registros sin cuerpo/secretos ni stack del error. Móvil: generación de sesión y revocación de familias, rechazo de respuestas antiguas y comprobación de permiso/GPS después del diálogo. Sin reenvío automático de operaciones inciertas ni cola offline.
- [Backend final](pruebas/backend-final.json): **112 casos**, 81 Spring/PostgreSQL rollback y31 unitarios/criptografía/HTTP loopback. Incluye17 nuevos de retiro personal y64 de regresión de retención/calificación. La ejecución previa V49 tuvo98 casos y4 errores; se conserva su diagnóstico. Se corrigió mediante **V50 aditiva**, sin editar V49 aplicada. [V50 reconciliada](base/v50-reconciliacion.json) y validación51; el primer postcheck de V50 conservó un literal49 incorrecto: su fallo también se conserva y no se repitió la migración.
- [Otras ejecuciones](pruebas/otras-ejecuciones.json): suite CRM241/47archivos; Flutter59 y analyze; después dos casos del formulario replican envío vacío→datos ficticios→503. No reprodujeron la reaparición de campos requeridos reportada por el usuario; no se inventa una corrección de producción. Recheck14 de criptografía/comparación/guardas tras ampliar el destino de restauración. No son pruebas físicas o de participantes reales.
- Retiro por finalidad: expediente, PII de ADMIN/CONDUCTOR, vínculos/HMAC, aceptación y recuperación; fechas exactas30/90d, delegación previa de ADMIN y ejecución técnica después, excepción justificada/vencimiento, planes con huella y negativa a repoblar. IDs, ledger, términos publicados y credencial SUPER_ADMIN conservados. Las17 pruebas usan hitos sintéticos dentro de transacciones revertidas; no se ha iniciado/cerrado un piloto real ni purgado los casos previos. La carrera entre conexiones independientes sigue sin acreditarse.
- [Monitor y reconciliación](disponibilidad/reconciliation.json): API Spring real de pruebas, PostgreSQL real sin detenerlo, monitor en otra JVM de la **misma PC**, reloj real y cadencia acelerada1s. Detección desde comando de parada2,804s; recuperación de salud desde detección13,024s. El cierre inicial falló por interrumpir una escritura; se mantiene [ese resultado](disponibilidad/result.json), sus muestras y alertas locales. Se corrigió la salida cooperativa y pasó su regresión. Reconciliación posterior acreditó ausencia de procesos propios/puerto, lock libre y todas las filas preservadas, sin repetir la caída. No son monitor independiente, alerta externa, cobertura24/7 o recuperación empresarial completa.
- [Poco, 503](telefono/resultado-humano.json): APK corregido aislado, una petición POST503 registrada, tiempo de respuesta del doble4,837ms. Usuario observó el mensaje previsto y avisos de campos. No se atribuye un tiempo de ausencia de reintentos más allá de la observación registrada. [TalkBack del login](telefono/talkback-login.json): respuesta humana afirmativa general; no existe transcripción por etiqueta ni validación del recorrido operativo.
- [Artefactos locales](artefactos.json): JAR V50 y APK debug separado `.l08`, con huellas e inventario. No firmados/publicados como entrega definitiva. El JAR no contiene configuración privada, `.env`, archivos de claves o respaldos. Compilarlo con `-DskipTests` no se cuenta como ejecución de pruebas.

## Restauración: evidencia parcial conservada

[Estado y límites](restauracion/estado.json). El respaldo V48 cifrado/autenticado es legible en esta PC. Las dos restauraciones se mantienen en la instancia propia55438: `ruta_fija_test` con región C y `ruta_fija_test_l08_locale` con Spanish_Peru.1252, esta última **autorizada por L08-REST-02**. No se sobrescribió5432 ni se regeneró el archivo cifrado.

La comparación inicial también confundía nodos JSON Long/Integer; se corrigió y probó sin ignorar claves, conteos o hashes. Persisten diferencias en **ACL, constraints e índices**, incluso con región coincidente: **no se declara restauración íntegra**. La inspección estática encontró, entre otros cambios de representación, casts de arrays al reconstruir CHECK/predicados; el contraste contra el origen actualV50 debe separar adiciones V49/V50 del snapshotV48. Las huellas originales no se sustituyen ni se atribuyen todas las diferencias a la región. Faltan equivalencia de esquema/permisos, comprobación de aplicación y cronometraje completo de recuperación. Mismo equipo no acredita medio independiente ni RPO horario.

## Caso físico en curso y protección

RUN `a999ef52-d87f-498d-9251-1581cbb72562`: una alta CONDUCTOR normal autorizada; ledger **1/3/7**, aceptaciones2. Marcador/lock activos y prueba de negativa antes del fixture comprobados. Los casos anteriores no se reabren. **Las suites que usan fixtures/base permanecen detenidas** durante la intervención. El usuario maneja la credencial sintética solo en terminal/app, sin chat, evidencia ni argumentos. Pendientes login personal, disponibilidad, consentimiento, GPS real en primer plano, servicio, cierre/logout y retiro selectivo. No se marcan por el mero arranque del arnés.

## Condiciones externas conservadas

El usuario confirmó pendientes el medio de respaldo separado, host independiente de monitoreo y personas/cobertura/escalamiento24/7. También siguen las condiciones reales de privacidad, infraestructura, preparación/títulos, aceptación ADMIN legítima y custodia/ensayo del titular. No se reducen mediante pruebas sintéticas. Carga25/p95, batería, flujos operativos con lector, zoom real, entrega firmada, retorno completo y revisión final siguen en la [matriz](../matriz-L08.md).

[Preservación](preservacion.json):144archivos congeladosL07,36del incremento1 y12protegidos correctos, incluidos final3/términos/cierres. Las fuentes nuevas se trazan por separado en `fuentes.sha256`; los manifiestos anteriores no se reescriben. Sin Git/GitHub, correo externo, despliegue o activación del piloto.


## Corte físico posterior ? Pausa por batería

2026-10-22. Caso `a999ef52-d87f-498d-9251-1581cbb72562`: login y declaración de disponibilidad registrados por HTTP200; el usuario confirma la navegación. Intervención física detenida por batería de 1% declarada, sin medición v?lida de consumo. La comprobación posterior de solo lectura confirma ledger **1/3/7**, dos aceptaciones, cuenta/conductor activos, sin consentimiento de ubicación, marcador conservado y lock de proceso libre. El puerto4311 ya no escucha; no se atribuye una salida normal ni ejecución de `pausar` sin confirmaci?n. **TEST_CLEANUP_RELEASED=false**: las suites que usan la base siguen suspendidas. Sin oferta/servicio/GPS, limpieza, correo o nuevas altas. Retomar ?nicamente el mismo RUN con `-Serve -ResumeRunId`, después de cargar el teléfono y comprobar conexión; no repetir `-Serve` sin ese parámetro. L-08 EN_CURSO; piloto bloqueado.

[Archivo saneado del corte](../conectado-a999ef52-d87f-498d-9251-1581cbb72562/pausa-bateria-archivo-v2.json). Las referencias anteriores a login pendiente o lock activo describen el corte anterior; no son el estado de este corte.


## L08-CON-01: reanudación comprobada

Mismo RUN `a999ef52-d87f-498d-9251-1581cbb72562`, resumeCount1; marcador/lock activos, ledger1/3/7 y dos aceptaciones conservados. USB4311 restablecido; permiso de ubicación sin modificar. Login y disponibilidad previos acreditados. Oferta/servicio pendientes; sin GPS ni benchmark de batería. TEST_CLEANUP_RELEASED=false; suites de base suspendidas. L-08 EN_CURSO; piloto bloqueado.
