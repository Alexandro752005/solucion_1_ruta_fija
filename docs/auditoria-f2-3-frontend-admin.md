# Auditoria F2.3 - CRM Angular ADMIN

Fecha de auditoria: 2026-09-22.

## Dictamen

El CRM Angular queda alineado con el backend V6: un `ADMIN` ve la navegacion y
capacidades completas de su tenant. No hay una ruta visible o accion de cliente
que dependa de `ADMINISTRADOR`, `COORDINADOR` o de la relacion
`group_coordinator`.

## Matriz de controles

| Control | Comprobacion | Resultado esperado |
| --- | --- | --- |
| Modelo de sesion | `USER_ROLES` contiene solo tres roles actuales | Conforme |
| Guardas y rutas | 10 rutas tenant requieren solo `ADMIN` | Conforme |
| Navegacion | ADMIN recibe todos los modulos CRM; SUPER_ADMIN conserva alcance global | Conforme |
| Usuarios | Formulario y filtro aceptan `ADMIN` y `CONDUCTOR`; valor inicial CONDUCTOR | Conforme |
| Grupos | Sin DTO, campo, modal ni cliente HTTP de coordinadores | Conforme |
| Dashboard y acciones | Sin ramas de administrador/coordinador; usa `ADMIN` | Conforme |
| Contrato | OpenAPI enum vigente y endpoint retirado ausente | Conforme |
| Calidad | Typecheck, Vitest, Maven/Failsafe y smoke nativo aprobados | Conforme |

## Residuales permitidos

Las menciones de roles antiguos fuera del CRM activo se clasifican asi:

- V1, V6, fixtures y scripts F2.1B: historia de migracion certificada.
- Pruebas backend de rechazo de claims antiguos: seguridad negativa intencional.
- Entidad y tabla `group_coordinator`: historia tecnica de solo lectura segun
  ADR-003.
- Evidencias y estados de fases anteriores: documentacion historica, no guia
  operativa vigente.

No es permitido conservar una mencion de esos roles en fuente Angular activa.

## Criterio de salida

F2.3 puede marcarse verde cuando la auditoria de cliente, pruebas de frontend,
pruebas backend/OpenAPI y smoke REST/WebSocket terminen sin fallos, el arbol
quede trazable en Git y no se haya iniciado trabajo de F2.4/F3.
