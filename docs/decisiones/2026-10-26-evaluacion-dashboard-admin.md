# Evolución de Resumen a Dashboard ADMIN: evaluación y fases

Fecha: **26/10/2026**, America/Lima. Estado: **evaluación terminada; implementación propuesta, no ejecutada**. Corte de fuentes inspeccionado: `fa9122b`.

## 1. Dictamen y alcance

La evolución es viable sobre el módulo existente: `/dashboard` ya presenta los cinco indicadores principales y consume servicios con aislamiento por organización. La propuesta transforma exclusivamente el contenido principal del **ADMIN**, con una composición próxima a la imagen suministrada, gráficos reales, estados claros y enlaces a los módulos actuales.

La similitud visual puede ser alta, pero una réplica literal de todos los textos y comportamientos de la imagen contradiría algunos contratos vigentes: seguimiento GPS del ADMIN, exposición de nombres, orden de atención a la seguridad, categorías de flota y porcentaje sin denominador. Se documentan las adaptaciones antes de implementar.

Se preservan el encabezado y la navegación global del CRM, la ruta `/dashboard`, las guardas de autenticación/términos/rol, el resumen de SUPER_ADMIN y la app Android. No se propone modificar las migraciones V1..V51, credenciales, habilitaciones ni transiciones operativas. No se necesitan nuevas tablas para la evolución inicial. Cualquier necesidad posterior de agregación se evalúa como consulta de solo lectura con contrato explícito.

La inspección fue estática. No se arrancaron servicios, no se consultó PostgreSQL ni se ejecutaron pruebas de producto en esta evaluación. Los conteos de pruebas de entregas anteriores no se presentan como validación de un Dashboard que todavía no se ha construido.

## 2. Lectura visual de la referencia

La referencia visible tiene **1106 × 628 píxeles**. Las coordenadas siguientes son aproximaciones sobre esa imagen rasterizada; no son mediciones instrumentales del archivo original ni una identificación certificada de su fuente tipográfica. Permiten fijar proporciones y luego contrastarlas mediante capturas del mismo tamaño.

| Zona | Geometría aproximada | Elementos que se deben reproducir |
| --- | --- | --- |
| Cabecera interna | y=15–66; saludo desde x≈5 | Fecha pequeña, título «Hola, Administrador», descripción con organización; a la derecha sesión, exportación y fecha |
| Cinco KPI | y≈87–154; alto ≈67 px; anchos ≈207 px | Tarjetas independientes; icono circular pastel a la izquierda, etiqueta, cifra grande, dato secundario y chevrón |
| Primera fila | y≈173–365; alto ≈192 px | Tres tarjetas de ≈348 px, separadas ≈18 px: flota, operación y mapa |
| Segunda fila | y≈383–572; alto ≈189 px | Incidencias, conductores, alertas; alineadas con la fila anterior |
| Gráficos circulares | Diámetro ≈104 px; grosor ≈12 px | Anillo, cifra central, descriptor y leyenda lateral con color, cantidad y porcentaje |
| Tarjeta de mapa | Interior ≈319 × 103 px | Fondo claro con retícula, trazado azul, puntos, controles y metadatos de actualización |
| Conductores | Dos filas visibles, alto ≈38 px | Indicador de estado, código, etiqueta secundaria, estado y acción de detalle |
| Alertas vacías | Recuadro interior ≈319 × 125 px | Icono de confirmación, mensaje principal y texto de apoyo |
| Pie | y≈591–625 | Texto discreto de producto y enlaces secundarios válidos |

La estructura dominante es **5 KPI + 6 paneles**, con dos filas de tres paneles y **tres gráficos de dona**. No se observan gráficos de barras ni series temporales que deban añadirse para cumplir esta imagen.

