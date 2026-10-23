# Matriz inicial de trazabilidad — L-00

**Corte:** 2026-10-13, America/Lima  
**Fuente normativa:** `specs/registro.json` y `specs/registro.md`, copia contrastada con el kit v3.1.

La matriz CSV deja una fila por cada regla o criterio registrado, conserva sus identificadores/estados fuente y lo vincula con módulos y lotes. Es un mapa de auditoría estática para ordenar el trabajo, no una declaración de que cada requisito ya esté implementado o aceptado.

## Conteo y tratamiento

| Tipo | Vigentes/activos | Sustituidos | Total |
|---|---:|---:|---:|
| Reglas | 109 | 9 | 118 |
| Criterios de aceptación | 162 | 9 | 171 |
| **Filas del CSV** | **271** | **18** | **289** |

Las filas sustituidas permanecen visibles para mantener la procedencia y apuntan a su reemplazo; no se convierten en trabajo activo. Las filas activas se distribuyen según `moduleRefs`, `lotRefs` y las tareas del tablero local, sin cambiar IDs ni contenido normativo del registro.

## Columnas del CSV

| Columna | Uso |
|---|---|
| `recordType`, `id`, `sourceStatus`, `supersededBy` | Tipo e identidad del requisito y estado conservado del registro fuente. |
| `moduleRefs`, `lotRefs` | Módulos y lotes que lo deben revisar; sirven para ordenar contratos y dependencias. |
| `currentCodePaths` | Carpetas/candidatos localizados en backend, CRM o Android para la inspección posterior. Una ruta presente no prueba que la regla esté implementada. |
| `requirementText`, `requiredEvidence` | Texto del requisito y evidencia necesaria para cerrarlo. |
| `implementationAssessment` | Hallazgo estático inicial del módulo; no sustituye un análisis de comportamiento por requisito. |
| `verificationRefs` | Decisiones o verificaciones del registro que condicionan su aceptación. |

El mapeo de rutas se hizo a nivel de módulo y directorio. En particular, mantenimiento (`MOD-MNT`) y rutas (`MOD-RTE`) requieren reconciliar sus superficies específicas de CRM/Android y el contrato backend al iniciar sus lotes. Por tanto, la matriz no debe leerse como una asignación exhaustiva de endpoint, DTO, entidad o pantalla.

## Cierre esperado por lote

Antes de cambiar una fila activa a implementada/verificada, el contrato del lote debe enlazar el requisito con el proceso/estado, endpoint y DTO aplicables, permiso/tenant, persistencia/migración, pantallas consumidoras y pruebas positivas/negativas. Las decisiones abiertas deben resolverse por el responsable indicado en el backlog, no mediante inferencias del código. Cada lote actualizará su disposición y evidencia local sin modificar el registro fuente.

El conteo se puede recalcular leyendo `docs/ejecucion/L-00-trazabilidad.csv` con `Import-Csv -Delimiter ';'`; debe mantenerse en 289 filas, 118 reglas y 171 criterios mientras la copia del registro fuente no cambie.
