# L-02 — Validación local y retorno seguro

Fecha: 2026-10-19, America/Lima. Solo `ruta_fija_test`; no autoriza desarrollo, piloto, despliegue ni L-03.

## Entorno y evidencia

- Runtime PostgreSQL `rf_test`, sin CREATE en public. Migrador `rf_migrator`, destino local de pruebas comprobado antes de V27/V28. Configuración privada existente; no copiar su contenido ni introducir secretos en argumentos. Estado final **V28**, validado después de todas las pruebas dirigidas.
- V26 → V27: una migración aditiva, sin transformar enlaces heredados en autorizaciones documentales. [Evidencia](../evidencia/L-02/incremento-1/backend__target__l02-v27-explicit-migration.json).
- V27 → V28: origen de incidencia `SYSTEM` con reportante nulo exclusivamente para ese origen; los orígenes humanos conservan la obligación de reportante. [Migración](../evidencia/L-02/aceptacion-final-V28/backend__target__l02-migration-v28.json) y [validación final](../evidencia/L-02/aceptacion-final-V28/post-suite-solo-lectura.json). `SYSTEM` no es un rol ni atribuye una detección automática al creador del conductor. La suspensión expresa sí identifica al ADMIN actuante.
- Las pruebas `QualificationIntegrationTest` heredan `L02PostgresIntegrationTest`: transacción con rollback, gates de identidad y términos activos, JWT firmados, correo doble y ningún TRUNCATE. No usar para este lote `NativePostgresIntegrationTest`, que limpia globalmente.
- Las dos pruebas de concurrencia usan conexiones independientes y conductores técnicos sin AppUser. Comprueban la exclusión PostgreSQL, no una aprobación personal. Retiran únicamente sus recursos identificados; no consumen cupos.
- Dos recursos técnicos quedaron tras el primer error del cleanup del test. Se inspeccionaron y recuperaron por UUID, con comprobación de forma y contadores. Los informes `recuperacion-fixture-tecnico-*.json` conservan el antecedente; no se borraron datos ajenos.

## Caso conectado cerrado

RUN_ID `44b3d67a-69ce-4169-89b4-62ebe8a40160`: CRM Angular en navegador real y repositorios Flutter sobre HTTP real, backend Spring y PostgreSQL. Solo correo y almacenamiento de sesión de prueba son dobles. No acredita Android físico ni revisión de títulos reales.

Se reutilizó el ADMIN sintético ya aceptado de L-01 mediante una familia refresh de prueba separada; no se cambió su contraseña ni se aceptaron términos otra vez. Invitación, OTP simulado, revisión personal y provisión del nuevo CONDUCTOR pasaron por los endpoints normales. El cupo histórico CONDUCTOR pasó de 0 a 1 y permanece consumido; SUPER_ADMIN/ADMIN permanecen 1/3.

El cierre selectivo revocó el período mediante API, deshabilitó la cuenta y Driver sintéticos y revocó sus refresh y la familia web creada por el ensayo. Conservó documentos estructurados, solicitud, revisiones, aprobación, período revocado y ledger. La contraseña sintética y las credenciales temporales del arnés se retiraron de su archivo privado. No se regeneró material del titular.

`TEST_CLEANUP_RELEASED=true` acredita marcador activo retirado y lock disponible. La barrera contra **limpieza global de casos históricos** sigue vigente en `NativePostgresTestDatabase`: cerrar el ensayo no autoriza truncar casos ni recuperar cupos. Las siguientes pruebas deben usar rollback o limpieza selectiva comprobada.

El arnés `L02ConnectedTrial --run-connected` es opt-in, de fuentes de prueba. Un caso cerrado se rechaza; no debe relanzarse para repetir la evidencia. Los intentos fallidos CRM se conservaron y se continuó el mismo caso. La causa era la omisión del motivo nuevo exigido por la UI tras una mutación; se corrigió el cliente de ensayo, manteniendo el control.

## Retorno de aplicación

1. Archivar resultado, logs saneados, manifiesto y estado público antes de intervenir.
2. Mantener V27/V28 y el binario actualizado. No volver a un binario antiguo que no comprenda el origen `SYSTEM`. No borrar filas Flyway, ejecutar repair, editar migraciones aplicadas, truncar tablas ni eliminar históricos como reversión.
3. Para detener la habilitación operativa local, iniciar el backend con la propiedad `app.qualification.operations-enabled=false` —por ejemplo mediante la variable de entorno de proceso `APP_QUALIFICATION_OPERATIONS_ENABLED=false`— y reiniciar solo la instancia autorizada de pruebas. No editar credenciales ni aplicar esta acción en desarrollo/piloto por inferencia.
4. El bloqueo conserva consulta y expediente; deniega activación, reserva, aceptación e inicio por la misma política. Un refresh/login relee el estado y emite alcance limitado. La prueba nativa del interruptor conserva aprobación y períodos; no acredita un despliegue/reinicio de producción.
5. Un servicio en curso conserva su ocupación hasta cierre/cancelación o relevo seguro expreso. La pérdida de vigencia no lo completa ni prorroga su vínculo automáticamente. Los futuros afectados conservan referencia pendiente de reasignación.
6. Revisar causa y evidencia antes de reactivar la propiedad. La reversión de esquema requiere otro procedimiento autorizado y comprobación de datos; no se ejecutó un down migration.

## Comprobación posterior al cierre del caso

La [verificación independiente V28](../evidencia/L-02/conectado-44b3d67a-69ce-4169-89b4-62ebe8a40160/verificacion-cierre-V28.json) comprueba cuenta/Driver deshabilitados, refresh revocados, período revocado e historial/aprobación conservados; marcador y archivo privado ausentes, lock libre, ledger **1/3/1** y huella pública CI-L01-07 sin cambios. El [postflight posterior a la suite](../evidencia/L-02/aceptacion-final-V28/post-suite-solo-lectura.json) confirma V28, Flyway correcto, dos aceptaciones y hashes L-01 intactos. No repetir el caso conectado cerrado ni ejecutar una limpieza global para comprobarlo.

## Límites previos al piloto

Continúan los bloqueos del acta L-01: ADMIN legítimo con aceptación propia, titular real con custodia independiente y ensayo, privacidad/identificación separadas, infraestructura/HTTPS/base piloto, copias cifradas/restauración y preparación de participantes. Los expedientes y metadatos nuevos siguen los plazos aprobados; antes de introducir PII real debe estar habilitado el procedimiento de retiro y excepciones del piloto. No se declara ejecutada una purga ni cumplimiento legal por esta validación sintética.

No correo externo, Git/GitHub, RENIEC/MTC/QR, offline, despliegue ni regeneración de final3. Dispositivo físico corresponde a L-08.
