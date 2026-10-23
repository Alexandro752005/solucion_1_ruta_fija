# L-05 — API de inicio, tramos y ubicación, 0.1.0

Contrato incremental implementado en V36; **no equivale al cierre de L-05**. [Contrato](L-05.md), [matriz](../evidencia/L-05/matriz-L05.md). Solo cuentas sintéticas/pruebas; S/0 y piloto bloqueado.

## Inicio online propio

`POST /api/v1/mobile/assignments/{offerId}/start` requiere sesión MOBILE CONDUCTOR, su oferta aceptada y habilitación vigente. El servidor mantiene el bloqueo de organización, refresca el conductor, comprueba versión, recursos/ventana/documentos y consentimiento persistido. El cuerpo conserva sus tres campos idempotentes y añade:

```json
{
  "clientEventId": "UUID del acto online",
  "version": 1,
  "occurredAt": "hora ISO del dispositivo, no hora real de inicio",
  "gps": {
    "permissionGranted": true,
    "locationServiceEnabled": true,
    "foreground": true
  }
}
```

No se envían coordenadas previas al inicio. Los tres booleanos son declaración del cliente sobre el sistema operativo; no atestación del hardware. Consentimiento ausente→409 `LOCATION_CONSENT_REQUIRED`; GPS incompleto/denegado/segundo plano→409 `LOCATION_PERMISSION_NOT_REPORTED`, sin cambiar ocupación ni crear tramo. La app de producción comprueba esos estados; permiso denegado permite seguir consultando la cuenta. Otorgar consentimiento es un acto explícito separado, sin iniciar ni enviar un punto.

Hora de inicio y registro son del servidor. Se crea un tramo original1 para el servicio/oferta/conductor/unidad y se descarta el snapshot anterior. No se reconstruye ejecución de ofertas históricas sin servicio. Repetición idéntica devuelve `X-Idempotent-Replay:true`, sin duplicar tramos; cambiar el contexto GPS del mismo evento produce conflicto. Los recibos anteriores sin GPS conservan su canonicalización original para consulta idempotente; no autorizan un inicio nuevo.

## Tramos y cierre

- `GET /api/v1/scheduled-services/{serviceId}/execution`: ADMIN propio, con gates vigentes.
- `GET /api/v1/mobile/assignments/{offerId}/execution`: CONDUCTOR propio, solo sus tramos. Oferta ajena→404, ruta CRM desde móvil→403, sin sesión→401.
- Campos: `id`, `assignmentId`, `driverId`, `vehicleId`, `ordinal`, `startedAt`, `endedAt`, `endKind`. Identificadores codificados y tiempos, sin PII ni coordenadas.
- `POST .../complete`: responsable propio, aceptación/inicio auténticos, versión y tramo abierto. Conserva originales y registra fin servidor/actor/evento `COMPLETED`; detiene/elimina snapshot vigente después de corroborar terminal. Pérdida de consentimiento/GPS no impide declarar un cierre seguro; no inventa corroboración.
- Alias CRM `POST /api/v1/assignments/{id}/complete` rechaza409 `ASSIGNMENT_INVALID_TRANSITION`: ADMIN no declara Ruta finalizada por el conductor, tampoco para ofertas heredadas. Esas rutas permanecen consultables, no se eliminan datos.

V36 impide más de un tramo abierto por servicio, conductor o unidad, exige correspondencia de la oferta y actor CONDUCTOR, y rechaza edición de originales/eliminación/reapertura. El fin solo se registra una vez. Aún pendientes relevo/interrupción y corrección por historial separado.

## Punto vigente

`PUT /api/v1/mobile/location/current` conserva latitud, longitud, precisión y `capturedAt`, y exige los booleanos `permissionGranted`, `locationServiceEnabled`, `foreground`. Solo se admite con consentimiento vigente, estado EN_SERVICIO y un tramo propio abierto; DISPONIBLE no habilita captura. Se comprueba después de esperar el mismo lock, con datos recargados. Una pérdida de habilitación posterior al inicio no se presenta como pérdida de GPS ni impide su cierre; la consulta móvil aprobada conserva la posibilidad de informar el punto del tramo en ejecución.

No se admiten captura futura ni anterior al inicio del tramo, ni fuera de la antigüedad configurada. Un punto atrasado no reemplaza un snapshot más reciente. El snapshot queda vinculado al tramo real; la corroboración terminal no utiliza un punto de otro servicio o sin proveniencia. Consentimiento retirado, cierre o eliminación expresa retiran el snapshot; nunca se copian coordenadas a auditoría/WebSocket.

**Límite de este incremento:** snapshot derivado con TTL; todavía no constituye historial GPS de siete días, purga histórica, acceso a puntos ni captura periódica30s. Estos criterios continúan pendientes en L05.4. Se conserva el máximo aprobado de siete días, también incidental, sin afirmar que ya esté implementado todo su recorrido. Pruebas de reloj/sensores sintéticos no acreditan calibración o dispositivo L-08.
