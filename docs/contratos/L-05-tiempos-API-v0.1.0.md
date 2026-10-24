# Correcciones de tiempos L-05 — API0.1.0

2026-10-20 America/Lima. Q-064, ámbito local de L-05; [contrato](L-05.md). No habilita piloto. V39 es aditiva: no reconstruye tramos ni reescribe ejecución, declaraciones, puntos o aceptaciones anteriores.

## Autoridad y consulta

| Método/ruta relativa a `/api/v1` | Actor / comportamiento |
|---|---|
| GET `scheduled-services/{id}/time-history` | ADMIN de la organización. Estado/versión del servicio, tramos originales y efectivos, revisiones antes/después y solicitudes del conductor. |
| POST `scheduled-services/{id}/time-corrections` | ADMIN propio; `operationKey` UUID, `expectedVersion`, `reason`≤300, `requestId` opcional, `segments` completos (`segmentId`, `startedAt`, `endedAt`). |
| GET `mobile/assignments/{id}/time-history` | CONDUCTOR propio con incorporación aprobada, aun sin habilitación para nuevos servicios. Solo los tramos y solicitudes de su participación; sin nombres, correos, documentos ni coordenadas. |
| POST `mobile/assignments/{id}/time-correction-requests` | CONDUCTOR responsable: `requestKey`, `reason`≤300. No recibe permiso de modificar tiempos. No almacena tiempos corregidos por sí mismo. |

Respuestas `Cache-Control: no-store`. Gates de términos, identidad y autorización vigentes. Motivos sin datos personales; se aplica la guarda existente de correo/documentos numéricos a estas entradas y se informa la restricción en las pantallas. No se afirma que una expresión regular detecte toda información personal en texto libre.

## Invariantes

- En este corte se aplica a servicios `COMPLETED` con todos sus tramos originales cerrados. Los estados terminales de interrupción/cancelación y su integración se contrastarán al completar ese incremento; no se presentan como verificados aquí.
- Se conservan todos los IDs y orden de los tramos. No se admiten repeticiones, elementos nulos, tramos ajenos o faltantes, fin anterior al inicio, tiempos futuros ni solapamientos entre tramos.
- Se contrasta la participación efectiva corregida del conductor/unidad contra otros servicios. Un tramo abierto de otro servicio sigue ocupando hasta su cierre, sin inventar fin.
- Cada revisión conserva motivo, ADMIN autor, hora servidor, versión anterior/resultante y antes/después por tramo. Un segundo ajuste parte del valor efectivo anterior, sin perder el original del servidor. Las tablas de correcciones, detalles y solicitudes no admiten edición/eliminación.
- Mismo actor/key/payload no crea otra revisión. Reutilizar key para otro contenido entra en conflicto. Versión obsoleta obliga a consultar y conservar el caso. Una solicitud atendida no se reasigna a otra corrección.
- Se conserva el resultado original de corroboración. El resultado respecto del tiempo corregido se vuelve a contrastar solo si el punto original todavía existe y está dentro de sus siete días; un punto posterior al nuevo cierre o anterior al inicio corregido no se presenta como válido. Si ya venció o falta, el ajuste queda **no contrastable**, sin recrear GPS ni prorrogar retención. Tampoco prueba todas las paradas o atribuye fraude.

## Clientes y errores

CRM distingue originales y valores efectivos, exige motivo y confirmación sin premarcar. El formulario usa la zona local del navegador y transmite ISO UTC. Las filas no editadas conservan la precisión original completa, aunque la entrada HTML tenga precisión de milisegundos. Flutter solo consulta y solicita revisión; no contiene un editor de horas operativas.

Resultado incierto: consultar historial por key, sin nuevo POST ni cola automática. Los POST no se reenvían por refresh de autenticación. La confirmación de una aceptación o acción física no se reconstruye con esta herramienta: el servidor conserva cuándo recibió el ajuste y quién lo autorizó.

La regresión de concurrencia detectó un upgrade de lock compartido de organización después del lock de actor. Las escrituras ahora toman `NO KEY UPDATE` de organización antes de actor y programación, usando la misma modalidad final; se preserva la validación contra revocación y desactivación. Una sesión inválida detectada desde el gate de términos se traduce al envelope HTTP401 de Spring Security, sin filtrar una excepción de dominio fuera de MVC ni omitir el gate.

Pruebas de este corte: [incremento3](../evidencia/L-05/incremento-3-tiempos/README.md). No sustituye carreras de negocio del relevo, recorrido TCP conectado ni dispositivo real. SRV-INT-L05-05 está aprobado y su implementación sigue pendiente en este corte. **L-05 EN_CURSO; L-06 no iniciada.**