El fondo es gris azulado muy claro; las tarjetas son blancas, con borde tenue y radios aproximadamente de 12–14 px. El azul identifica enlaces y trazado; verde disponibilidad/normalidad; ámbar pendientes; rojo/rosa incidencias; violeta servicios. Las sombras son mínimas. Como punto de partida visual, se proponen tokens locales cercanos a fondo `#f3f5f9`, superficie `#ffffff`, azul `#3b82f6`, verde `#10b981` y borde `#e5eaf2`; son una propuesta de reproducción, no colores extraídos píxel a píxel.

La imagen usa textos muy pequeños. La implementación debe conservar su jerarquía y densidad mediante una adaptación legible, con foco visible, leyendas textuales, objetivos táctiles y ampliación del texto. No se reducirá la escala de todo el módulo para forzar su encaje. La captura de comparación se recortará al contenido principal; el encabezado global del sistema seguirá visible fuera de ese recorte.

## 3. Estado actual comprobado

| Elemento inspeccionado | Situación real | Consecuencia para el diseño |
| --- | --- | --- |
| `DashboardPage` | Compartido por ADMIN y SUPER_ADMIN, con ramas separadas | La nueva vista se aislará en un componente ADMIN y conservará la rama de plataforma |
| Plantilla actual | Prioridades, cinco indicadores, actividad reciente y accesos rápidos | Los KPI se reutilizan; prioridades y accesos no deben desaparecer al reorganizar |
| Estilo | Retícula simple, bordes amarillos y radio global de 2 px | Los radios, fondos y acentos de la referencia se aplicarán solo dentro del nuevo Dashboard |
| API de resumen | Contadores de prioridad, servicios canónicos en curso y recursos activos | Es una base útil; no contiene todas las series, listas y datos que muestra la imagen |
| Reporte de disponibilidad | Recuentos por estado de conductor/vehículo y tres contadores operativos | Permite flota y KPI, pero sus estados registrados no acreditan por sí solos habilitación operativa |
| Reportes de servicios | Cohorte, corte, zona horaria, cierre válido, totales y emisiones exportables | Fuente adecuada para operación y cumplimiento diario, sin confundir ofertas con servicios |
| Incidencias | Totales por estado en reportes y listado filtrable | Permite la dona con los cuatro estados reales y un período explícito |
| Conductores | Listado paginado; proyección ordinaria por códigos, sin PII | La lista visual mostrará códigos reales y estado; no los nombres de muestra de la imagen |
| Avisos | Bandeja personal del ADMIN y consulta separada de fallos de entrega | No existe un único «estado global de salud» deducible de una página de avisos |
| Tiempo real | WebSocket con ticket y reconexión; invalida y recarga consultas | Se reutilizará sin añadir sockets por tarjeta; falta exponer el estado de conexión a la vista |
| GPS | Captura consentida y acceso restringido; no hay consulta global de posiciones para ADMIN | «Mapa en vivo» con vehículos requiere otra evolución contractual, no solo CSS |
| Dependencias | Angular 22.2.3; sin librería dedicada de gráficos o mapas en `package.json` | Las tres donas pueden resolverse con SVG local, sin servicios pagados ni nuevas dependencias pesadas |

Se identificaron además dos puntos que deben resolverse en el contrato de datos: el contador antiguo de servicios en curso en disponibilidad cuenta ofertas `assignment.EN_SERVICIO`, mientras el resumen nuevo cuenta servicios canónicos `scheduled_service.IN_SERVICE`; y las consultas de disponibilidad agrupan estados sin filtrar necesariamente por `active`. La nueva interfaz no debe mezclar sus universos ni reinterpretar «disponible» como «apto para conducir».

## 4. Correspondencia de cada bloque con datos reales

