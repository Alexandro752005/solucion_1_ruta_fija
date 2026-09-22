# F1.5 — Proxy Angular y tiempo real nativo

F1.5 mantiene el CRM en un solo origen durante desarrollo:

~~~text
Navegador http://localhost:4200
  ├── /api → http://127.0.0.1:8080
  └── /ws  → ws://127.0.0.1:8080
~~~

El proxy de Angular configura `ws: true` para `/ws` y conserva el origen del
navegador. Así el backend puede validar `http://localhost:4200` durante el
handshake, sin exponer el JWT en la URL: el socket usa un ticket efímero de un
solo uso emitido por la API.

## Smoke test completo

Con 8080 y 4200 libres:

~~~powershell
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Verify
~~~

La verificación realiza, exclusivamente contra `ruta_fija_test`:

1. Limpieza protegida y arranque temporal del backend en `127.0.0.1:8080`.
2. Arranque temporal de Angular en `http://localhost:4200`.
3. Login, `/auth/me` y refresh a través de `/api`.
4. Emisión de ticket, conexión a `/ws/operations`, recepción de
   `stream.ready` y rechazo de la reutilización del ticket.
5. Cierre verificado de ambos procesos y nueva limpieza de la base de pruebas.

La contraseña de la semilla temporal se genera en memoria, no se imprime y no
queda en la base. El runtime normal sigue con semillas desactivadas.

Una ejecución aprobada concluye con:

~~~text
F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0
~~~

Si los puertos pertenecen a otro proceso, el script se detiene sin cerrarlo.
Para retirar únicamente un estado de smoke previamente registrado:

~~~powershell
.\scripts\Invoke-RutaFijaRealtimeProxySmoke.ps1 -Action Stop
~~~
