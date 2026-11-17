# Dashboard ADMIN: QA funcional, visual y de regresión — fase 5

Fecha: **26/10/2026**, America/Lima. Autorización: «OK VERDE» después de la [fase 4](dashboard-admin-fase-4-controles-2026-10-26.md). Dictamen: **QA_LOCAL_APROBADA_CON_OBSERVACIÓN_PREEXISTENTE_DEL_SHELL**. La aprobación corresponde al main ADMIN y las dependencias descritas en DA-01; no equivale a certificación integral de accesibilidad ni a revisión visual humana. **La fase 6 de entrega local y documentación final permanece pendiente.**

El candidato funciona en **http://127.0.0.1:4300/dashboard**, perfil ADMIN. Se mantienen las reglas, permisos, fórmulas y límites del [contrato DA-01](../contratos/DA-01-dashboard-admin-v0.1.0.md).

## Resultado de las verificaciones

| Área | Resultado de esta fase |
| --- | --- |
| Frontend completo | **338 pruebas aprobadas en 52 archivos**, 0 fallos |
| Backend, suite segura | **261 pruebas aprobadas en 51 clases**, sin conexión a base compartida |
| Integración de filtros/servicios y PostgreSQL | **6 pruebas aprobadas**, 0 fallos/errores/omitidas; clúster aislado en 55442 |
| Tipos y compilación Angular | `typecheck`, producción y revisión aislada: código 0; compilaciones frontend sin advertencias |
| Navegador con API nativa | **3 roles reales, 8 tamaños y 11 escenarios**, 0 excepciones JavaScript y 0 comandos de negocio inesperados |
| Accesibilidad automatizada | **138 nodos de texto**, contraste mínimo medido **5,21:1**, 0 incumplimientos en los nodos examinados, 0 controles interactivos sin nombre |
| Diálogo de exportación | Tab, Shift+Tab, Escape, foco visible y devolución del foco aprobados en escritorio, 390 px, 320 px y ampliación CSS al 150 % |
| Movimiento reducido | Preferencia emulada y ausencia de animaciones del Dashboard comprobadas |
| Visor aislado | Controles y geometría sintética aprobados; 0 solicitudes API, 0 WebSocket y 0 errores JavaScript |
| Integridad | **105 tablas protegidas idénticas**; snapshot L-00, términos, aprobación, contratos, shell y fuentes Java de producción conservados |

Las comprobaciones de accesibilidad se limitan a DOM, árbol accesible, contraste calculado y teclado automatizado. No se realizó una nueva prueba humana con lector de pantalla ni se declara conformidad completa WCAG.

## Hallazgos reproducidos y corregidos

| Hallazgo | Antes | Corrección y verificación |
| --- | --- | --- |
| QA-01 · Cambio automático de día/zona | Una respuesta del período anterior podía terminar después del cambio de contexto y aceptarse como vigente | La clave de período incluye día y zona; se cancelan ambas consultas anteriores. Dos regresiones fallaron antes y aprobaron después |
| QA-02 · Catálogo y detalle fuera de orden | Un catálogo en cola podía anunciar otra versión mientras seguía pendiente el detalle anterior | El catálogo nuevo cancela el detalle superado antes de cargar la versión correspondiente; regresión aprobada |
| QA-03 · Resumen rechazado | Si el resumen fallaba inicialmente, los gráficos dependientes podían quedar en carga sin que sus consultas llegaran a iniciarse | Se finaliza ese estado como «No disponible», sin ceros ficticios. Verificado con prueba de respuesta prohibida y navegador |
| QA-04 · Ciclo de foco del diálogo | La secuencia de Tab podía salir del contenido modal | Se acotan Tab y Shift+Tab a los controles visibles y habilitados; Escape y retorno del foco mantienen su comportamiento nativo |
| QA-05 · Texto de dato anterior | Un fallo ya terminado conservaba el rótulo «actualizando» | Se distingue actualización en vuelo de «actualización pendiente» |
| QA-06 · Hora de respaldo sin zona | Si faltaba el contexto del resumen, algunas horas se formateaban en UTC sin identificarlo | Se añade UTC explícito a esas horas y se evita duplicarlo en la etiqueta de corte. Regresión y captura de error verificadas |

Las tres regresiones de concurrencia registraron **3 fallos antes de corregir el producto**; las comprobaciones añadidas del resumen y UTC también fallaron antes. La suite final aprobó todos sus casos. Los registros anteriores se conservan como evidencia, sin borrar el diagnóstico.

Los cambios de producto se limitan a `admin-dashboard.facade.ts`, `admin-dashboard.adapter.ts`, `admin-dashboard.component.ts` y `dashboard-metric.component.ts`. Se amplió la suite de la facade y se añadió `DashboardQaIsolatedIT.java`. No se modificaron Java de producción, SQL, migraciones, guardas, autenticación, shell, Android, dependencias ni reglas de negocio.

