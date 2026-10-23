# REC-CI07-03 — Implementación de preparación sintética y evidencias

## Autorización y estado

El titular autorizó expresamente **REC-CI07-03 v0.1.0 con sus límites**, incluida la preparación excepcional de una nueva pública del mismo fixture, conservando la inscripción HTTP original como antecedente de la clave anterior. [Propuesta inmutable](../../operacion/L-01-REC-CI07-03-clave-temporal-propuesta.md), SHA `1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8`; [registro de autorización](../../operacion/L-01-REC-CI07-03-autorizacion-2026-10-17.json), SHA `434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6`.

**Implementada y compilada solo en fuentes test. No se generó, abrió o sustituyó material del operador y no se ejecutó la preparación contra el caso.** CI07 manual continúa PENDING; L-01 EN_CURSO.

## Cambios

- Nuevo comando `preparar-clave` en el mismo arnés. Autenticación y reautenticación mediante login normal, sesión vigente, pública X.509 Ed25519 y huella coincidente. Solo recibe material público; no recibe privada, contraseña de cifrado, ruta privada o código de emergencia.
- Confirmación expresa de la huella, comprobaciones personales de principal/copia y disponibilidad del sustituto vigente antes de escribir. Las comprobaciones locales declaradas por el operador se distinguen del resultado técnico de recuperación.
- Guardas exactas de base/rol/V26, caso/cuentas/instalación, marcador, lock propio, identidad, aceptación, ledger, pública original, una inscripción y reparación REC-CI07-02 existente. Rechaza reto vigente, prueba/completado incompatibles y preparación anterior.
- Transacción con los bloqueos de cuotas existentes y bloqueo de filas; vuelve a validar las guardas. Único UPDATE de `public_key_x509`/`updated_at`, condicionado a pública anterior; INSERT de auditoría específica. Ante fallo, rollback. Sin cambios de código de emergencia/hash, cuentas, instalación, inscripción original, retos, aceptación o cupos. No grants/DDL/migraciones.
- Referencia pública nueva fijada por la confirmación y auditoría persistida; reanudación solo si ambas corresponden al valor actual. No acepta cualquier pública tras reiniciar ni permite una segunda preparación.
- Evidencia distingue `originalEnrollmentResponseCount=1`, `syntheticFixtureCodeRepairCount=1` y futuro `syntheticFixtureKeyReplacementCount=1`. La nueva pública sería mantenimiento del fixture, no otra inscripción HTTP. Recuperación exige las condiciones normales más correspondencia real de auditoría, huella nueva como baseline y huella igual al completar.
- Conserva las declaraciones previas de recuperación como antecedente y exige confirmar de nuevo uso de la copia/recuperación; aceptación ADMIN y su gate pendiente intactos. Actualiza el modo de inspección existente, sin iniciar Spring ni modificar el caso.

El cliente/JAR, endpoints, DTO, algoritmo y clases productivas no cambian. La utilidad actual genera y comprueba las futuras claves únicamente cuando el operador ejecute las etapas indicadas.

## Pruebas dirigidas finales

[Log final](REC-CI07-03-2026-10-17/unit-final-54.log), SHA **F774BF52FB613DBF39D96DAEC08D1A0D6E7ECF2BE929AC4F84E8DFC7B780A9E1**:

| Conjunto | Resultado |
| --- | --- |
| ManualL01HumanTrialTest | 23/23 |
| ManualL01RecoveryFixtureKeyReplacementTest | 17/17 |
| ManualL01RecoveryFixtureRepairTest, regresión dirigida | 14/14 |
| Total de la última ejecución | **54/54, sin fallos/errores/skips** |

Dobles y claves ficticias efímeras: sin Spring, credenciales de configuración de BD, SMTP, privadas del operador ni cambios en su fixture. Cubren cada guarda ausente/falsa, autenticación y reautenticación, cancelación/confirmaciones incompletas, pública/huella inválidas, sesión rechazada, cambio de estado durante confirmación, repetición/concurrencia en store simulado, actualización condicionada, rollback de auditoría, referencias restauradas, no atribución de inscripción HTTP y persistencia separada de los controles humanos. La sustitución sola no completa CI07; falta cualquier control normal o auditado y el resultado sigue pendiente.

El primer intento tuvo2 fallos y1 error por una sobrecarga no implementada en el doble JDBC (`No DataSource set`); no había datasource real configurado. Se corrigió el doble y se conservó [ese log](REC-CI07-03-2026-10-17/unit-inicial-fallo-doble-jdbc.log). Los logs intermedios de53/54 pruebas también se conservan; **no se suman** al resultado final.