| Bloque de la imagen | Fuente disponible | Decisión de implementación propuesta |
| --- | --- | --- |
| Conductores disponibles | `/reports/availability`, grupo `DISPONIBLE` | Mantener la definición de disponibilidad registrada. Total de registrados del mismo universo; no sustituirlo por `activeDrivers` |
| Vehículos disponibles | Mismo reporte, grupo `DISPONIBLE` | Mismo criterio. Habilitación y autorización siguen en el servidor |
| Asignaciones futuras | `futureScheduledAssignments` | Mantener el significado de ofertas `SCHEDULED` posteriores al corte; no llamarlas total de servicios del día |
| Servicios en curso | `/dashboard/admin`, `servicesInProgress` | Fuente canónica para servicios; documentar la diferencia con el contador legado de ofertas |
| Incidencias abiertas | Resumen o disponibilidad, estados `OPEN` + `FOLLOW_UP` | Un solo origen declarado, con corte visible; no duplicar el mismo pendiente como dos alertas distintas |
| Estado de la flota | Distribución de vehículos de disponibilidad | Partición completa y mutuamente excluyente de estados registrados |
| Resumen de operación | `/reports/services?from=...&to=...` | Porcentaje y cohorte canónicos. Dona de completados frente a no completados; desglose completo en leyenda/detalle |
| Incidencias por estado | `/reports/incidents?from=...&to=...` | `OPEN`, `FOLLOW_UP`, `RESOLVED`, `CLOSED_BY_DRIVER`, con etiquetas fieles |
| Conductores | `/drivers`, paginación y orden explícitos | Vista corta por códigos con «Ver conductores». El menú solo enlaza a acciones ya autorizadas |
| Alertas y notificaciones | Prioridades + `/notices`, con sus alcances separados | Presentar prioridad operativa y avisos personales como conceptos distintos. Consultar no confirma lectura |
| Fecha | Zona organizacional del reporte y corte del servidor | Fecha real; ninguna fecha, nombre de empresa o cifra de la imagen queda hardcodeada |
| Exportar resumen | Emisiones de reportes canónicos y formatos existentes | Definir alcance. Reutilizar emisión/versión/exportación explícitas, no un archivo que aparente cubrir datos que no contiene |
| Mapa | Rutas/paradas versionadas disponibles en `/routes` | Propuesta compatible: esquema de rutas aprobadas, claramente etiquetado, sin puntos personales ni vehículos ficticios |

### Definiciones que evitan gráficos engañosos

- **Flota:** incluir `DISPONIBLE`, `EN_SERVICIO`, `ATENCION`, `MANTENIMIENTO` e `INACTIVO`. La imagen solo muestra cuatro categorías; ocultar `ATENCION` perdería información de seguridad. Cada vehículo aporta a una sola categoría del gráfico y el centro muestra el total del mismo universo.
- **Operación:** usar el porcentaje emitido por el reporte canónico. Con cero programados, `completionPercent` es nulo: mostrar «Sin servicios programados», no un cumplimiento del 0 %. Cancelaciones, interrupciones y relevos siguen visibles y no se retiran del denominador para mejorar el indicador. No sumar subtotales solapados.
- **Incidencias:** la cohorte del gráfico se define por fecha de reporte; «abiertas ahora» puede incluir incidencias anteriores al día seleccionado. Esa diferencia debe verse en las etiquetas y explicaciones. `CLOSED_BY_DRIVER` conserva el significado de cierre por conductor, sin presentarlo como resolución del ADMIN.
- **Porcentajes:** derivar del denominador explícito del panel. Si hay redondeo, conservar los valores exactos en el dato textual y evitar aparentar un total exacto incorrecto. Las leyendas son comprensibles sin depender del color.
- **Fecha:** el selector afecta al reporte diario y al gráfico de incidencias. Disponibilidad, prioridades y avisos siguen identificados como «Ahora»; no hay snapshots históricos de disponibilidad acreditados para reconstruir cualquier fecha pasada. Mantener también la ventana informativa de próximas 24 h de prioridades.
- **Cortes:** las API actuales devuelven cortes independientes. Mostrar actualización por panel o formalizar una agregación coherente; no afirmar que varias respuestas constituyen automáticamente un mismo snapshot.
- **Error:** un 403, timeout, desconexión o error parcial se presenta como «No disponible» o dato desactualizado. Solo una consulta correcta acredita cero o «Sin pendientes». Una página vacía de avisos no demuestra que todas las colas de seguridad estén vacías.

## 5. Adaptaciones necesarias respecto a la imagen

