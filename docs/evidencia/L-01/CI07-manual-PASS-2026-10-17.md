# L-01 — Recuperación manual local contrastada

Fecha local **2026-10-17, America/Lima**. **L-01 EN_CURSO**, piloto bloqueado y L-02 sin iniciar. Este corte acredita el ensayo técnico de CI-L01-07 como evidencia de SC-07; la aprobación conjunta SC-03/SC-07 sigue sin aplicar porque falta el gate previo de CI-L01-03.

## Resultado y correspondencia

| Referencia | Valor contrastado |
| --- | --- |
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| Segmento | `e140a035-8211-4037-82e6-388329f1cfc8` |
| Cuenta SUPER_ADMIN sintética | `088518c1-821c-4316-9f87-090f51ac44e0`, conservada |
| Resultado | **PASS_LOCAL_TEMPORARY_COPY_ONLY**, archivado y contrastado |
| Archivo humano | [observacion-1792287275662.json](ensayos-humanos-140c10a1-be0f-4d08-a6d4-c796e2ed107a/observacion-1792287275662.json) |
| SHA-256 | **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**, igual al comunicado y al lateral |
| Observación del arnés UTC | `2026-10-18T01:34:35.651523300Z` |
| Entorno nativo | `ruta_fija_test` / `rf_test` / V26; solo lectura efectiva y rollback |
| Contraste nativo UTC | `2026-10-18T01:40:55.056433400Z` |
| Evidencia nueva | [Consolidado](CI07-manual-contrastado-2026-10-17/contraste-consolidado.json), [SELECT y resultados](CI07-manual-contrastado-2026-10-17/contraste-nativo-solo-lectura.json), [lector reproducible](CI07-manual-contrastado-2026-10-17/ManualL01RecoverySuccessReadOnly.java) |

El archivo humano satisface el predicado vigente del arnés `recoveryEvidenceComplete`, sin cambiarlo. La comprobación auxiliar solo consulta el caso autorizado y no inicia Spring, Flyway, HTTP, SMTP, recuperación, limpieza o generación de claves. El agente no introduce ni recupera secretos.

## Controles acreditados y procedencia

- **Firma/reto y correo nuevo:** reto `fa9aa2ae-8e4b-4eab-bbb5-55d86f016036`, instalación prevista, prueba validada y consumo antes de vencer. Cero fallos de prueba. OTP consumido; `otp_hash` y `verification_token_hash` quedan nulos, comprobados mediante booleanos SQL.
- **Consumo preciso:** PostgreSQL **2026-10-18T01:30:47.240995Z**. El archivo publica **01:30:47.240Z** por su serialización a milisegundos; diferencia **995 microsegundos**. Se comparan ID y tiempos a la precisión publicada y se conserva el valor nativo completo. No se reescribe el archivo original.
- **Cuenta y contraseña:** misma cuenta activa/verificada, correo nuevo esperado, rol SUPER_ADMIN y organización nula. Versión de sesión **0→1**. Login recuperado observado por el arnés y auditoría posterior presente; el operador realizó el cambio de contraseña y el login, sin revelar valores.
- **Código de emergencia:** comparación del sustituto anterior con el hash vigente da rechazo y comparación del nuevo código da coincidencia, según los resultados ya archivados del observador en memoria. Una respuesta de finalización observada y confirmación humana del recorrido. El agente no consulta el hash ni repite el código; la entrega original durante la primera inscripción continúa **NO acreditada**.
- **Sesiones:** el archivo observa rechazo HTTP de **3 access +3 refresh**. La auditoría de finalización `5c2ffbde-3572-44a2-b4f0-9d23e380c8bb` registra **5 refresh revocados** por la operación. Los **14 refresh creados antes de recuperar** figuran revocados al consultar; no se atribuye a esta operación la revocación de los que ya lo estaban.
- **Pública:** se conserva durante la recuperación normal la nueva SHA **5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433**. La inscripción HTTP original permanece1 y ligada a5b727e9a…; reparación de código REC02=1 y sustitución pública REC03=1, auditoría `58b926a7-bf2d-4ff1-a978-feea67d1e9c4`, mantienen sus referencias. No se afirma que la pública antigua permaneciera a través del mantenimiento aprobado.
- **Historial:** ledger **1 SUPER_ADMIN /2 ADMIN /0 CONDUCTOR**, cupos **0/2/20**, identidad y organización conservados. Aceptación ADMIN `c496b63c-735f-48cc-be56-90f7ec1464e1` intacta y documento publicado ADMIN1.0.0 SHA20FFD952… sin cambios.

### Hallazgo: hubo un reenvío simulado

Se observaron **dos emisiones**:01:29:05Z y01:29:26Z, con dos marcas de envío conservadas y tercer espacio vacío. Por tanto, hubo **un reenvío simulado**; no se registra cero. Difiere de la instrucción operativa de pulsar Enter sin S. El cliente vigente solicita el reenvío solo al confirmar S; no se atribuye sin evidencia el gesto concreto del operador.

