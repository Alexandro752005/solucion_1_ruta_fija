# ADR-007 — Ubicación manual en primer plano (M7)

## Contexto

El backend ya expone rutas móviles protegidas para consentimiento, actualización
de ubicación vigente y eliminación del punto. La base de datos conserva como
máximo un punto por conductor; no existe historial de recorridos.

M7 habilita el uso de esas rutas desde Flutter sin extender la autoridad del
cliente ni abrir capacidades de seguimiento continuo.

## Decisión

1. La aplicación solicita ubicación solo después de que el conductor presiona
   **Autorizar y actualizar ubicación** dentro de la pantalla correspondiente.
2. Se muestran dos decisiones diferentes: el consentimiento funcional que se
   registra en el servidor y el permiso Android mientras se usa la aplicación.
   Rechazar el permiso no activa el consentimiento ni envía coordenadas.
3. Se usa `geolocator` 14.0.3 para consultar el servicio de ubicación, pedir el
   permiso y obtener un único punto con límite de 15 segundos.
4. El manifiesto declara `ACCESS_COARSE_LOCATION` y
   `ACCESS_FINE_LOCATION` para que Android pueda respetar la elección del
   usuario entre precisión aproximada o precisa. No declara permiso de
   ubicación en segundo plano ni un servicio de ubicación.
5. Flutter no muestra coordenadas, no las escribe en almacenamiento local y no
   conserva una cola, stream o historial. Solo envía latitud, longitud,
   precisión, instante de captura y la atestación de permiso que exige el
   contrato.
6. El servidor mantiene la autoridad: acepta el punto únicamente para el
   conductor móvil autenticado, con consentimiento activo y disponibilidad
   `DISPONIBLE` o `EN_SERVICIO`; conserva un único punto y aplica expiración.
7. El conductor puede eliminar el punto vigente sin retirar el consentimiento,
   o retirarlo; al retirarlo el servidor elimina el punto en la misma operación.

## Consecuencias

- M7 suma 8 puntos verificables a la matriz móvil: **66/80** con M1–M7.
- No requiere una migración ni modifica el contrato público de Spring Boot.
- Quedan explícitamente fuera M8 y M9: cola offline, ubicación continua,
  ejecución en segundo plano, mapas, fotos, notificaciones y entrega release.

## Fuentes de decisión

- [Android: solicitar acceso de ubicación en tiempo de ejecución](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Geolocator 14.0.3](https://pub.dev/packages/geolocator)