## Roles, empresas y guardas

En el navegador se utilizó el login habitual con credenciales privadas existentes:

- **ADMIN:** accede al Dashboard y los cinco KPI coinciden con la API: **1 conductor disponible de 2, 2 vehículos disponibles de 2, 0 asignaciones futuras, 0 servicios canónicos en curso y 0 incidencias abiertas**.
- **SUPER_ADMIN:** login HTTP 200 y consulta exclusiva del resumen de plataforma. La navegación forzada a Conductores termina en `/forbidden` sin consultar su endpoint.
- **CONDUCTOR:** el intento de login en el CRM recibe **HTTP 403** y no carga ninguno de los dos Dashboard. No se alteró su acceso móvil ni su contraseña.

La prueba de renovación distingue dos situaciones: un endpoint que sigue devolviendo 401 después de una renovación correcta produce un fallo acotado de esa fuente, sin datos inventados ni bucle de reintentos; un 401 de la propia renovación exige iniciar sesión y elimina el Dashboard de la pantalla. Se ajustó la expectativa del ensayo a los interceptores existentes; **no se cambiaron sus reglas**.

Para comprobar aislamiento entre empresas se utilizó una copia privada de `ruta_fija_test` en **`ruta_fija_dashboard_qa@127.0.0.1:55442`**. La clase nueva exige exactamente ese destino y el ejecutor requiere cero pruebas omitidas. Se ejercitaron MockMvc con filtros y servicios reales y PostgreSQL real; el envío de correo fue un doble de prueba.

Los seis casos comprobaron dos ADMIN de organizaciones distintas, un parámetro de organización ajena ignorado por el alcance del actor, zonas de organización, denegaciones por términos/identidad, restricciones de rol, consultas sin auditoría de escritura y recuperación de emisiones con rechazo de lectura por otro ADMIN. Las fixtures y operaciones de ese ensayo se revirtieron por transacción.

El primer diseño de la fixture agotaba correctamente la cuota histórica de ADMIN. Se ajustó para crear solo una cuenta transaccional y reutilizar una cuenta de la **copia**; no se elevó la cuota, no se limpió el ledger y no se cambiaron funciones o triggers. Al terminar, las **105 tablas de la copia coincidían nuevamente con su origen** y se detuvo únicamente ese clúster. Los archivos privados se conservaron; PostgreSQL compartido permaneció activo.

## Fallos parciales, cortes y efectos

La revisión en Edge incluyó la respuesta normal, dato anterior tras 503, indisponibilidad de disponibilidad, resumen 403, servicios 400, incidencias 503, conductores 503, avisos 403, catálogo 503, resumen 401 con renovación válida y renovación 401. Los fallos se inyectaron en el transporte de ese navegador; no se modificaron datos ni se provocó una caída real del servidor.

Se comprobaron fuentes independientes: un listado de conductores fallido no sustituye el denominador de disponibilidad; servicios o incidencias fallidos no se dibujan como una dona válida de cero; avisos rechazados no producen un estado favorable; un resumen prohibido deshabilita la fecha y no consulta los reportes diarios ni recurre a endpoints de plataforma. Las consultas mantienen sus cortes propios y los datos anteriores se identifican como tales.

El escenario HTTP 400 de servicios verifica la presentación ante una fuente rechazada; no se atribuye un nuevo ensayo de volumen con más de 10.000 filas. La lógica de emisión y permisos también se ejercitó en la copia de PostgreSQL, sin crear reportes en las bases compartidas.

Las **106 tablas nativas** se compararon antes/después. Los únicos cambios de huellas fueron `app_user`, `audit_event`, `refresh_token` y `driver`: sesiones ordinarias y mantenimiento de vigencias existente. Las tablas de reportes, aceptaciones y restantes datos de negocio conservaron sus huellas. **No se creó una tercera emisión**: las versiones locales 1 y 2 de fase 4 permanecen intactas.

## Revisión visual y teclado

Se registraron ventanas de **1440, 1280, 1201, 1106, 1024, 768, 390 y 320 px**, además de ampliación CSS al 150 %. Las columnas se contrastaron con el ancho útil del contenedor. En la ventana de referencia de 1106 px se conservaron cinco KPI y tres columnas de paneles; las ventanas menores reordenaron los bloques sin ocultar información.

La comparación con la referencia verifica la composición aprobada: cabecera, tarjetas, seis paneles, tres donas, colores y jerarquía. Las diferencias acordadas en DA-01 permanecen: prioridad de seguridad antes de los KPI, texto legible, categorías completas, altura adaptable, códigos de conductor, cifras reales y esquema de rutas sin seguimiento personal. **No se declara igualdad píxel a píxel** ni se fuerzan alturas, escalado o recortes para aparentarla.

