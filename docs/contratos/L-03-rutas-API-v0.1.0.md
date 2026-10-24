# L-03 — Rutas versionadas, API 0.1.0

Catálogo implementado y probado: **7 pruebas HTTP/PostgreSQL y 6 CRM**, V30 aplicada solo en `ruta_fija_test`, ledger **1/3/1** y dos aceptaciones conservados. El incremento inicial se amplió mediante V32 y el [contrato de integración](L-03-integracion-ruta-disponibilidad-v0.1.0.md): nueva asignación captura versión, cambio expreso exige nueva aceptación y Android consulta el recorrido propio. [Cierre local y límites](../evidencia/L-03/matriz-final-funcional-local-2026-10-19.md); piloto bloqueado.

## Catálogo

ADMIN del tenant, gates de identidad/términos y GET no-store:

- GET `/api/v1/routes?limit=100&offset=0`: resúmenes propios, incluidos retirados. Límite1…100, offset≥0.
- GET `/api/v1/routes/{id}`: cabecera, versión de control, `currentVersionId` y todas las revisiones inmutables.
- POST `/api/v1/routes`: `{name,operationKey,reason,stops}`; crea cabecera/revisión1 atómicamente, HTTP200. Repetición exacta de clave/actor/cuerpo devuelve la misma ruta; otro cuerpo409.
- POST `/api/v1/routes/{id}/versions`: anterior más `expectedVersion`; crea revisión nueva y conserva íntegra la anterior. Ruta retirada exige reactivación expresa. Versiones obsoletas/colisión de nombre409.
- POST `/api/v1/routes/{id}/status`: `{expectedVersion,operationKey,reason,active}`. Retirada para usos nuevos sin borrar revisiones ni reinterpretar servicios anteriores.
- GET `/api/v1/routes/operations/{key}`: resultado persistido de la operación del mismo actor/tenant, para consultar después de una respuesta incierta. No es credencial; otra persona no obtiene la operación por conocer su clave.404 no se convierte en éxito.

Parada: `{key,name,kind:START|INTERMEDIATE|END,latitude?,longitude?}`. Entre3 y100 paradas; primera START, última END y todas las demás INTERMEDIATE, claves únicas y nombres de lugares. Coordenadas opcionales, ambas o ninguna, límites geográficos válidos. No se deduce un terminal configurado cuando no hay punto ni se presenta esta configuración como prueba GPS/dispositivo.

Cada revisión guarda ID, número, nombre/paradas, SHA-256 determinista del contenido, actor y hora del servidor. El orden de propiedades para la huella es explícito. Trigger impide UPDATE/DELETE de la revisión. Cabecera/versión circular se comprueba con FK diferida al finalizar la transacción; no se usan repair ni desactivación de constraints.

## Referencia asignada

GET ADMIN `/api/v1/assignments/{id}/route` y CONDUCTOR móvil propio `/api/v1/mobile/assignments/{id}/route`: `{assignmentId,versionId?,referenceStatus,route?}`. No eligen orgId ni permiten editar al conductor. Una asignación antigua sin versión se muestra **LEGACY_UNRECORDED**, sin fabricar paradas pasadas. La columna nueva es nullable para esa historia. La integración verificada de nuevas asignaciones/versiones se describe en el contrato complementario y su evidencia V32.

## Cliente y seguridad

CRM `/routes`, solo ADMIN, navegación visible. Formulario de lugares con inicio/intermedias/fin, ordenación y coordenadas opcionales; guarda versión nueva, muestra anteriores, permite retirar/reactivar con motivo. Sin mapas ni captura de ubicación personal.

Ninguna mutación se reenvía automáticamente. Tras respuesta de transporte incierta se bloquea otra escritura y se consulta la operación. Rechazo de validación definitivo permite corrección manual; no muestra éxito. Una respuesta vieja de detalle no sobrescribe un borrador nuevo. Rol indebido403, objeto ajeno404, sesión inválida401; las pruebas distinguen el límite por tenant del límite de rol y usan recursos de prueba sin nuevas cuentas.

Las siete pruebas de catálogo por sí solas no acreditan una asignación completa. La evidencia complementaria V32 verifica captura, sustitución, nueva aceptación y declaración de terminal en backend/PG, con presentación móvil comprobada por separado. No acredita dispositivo físico ni recorrido GPS completo. L-05/L-08 y el piloto permanecen fuera de esta ejecución.
