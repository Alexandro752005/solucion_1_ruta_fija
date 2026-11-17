# Cartografía, licencia y límites de uso

Evaluación técnica de fuentes oficiales consultadas el 11/11/2026. Alcance: implementación local y pruebas sintéticas. No autoriza un despliegue empresarial ni sustituye la evaluación de privacidad del responsable.

## Componentes separados

GPS Android obtiene posición del dispositivo; Leaflet y flutter_map la representan; un servidor de teselas aporta imágenes de calles. Ni el GPS ni estas bibliotecas requieren una cuenta con facturación. Las dependencias se fijan con lockfile y sus licencias deben acompañar distribución; OSM exige atribución sobre la cartografía.

- Angular: Leaflet estable 1.9.4 empaquetado localmente, evitando la beta 2.0 y CDN.
- Flutter: flutter_map 8.3.2 (BSD-3-Clause), versión estable publicada, con caché HTTP integrada desde v8.2; latlong2 y enlace de atribución cuando correspondan. Preservar el parche Geolocator existente.

## Condiciones para teselas OSM públicas

URL HTTPS oficial, atribución visible/enlazada, Referer web y User-Agent Android propio, caché conforme a cabeceras, sin predescarga de regiones ni solicitudes masivas. En navegador la caché HTTP normal cumple el mecanismo; en Android se conserva la caché de imágenes de flutter_map. No se guardan muestras GPS para reenvío ni se ofrece operación offline.

Las teselas públicas tienen capacidad limitada y servicio de mejor esfuerzo. La implementación no compra servicios ni crea claves de pago. Visualización interactiva local moderada es distinta de demostrar capacidad para cinco administradores y hasta cuarenta conductores por administrador. Ninguna prueba de estrés se ejecuta contra OSM; las pruebas de carga usan datos/teselas sintéticos.

## Privacidad de terceros

El proveedor cartográfico puede observar la dirección IP y el área del viewport, incluso en mapa personal. La app debe explicarlo antes de cargar el mapa y enlazar atribución/términos; no transmitir JWT, nombre, placa, identificadores de organización ni feed GPS al proveedor. No insertar coordenadas en logs, almacenamiento de respuestas, analítica o capturas de evidencia real. Los índices de imágenes del mapa son tráfico técnico cartográfico, separado de la API de posiciones.

## Puerta de producción

Sin despliegue externo automático. Antes de un piloto real: confirmar representante autorizado y aviso de privacidad, elegibilidad de uso, capacidad/controles del proveedor y alternativas S/0 si se retira el acceso. No prometer SLA ni «gratuito ilimitado». Si esas condiciones no se acreditan, se bloquea el despliegue; la entrega local no las da por resueltas.

## Fuentes oficiales

- [Política de teselas OSM](https://operations.osmfoundation.org/policies/tiles/).
- [Términos OSMF](https://wiki.osmfoundation.org/wiki/Terms_of_Use).
- [Uso directo de OSM en flutter_map](https://docs.fleaflet.dev/tile-servers/using-openstreetmap-direct).
- [Caché HTTP flutter_map](https://docs.fleaflet.dev/layers/raster-tile-layer/caching).
- [Versión y licencia flutter_map](https://pub.dev/packages/flutter_map).
- [Leaflet](https://leafletjs.com/).

La comprobación de comportamiento de las dependencias instaladas, identificación/atribución y ausencia de facturación se registra con las pruebas y revisión de código; leer estas fuentes no prueba por sí solo conformidad de una app todavía en construcción.
