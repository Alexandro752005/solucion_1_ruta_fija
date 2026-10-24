# Evidencia F5.2 — Demo gratuita temporal

- Fecha: 2026-09-30.
- Estado automático: aprobado.
- Estado de la puerta: pendiente de prueba funcional del propietario y cierre
  controlado de la URL.

## Sesión temporal observada

~~~text
DEMO_URL=https://invest-loose-tan-understood.trycloudflare.com
MOBILE_API=https://invest-loose-tan-understood.trycloudflare.com/api/v1
~~~

La dirección es aleatoria y solo existe mientras el proceso cloudflared de esta
sesión permanezca activo. No debe registrarse como dirección permanente.

## Evidencia automática

~~~text
F5_2_QUICK_DEMO_AUDIT=PASS design=CRM_API_ONLY postgresql=PRIVATE origin=LOOPBACK angular=PRODUCTION websocket=SUPPORTED_CONFIGURED cloudflared_signature=VALID live=PASS
F5_2_FRONTEND_RUNTIME=PASS configuration=production
~~~

La solicitud externa devolvió:

~~~text
HTTP/1.1 200 OK
Content-Type: text/html
Server: cloudflare
~~~

## Controles comprobados

| Control | Resultado |
| --- | --- |
| CRM público | HTTP 200 con la aplicación Angular. |
| API pública | La ruta de sesión llegó al backend y respondió 401 sin credenciales, como corresponde. |
| Angular público | Configuración production y proxy de API/WebSocket. |
| Backend | Escucha exclusivamente en 127.0.0.1:8080. |
| PostgreSQL | Escucha exclusivamente en loopback:5432; no forma parte del comando del túnel. |
| Cloudflare | cloudflared 2026.9.3, firma Authenticode válida de Cloudflare, Inc. |
| Integridad | SHA-256 F096265EC2FCBE9BB6E2D64268DB167CED3FCBB83D894BDB9E2FCDB26F2EA7E2. |
| Persistencia del túnel | Proceso temporal, sin servicio de Windows ni actualización automática. |

## Validación humana pendiente

El propietario debe completar con datos ficticios:

1. Ingreso ADMIN mediante DEMO_URL.
2. Ingreso CONDUCTOR desde Flutter configurado con MOBILE_API.
3. Aceptación, inicio y cierre de una asignación.
4. Observación del cambio en CRM.
5. Consulta del mismo resultado en reportes y auditoría.
6. Ejecución de finalizar_demo_temporal.bat y comprobación de que la URL deja de
   responder y PostgreSQL queda detenido.

F5.2 no se cierra únicamente con la prueba automática. Requiere confirmación
VERDE del propietario después de completar este recorrido y cerrar la sesión.