El contrato vigente permite reenvíos bajo **3 envíos/60 minutos**, conservando sus contadores; este caso permanece dentro del límite. No es una entrega externa ni una repetición de CI01 y no invalida el requisito de recuperación vigente. Se conserva el hallazgo; no se repite el ensayo para producir otro conteo. [Diagnósticos del lector](CI07-manual-contrastado-2026-10-17/notas-del-lector.json) documentan la corrección de dos supuestos auxiliares —precisión temporal y exactamente un envío— sin cambios en el producto, el protocolo o el archivo humano.

## Revisión de los doce criterios: evidencia nueva y reutilizada

Se conserva la [revisión integral anterior](revision-integral-previa-SC-2026-10-17.md) y el [mapeo CI/AC](matriz-cierre-L01-revision-2026-10-14.md). Los seis logs consolidados anteriores mantienen sus SHA; no se reejecutan suites mientras siga la protección. Los resultados siguientes respetan sus límites originales.

| CI | Estado y evidencia | Entorno y límite |
| --- | --- | --- |
| CI01 | **VERIFICADO**; [OTP real y consumo](cierre-parcial-CI01-y-terminos-2026-10-17.md) conservados | Test y entrega autorizada anterior. No se repite SMTP. |
| CI02 | Cobertura anterior de límites/seguridad OTP reutilizada; [V26 y negativas](incremento-Chiclayo-2026-10-15.md). Caso actual2/3, OTP/capacidad consumidos | PG test, correo doble. No se simula que se volvió a ejecutar toda la frontera. |
| CI03 | **PARCIAL**: publicación/aceptación manual sintética persistida; **falta gate backend anterior a aceptar** | Misma aceptación, cuenta y cupos. El archivo actual sigue `manualSyntheticAdminResult=PENDING`; no se reconstruye el pasado. |
| CI04 | Alta/activación/reemplazo/ID-01/cupos acreditados en [evidencia anterior](incremento-Chiclayo-2026-10-15.md), más alta manual sintética preservada | Test, identidades sintéticas. No participantes reales. |
| CI05 | Aislamiento/autorización y concurrencia anteriores reutilizados | Dos organizaciones sintéticas; cobertura de operaciones L-01, no de futuros módulos. |
| CI06 | Backend/CLI y negativas anteriores, más recuperación normal de este caso contra backend real de test | Ed25519 vigente. Preparaciones excepcionales documentadas por separado. |
| CI07 | **ENSAYO TÉCNICO LOCAL VERIFICADO**, resultado y archivo de este corte | Clave/cuenta temporales y copia misma PC. Evidencia requerida por SC-07 disponible; custodia real original pendiente. |
| CI08 | Recuperación asistida CONDUCTOR conectada anterior reutilizada | Backend/CRM/Flutter test y correo doble; no entrega externa nueva. |
| CI09 | Invitaciones, revisión documental y provisión anteriores reutilizadas | Contrato Chiclayo y PG test; Q-003/PB-01.5 permanecen en L-01. |
| CI10 | Revocación/auditoría anteriores, ampliadas con las sesiones y auditoría de este ensayo | Rechazo HTTP archivado más estados persistidos SELECT; no repetición de sesiones por el agente. |
| CI11 | Wrapper conectado, CRM121 y Flutter74/analyze anteriores conservan huellas; exclusión offline anterior acreditada | Test, sin nueva suite o dispositivo físico. Dispositivo físico sigue en L-08. |
| CI12 | V26 consultado, trazabilidad y huellas final3 verificadas otra vez sin regenerar | Solo test/archivos locales; sin desarrollo/piloto, despliegue o restauración real. Liberación del ensayo aún pendiente. |

Huellas final3: ZIP **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**, manifiesto **E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD**. También coinciden términos20FFD952…, JAR91913FF7… y aceptación histórica9319F045…; las doce comprobaciones de archivos están en el consolidado.

## Lista única restante

1. **CI03 — Gate previo:** no existe evidencia suficiente de una operación protegida rechazada por términos pendientes antes de aceptar y permitida después. La aceptación actual se conserva. Otro ADMIN continúa **no autorizado**; no crear otra cuenta, borrar aceptación o cambiar estados para simular esa comprobación. Para continuar se necesita una vía expresamente autorizada que satisfaga el criterio o una modificación explícita del alcance; ninguna se aplica aquí.
2. **Adenda y salida, dependientes de1:** una vez acreditado también `PASS_MANUAL_SYNTHETIC_ONLY`, aplicar SC-03/SC-07 ya aprobados, archivar ambos resultados y ejecutar solo la salida prevista del arnés. Verificar marcador/lock/liberación y ledger, sin forzar el desbloqueo. Después concordar revisión final y acta si todos los criterios siguen acreditados.

**TEST_CLEANUP_RELEASED=false**. Marcador y lock siguen activos; mantener A abierta y no ejecutar `salir`, limpiezas o suites que limpien. CI07 no requiere otra recuperación. La aceptación propia de ADMIN legítimo y la custodia/preparación/ensayo del titular real, además de privacidad/infraestructura, permanecen requisitos de habilitación del piloto. Este corte no aplica la adenda, no emite acta ni inicia L-02.
