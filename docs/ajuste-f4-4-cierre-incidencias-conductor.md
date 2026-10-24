# Ajuste F4.4.1 — Cierre de incidencias por conductor

## Decisión funcional

El conductor no elimina una incidencia ni crea otra cuando el hecho fue
solucionado. Puede cerrar una sola vez una incidencia que él mismo reportó
desde Flutter. El mismo registro conserva el estado, el actor y la hora del
cierre para el CRM, los reportes y la auditoría.

La opción se llama **Cerrar incidencia definitivamente**. No se ofrece un
botón de reapertura o edición al conductor. El ADMIN conserva la facultad de
atender el caso desde el CRM sin duplicar la incidencia.

## Transición de estado

```text
OPEN / FOLLOW_UP
       │ conductor autenticado, reportante y fuente MOBILE_APP
       ▼
CLOSED_BY_DRIVER
       │ ADMIN registra atención
       ├──► FOLLOW_UP
       └──► RESOLVED
```

`CLOSED_BY_DRIVER` no cuenta como incidencia abierta en el indicador operativo.
Cuando el ADMIN inicia seguimiento, vuelve a contar como `FOLLOW_UP`; los datos
del cierre original se mantienen intactos.

## Reglas de seguridad y trazabilidad

- La API deriva usuario, conductor, tenant y hora del servidor; Flutter solo
  envía la `version` de concurrencia.
- Solo se acepta `POST /api/v1/mobile/incidents/{incidentId}/close` para una
  sesión `MOBILE` de rol `CONDUCTOR`.
- La incidencia debe ser del conductor autenticado, tener origen `MOBILE_APP`
  y haber sido reportada por su mismo usuario.
- Un segundo envío del mismo conductor, mientras aún está
  `CLOSED_BY_DRIVER`, devuelve el mismo estado sin crear una segunda auditoría.
- Después de que ADMIN la atiende, cualquier nuevo cierre del conductor se
  rechaza con `INCIDENT_INVALID_STATE`.
- No existe `DELETE FROM incident`, ni en la API ni en la migración.
- El evento de auditoría es `INCIDENT_CLOSED_BY_MOBILE_REPORTER`; el CRM recibe
  el evento WebSocket `incident.closed-by-driver` y recarga sus datos reales.

## Cambio físico V11

`V11__driver_closed_mobile_incidents.sql` agrega:

| Elemento | Finalidad |
| --- | --- |
| `CLOSED_BY_DRIVER` | Estado de cierre que no confunde la acción del conductor con una resolución administrativa. |
| `incident.driver_closed_by` | Usuario autenticado que efectuó el único cierre. |
| `incident.driver_closed_at` | Hora UTC decidida por el backend. |
| Constraints de par y estado | Impiden un estado cerrado sin actor/hora, o datos de cierre incompletos. |
| Índice por organización y fecha de cierre | Mantiene consultas operativas y auditoría eficientes. |

La V11 no toca datos de negocio existentes ni cambia los permisos DML de
`rf_app`. Solo `rf_migrator` puede alterar el esquema, y `rf_app` continúa sin
permiso `CREATE` en `public`.

## Aplicación segura en desarrollo

Con API y CRM detenidos, desde la raíz del proyecto ejecute:

```powershell
.\scripts\Invoke-RutaFijaF44DriverIncidentClosureMigration.ps1
```

El script se niega a usar otro destino que
`solucion_ruta_fija_1` en `127.0.0.1:5432`, comprueba que la configuración
privada esté ignorada por Git, crea un backup custom en `backups/f4-4`, lo
valida con `pg_restore --list`, ejecuta únicamente V11 y verifica:

- historial Flyway V1–V11;
- columnas y constraints de cierre;
- preservación de conteos de datos;
- DML de `rf_app` sobre `incident` y ausencia de DDL;
- ausencia total de Docker.

La salida de éxito empieza con
`F4_4_INCIDENT_CLOSURE_MIGRATION=PASS`. Se puede ejecutar nuevamente: si V11
ya está aplicada, informa `state=already_applied` y no toca datos.

## Prueba manual mínima

1. Inicie API, CRM y Flutter tras aplicar V11.
2. Ingrese como CONDUCTOR, reporte una incidencia y abra su detalle.
3. Confirme **Cerrar incidencia definitivamente**.
4. Verifique en Flutter que ya no aparece el botón de cierre.
5. Verifique en CRM una sola fila, estado **Cerrada por conductor**, nombre y
   fecha de cierre.
6. Como ADMIN registre un seguimiento; confirme que se conserva el cierre del
   conductor y el estado se convierte en `FOLLOW_UP` o `RESOLVED`.
7. Intente cerrar nuevamente desde Flutter: debe rechazarse, sin crear datos
   ni auditoría adicional.
