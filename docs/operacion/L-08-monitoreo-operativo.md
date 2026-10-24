# L-08: monitoreo operativo y escalamiento

2026-10-23 America/Lima. CI-L08-04 y control de RPO CI-L08-06. Dueño técnico: A-OPS. Candidato local S/ 0; encendido real condicionado a un equipo de monitor separado, canales probados y una guardia nominal aceptada.

## Matriz de sondas

| Sonda | Criterio | Cadencia / timeout | Incidente |
|---|---|---|---|
| API Spring Actuator `/actuator/health/readiness` | HTTP200, JSON `status=UP`; incluye `readinessState,db` según `application.yml` | 60 s / 5 s | No disponible, timeout, JSON inválido, redirect o estado distinto |
| Angular, raíz CRM | HTTP200 y marcador `<app-root` del documento | 60 s / 5 s | Respuesta ausente/incorrecta; no acredita por sí sola que JS arrancó |
| Proxy CRM → API | URL de health expresamente publicada por proxy, HTTP200 `UP` | 60 s / 5 s | Caída/ruta errónea/API no enrutable; configurar ruta real, no asumir que proxy de desarrollo la publica |
| PostgreSQL | `pg_isready`, host127.0.0.1 y puerto elegido, código0 | 60 s / 5 s | No acepta conexiones; no acredita consultas autenticadas ni integridad de filas |
| Respaldo | último snapshot cifrado/copiadο/autenticado fuera del servidor con edad≤3600 s | 60 s | Medio ausente, respaldo antiguo/hash/tag inválido o verificación diaria no realizada |

No publicar detalles internos de Actuator. El proyecto expone health/info y oculta componentes/detalles; SMTP no integra la sonda para impedir correo externo accidental. Un monitor independiente requiere una URL alcanzable por su equipo, sin credenciales/query en URLs ni cambios de firewall amplios. PostgreSQL mantiene su límite de red: no abrirlo públicamente para monitorizarlo; una sonda local entrega estado mínimo al monitor externo a través de un canal autenticado definido antes del piloto.

## Implementación local

[Watch-RutaFijaL08Health.ps1](../../scripts/Watch-RutaFijaL08Health.ps1) deja Plan por defecto. `Probe -Execute` toma una muestra; `Watch -Execute -Cycles N` ejecuta una serie finita. Los timeouts son acotados, los redirects no se aceptan y cada ciclo se registra inmediatamente en `EvidenceFile.events.jsonl`, además del resumen final creado una única vez. Un fallo genera `INCIDENT_REQUIRES_HUMAN_ACK`. Esta implementación conserva un incidente **local**: no envía correo/SMS ni demuestra entrega externa o lectura humana.

```powershell
./scripts/Watch-RutaFijaL08Health.ps1
./scripts/Watch-RutaFijaL08Health.ps1 -Action Watch -Execute -Cycles 60 `
  -ApiHealthUrl 'http://127.0.0.1:8080/actuator/health/readiness' `
  -AngularUrl 'http://127.0.0.1:4300/' `
  -ProxyHealthUrl 'http://127.0.0.1:4300/actuator/health/readiness' `
  -EvidenceFile 'D:/evidencias/monitor-UTC-unico.json'
```

El ejemplo del proxy se sustituye por la ruta efectivamente configurada y probada. No modifica `frontend/proxy.conf.json`, arranca servicios ni instala tareas. La cota conservadora cuenta el resto del ciclo en que ocurre el fallo y todo el ciclo siguiente: cadencia60 s + 8 sondas×5 s + margen5 s = **cota de diseño105 s**, menor a300 s; se comprueba antes de ejecutar. En Windows, resolución DNS puede exceder el timeout de una petición HTTP: esta cota exige resolución previamente acotada/verificada; el ensayo usa IP loopback y no acredita DNS de producción. Es una cota bajo un proceso y host operativos: el mismo equipo no puede detectar su propia pérdida total. La prueba de este incremento utiliza HTTP mínimo sintético para el contrato y PostgreSQL16 real aislado; separa claramente esa procedencia de una caída real del stack de aplicación.

## Guardia y escalamiento 24/7 exigidos para encendido real

La siguiente matriz es vinculante como condición de transición, no una afirmación de disponibilidad humana ya existente:

| Momento desde la detección | Acción | Responsable que debe quedar nombrado antes del piloto |
|---|---|---|
| Inmediato | Crear incidente con UTC, sonda, alcance, último backup íntegro y RPO observado; avisar por el canal gratuito previamente probado | Monitor en host independiente y operador de guardia |
| ≤5 min | Acusar recibo y clasificar disponibilidad, datos/privacidad o dispositivo | Operador primario nominal |
| 5 min sin acuse | Escalar al suplente y al titular; registrar entrega y recepción | Suplente nominal y titular SUPER_ADMIN |
| ≤15 min | Aislar componente y escoger retorno/restauración; suspender confirmaciones nuevas si no existe API disponible | Responsable de operaciones autorizado |
| ≤30 min | Objetivo de recuperación ordinaria verificada desde un cliente; si no, declarar contingencia mayor y activar recuperación | Operaciones y responsable técnico |
| ≤4 h desde incidente mayor | Objetivo RTO hasta operación recuperada; conservar el mismo reloj en fallos/reintentos | Operaciones, titular y verificador de calidad |

La guardia debe cubrir los siete días y todas las horas con sustituto, contacto, aceptación personal y prueba fuera del horario habitual. Antes del encendido, ejecutar caída controlada desde el host secundario y conservar emisión, entrega, acuse, escalamiento y recuperación. Se admiten herramientas gratuitas ya disponibles y autorizadas; no se crean cuentas/servicios de pago ni se envían mensajes externos durante esta validación. Un documento o tarea programada no acredita guardia24/7.

## Salida local y pendientes reales

La sonda y su detección de pérdida controlada se prueban con [Test-RutaFijaL08Continuity.ps1](../../scripts/Test-RutaFijaL08Continuity.ps1); resultados nuevos y fallos conservados en [incremento-5/ops](../evidencia/L-08/incremento-5-candidato-final/ops/README.md). Los resultados históricos de detección2,804 s/recuperación13,024 s permanecen históricos. Para el candidato local se verifica el mecanismo y el procedimiento. Siguen pendientes, antes del piloto, host independiente, rutas de operación reales, canales entregados/probados, nombres y aceptación de la guardia, ejecución continua y recuperación ordinaria del stack real. No afirmar esos recursos por la autorización general de pruebas.