**Prioridad operativa.** El contrato L-07 y una prueba existente colocan la atención de seguridad antes del estado operativo. Se propone una franja compacta de prioridades sobre los indicadores cuando existan pendientes, con el orden aprobado y acceso al detalle completo. En el escenario vacío puede permanecer compactada. Trasladar todo al panel inferior de alertas eliminaría una garantía actual.

**Mapa.** Para esta evolución, la opción compatible es una tarjeta «Mapa de rutas» o «Esquema de ruta», con trazados registrados, enlaces a rutas y un estado vacío real cuando no haya geometría. No se dibujarán vehículos, trayectorias personales, nombres de barrios inventados ni coordenadas 0,0 para simular cobertura. Si no existe una vista ampliada de mapa, el enlace abrirá un visor local del Dashboard o el módulo de rutas; no apuntará a una ruta inexistente. Un mapa geográfico con cartografía externa requeriría evaluar proveedor, licencia, disponibilidad, privacidad y costo por separado.

El seguimiento **«en vivo» de conductores por ADMIN no forma parte del permiso actual**. Requiere definir propósito, acceso, consentimiento, visibilidad, frescura, retención y contrato específicos antes de implementarlo. Activar captura GPS, usar el flujo de acceso propio del conductor como atajo o exponer coordenadas desde el resumen violaría el alcance que se debe conservar.

**Identidad.** Conservar códigos como `CONDUCTOR-…`; no reintroducir nombres, documentos, teléfonos o datos de expediente porque aparezcan ejemplos nominales en la imagen. Para abreviar códigos se necesita una referencia inequívoca con acceso al código completo, no índices de fila que cambien al ordenar.

**Exportación.** La exportación de servicios ya existe y conserva emisión, versión, corte y huella. Se propone que el control del Dashboard abra un diálogo que declare «Reporte de servicios del período», reutilice ese flujo y emita solo por acción expresa. Exportar las seis tarjetas completas con disponibilidad, avisos y rutas sería un nuevo producto de reporte que debe definirse aparte; no se presentará un PDF de servicios como si exportara todo el Dashboard.

**Lectura y acciones.** La vista y sus refrescos no aceptan términos, marcan avisos como leídos, activan conductores, cambian disponibilidad ni aceptan servicios. Cualquier acción de escritura conserva la confirmación y el flujo del módulo correspondiente.

## 6. Solución técnica propuesta

Mantener `DashboardPage` como entrada por rol y extraer su contenido ADMIN a una vista standalone dedicada. Aislar bajo `features/dashboard/` la composición, las tarjetas y una dona SVG reutilizable con leyenda textual. Reutilizar Signals, OnPush, RouterLink, servicios HTTP, interceptores y control de errores actuales.

Usar un adaptador/facade de Dashboard que transforme los DTO existentes a modelos de presentación, sin inventar nuevas reglas de habilitación. Separa carga, resultado válido vacío, error y último resultado conocido por panel. Controla la secuencia de consultas al cambiar fecha, cancela suscripciones al salir y evita que una respuesta antigua sustituya el período más reciente.

Los estilos locales usan tokens propios de superficie, radio, separación y color; no modifican `:root`, el componente shell ni los estilos de SUPER_ADMIN. La retícula de escritorio conserva cinco KPI y tres columnas cuando el **ancho útil del contenido** lo permita; en tamaños intermedios pasa a dos columnas y en móviles a una. Los breakpoints se ajustan mediante la maqueta, sin comprimir texto ni ocultar botones.

La actualización reutiliza un solo canal operativo y agrupa ráfagas de invalidación para no disparar una consulta por cada tarjeta y evento. Una etiqueta «Sesión activa» describe autenticación; la conexión WebSocket y la frescura de los datos se presentan por separado. Si se define sondeo de respaldo, tendrá intervalo limitado, pausa con la pestaña oculta y cancelación al desmontar; no se establece como requisito sin medir primero la necesidad.

