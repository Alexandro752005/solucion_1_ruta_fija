# Estado de la Fase 3

- Fase: 3 — Operación web, comunicación y reportes reales.
- Madurez objetivo acumulada: 50–75 % del MVP.
- Estado: implementada y validada localmente con Docker el 2026-08-29.
- Base de datos: PostgreSQL 16 local dentro de Docker Desktop; no se utiliza
  ninguna instancia externa o administrada.

## Resultado entregado

El CRM permite programar y administrar asignaciones reales, reservar e iniciar
servicios, registrar incidencias, publicar anuncios y consultar reportes contra
la información persistida. El frontend contiene rutas protegidas para
`/assignments`, `/incidents`, `/announcements` y `/reports`.

| Capacidad | Roles | Resultado |
| --- | --- | --- |
| Asignaciones | `ADMINISTRADOR`, `COORDINADOR` | Alta directa como `SCHEDULED`, edición previa a reserva, reserva, inicio, cierre, cancelación e idempotencia. |
| Disponibilidad del conductor | `ADMINISTRADOR`, `COORDINADOR` | Transiciones administrativas controladas; reserva y servicio solo a través de una asignación. |
| Vehículos | Operación interna | El vehículo no se vuelve `RESERVADO`; solo cambia a `EN_SERVICIO` al iniciar y a `DISPONIBLE` al cerrar. |
| Incidencias | `ADMINISTRADOR`, `COORDINADOR` | Registro CRM, seguimiento y resolución con auditoría. |
| Anuncios | `ADMINISTRADOR`, `COORDINADOR` | Audiencia por organización o grupo visible; sin confirmación ficticia de lectura. |
| Reportes | `ADMINISTRADOR` | Disponibilidad actual y consultas persistidas de asignaciones/incidencias por rango, con CSV. |
| Actualización web | `ADMINISTRADOR`, `COORDINADOR` | WebSocket con ticket efímero, origen validado y difusión aislada por tenant. |

## Criterios de salida

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Modelo de estados acordado | Verificado | Flujo conductor y vehículo implementado, incluido retiro de `DESCONECTADO` en V4. |
| Sin aceptación móvil simulada | Verificado | La API crea directamente `SCHEDULED`; no existen rutas de aceptación/rechazo de conductor. |
| Reportes reales | Verificado | `ReportService` consulta asignaciones e incidencias persistidas con límites de rango y resultado. |
| Aislamiento, RBAC y auditoría | Verificado | Integración de operación cubre grupos, tenant, acciones y registros de auditoría. |
| Calidad backend | Verificado | 20 pruebas unitarias y 10 de integración sin fallos. |
| Calidad frontend | Verificado | Typecheck, build y 26 pruebas sin fallos. |
| Contenedores locales | Verificado | PostgreSQL, backend y frontend reconstruidos y saludables. |

Los detalles técnicos y decisiones están en
[fase-3-diseno.md](fase-3-diseno.md); la ejecución concreta se conserva en
[evidencia-fase-3-2026-08-29.md](evidencia-fase-3-2026-08-29.md).
