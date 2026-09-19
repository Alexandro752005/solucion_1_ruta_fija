# Auditoría final de la Fase 5

Fecha de revisión: 2026-08-30.

## Dictamen ejecutivo

**APROBADA.** La Fase 5 cumple el objetivo de unificar la experiencia visual
del CRM sin ampliar silenciosamente el producto ni modificar la autoridad del
backend. Las rutas, payloads, reglas operativas, roles, persistencia y controles
de seguridad se conservaron.

## Controles revisados

### Arquitectura y contratos

- No existen cambios bajo `backend/`.
- No se modificaron `compose.yaml`, Flyway, entidades, repositorios ni DTO.
- Los servicios Angular existentes conservan las rutas HTTP y estructuras de
  solicitud/respuesta.
- El nuevo módulo Organización de ADMINISTRADOR consume exclusivamente la
  identidad de sesión y no inventa un endpoint de actualización.

### Seguridad y permisos

- La topbar se filtra por rol y las rutas siguen protegidas por `roleGuard`.
- SUPER_ADMIN no recibe accesos a operación de una organización.
- ADMINISTRADOR conserva Reportes y Auditoría; COORDINADOR conserva únicamente
  su ámbito operativo.
- Formularios de usuarios no permiten crear SUPER_ADMIN ni seleccionar tenant.
- Auditoría permanece append-only y su detalle no renderiza HTML arbitrario.
- El CSV conserva neutralización de fórmulas.
- La CSP de scripts se conserva sin `unsafe-inline`; se desactivó la inserción
  de CSS crítico de Angular que generaba un manejador `onload` inline bloqueado.

### Reglas operativas

- Conductores: las transiciones manuales se limitan a Disponible, Descanso y No
  disponible; Reserva y En servicio siguen controladas por Asignaciones.
- Vehículos: En servicio no puede seleccionarse manualmente.
- Asignaciones: se conserva la reserva, control de versión, idempotencia,
  inicio, finalización y cancelación con motivo obligatorio.
- Incidencias: se conserva el control de versión y el responsable/tiempo del
  servidor.
- Comunicados: no se muestran estado ni lecturas inexistentes.

### Experiencia y accesibilidad

- No existe sidebar de escritorio.
- Controles rectangulares, foco visible, texto de alto contraste y diseño
  adaptable desde 320 px.
- Los formularios de alta y edición dejaron de ocupar permanentemente la vista.
- Acciones de riesgo usan diálogo accesible; se eliminaron `confirm` y `prompt`
  nativos del frontend.
- Los estados técnicos se traducen a etiquetas humanas en las vistas.
- Modal con foco inicial, Escape, trampa de foco, retorno al origen y limpieza
  segura al destruir el componente.

### Portabilidad

Se corrigió `.gitignore`: la regla de artefactos `reports/` ignoraba de forma
accidental `frontend/src/app/features/reports/`. Ahora solo excluye `/reports/`
en la raíz, por lo que el módulo de Reportes y su prueba forman parte del
proyecto al clonar el repositorio.

## Evidencia automatizada

- Frontend: typecheck aprobado.
- Frontend: 15 archivos y 37 pruebas aprobadas.
- Frontend: build de producción aprobado sin advertencias.
- Dependencias de producción: `npm audit --omit=dev --audit-level=high` sin
  vulnerabilidades reportadas.
- Backend: 21 pruebas unitarias aprobadas.
- Backend/PostgreSQL 16: 13 pruebas de integración aprobadas, sin omisiones.
- Flyway: 5 migraciones validadas en bases efímeras.

La evidencia de Docker y salud se registra en
`docs/evidencia-fase-5-2026-08-30.md`.

## Riesgos residuales declarados

- No existe administración global de cuentas ADMINISTRADOR.
- No existe auditoría global para SUPER_ADMIN.
- No se incorporaron pruebas visuales de regresión por píxel; la aceptación
  visual requiere revisión humana en los navegadores objetivo.
- El despliegue sigue siendo local mediante Docker y no sustituye una revisión
  de infraestructura productiva.

Ningún riesgo residual contradice el alcance aprobado de la Fase 5.