La primera opción reutiliza endpoints actuales. Si descargar reportes completos o efectuar múltiples consultas produce una carga innecesaria, se propondrá una ampliación **aditiva y de solo lectura** del contrato de Dashboard para agregados mínimos. Conservará autorización ADMIN, empresa obtenida del actor, `no-store` y ausencia de PII/GPS. No cambia el significado de campos existentes, ni agrega acceso operacional a SUPER_ADMIN, ni exige una migración por motivos puramente visuales.

Los enlaces secundarios del pie solo usarán destinos reales. «Soporte», «Términos» o «Privacidad» no se crearán como enlaces vacíos ni se asociarán a documentos distintos por su apariencia.

## 7. Fases secuenciales de ejecución

| Fase | Entregable | Condición para avanzar |
| --- | --- | --- |
| **0. Evaluación y límites — completada** | Este análisis, inventario de fuentes y diferencias respecto a la imagen | Referencia visual descompuesta; brechas identificadas sin modificar código |
| **1. Contrato visual y de indicadores** | Especificación de cada KPI, denominadores, períodos, estados, cortes, mapa, prioridades y exportación; contrato aditivo solo si hace falta | Todos los datos tienen fuente, etiqueta, universo y estado vacío/error; decisiones del mapa y exportación explícitas |
| **2. Maqueta aislada del ADMIN** | Nueva composición local con cinco tarjetas, seis paneles, tres donas y franja condicional de prioridades, usando fixtures exclusivamente de prueba | Comparación del recorte principal con la referencia; shell y SUPER_ADMIN intactos; lectura y navegación accesibles |
| **3. Conexión a datos reales** | Facade, consultas existentes, gráfico de flota, operación canónica, incidencias, conductores y avisos con límites de paginación | Cifras contrastadas contra sus respuestas API; cero/ausencia/error diferenciados; sin cambios de permisos o estados |
| **4. Controles y actualización** | Selector de período, enlaces útiles, visor de ruta compatible, exportación explícita y refresco por eventos | No hay acciones ficticias ni lecturas confirmadas al consultar; fechas y cortes coherentes; sin ráfagas de peticiones |
| **5. QA funcional, visual y de regresión** | Pruebas de Dashboard/roles/rutas, captura visual por tamaño y evidencia de errores parciales | Casos críticos aprobados; igualdad de datos verificada; sin regresiones del shell, SUPER_ADMIN y guardas |
| **6. Entrega local y documentación** | Manual actualizado, capturas antes/después, comandos/resultados, manifiesto SHA-256 y procedimiento de reversión | Cambios acotados al Dashboard y dependencias expresamente justificadas; candidato revisable y reversión preparada |

Cada fase se revisa antes de avanzar a la siguiente; no se ejecutarán todas simultáneamente. Un diseño parecido no acredita la corrección de los indicadores, y una prueba unitaria aprobada no acredita por sí sola la fidelidad visual en navegador.

### Archivos previstos, sujetos a la fase 1

- `frontend/src/app/features/dashboard/dashboard.page.{ts,html,scss}` y su suite: integración de la nueva rama ADMIN conservando la de SUPER_ADMIN.
- Archivos nuevos dentro de `features/dashboard/` para composición ADMIN, tarjetas, dona, controles y facade/modelos de presentación.
- `frontend/src/app/core/dashboard/dashboard-api.service.ts` solo si se acuerda un DTO aditivo; reutilizar servicios de reportes, operaciones, rutas, conductores y avisos.
- Backend de `dashboard/` únicamente si hace falta agregación de lectura. Pruebas de aislamiento por organización y permisos si se cambia un endpoint.
- Documentación de operación y evidencia nueva. Las actas y evidencias selladas anteriores se conservan.

No se prevén cambios en `mobile/`, credenciales, términos publicados, reglas de habilitación, launchers ni migraciones por esta composición visual. Una necesidad que exceda esos límites se declara antes de incorporarla al alcance.

## 8. Matriz mínima de aceptación y QA

