# F3.1A - ADR de contrato móvil y estados

## Propósito

F3.1A es el primer bloque del día 5 de la Etapa 2. Fija las decisiones que
deben guiar V7, V8, la API móvil y Flutter antes de modificar el esquema o
publicar una ruta `/mobile/*`.

La decisión aprobada está en
[ADR-004](decisiones/ADR-004-contrato-movil-estados-y-ubicacion.md).

## Entrada comprobada

- G2 cerró la consolidación de roles: solo operan `SUPER_ADMIN`, `ADMIN` y
  `CONDUCTOR`.
- PostgreSQL 16 funciona de forma nativa y la línea base usa Flyway V1-V6.
- Las restricciones GiST V5, la auditoría append-only y el aislamiento por
  tenant están vigentes.
- Docker no participa en el arranque, pruebas ni persistencia local.

## Alcance exacto

Esta subfase documenta y verifica estas decisiones:

1. `ADMIN_DIRECT` conserva creación CRM en `SCHEDULED`.
2. `MOBILE_CONFIRMATION` empieza en `PENDING_RESPONSE` y solo el conductor
   autenticado podrá aceptar o rechazar en F3.3.
3. La aceptación móvil reserva al conductor; la reserva futura nunca asigna
   `RESERVADO` al vehículo.
4. V7 ampliará estado, plazo y respuesta de `assignment`, y sus exclusiones
   GiST incluirán la respuesta pendiente.
5. V8 añadirá una sola ubicación vigente por conductor, por UPSERT, con
   consentimiento funcional, vigencia y ausencia de historial.

No se aplica V7/V8, no se modifica PostgreSQL, no se crean endpoints móviles,
no se instala Flutter y no se altera Angular en F3.1A.

## Verificación reproducible

Desde la raíz del repositorio, con PowerShell:

~~~powershell
.\scripts\Test-RutaFijaF31aMobileContract.ps1 -RequirePreImplementation
~~~

Salida esperada:

```text
F3_1A_CONTRACT=PASS phase=pre_implementation response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only consent=required vehicle_reserved=0 docker=0
```

La tarea equivalente de VS Code se llama **Ruta Fija: validar ADR móvil y
estados (F3.1A)**.

## Criterio para marcar verde

F3.1A queda en verde únicamente si:

- la ADR define ambos modos, la máquina de estados, la transición auténtica y
  las reglas de privacidad;
- la validación anterior aprueba con esquema V1-V6 y cero rutas móviles;
- `VehicleStatus` conserva cuatro estados sin `RESERVADO`;
- el árbol de trabajo solo contiene los documentos, la validación y su evidencia
  de F3.1A antes del commit;
- no se inició F3.1B.

## Entrega a F3.1B

Con F3.1A verde, F3.1B podrá crear y ensayar las migraciones
`V7__mobile_assignment_workflow.sql` y
`V8__mobile_current_location.sql`. Deberá validar migración nueva y actualización
desde V5/V6, sin tocar credenciales ni usar Docker.
