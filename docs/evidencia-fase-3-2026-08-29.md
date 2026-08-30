# Evidencia de Fase 3 — 2026-08-29

## Validación de código

| Comprobación | Resultado |
| --- | --- |
| `backend/mvnw.cmd verify` | Correcto: 20 pruebas unitarias y 10 pruebas de integración, sin errores ni fallos. Las integraciones usan PostgreSQL 16 mediante Testcontainers y aplican Flyway V1–V4. |
| `OperationFlowIT` | Correcto: cubre creación `SCHEDULED`, idempotencia, conflicto de agenda, reserva, inicio, cierre, incidencia, reporte, anuncio, ticket de WebSocket y aislamiento por tenant. |
| `frontend/npm.cmd run typecheck` | Correcto. |
| `frontend/npm.cmd run test:ci` | Correcto: 10 archivos y 26 pruebas. |
| `frontend/npm.cmd run build` | Correcto: build de producción Angular generado. |

## Validación de contenedores

La reconstrucción local aplica Flyway V3 y V4 a la base PostgreSQL 16 del
contenedor y deja `postgres`, `backend` y `frontend` en estado saludable. El
health endpoint del backend responde `UP` después de la reconstrucción.

## Trazabilidad funcional

- La asignación web no finge aceptación del conductor: entra como `SCHEDULED`.
- La reserva mueve únicamente al conductor a `RESERVADO`; el vehículo no adopta
  ese estado físico.
- El inicio mueve conductor y vehículo a `EN_SERVICIO`; el cierre restituye
  ambos a `DISPONIBLE`.
- Los reportes se obtienen de filas persistidas, no de datos de demostración de
  interfaz.
