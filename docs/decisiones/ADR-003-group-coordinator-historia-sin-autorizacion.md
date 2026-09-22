# ADR-003 - group_coordinator se conserva como historia, no como autorizacion

- Estado: aceptado e implementado en F2.2.
- Fecha: 2026-09-22.

## Contexto

Antes de la unificacion, la relacion `group_coordinator` restringia parte de
la visibilidad operativa de un coordinador. El producto elimina ese actor y
fusiona sus capacidades con `ADMIN`. Borrar fisicamente la relacion durante la
migracion de roles destruiria informacion historica y ampliaria el riesgo de
la entrega.

## Decision

La tabla y su entidad se retienen solo para historia tecnica. El backend:

- no publica endpoints para asignar o retirar coordinadores;
- no incluye coordinadores en la respuesta de grupos;
- no usa la relacion en filtros, servicios, autorizacion ni broadcast;
- expone exclusivamente un repositorio de lectura sin `save` ni `delete`.

## Consecuencias

La transicion conserva trazabilidad sin mantener un segundo rol
administrativo. Una futura depuracion de datos debe tener su propia migracion,
retencion definida y aprobacion; no forma parte de F2.2.
