# F1.1B — Arranque nativo controlado

F1.1B permite ejecutar Ruta Fija localmente con PostgreSQL 16 instalado en
Windows, Spring Boot y Angular, sin Docker ni Compose. La fase presupone las
puertas verdes F1.2 y F1.3.

## Inicio y cierre

Desde la raíz del repositorio:

~~~text
.\iniciar_ruta_fija.bat
~~~

El script valida primero F1.3, empaqueta el backend y luego inicia procesos
locales separados para API y CRM. Cuando finaliza correctamente, los accesos
son:

- CRM: http://localhost:4200
- Health de API: http://127.0.0.1:8080/actuator/health
- OpenAPI: http://127.0.0.1:8080/v3/api-docs

Para comprobar un runtime que ya está iniciado:

~~~text
.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test
~~~

Para detener solamente los procesos que inició Ruta Fija:

~~~text
.\finalizar_ruta_fija.bat
~~~

El cierre libera los puertos 4200 y 8080; PostgreSQL 16 continúa como servicio
local de Windows. Esto permite ahorrar recursos cuando no se está demostrando
el CRM.

## Controles aplicados

- La configuración se lee de backend/.local/ruta-fija-native.env, que continúa
  ignorado por Git.
- Antes de iniciar, se ejecuta la auditoría F1.3 y se exige PostgreSQL local,
  Flyway V1-V5, semillas desactivadas y roles separados.
- El backend se ejecuta con el JDK real, no con el lanzador del PATH, para que
  su PID sea verificable.
- El CRM se ejecuta con el proceso Node de Angular y escucha solo en
  localhost:4200.
- Se registran PIDs, puertos y marcadores de comando en .runtime/, carpeta
  local ignorada por Git. No se almacenan secretos allí.
- Si 8080 o 4200 pertenecen a un proceso ajeno, el inicio se cancela. El cierre
  tampoco detiene procesos no registrados o cuya identidad no coincida.
- API queda enlazada a 127.0.0.1:8080 y el proxy HTTP de Angular apunta a esa
  misma dirección local.

Una ejecución aprobada imprime:

~~~text
F1_1B_RUNTIME=PASS backend=UP frontend=UP ports=8080,4200
F1_1B_START=PASS procesos=controlados docker=0
~~~

## Límites intencionales

- Las semillas permanecen desactivadas; por ello F1.1B demuestra disponibilidad
  de la plataforma, no cuentas de demostración ni flujos autenticados.
- Las pruebas de integración sin Testcontainers pertenecen a F1.4.
- El proxy WebSocket /ws y su prueba de tiempo real pertenecen a F1.5.
- La actualización integral de README y manuales heredados pertenece a F1.6.
- Los archivos históricos de Docker se preservan hasta contar con evidencia de
  recuperación posterior; no son necesarios para iniciar ni cerrar F1.1B.

Tras cerrar el runtime puede repetirse el inicio. Si ya está activo y sano, una
nueva orden de inicio es idempotente y no crea procesos duplicados.