Concurrencia y rollback aquí se probaron con dobles. No se afirma una sustitución ni dos commits concurrentes sobre el caso PostgreSQL humano.

## PostgreSQL real, sin mutar el fixture

[Consulta y planes SQL](REC-CI07-03-2026-10-17/native-readonly.json), SHA **B92FC8A4B938C9D928597CC880C756A4947C3ABADCC650FC995248D9467B7231**, corte **2026-10-17T17:01:03.669095800Z**:

- `ruta_fija_test` / `rf_test` / V26; mismo RUN_ID140c10a1 y segmento previo82bffcf1.
- **17/17 guardas** consultadas, incluida la autorización, pública original, reparación REC02 exacta, ausencia de preparación anterior, estado de reto y protección.
- El inspector tomó únicamente como referencia el PID backend titular del lock ya existente39116. No adquirió/desbloqueó ese lock ni se hizo pasar por el proceso interactivo: las guardas se consultaron con esa referencia explícita para diagnóstico. El comando real exigirá de nuevo su **propia** sesión titular al ejecutarse.
- EXPLAIN de UPDATE e INSERT, **sin ANALYZE**; planes presentes. Transacción de solo lectura y rollback; cero DML/DDL/commit de aplicación, HTTP/SMTP, claves del caso generadas o códigos/hashes secretos seleccionados.
- Historial1/2/0, cupos0/2/20, dos identidades, una aceptación1.0.0, pública/credencial y marcador conservados; lock activo al corte. TEST_CLEANUP_RELEASED=false.

El plan SQL y las guardas no acreditan el commit ni el recorrido manual; se contrastarán después de cada intervención personal. No se ejecutaron pruebas que limpien la base. Las pruebas finales posteriores reforzaron los predicados de evidencia; los SQL y guardas consultados en este corte permanecieron iguales.

## Archivo y artefactos preservados

[Manifiesto de17 archivos](REC-CI07-03-2026-10-17/manifest.sha256), SHA **2B210DB1880CA1693C9A88FC6834F0DA9ABDF70E157BB2C715039D6E914934A3**: fuentes test, lector nativo, logs, autorización/propuesta y guía. [Huellas inmutables comprobadas](REC-CI07-03-2026-10-17/immutable-artifact-hashes.json):

- final3 ZIP: `3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B`.
- Manifiesto L-00: `E78D7002D5C85B24200BE21B8FD88CE770D36333CA49CAA5351248CFD0E28BAD`.
- Términos publicados: `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417`.
- JAR del cliente: `91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A`.
- Observación humana original: `9319F045F65FD4EE61B9A07EDEC3000E5EA6AC0E75618D99E2C4896843B8A719`.

final3 no se regeneró. Backups públicos de fuentes/documentos en `memory/backup/2026-10-17-pre-REC-CI07-03-implementacion`; el original del inspector se conserva desde su snapshot inmutable REC02 y su extensión intermedia se distingue en otro archivo. No contiene claves, passwords, códigos o configuración privada.

## Intervención pendiente y límites de cierre

[Guía REC-CI07-03 v1.0.0](../../operacion/L-01-REC-CI07-03-ejecucion.md). Etapa actual: el operador archiva con `verificar`, sale coordinadamente con marcador conservado y TEST_CLEANUP_RELEASED=false, reanuda el mismo RUN mediante `-Serve -ResumeRunId`, y confirma login cargado/comando nuevo. Esperar comprobación de marcador/lock antes de generar nuevas claves. No volver a solicitar la autorización REC03.

Después: generación personal en destino nuevo sin sobrescribir, dos checks locales, confirmación/registro de la pública y auditoría, protocolo normal con copia nueva y sustituto vigente, OTP simulado sin reenvío, contraseña nueva, rotación, login y archivo. Ninguna etapa se declara realizada anticipadamente.

CI03 sigue PENDING por gate previo; no otro ADMIN autorizado. SC03/SC07 continúan aprobados condicionalmente sin aplicar hasta acreditar ambos ensayos. CI07 PENDING; solo PC confirmada, ninguna custodia independiente/titular real inferidos. L-01 EN_CURSO, piloto bloqueado, L-02 sin iniciar. CI01 permanece VERIFICADO sin repetir correo. S/0, sin Git/GitHub, SMTP externo, despliegue, cuentas reales, material definitivo o migraciones de desarrollo/piloto.
