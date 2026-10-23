# Acta L-02 — Cierre funcional local con piloto bloqueado

**Estado: VERIFICADA. Tipo de cierre: FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Fecha: **2026-10-19, America/Lima**. A-COORD registra la conclusión del alcance local autorizado por el titular mediante `OK VERDE` y su instrucción de completar L-02. CARD-01 permanece confirmada: **una sola unidad vigente por CONDUCTOR** y exclusividad inversa. No se solicita otra aprobación equivalente para este cierre.

## 1. Alcance cerrado

[Contrato 0.2.0](../contratos/L-02.md), [contrato inicial conservado](../contratos/antecedentes/L-02-v0.1.0.md) y [API](../contratos/L-02-API-v0.2.0.md). Se completan L02.0…L02.4: estado posterior a provisión, separación aprobación/habilitación, vigencias/renovación, suspensión y efectos diferenciados, corrección material sin sustituir identidad, revisión separada empresa/unidad, solicitudes y vínculos temporales, continuidad CRM/Android, autorización/aislamiento, idempotencia, concurrencia y retorno seguro.

L-01 se reutiliza como antecedente acreditado; no se repitieron OTP real, aceptación humana ni recuperación. No se trasladó un criterio obligatorio local ni se modificaron SC-03/SC-07. Este cierre no habilita el piloto ni adelanta L-03.

## 2. Matriz final y resultados

La [matriz final de los diez criterios](../evidencia/L-02/matriz-final-funcional-local-2026-10-19.md) forma parte de esta acta: cada CI identifica comportamiento, evidencia, entorno, resultado y límites. **CI-L02-01…10 VERIFICADOS en el alcance funcional local; no queda criterio obligatorio local pendiente.** El conteo de pruebas complementa las evidencias de comportamiento y persistencia.

| Comprobación ejecutada | Resultado y evidencia | Límite |
|---|---|---|
| Backend HTTP, JWT, gates, transiciones y PG | [44 casos](../evidencia/L-02/aceptacion-final-V28/TEST-pe.rutafija.qualification.QualificationIntegrationTest.xml), cero fallos/errores/omitidos. | Spring/MockMvc y PostgreSQL reales con rollback; fixtures sintéticos, no participantes reales. |
| Concurrencia persistente | [2 carreras](../evidencia/L-02/aceptacion-final-V28/TEST-pe.rutafija.support.L02TemporalConcurrencyTest.xml), ganador commit y perdedor `23P01`. | Drivers técnicos sin AppUser; no nuevas altas. |
| Regresión exacta de OTP/invitación y guardia de limpieza | 3 OTP + 2 hash + 1 límite invitación + 1 protección históricos: **7**, todos correctos. [Archivo final](../evidencia/L-02/aceptacion-final-V28/README.md). Total backend dirigido: **53**. | Dobles donde corresponde; sin otro SMTP ni limpieza global. |
| CRM | [20 pruebas](../evidencia/L-02/aceptacion-final-V28/frontend__.angular__l02-v28-tests.log), typecheck y build correctos. | Pruebas de componentes con dobles, separadas del navegador conectado. |
| Flutter | [86 PASS, 2 omitidas optativas](../evidencia/L-02/aceptacion-final-V28/mobile__.dart_tool__l02-v28-tests.log); analyze sin incidencias y APK debug correcto. | Suite configurada; no incluye por equivalencia pruebas históricas offline excluidas ni dispositivo físico. |
| Clientes conectados | [Caso único completo](../evidencia/L-02/conectado-44b3d67a-69ce-4169-89b4-62ebe8a40160/resultado.json): `PASS_REAL_CRM_BROWSER_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL`. | Navegador CRM real y repositorios Flutter de producción con HTTP/PG reales; correo y almacenamiento de sesión de prueba simulados. Automatizado, no ensayo humano nuevo. |
| Migración y postflight | [V27](../evidencia/L-02/incremento-1/backend__target__l02-v27-explicit-migration.json), [V28](../evidencia/L-02/aceptacion-final-V28/backend__target__l02-migration-v28.json), [validación posterior a la suite](../evidencia/L-02/aceptacion-final-V28/post-suite-solo-lectura.json). | Solo `ruta_fija_test`; no base vacía, desarrollo, piloto o down migration. |
| Empaquetado | Maven offline `-DskipTests package` exit0, [manifiesto de artefactos](../../artifacts/L-02-2026-10-19/manifest.json). | Artefactos locales sin despliegue; JAR sin archivos privados ni arnés de ensayo. |

Las últimas correcciones posteriores a la suite nativa fueron exclusivamente cinco rótulos de error con acentos; el empaquetado compiló esas fuentes sin repetir ensayos humanos ni la suite completa. La evidencia congelada distingue cada ejecución previa y sus errores, sin sustituirlos por resultados posteriores.

## 3. Caso conectado, contadores y limpieza

RUN_ID **44b3d67a-69ce-4169-89b4-62ebe8a40160**. Se mantuvieron el ADMIN sintético **92ebc07c-1319-4850-a734-f792cb299979**, su aceptación y la organización **1a759298-7831-4aaf-9299-c3920a9cc28a**. Se utilizó una familia refresh de prueba separada; no se restableció su contraseña ni se aceptaron términos otra vez.

Invitación, OTP simulado, expediente personal y provisión del CONDUCTOR se ejecutaron por los mecanismos HTTP normales. Cuenta nueva **3fb1f499-81d2-45dd-8e60-b53f6eb7ff79**, Driver **5712d0db-e92b-4988-9809-a49d04a194ef**, solicitud **75e0c833-c0ad-4fa8-bc36-f27221ac30c7**. Flutter acreditó consulta limitada y solicitud idempotente; el navegador completó revisión empresa/vehículo, decisión, período y activación expresa, comprobando retiro de PII de la vista ordinaria.