El Dashboard no presentó desbordamiento horizontal en los tamaños probados. Se reprodujo la observación previa: **la cabecera general del CRM desborda a 320 px**. Sus fuentes conservaron el hash inicial, por lo que no constituye una regresión introducida por este Dashboard. La aprobación local no afirma que toda la página esté libre de ese problema.

El catálogo real sigue sin rutas registradas. La geometría y los controles de ampliación se comprobaron en la revisión aislada de loopback **4312**, con datos identificados como sintéticos y conexión API bloqueada. Esa evidencia no se presenta como una ruta operativa real.

## Comandos, registros e integridad

Comandos npm desde `frontend/`; restantes comandos desde la raíz:

| Comando | Resultado |
| --- | --- |
| `npm.cmd run typecheck` | Código 0 |
| `npm.cmd run test:ci` | 338 aprobadas |
| `npm.cmd run build` | Código 0, sin advertencias |
| `npm.cmd run build -- --configuration=dashboard-preview` | Código 0, sin advertencias |
| `./scripts/Test-RutaFijaBackendUnitSafe.ps1 -Execute` | 261 aprobadas; [evidencia fresca](../evidencia/auditoria-ide-2026-10-23/backend/run-b9e7cb4c-65b1-4b51-8093-53a0805c94ee/result.json) |
| `python .runtime/dashboard-admin-fase-5-20261026/run-isolated-it.py` | 6 aprobadas, ejecutadas con el clúster separado preparado; quedó detenido al cierre |
| `node .runtime/dashboard-admin-fase-5-20261026/check-browser.mjs` | Tres roles, once escenarios, ocho tamaños: PASS |
| `node .runtime/dashboard-admin-fase-5-20261026/check-route-preview.mjs` | Visor y presentación aislados: PASS |
| `./scripts/Invoke-RutaFijaNativeRuntime.ps1 -Action Test` | Backend y frontend UP, 8080/4300 |
| `./scripts/Test-RutaFijaLocalSecretPolicy.ps1 -Action Check -Execute` | Exclusión privada y ACL aprobadas |

Al iniciar la comprobación visual no existía runtime nativo activo: API, CRM y maqueta estaban detenidos. Se conservaron los errores de navegación previos y se inició el entorno con `iniciar_ruta_fija.bat`; la compuerta aprobó el esquema vigente y se reutilizó el JAR, sin migraciones. El JAR mantiene su SHA-256 de fase 3/4. La preparación del clúster privado requirió corregir el manejo de salida de un proceso en Windows; no se detuvieron procesos ajenos ni se modificaron las reglas del proyecto para ello.

Antes de editar se respaldaron **866 archivos**. Los helpers, volcados, respuestas completas, perfiles de navegador y credenciales del clúster aislado están exclusivamente en `backend/.local/accesos/dashboard-admin-fase-5-20261026/` o `.runtime/`, excluidos del repositorio. Las 105 tablas protegidas, los inmutables L-00/legales y las 51 migraciones nativas correctas se verificaron nuevamente. No hubo limpieza global, publicación de secretos, commits ni push.

## Evidencias y salida

- [Resumen verificable de QA](../evidencia/dashboard-admin-fase-5-2026-10-26/validation.json).
- [Navegador: datos, roles, fallos y tamaños](../evidencia/dashboard-admin-fase-5-2026-10-26/browser-validation.json).
- [Contraste, árbol accesible y movimiento reducido](../evidencia/dashboard-admin-fase-5-2026-10-26/accessibility-validation.json).
- [Teclado y foco de diálogos](../evidencia/dashboard-admin-fase-5-2026-10-26/dialog-keyboard-validation.json).
- [Seis integraciones aisladas](../evidencia/dashboard-admin-fase-5-2026-10-26/isolated-it-result.json) y [rollback/cierre](../evidencia/dashboard-admin-fase-5-2026-10-26/isolated-close-result.json).
- [Escritorio de referencia](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-admin-1106.png), [390 px](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-admin-390.png), [320 px](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-admin-320.png) y [150 %](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-admin-zoom150.png).
- [Fallo parcial sin ceros ficticios](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-error-availability.png), [resumen prohibido y UTC explícito](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-error-summary.png) y [dato anterior](../evidencia/dashboard-admin-fase-5-2026-10-26/qa-error-stale.png).
- [Visor aislado](../evidencia/dashboard-admin-fase-5-2026-10-26/visor-aislado/visor-controles.png) y [manifiesto SHA-256](../evidencia/dashboard-admin-fase-5-2026-10-26/manifest.json).

**Fase 5 cerrada en su alcance local.** Continúa la fase 6 para manual, comparación de entrega, manifiesto definitivo y reversión. Esta acta no adelanta esa entrega ni modifica el cierre histórico de L-08 o las condiciones del piloto real.
