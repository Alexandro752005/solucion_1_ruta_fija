# Diseño y límites de la Fase 5

- Fase: 5 — sistema visual integral y experiencia de usuario.
- Línea base: `7422a75` (`main`).
- Tipo de transformación: grado 2, estructural de frontend.
- Backend, contratos JSON, seguridad, base de datos y reglas operativas: sin
  cambios.

## Objetivo

Unificar el CRM Ruta Fija bajo una interfaz blanca, negra y amarilla vial, con
topbar horizontal, controles rectangulares, tablas limpias, microcopy en
español y componentes reutilizables. La aplicación conserva exactamente las
capacidades funcionales verificadas al cierre de la Fase 4.

## Línea base verificada

Antes de modificar el frontend se ejecutaron satisfactoriamente:

- `npm.cmd run typecheck`.
- `npm.cmd run test:ci`: 12 archivos y 30 pruebas aprobadas.
- `npm.cmd run build`: compilación de producción aprobada.

## Restricciones no negociables

La Fase 5 no puede modificar:

- endpoints o propiedades JSON;
- DTO, entidades JPA o migraciones Flyway;
- JWT, refresh token, RBAC o aislamiento por organización;
- reglas de asignaciones, disponibilidad o concurrencia;
- WebSocket, reportes, auditoría o formatos ya entregados;
- PostgreSQL, Compose ni scripts de arranque/cierre;
- alcance web confirmado: no se incorporan aplicación móvil, GPS ni aceptación
  del conductor.

## Navegación por rol

| Rol | Navegación visible |
| --- | --- |
| `ADMINISTRADOR` | Resumen, Usuarios, Organización, Grupos, Conductores, Vehículos, Asignaciones, Incidencias, Comunicados, Reportes, Auditoría |
| `COORDINADOR` | Resumen, Grupos, Conductores, Vehículos, Asignaciones, Incidencias, Comunicados |
| `SUPER_ADMIN` | Resumen, Organizaciones |

`CONDUCTOR` no dispone de interfaz CRM en el alcance actual.

## Adaptaciones obligatorias de los documentos visuales

| Propuesta documental | Adaptación compatible con el sistema real |
| --- | --- |
| Organización editable por `ADMINISTRADOR` | Vista de contexto en solo lectura basada en la sesión; no existe endpoint de edición para ese rol. |
| `IN_PROGRESS` en asignaciones | Se conserva `EN_SERVICIO` y se traduce como `En servicio`. |
| Estado `DESCONECTADO` del conductor | No se presenta; fue retirado por corresponder al futuro alcance móvil. |
| Ocultar organizaciones al `SUPER_ADMIN` | Se conserva el módulo porque es una capacidad central y validada. |
| Administradores globales | Diferido: no existe contrato global para listar, crear o editar administradores. |
| Auditoría global | Diferido: la auditoría actual pertenece al tenant del `ADMINISTRADOR`. |
| Métricas globales de administradores | Se omiten mientras no exista una fuente autorizada. |
| Cantidad de conductores en cada fila de grupos | Se omite en la lista para evitar consultas N+1; puede consultarse en el detalle existente. |
| Estado de comunicados | Se omite porque el modelo actual no tiene estado ni confirmación de lectura. |
| CSV solo desde backend | Se conserva el CSV seguro ya implementado en frontend, además de PDF/XLSX del backend. |

## Componentes compartidos previstos

- topbar y navegación por rol;
- encabezado de página;
- botones primario, secundario y de riesgo;
- barra de búsqueda y filtros;
- contenedor de tabla y paginación;
- indicador de estado con texto e icono/punto;
- modal y diálogo de confirmación accesibles;
- alertas de confirmación y estados de carga, vacío, sin resultados y error.

Las tablas y formularios conservan tipado y lógica específica por módulo. Los
componentes compartidos resuelven estructura y comportamiento transversal, no
duplican la lógica de negocio.

## Puerta de salida por módulo

Antes de avanzar al módulo siguiente deben cumplirse:

1. Typecheck, pruebas frontend y build aprobados.
2. Mismas rutas HTTP y mismos payloads que la línea base.
3. Acciones visibles acordes con el rol y el estado del recurso.
4. Estados de carga, vacío, error, éxito y confirmación.
5. Navegación por teclado, foco visible y zoom al 150 %.
6. Sin enums, UUID, fases, porcentajes o comentarios técnicos en la vista.
7. Sin modificaciones en `backend/`, migraciones ni `compose.yaml`.

## Capacidades diferidas

`Administradores` y `Auditoría global` del `SUPER_ADMIN` requieren una
ampliación funcional independiente, denominada provisionalmente Fase 5B. Esa
ampliación necesitaría contratos API, autorización global explícita, filtros
multiempresa, auditoría y pruebas negativas entre organizaciones; no forma
parte del rediseño visual aprobado.
