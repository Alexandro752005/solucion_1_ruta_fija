# Contrato de tarea local

| Campo | Contenido obligatorio |
|---|---|
| ID / lote | Referencia exacta en `docs/seguimiento/tareas.json` |
| Base local | Versión del registro, fecha/hora de captura y huella SHA-256 del artefacto relevante |
| Resultado | Capacidad observable y actor del producto |
| Alcance | Incluido, excluido y bloqueantes concretos; siempre solo `SUPER_ADMIN`, `ADMIN`, `CONDUCTOR` |
| Reglas | IDs vigentes de decisiones, reglas y aceptación; no renumerar |
| Dueño / consumidores | Módulo del dato; backend, CRM, Android y base afectados |
| Permisos | Rol × organización/objeto × acción × estado; revocación y aislamiento |
| Datos / estados | Campos, relaciones, unicidad, vigencia, transiciones y retención |
| API | Operación, DTO, respuesta, error y recuperación; distinguir lo existente de lo propuesto |
| Concurrencia | Resultado del segundo actor, atomicidad y reintentos |
| CRM / Android | Vistas y estados carga, vacío, error, denegación, conflicto y conexión |
| Propiedad local | Perfil responsable, archivos exclusivos y escritor único para artefactos compartidos |
| Datos / migración | Flyway aplicable, compatibilidad, respaldo/retorno y datos que deben conservarse |
| Comprobaciones | Comandos autorizados, caso observable, resultado esperado y evidencia local |
| Cierre | `IMPLEMENTADA`, `REVISADA`, `VERIFICADA` o `BLOQUEADA`; pendientes y dependencia siguiente |

## Aceptación por proceso

Un ADMIN de su organización origina una oferta válida; el servidor reserva el intervalo y el conductor acepta antes del menor plazo entre diez minutos y el inicio. La concurrencia no puede producir reservas incompatibles. Rechazo/vencimiento libera el intervalo sin borrar el vínculo de unidad. Si se pierde la respuesta, el cliente consulta el estado registrado y no confirma una acción duplicada. Ningún cambio se confirma offline.

Los endpoints, estados y restricciones concretos se derivan del código y del registro v3.1; una propuesta no se documenta como API implementada.
