# Contrato de implementación local — Mapa en vivo

Fecha: 11/11/2026. Autoridad: objetivo actual de diez fases, decisiones confirmadas del paquete y contratos reales inspeccionados. Este documento coordina las superficies; no acredita una implementación o prueba todavía.

## Decisiones cerradas para los incrementos

- Reutilizar `PUT /api/v1/mobile/location/consent`, `PUT /api/v1/mobile/location/current` y `DELETE /api/v1/mobile/location/current`. No segundo canal de subida, temporizador GPS, backend o base de negocio.
- Reutilizar `driver_current_location`, su vínculo a tramo/asignación y el historial de servicio existente de siete días. No migración nueva para el mapa. TTL vigente configurable: cinco minutos por defecto; no cambiarlo a 120 s.
- Ubicación vigente durante menos de 120 s; desactualizada desde 120 s mientras siga autorizada y no expire. Al caducar se retira, sin convertir un refresco en nueva captura.
- El mapa personal solo consume posición efímera del dispositivo. Nunca envía coordenadas a Ruta Fija antes de servicio confirmado. Si ya existe servicio compartiendo, la UI informa ese estado aunque se entre por pestaña personal.
- Consentimiento ya existente revocable; separado del permiso Android. Revocar invalida inmediatamente estado/captura pendientes del controlador. El servidor conserva la defensa transaccional ante carreras.
- Confirmación del usuario recibida el 11/11/2026: navegación móvil Inicio, Disponibilidad, Asignaciones, Mapa y Más. Más conserva Incidencias, Comunicados y Perfil y sus rutas. No eliminar acceso consultivo a cuentas limitadas.
- «Ruta finalizada» es declaración validada por backend, no prueba física de recorrido ni una segunda transición. Reutilizar complete y sus validaciones/confirmación/recuperación actuales; volver desde mapa no finaliza.
- Conservar presentación, componente, HTML y estilos del topbar CRM. Activar únicamente los dos accesos ADMIN ya previstos en sus metadatos de navegación, al quedar implementada `/live-map`; SUPER_ADMIN conserva su menú sin mapa.
- Mantener compatibilidad base Android existente (minSdk 29); alcance de aceptación nuevo Android 12+. No bajar requisitos ni afirmar pruebas físicas en versiones no ejecutadas.
- Confirmación posterior del usuario: conservar las cuotas históricas de cuatro ADMIN y veinte CONDUCTOR globales. La ampliación a cinco ADMIN y doscientos CONDUCTOR queda pendiente, fuera de este incremento. No desactivar sus triggers ni adulterar el ledger para una carga de prueba.

## Consulta ADMIN nueva

`GET /api/v1/live-map/vehicles?limit=5&groupId=<uuid opcional>&driverName=<texto opcional>`

`limit` admite exclusivamente 5, 10, 15 o 20, por defecto 5. Nombre máximo 100 caracteres; SQL parametrizado y caracteres de búsqueda tratados como texto. Organización derivada exclusivamente de la sesión. Grupo de otra organización produce conjunto vacío sin revelar existencia.

Respuesta JSON:

```json
{
  "serverTime": "UTC ISO8601",
  "limit": 5,
  "items": [{
    "driverId": "uuid", "vehicleId": "uuid",
    "driverName": "nombre", "plate": "placa",
    "latitude": 0, "longitude": 0,
    "capturedAt": "UTC ISO8601", "receivedAt": "UTC ISO8601",
    "expiresAt": "UTC ISO8601", "freshness": "CURRENT o STALE"
  }]
}
```

El ejemplo no contiene telemetría. Coordenadas solo para el mapa, nunca como texto, URL, log, storage, exportación, captura de evidencia con datos reales o evento WebSocket. Orden captura descendente y driverId estable. Todas las filas deben vincular organización, conductor con consentimiento, cuenta activa, tramo abierto, asignación EN_SERVICIO y servicio vigente; no exigir nuevamente elegibilidad de inicio que interrumpa una ejecución ya iniciada legítimamente.

`GET /api/v1/live-map/groups` → `{"items":[{"id":"uuid","name":"nombre"}]}`. Proyección mínima de grupos activos propios; filtrado de conductores mediante membresía vigente, no campo legacy.

Ambas respuestas `Cache-Control: no-store`. Sin sesión 401; rol distinto de ADMIN 403; filtros inválidos 400. Verificar además en servicio, sin confiar en guard Angular.

## Actualización y privacidad CRM

Fachada de alcance página con un solo dueño de conexión/suscripciones. Reutilizar WebSocket existente únicamente como invalidación sin coordenadas; reconciliación REST cada 15 s, sin solicitudes solapadas. Debounce de nombre 300 ms. Al ocultar página, perder sesión/rol/organización o destruir: cancelar, cerrar suscripciones y vaciar datos sensibles. Ante 401/403 o estado incierto de autorización: purgar; no conservar un supuesto «último corte válido». Retirar por `expiresAt` entre consultas. No extrapolar movimiento ni recentrar automáticamente tras interacción manual.

Composición CRM canónica: solo Main de CRM_04, mapa izquierdo y listado derecho; filtros, 5/10/15/20, grupos, nombre, selección y centrar, ficha existente; sin buscador de zonas. Popup/lista: conductor, placa y hora real; vigencia acompañada de texto. Clustering de proximidad y lista accesible alternativa. Vacío/error/tiles/desconexión/sesión vencida deben tener estados claros.

## Cartografía y costo

Bibliotecas locales sin API de facturación: Leaflet estable para Angular y flutter_map estable para Flutter, con versiones exactas y lockfiles. Teselas HTTPS configurables, atribución OSM visible/enlazada, User-Agent propio Android, Referer web y caché HTTP de imágenes respetada. Caché de imágenes del proveedor no equivale a cola GPS ni operación offline. Sin prefetch de zonas ni carga automatizada de tiles públicos. El proveedor puede conocer IP y zona del viewport: aviso previo visible, sin enviar nombres, placas ni JWT.

OSM público solo para visualización interactiva local/moderada conforme a sus términos vigentes; ningún SLA ni capacidad de producción 5 ADMIN × 40 conductores se presume aprobada. No despliegue externo ni contratación; carga sintética usa tiles locales/dobles. Evaluación de producción y representación legal permanecen puertas de despliegue, no se fingen resueltas.

## Verificación y reversión

Pruebas nuevas con evidencia RED/GREEN cuando viable, regresión de las tres superficies, PostgreSQL exclusivo y datos sintéticos; ningún TRUNCATE ni migración sobre `ruta_fija_test` o `solucion_ruta_fija_1`. Baseline fuente con SHA-256 en `verificacion/baseline`; restauración solo de archivos modificados y con procesos de desarrollo detenidos, sin alterar bases. Cada agente entrega archivos/métodos reales, comandos/resultados, riesgos y pruebas pendientes. Aprobación independiente por código y pruebas, no por declaración del autor.
