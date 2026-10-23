# L07.3 — Resumen por rol, API v0.1.0

2026-10-21. Implementación por verificar. GET `/api/v1/dashboard/admin`: solo ADMIN activo de su organización, sin selector empresarial; snapshot transaccional de contadores, sin PII, nombres, documentos, coordenadas ni detalle de otras organizaciones. Orden: incidencias CRITICAL abiertas; atenciones seguras de recursos sin resolución explícita; servicios sin aceptación previstos hasta las próximas24h (incluye atrasados); servicios pendientes de reasignar; ofertas pendientes ya vencidas; ofertas que vencen dentro de24h; incidencias abiertas; incorporaciones PENDING_REVIEW. Las ventanas son informativas y no modifican vencimientos, disponibilidad ni autorizaciones. La atención no se libera por leer el resumen. Los contadores pueden coincidir porque representan colas independientes, no categorías sumables.

GET `/api/v1/platform/dashboard`: solo SUPER_ADMIN, agregado de organizaciones y cuentas ADMIN de plataforma. No incluye métricas, recursos, expedientes ni actividad operativa empresarial. ADMIN y CONDUCTOR no acceden a este endpoint; SUPER_ADMIN no accede al resumen operativo. En ambos casos se comprueba actor en servidor y se responde `Cache-Control: no-store`. Sin nuevos permisos, DDL o auditoría de cada refresco.

El CRM conserva disponibilidad y actividad reciente propias como información secundaria. Si falla el resumen de plataforma, no recurre a endpoints de operación ni auditoría empresarial. Cero significa contador consultado con éxito; un fallo se presenta como no disponible, nunca como cero.

## Contraste local posterior — 2026-10-21

[Acta L07](../actas/L-07-cierre-funcional-local-2026-10-21.md) y [matriz final](../evidencia/L-07/matriz-L07.md):verificación funcional local con sus límites. Las notas iniciales de implementación pendiente son históricas. No implica ensayo físico,habilitación del piloto o ejecución de purgas/copia reales.
