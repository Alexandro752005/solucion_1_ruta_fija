# F5.2 — Demo gratuita temporal

## Objetivo

Publicar durante una demostración el CRM y sus rutas de API/WebSocket mediante
una URL HTTPS aleatoria de Cloudflare Quick Tunnel. PostgreSQL, backend y
frontend conservan escucha local; el único origen del túnel es
http://127.0.0.1:4300.

Flujo:

~~~text
Navegador o Flutter
        |
        | HTTPS / WSS
        v
URL aleatoria *.trycloudflare.com
        |
        | cloudflared, proceso temporal
        v
Angular 127.0.0.1:4300
        |-- /api --> Spring Boot 127.0.0.1:8080
        |-- /ws  --> Spring Boot 127.0.0.1:8080
                         |
                         v
                PostgreSQL 127.0.0.1:5432
~~~

El puerto 5432 no aparece en la configuración del túnel y nunca se publica.

## Preparación inicial

El primer inicio descarga cloudflared dentro de .tools, verifica el checksum
SHA-256 publicado en la versión oficial y exige una firma Authenticode válida
de Cloudflare, Inc. No instala un servicio de Windows.

También puede prepararse manualmente:

~~~powershell
.\scripts\Install-RutaFijaCloudflared.ps1
~~~

## Iniciar la demostración

Desde la raíz:

~~~powershell
.\iniciar_demo_temporal.bat
~~~

La salida muestra DEMO_URL y MOBILE_API. La URL vigente también queda en:

~~~text
.runtime\ruta-fija-demo-url.txt
~~~

El backend autoriza ese origen únicamente durante este proceso, usa cookie
Secure para la sesión web HTTPS y Angular sirve su configuración optimizada de
producción sin mapas de código. Cada reinicio genera otra dirección.

## Ejecutar Flutter contra la URL temporal

Con el teléfono autorizado, copie MOBILE_API sin corchetes ni credenciales:

~~~powershell
Set-Location mobile
flutter run --debug --dart-define=RF_API_BASE_URL=https://URL-TEMPORAL.trycloudflare.com/api/v1
~~~

No use adb reverse para esta modalidad: el teléfono consume la misma API HTTPS
que se está mostrando públicamente.

## Guion funcional obligatorio

1. ADMIN inicia sesión en DEMO_URL y crea o identifica una asignación válida.
2. CONDUCTOR inicia sesión en Flutter usando MOBILE_API.
3. CONDUCTOR acepta, inicia y completa la asignación.
4. El CRM observa el cambio en tiempo real o al refrescar la consulta.
5. ADMIN comprueba el estado en reportes y el evento en auditoría.
6. Si se demuestra una incidencia, el conductor la registra o cierra y ADMIN
   verifica la misma entidad; no debe duplicarse.

Use únicamente información ficticia y contraseñas temporales fuertes. Quick
Tunnel no añade una pantalla privada de Cloudflare Access.

## Auditoría con la demo activa

~~~powershell
.\scripts\Test-RutaFijaF52QuickDemo.ps1 -RequireLive
~~~

Resultado automático esperado:

~~~text
F5_2_QUICK_DEMO_AUDIT=PASS design=CRM_API_ONLY postgresql=PRIVATE origin=LOOPBACK angular=PRODUCTION websocket=SUPPORTED_CONFIGURED cloudflared_signature=VALID live=PASS
~~~

La auditoría confirma la página pública, la llegada a la API, los listeners
locales y la firma del binario. El guion ADMIN–CONDUCTOR requiere validación
humana porque utiliza cuentas y datos reales de demostración.

## Cierre inmediato

~~~powershell
.\finalizar_demo_temporal.bat
~~~

El cierre corta primero cloudflared y después detiene frontend, backend y
PostgreSQL. finalizar_ruta_fija.bat también detecta una demo activa y aplica el
mismo cierre.

## Límites aceptados

- Es una función gratuita de desarrollo y demostración, no un despliegue.
- La URL cambia al reiniciar y no tiene garantía de disponibilidad.
- El límite actual es 200 solicitudes simultáneas en curso.
- Quick Tunnel no soporta Server-Sent Events; Ruta Fija utiliza WebSocket.
- Cloudflare Tunnel declara soporte completo para WebSocket.

Fuentes oficiales:

- [Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/)
- [Descargas de cloudflared](https://developers.cloudflare.com/tunnel/downloads/)
- [Compatibilidad WebSocket](https://developers.cloudflare.com/cloudflare-one/faq/cloudflare-tunnels-faq/)

## Puerta de salida

F5.2 recibe VERDE únicamente cuando:

- la auditoría automática termina en live=PASS;
- el propietario completa el guion ADMIN → CONDUCTOR → CRM →
  reporte/auditoría;
- el cierre revoca la URL y deja los servicios detenidos.