Se recuperaron los intentos CRM fallidos en el mismo caso. El cliente de ensayo omitía reintroducir el motivo que la UI borra después de una mutación; se corrigió el cliente, sin relajar el requisito, repetir altas ni fabricar éxito. Dos restos técnicos del primer fallo de limpieza de concurrencia se recuperaron exclusivamente por sus UUID y forma comprobada; sus informes históricos permanecen en la carpeta de evidencia.

Antes de liberar el ensayo se archivaron resultados. Salida prevista: **PASS_DISABLED_AND_TOKENS_REVOKED_LEDGER_PRESERVED**. Revocación del período mediante API, cuenta/Driver deshabilitados y sesiones temporales revocadas; solicitud, aprobación, revisiones, período revocado y ledger conservados. Archivo privado y marcador activo retirados; servidor/proxy del ensayo cerrados. [Verificación independiente V28](../evidencia/L-02/conectado-44b3d67a-69ce-4169-89b4-62ebe8a40160/verificacion-cierre-V28.json).

- **TEST_CLEANUP_RELEASED=true**, lock disponible. No autoriza limpieza global: la guardia de históricos sigue activa. Pruebas futuras usarán rollback o limpieza selectiva comprobada.
- Histórico **1 SUPER_ADMIN / 3 ADMIN / 0 CONDUCTOR → 1 / 3 / 1**. Cupos restantes **0 / 1 / 19**. No se reiniciaron ni recuperaron cupos por deshabilitación.
- **2 aceptaciones** previas conservadas, con su huella de conjunto; fixture humano y clave pública CI-L01-07 sin cambios. El postflight posterior a todas las pruebas confirma V28 y estas mismas cifras.
- **0 intentos SMTP externos** en L-02; no claves definitivas, cuentas reales, Git/GitHub ni modificaciones de final3.

## 4. Migraciones y retorno

Runtime **rf_test**, migrador **rf_migrator**, destino exclusivamente **ruta_fija_test**. V27 incorpora revisión/elegibilidad/periodos/historial y restricciones temporales. V28 incorpora incidentes automáticos `SYSTEM` con reportante nulo solo para ese origen; no crea un rol ni atribuye al creador una detección automática. Orígenes humanos conservan su reportante obligatorio.

Se validó V28 tras la suite y la preservación antes/después; no se repararon checksums ni alteraron migraciones aplicadas. [Procedimiento de retorno](../operacion/L-02-validacion-y-retorno.md): mismo binario actualizado y `app.qualification.operations-enabled=false`, conservando V27/V28, consulta e historial. El interruptor está probado; no se ejecutó un retorno de producción, restauración o down migration. No volver a un binario incompatible con `SYSTEM`.

## 5. Integridad y archivo

[Siete inmutables preservados](../evidencia/L-02/verificacion-final/inmutables-preservados.json): snapshot y manifiesto final3, términos ADMIN 1.0.0, acta L-01, aceptación humana, recuperación humana local y CLI anterior. Términos: **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**. Snapshot final3: **3806510606A1565F23B8C526A13CC1049BC79141BABDB8E1316F042488B3D11B**.

Los documentos mutables de planificación evolucionan legítimamente; no se reescribe el manifiesto histórico de L-01. [Respaldo público previo al cierre](../../memory/backup/2026-10-19-pre-cierre-L02) y [respaldo al inicio](../../memory/backup/2026-10-18-inicio-L02) preservan sus cortes. [Índice final y comandos](../evidencia/L-02/aceptacion-final-V28/README.md) y [manifiesto de cierre](../evidencia/L-02/manifest-cierre.sha256) permiten contrastar documentos, fuentes y evidencia sin copiar configuración privada.

Contrato, matriz, ejecución, backlog, tablero y memoria expresan el mismo cierre. Los cortes antiguos EN_CURSO se conservan como historia, no estado actual. No se atribuye a una revisión independiente nueva lo que solo acredita la ejecución/revisión de A-COORD.

## 6. Piloto real todavía bloqueado

Se conservan, sin marcarlos satisfechos, los requisitos originales del cierre L-01:

1. ADMIN legítimo preparado que acepte personalmente el documento publicado. Las aceptaciones sintéticas no representan participantes reales.
2. Titular real: preparación e inscripción definitiva autorizadas, clave privada cifrada fuera del servidor/repositorio, respaldo cifrado independiente y comprobado, código separado y ensayo del titular. Solo está confirmada una PC Windows; la copia local temporal no acredita independencia.
3. Identificación/privacidad separadas aprobadas y condiciones de protección, retiro a los plazos acordados y excepciones antes de introducir datos reales. No se declara ejecutada una purga por documentarla.
4. Infraestructura/base piloto/HTTPS, copias cifradas y restauración comprobada; siguen sin acreditarse. No se habilitaron con este trabajo.
5. Preparación real de participantes, organizaciones, recursos y títulos aplicables. Se mantienen cupos históricos 1/4/20; cinco referencias empresariales con al menos una sin operación, sin cuentas compartidas. Los títulos reales requieren revisión, no inferencia.
6. Inicio efectivo y fin/cierre siete días después según la decisión vigente; no se retrofechan. La prueba física Android pertenece a L-08.

**Resultado final: L-02 VERIFICADA, FUNCIONAL_LOCAL_PILOTO_BLOQUEADO. S/0 y sin offline. L-03 continúa NO INICIADA y NO AUTORIZADA; requiere su propio OK VERDE.**
