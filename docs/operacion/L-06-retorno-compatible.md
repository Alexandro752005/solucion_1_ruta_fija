# L-06 — Retorno compatible comprobado

2026-10-21. Caso sintético L06-CON-01 `51e69ee6-e4a0-4c3f-9a35-f19de0072422`, únicamente ruta_fija_test/rf_test, V44. [Resultado](../evidencia/L-06/cierre/recorrido-conectado-contrastado.json), [arnés vigente](../../backend/src/test/java/pe/rutafija/support/L06ConnectedTrial.java).

1. El arnés arrancó un segundo contexto real del backend con las mismas clases compiladas y esquema, `app.qualification.operations-enabled=false`, puerto loopback temporal y correo simulado que rechaza cualquier intento. Tareas programadas/Flyway deshabilitados para este contexto de observación.
2. GET de calificación propia devolvió200, `operationalEnabled=false` y únicamente el bloqueo `OPERATIONAL_FEATURE_DISABLED`. Consulta del incidente propio y bandeja200, con autenticación normal.
3. PUT de disponibilidad devolvió409 `RESOURCE_CONFLICT` con el mensaje vigente de necesidad de habilitación operativa. Perfil antes/después conservó idéntica disponibilidad. La expectativa inicial403 del arnés era incorrecta y se conserva como diagnóstico; no se modificó el contrato del backend para ajustarse al ensayo.
4. El contexto temporal se cerró y el caso se retiró selectivamente en el contexto test normal. No hubo binario antiguo, restauración de base, SQL inverso, estado forzado ni despliegue.

Resultado: `PASS_SAME_BINARY_DISABLED_OPERATIONS_OWN_CONSULTATION_PRESERVED`. La comprobación no acredita restauración total/RTO/RPO ni un cambio de versión en producción; esas validaciones siguen en sus contratos. No ejecutar otra vez el caso ya cerrado: su contador/historial se conserva y el arnés rechaza reabrirlo.
