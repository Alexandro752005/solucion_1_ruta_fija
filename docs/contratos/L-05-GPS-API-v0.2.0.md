# API GPS L-05 — v0.2.0

Incremento local sobre [ejecución0.1.0](L-05-ejecucion-API-v0.1.0.md). No habilita piloto. Decisiones GPS-L05-01, GPS-RET-L05-03 y GPS-ACCESS-L05-04 aprobadas en [registro local](../../specs/decisiones-L05-2026-10-19.json). Los permisos y el estado del dispositivo son declaraciones del cliente, no atestación del hardware.

## Captura y resumen

- La app intenta una lectura cada30s solamente en primer plano, con sesión, consentimiento, permiso y ubicación habilitada, durante su tramo `EN_SERVICIO`. Se detiene al perder esas condiciones, salir, terminar o cambiar de sesión. No tiene cola ni reenvío automático de puntos fallidos.
- Antes de Ruta finalizada se intenta una lectura puntual; su ausencia no impide declarar un cierre seguro. La pantalla informa antigüedad y señala lecturas de más de120s. No imprime coordenadas en el resumen.
- El adaptador conserva el timestamp del sensor y rechaza precisión ausente, negativa o no finita. Una lectura con precisión medida insuficiente para corroborar no se transforma en prueba válida por cambiar su hora.
- El PUT existente de ubicación exige tramo propio abierto; registra un punto inmutable y deriva el snapshot actual. Un timestamp repetido con valores distintos produce conflicto, no sobrescritura ni nueva vigencia. Inicio/cierre retiran el snapshot; el historial expira **168h desde captura**, no desde recepción, revisión o incidencia.
- La corroboración de Ruta finalizada usa exclusivamente el punto vivo de su segmento y terminal de la versión capturada:200m/120s/100m. `DISCREPANCY` crea observación persistida y aviso `service.closure.observed`, sin coordenadas, atribución de fraude ni prueba del recorrido completo.

## Solicitud propia y revisión

Todas las rutas son autenticadas y devuelven `Cache-Control: no-store`. Los gates de identidad, términos y autorización server-side siguen vigentes. SUPER_ADMIN no obtiene permiso GPS global.

| Método/ruta relativa a `/api/v1` | Actor y contrato |
|---|---|
| POST `mobile/gps/access-requests` | CONDUCTOR propio. `requestKey`, `serviceId`, opcionales `fromAt`/`toAt`. Omisiones capturan inicio propio/hora servidor una sola vez; mismo key/payload devuelve solicitud original, cambio entra en conflicto. No requiere habilitación para aceptar nuevas ofertas, sí cuenta/incorporación aprobada. |
| GET `mobile/gps/access-requests?serviceId=…` | Solo solicitudes propias; limit1…100/offset0…100000. |
| GET `mobile/gps/access-requests/{id}` | Estado propio; identificadores y tiempos, sin puntos. |
| GET `gps/access-requests?serviceId=…` | ADMIN propio; mismo límite. Servicio opcional se valida contra su organización. No entrega coordenadas. |
| POST `gps/access-requests/{id}/review` | ADMIN propio: `expectedVersion`, `approved`, `identityConfirmed=true`, `verificationReference=RESTRICTED_IDENTITY_MATCH`, `currentPassword`. Reautenticación limitada; códigos sintéticos inequívocos en las pruebas. No es un proveedor externo de identidad. |
| POST `mobile/gps/access-requests/{id}/points` | Solo solicitante autenticado y reautenticado: `currentPassword`, limit1…1000, offset0…100000. No enlace público, correo, descarga ni archivo persistido. Otra cuenta no consume la autorización. |

Revisión operativa prevista en48h; `OVERDUE` informa demora sin aprobar automáticamente ni prometer disponibilidad permanente. Estados persistidos `PENDING`, `APPROVED`, `DENIED`, `NO_DATA`. La autorización vence al menor de **15min** y el vencimiento original más próximo de los puntos de su alcance. `EXPIRED` es estado efectivo de lectura, no prórroga ni modificación de la decisión. La entrega comprende únicamente puntos capturados dentro del intervalo propio y recibidos antes de revisión, todavía vivos. Revisión repetida no amplía alcance ni plazo.

La app exige una entrada restringida expresa para ver puntos y limpia esa vista al vencer, pasar a segundo plano o cerrarse. Cada página nueva exige reautenticación. El CRM comprueba identidad únicamente en la revisión autorizada, exige casilla sin premarcar y contraseña, y no transforma la revisión en una vista ordinaria de trayectorias. Resultado incierto obliga a consultar, no a repetir la mutación automáticamente. Contraseña inválida no provoca refresh/reenvío del POST ni cierre de la sesión ordinaria por inferencia.

## Incidencias y eliminación

| Método/ruta | Contrato |
|---|---|
| POST `incidents/{id}/gps-evidence` | ADMIN propio reautenticado, `pointId`, justificación `SAFETY_REVIEW` o `SERVICE_DISCREPANCY`. Punto de misma organización/conductor/servicio. Enlaza el punto original; no duplica coordenadas ni prolonga retención. |
| GET `gps/incident-evidence/{id}` | ADMIN propio; referencia, justificación, expiry y disponibilidad, sin coordenadas. |

V37 guarda puntos, referencias y solicitudes. V38 permite únicamente el desvínculo de la referencia GPS de una declaración cuando la purga ya eliminó el punto: conserva resultado, originales y todas las demás columnas. No permite desligar un punto existente, reescribir la declaración ni borrarla. La purga también nulifica referencias incidentales; la auditoría conserva resultados/referencias sin coordenadas. Los lectores filtran vigencia aunque el barrido aún no haya ejecutado.

El mantenimiento programado permanece habilitado por defecto. Las fixtures rollback deshabilitan ese job y prueban el mismo algoritmo acotado a su organización: adelantar su reloj siete días nunca debe purgar datos ajenos desde otra conexión. Esto no acredita acceso físico, batería, exactitud GPS ni cumplimiento jurídico: siguen L-08 y los bloqueos de preparación del piloto.
