# Estado de la Fase 2

- Fase: 2 — Gestión administrativa de recursos
- Madurez objetivo acumulada: 25–50 % del MVP
- Estado: completada y validada localmente con Docker el 2026-08-29
- Base de datos: PostgreSQL 16 local dentro de Docker Desktop; no se usa una
  instancia externa ni administrada.

## Resultado entregado

El CRM web ya administra recursos reales de la organización autenticada. Las
operaciones se realizan a través de la API, persisten en PostgreSQL y generan
eventos de auditoría. No se usan datos simulados en las pantallas.

| Recurso | Roles con acceso | Capacidades entregadas |
| --- | --- | --- |
| Organizaciones | `SUPER_ADMIN` | listar, crear, editar y cambiar estado |
| Usuarios | `ADMINISTRADOR` | listar, buscar, crear, editar, activar y desactivar; revocación de sesiones al desactivar |
| Grupos | `ADMINISTRADOR`, `COORDINADOR` | consulta por visibilidad; administración y asignación/retiro de coordinadores para administrador |
| Conductores | `ADMINISTRADOR`, `COORDINADOR` | consulta por grupo visible; alta, edición, activación y desactivación para administrador |
| Vehículos | `ADMINISTRADOR`, `COORDINADOR` | consulta; alta, edición y estado administrativo para administrador |
| Vínculo conductor–vehículo | `ADMINISTRADOR` | vincular, desvincular y marcar vehículo principal |

El frontend incorpora las rutas `/users`, `/groups`, `/drivers`, `/vehicles` y
`/organizations`, protegidas por guardas de rol. La API vuelve a aplicar cada
permiso; la interfaz no es el control de seguridad.

## Datos y seguridad

- Flyway `V2__fleet_administration.sql` agrega `transport_group`,
  `group_coordinator`, `driver`, `vehicle` y `driver_vehicle_link`.
- El `organization_id` se deriva siempre del usuario autenticado. Las búsquedas
  y lecturas de recursos usan ese tenant; los identificadores de otra
  organización responden como no encontrados.
- Usuarios, grupos, conductores, vehículos y vínculos producen eventos de
  auditoría. Las sesiones de un usuario desactivado se revocan.
- Un conductor puede asociarse opcionalmente a un usuario activo con rol
  `CONDUCTOR`. Se añadió `full_name` propio al conductor: el modelo de origen
  admitía `user_id` opcional, pero el CRM necesita identificar también a los
  conductores sin cuenta, sin duplicar contraseñas ni añadir aplicación móvil.
- El estado `EN_SERVICIO` de un vehículo no puede establecerse manualmente;
  queda reservado para las futuras asignaciones operativas.

## Límites deliberados

Esta fase no incorpora mapas, geolocalización, aplicación móvil, aceptación o
rechazo del conductor, asignaciones, cambio operativo de disponibilidad,
WebSocket, incidencias, anuncios, métricas de tablero ni reportes. Esas
capacidades requieren endpoints operativos reales y pertenecen a las fases
siguientes.

## Uso local

Con Docker Desktop iniciado, desde la raíz del proyecto:

```powershell
docker compose --profile app up -d --build
docker compose --profile app ps
```

Abra `http://localhost:4200`. El perfil local incluye organizaciones y cuentas
de demostración sólo para validar roles; cree grupos, conductores y vehículos
desde el CRM cuando desee cargar datos de prueba. No se requieren PostgreSQL,
pgAdmin ni servicios externos fuera de Docker Desktop.

## Criterios de salida verificados

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Migración de administración | Verificado | Flyway registra V1 y V2 con éxito en PostgreSQL 16 local |
| CRUD administrativo y vínculos | Verificado | `FleetManagementIT` cubre altas, cambios, vínculos, estados y restricciones |
| RBAC y aislamiento | Verificado | coordinador en solo lectura y caso de otra organización cubiertos por integración |
| Auditoría de cambios | Verificado | integración comprueba eventos administrativos persistidos |
| CRM Angular integrado | Verificado | pantallas, rutas protegidas y servicio HTTP contra `/api/v1` |
| Calidad frontend | Verificado | typecheck, build y 24 pruebas aprobadas |
| Calidad backend | Verificado | 20 pruebas unitarias y 9 de integración, sin fallos ni omisiones |
| Contenedores locales | Verificado | PostgreSQL, backend y frontend saludables |

Evidencia detallada: [evidencia de Fase 2](evidencia-fase-2-2026-08-29.md).

## Cierre histórico

La Fase 2 queda preservada como la base administrativa validada. Sus límites
operativos fueron resueltos en la Fase 3, cuya definición y evidencia se
encuentran en [fase-3-estado.md](fase-3-estado.md).