1. **Composición:** cinco KPI y seis paneles en el escritorio de referencia; tres donas con centro, leyenda, valores y enlaces. Foco y textos no quedan recortados.
2. **Rol y empresa:** ADMIN A no consulta ni ve recursos de B. SUPER_ADMIN conserva su resumen y no solicita endpoints operativos. CONDUCTOR no obtiene acceso al CRM por esta vista. Se mantienen guardas de sesión y términos.
3. **Cómputos:** probar flota con los cinco estados, oferta múltiple de un servicio, cancelación, interrupción, relevo, incidencia antigua aún abierta y estados no reconocidos. No mejorar porcentajes eliminando casos desfavorables.
4. **Estados vacíos:** sin vehículos, sin programados, sin incidencias, sin conductores, sin geometría y sin avisos; dona neutra y texto fiel. Porcentaje nulo no se convierte en cero.
5. **Fallos:** error individual de cada API, 401/403, pérdida/reconexión WebSocket y dato anterior visible como desactualizado. Nunca mostrar «Todo en orden» por una consulta fallida.
6. **Fecha y concurrencia:** límites del día en zona organizacional, período permitido por el reporte, respuestas fuera de orden y cambio de fecha durante una recarga. No presentar disponibilidad actual como histórica.
7. **Privacidad y efectos:** ningún nombre real/DNI/contacto ni punto GPS personal en el Dashboard o exportación. Abrir/refrescar no confirma avisos ni cambia estados del sistema.
8. **Exportación:** contenido y corte de la emisión seleccionada, formato, nombre del archivo, operación repetida e incertidumbre de respuesta; no generar una emisión durante la carga automática.
9. **Visual/responsivo:** comparar el recorte principal a 1106 × 628 y revisar anchos útiles de escritorio amplio, portátil, tableta y 390/320 px. Revisar también zoom/texto ampliado al 150 %, teclado, foco, contraste y movimiento reducido. Los gráficos tienen alternativa textual.
10. **Carga:** no hay llamadas por cada fila de conductor, conexiones duplicadas, descargas masivas para mostrar un total o refrescos sin cancelación al salir. El presupuesto de consultas se documenta tras medir la línea base.

Comandos previstos para la implementación, desde `frontend/`, no ejecutados durante esta evaluación:

```powershell
npm.cmd run typecheck
npm.cmd run test:ci -- --include=src/app/features/dashboard/dashboard.page.spec.ts --include=src/app/layout/app-shell.component.spec.ts --include=src/app/app.routes.spec.ts
npm.cmd run test:ci
npm.cmd run build
```

Las suites específicas nuevas se incorporarán al comando de fase correspondiente. La comparación visual requiere navegador real y datos sintéticos controlados; no basta jsdom. Si se amplía backend, ejecutar la suite segura y las integraciones pertinentes en una base desechable, sin arnés ni limpieza de la fixture histórica local. No es necesario reinstalar la APK para validar una evolución exclusivamente web.

## 9. Evidencias y cierre de esta evaluación

Las referencias primarias son el código y los contratos inspeccionados: DashboardPage/API, DashboardService/Contract/Controller, OperationReportRepository, ReportService, ServiceReportQuery, API de reportes canónicos, API de rutas, proyección ordinaria de conductores, bandeja de avisos y ServiceGpsAccess/Controller.

Especialmente vinculantes: [resumen por rol](../contratos/L-07-resumen-API-v0.1.0.md), [reportes y emisiones](../contratos/L-07-reportes-API-v0.1.0.md) y [contrato L-07](../contratos/L-07.md). El [inventario de inspección](../evidencia/dashboard-admin-evaluacion-2026-10-26/observacion-estatica.json) registra sus hashes y el alcance estático de este trabajo.

**Propuesta recomendada:** evolución incremental del main ADMIN, componentes aislados y SVG, datos existentes con definiciones explícitas, prioridad de seguridad conservada y tarjeta de rutas compatible con el acceso actual. El seguimiento GPS en vivo y una exportación integral de todas las tarjetas permanecen como decisiones de alcance adicionales; no se simulan ni se habilitan mediante cambios de presentación.
