# L02.0 — Contrato, estado de incorporación y cardinalidad heredada

Fecha local: **2026-10-18, America/Lima**. L-02 **EN_CURSO**. Este es el primer incremento de implementación, no un acta ni una aceptación funcional de todo el lote.

## Autorización y decisiones

El titular dio `OK VERDE` para iniciar L-02 después del cierre funcional local de L-01. Confirmó **una sola unidad vigente por CONDUCTOR**; [registro CARD-01](../../../specs/decisiones-L02-2026-10-18.json). Se conserva Q-029: la unidad tampoco se comparte simultáneamente entre conductores. No se amplían cupos ni se sustituyen personas/unidades automáticamente.

[Contrato0.1.0](../../contratos/L-02.md), [ejecución](../../ejecucion/L-02.md) y [matriz](matriz-L02.md) concretan las subfases y separan los tramos reutilizados de L-01 de lo que debe desarrollarse después. No se recrea el expediente ni se repiten las intervenciones de OTP/recuperación/aceptación.

## Implementación

### Backend

- Nuevo `GET /api/v1/drivers/{driverId}/incorporation-status`: ADMIN propio, objeto filtrado por tenant, gates habituales y no-store. Consulta transaccional de solo lectura/repeatable-read. Ningún orgId de cliente concede alcance.
- DTO con IDs y estados de aprobación personal, cuenta/provisión, identidad revisada, grupo, Driver, revisión/vigencia personal, cantidad de vínculos heredados y bloqueos. La consulta no contiene nombres, correo, DNI, licencia, documentos o HMAC. El resumen documental lee metadatos, no campos privados; desconocido o ambiguo requiere revisión.
- Cuenta activa no se convierte en Driver habilitado; estado DISPONIBLE tampoco implica permiso. `operationalEnabled:false` mantiene el bloqueo explícito de unidad/servicio del flujo de activación existente. No es una simulación de comprobación positiva ni un bypass del gate.
- Vinculación heredada rechaza otra unidad activa para el mismo conductor y otro conductor activo para la misma unidad. Escritores de enlace/desenlace/principal adquieren Driver→Vehicle con bloqueo pesimista antes de consultar/modificar. Repetir la pareja existente no crea una nueva. No se limpian vínculos previos ni se cambia su vigencia por inferencia.
- Sin migración en este incremento. Los bloqueos y consultas están implementados, **no se declaran comprobados con concurrencia PostgreSQL** por compilar. La defensa persistente y el modelo temporal completo pertenecen al incremento siguiente del contrato.

### CRM

- Detalle de conductor muestra «Incorporación y condiciones de operación» con estados separados y mensajes seguros. Maneja carga/error/reconsulta; una respuesta no disponible no se presenta como conforme.
- No se muestran datos personales ni se vuelcan códigos de error no controlados. Las respuestas de un detalle anterior se descartan mediante secuencia de consulta y comprobación de driverId al cerrar/cambiar la vista.
- La selección heredada no ofrece agregar otra unidad cuando ya existe un vínculo activo. El servidor mantiene su propia denegación; ocultar el botón no es la evidencia del control.
- Se adaptó únicamente el fixture HTTP del test de privacidad existente a la consulta adicional; no se añadieron casos nuevos ni se ejecutó la suite de pruebas en este incremento.

Android no se modificó en este corte. Sigue pendiente la continuidad posprovisión sin acceso operativo indebido, junto con revisión de unidad, suspensión y vigencias. No se presenta esta entrega como recorrido conectado completo.

## Comprobaciones ejecutadas y límites

| Comando | Resultado | Alcance |
|---|---|---|
| Backend: `mvnw.cmd --offline --batch-mode --no-transfer-progress compile` | BUILD SUCCESS, exit0; [log](incremento-0/backend-compile.log) | Compilación Java. No inicia Spring, PostgreSQL, Flyway o SMTP ni ejecuta tests. Advertencia existente de API deprecada en gate de identidad. |
| CRM: `npm.cmd run typecheck` | exit0; [log](incremento-0/crm-typecheck.log) | TypeScript de aplicación y specs. No ejecución de tests. |
| CRM: `npm.cmd run build:development` | exit0; [log](incremento-0/crm-build.log) | Compilación Angular, incluidas plantillas. No despliegue, navegador o validación visual interactiva. |
| Archivos del cierre L-01 | [Preflight](preflight-archivo-L01-2026-10-18.json):49/50 coinciden; backlog mutable distinto previamente | Backlog actual preservado/respaldo sin atribuir causa ni restaurar sobre ediciones. Acta/matriz/evidencias/final3/términos originales conservados. |

Las huellas de código, logs y documentación están en [resultado/manifiesto del incremento](incremento-0/resultado.json). No se atribuyen a esta implementación las pruebas anteriores de L-01. No se ejecutaron consultas a base de datos ni pruebas automáticas, SMTP, migraciones o limpiezas en este arranque. Los contadores1/3/0 son el último corte archivado de L-01, **no una lectura actual**.

## Pendientes concretos

1. Comprobar el incremento contra backend/PG test: roles, tenant ajeno, no PII, solicitud ausente/ambigua, catálogo, revisión y vencimientos; carreras de vinculación/desvinculación y reintento de misma pareja. Conservar datos/ledger; no ejecutar suites que limpien globalmente el caso por comodidad.
2. Implementar vigencias y suspensión separadas, corrección material con ID-01 e historial; no sobrescribir vínculos HMAC o sustituir personas.
3. Completar revisión/solicitud de unidad y vínculo temporal con historia/retorno, tipo propio/empresarial y días Lima conforme al contrato; no habilitar por solo `active`.
4. Resolver la dependencia circular heredada mediante el flujo explícito y conectar Android/CRM; completar la revisión independiente y la matriz antes de cerrar L-02.

Piloto real bloqueado por los requisitos operativos de L-01. Sin L-03, Git/GitHub, despliegue, correo externo, integración estatal, QR, offline, claves definitivas, cargas reales o regeneración de final3.
