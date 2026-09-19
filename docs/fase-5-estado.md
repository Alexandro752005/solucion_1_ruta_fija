# Estado y cierre de la Fase 5

## Resultado

La Fase 5 completa el rediseño visual integral del CRM Ruta Fija como una
transformación estructural de frontend. La solución conserva los contratos,
permisos, reglas operativas, persistencia, auditoría y despliegue entregados en
la Fase 4.

La identidad visual utiliza blanco, negro, gris y amarillo vial. La navegación
lateral fue sustituida por una topbar horizontal sensible al rol, con un menú
superior adaptable en pantallas estrechas.

## Módulos terminados

| Ámbito | Resultado de Fase 5 |
| --- | --- |
| Acceso | Formulario compacto, accesible y sin información técnica. |
| Resumen | Indicadores y actividad persistida para ADMINISTRADOR; accesos acordes con cada rol. |
| Usuarios | Filtros, tabla, alta/edición en modal y confirmación de activación/desactivación. |
| Organización | Vista de contexto en solo lectura, acorde con el contrato disponible. |
| Grupos | Alta/edición en modal y gestión protegida de coordinadores. |
| Conductores | Filtros, detalle integrado, disponibilidad administrativa y vínculos vehiculares. |
| Vehículos | Inventario, detalle, edición y cambios administrativos permitidos. |
| Asignaciones | Programación, reserva, inicio, finalización y cancelación con motivo. |
| Incidencias | Registro, detalle, seguimiento y resolución con control de versión. |
| Comunicados | Publicación por audiencia autorizada y detalle de solo lectura. |
| Reportes | Consulta y exportación PDF, Excel y CSV según capacidad existente. |
| Auditoría | Filtros legibles, tabla de solo lectura y detalle técnico controlado. |
| Organizaciones | Gestión global disponible para SUPER_ADMIN mediante el contrato existente. |

## Navegación final por rol

| Rol | Módulos visibles |
| --- | --- |
| ADMINISTRADOR | Resumen, Usuarios, Organización, Grupos, Conductores, Vehículos, Asignaciones, Incidencias, Comunicados, Reportes y Auditoría. |
| COORDINADOR | Resumen, Grupos, Conductores, Vehículos, Asignaciones, Incidencias y Comunicados. |
| SUPER_ADMIN | Resumen y Organizaciones. |

El rol CONDUCTOR permanece fuera del CRM web, conforme al alcance aprobado.

## Compatibilidad preservada

- Sin cambios en `backend/`, DTO, endpoints, migraciones o seguridad.
- Sin cambios en PostgreSQL 16, Docker Compose ni scripts de inicio/cierre.
- `SCHEDULED` se presenta como `Programada`; `EN_SERVICIO`, como `En servicio`.
- La UI no permite seleccionar manualmente estados operativos protegidos.
- La reserva de asignaciones continúa disponible y no cambia físicamente el
  vehículo hasta iniciar el servicio.
- No se simula aceptación del conductor, GPS, chat ni acuse de lectura.
- El aislamiento por organización y la autorización final permanecen en el
  backend.

## Componentes transversales

Se incorporaron encabezado de página, indicador de estado, paginación, modal y
diálogo de confirmación reutilizables. El modal controla foco, tecla Escape,
retorno de foco, bloqueo de desplazamiento y convivencia de diálogos apilados.

## Alcance diferido

La administración global de cuentas ADMINISTRADOR y la auditoría global no se
muestran porque el backend actual no ofrece contratos autorizados para esas
capacidades. Su implementación requiere una ampliación funcional independiente
(Fase 5B); no se sustituyó por información local, acciones deshabilitadas ni
datos simulados.

## Dictamen

La transformación es compatible con la idea central del software y no altera
su arquitectura funcional. La Fase 5 queda apta para ejecución local y revisión
funcional mediante Docker.
