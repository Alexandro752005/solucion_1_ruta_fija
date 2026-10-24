# L-07 — API de auditoría, v0.1.0

2026-10-21. Incremento de alcance/redacción y denegaciones; [contrato del lote](L-07.md). No acredita todavía la retención completa, cobertura de diferencias de cada módulo o recorrido conectado CRM.

## Contratos y permisos

| Actor | Ruta | Alcance |
|---|---|---|
| ADMIN | GET `/api/v1/audit-events`, `/{id}` | Solo eventos con su organización actual, obtenida del actor autenticado. |
| SUPER_ADMIN | GET `/api/v1/platform/audit-events`, `/{id}` | Solo eventos de administración de plataforma (`organization_id IS NULL`). Ningún parámetro abre una organización. |
| CONDUCTOR | Ninguna de esas rutas |403; conserva historiales/resultados propios de cada proceso. |

Listado con filtros acción/tipo/instantes y paginación1…100, orden validado. Rango inverso400, detalle ajeno404, sesión ausente/inválida401, rol ajeno403. Respuestas no-store; no endpoints de escritura o borrado de auditoría. Las vistas de CRM eligen ruta por el rol y no recurren a la otra si falla.

DTO conserva IDs internos/actor codificado/acción/entidad/correlación/fecha servidor y agrega `organizationId`, `scope=PLATFORM|ORGANIZATION` y `outcome`. Nuevos eventos clasifican `SUCCEEDED|DENIED|FAILED` desde la acción del servidor; el cliente no decide el resultado. Legados sin clasificación explícita muestran `LEGACY_RECORDED`, sin backfill de una comprobación no ejecutada.

## Metadatos

Una única proyección positiva se aplica antes de guardar nuevos eventos y al consultar los antiguos. Admite referencias UUID, versiones/fechas/estados técnicos y estructuras acotadas; descarta campos desconocidos/texto libre, secretos, contactos/identidad y coordenadas, también anidados. Huellas públicas de contenido/clave se diferencian de hashes de tokens/DNI. Un cambio personal estructurado puede indicar el campo pero conserva anterior/nuevo como `[REDACTADO]`, sin duplicar PII en este registro ordinario. Motivos/evidencias libres siguen en sus procesos restringidos y se enlazan mediante referencias permitidas; no se revelan mediante esta API.

Proteger la lectura de un registro legado no implica retirarlo de la base; la retención aprobada se implementa por separado. La proyección no habilita acceso a expedientes ni sustituye pruebas de cada evento/corrección.

## Denegaciones

Filtro situado tras autenticación Bearer y antes de autorización:registra401/403 y404 de objeto reconocido en rutas protegidas relevantes, una vez, después del resultado de negocio. Token inválido anterior al filtro se registra desde el entry point sin repetirlo. Flujos de autenticación/recuperación ya auditados se mantienen separados; GET correctos/refrescos de pantalla no crean eventos.

`SECURITY_ACCESS_DENIED` incluye actor/organización si pueden validarse en servidor; en otro caso autoría desconocida. Solo método, módulo normalizado y estado HTTP, nunca URL/query/cuerpo/cookie/cabecera/valor del token o recurso ajeno. Se conserva el rechazo si falla persistir su auditoría y se registra un fallo técnico sin secretos; no se simula auditoría guardada. La transacción REQUIRED comienza tras el servicio en tráfico normal y permanece dentro del rollback de las fixtures MockMvc; no se crea una conexión independiente que deje registros de prueba persistentes.

Las precisiones REP-L07-01/AUD-L07-02 están aprobadas en el registro0.3.0. Esta API no activa purga, SMTP/push, nuevas cuentas persistentes, piloto o L08.

## Contraste local posterior — 2026-10-21

[Acta L07](../actas/L-07-cierre-funcional-local-2026-10-21.md) y [matriz final](../evidencia/L-07/matriz-L07.md):verificación funcional local con sus límites. Las notas iniciales de implementación pendiente son históricas. No implica ensayo físico,habilitación del piloto o ejecución de purgas/copia reales.
