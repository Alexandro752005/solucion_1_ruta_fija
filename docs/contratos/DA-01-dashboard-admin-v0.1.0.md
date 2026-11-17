# DA-01 — Contrato visual y de indicadores del Dashboard ADMIN

Versión: **0.1.0**. Fecha: **26/10/2026**, America/Lima. Fase: **1 — especificación**. Corte de código inspeccionado: `fa9122b`.

Autorización de alcance: «OK VERDE, Vamos rápidamente con el apartado 1. Contrato visual y de indicadores». Esta autorización permite formalizar esta fase. El documento no acredita una aprobación visual humana de una maqueta aún no construida.

Este contrato concreta la [evaluación previa](../decisiones/2026-10-26-evaluacion-dashboard-admin.md). Su [anexo estructurado](DA-01-dashboard-admin-v0.1.0.json) define fuentes, fórmulas, estados, decisiones y escenarios de aceptación. Las fórmulas del anexo son especificación, no código ejecutable de la aplicación.

## 1. Alcance y decisiones cerradas

| ID | Decisión vinculante para esta evolución |
| --- | --- |
| DA-D01 | Evolucionar únicamente el contenido principal ADMIN de `/dashboard`, conservando shell, navegación y guardas actuales. Mantener la vista SUPER_ADMIN separada y sin consultas operativas |
| DA-D02 | Cabecera, cinco KPI, seis paneles y tres donas SVG; sin nueva librería gráfica, fuentes remotas ni servicios comerciales |
| DA-D03 | Conservar «Atención prioritaria» antes de los KPI. Cuando todas las colas se consulten correctamente y sean cero, reducirla a un estado compacto; con pendientes, error o datos vencidos conservarla visible |
| DA-D04 | Mapa de **rutas registradas**, con rótulo «Esquema de ruta». No seguimiento personal, vehículos en movimiento ni endpoints GPS en esta evolución |
| DA-D05 | Botón «Exportar resumen» abre «Reporte de servicios del período». Solo exporta una emisión canónica expresamente creada o seleccionada; no promete exportar las seis tarjetas |
| DA-D06 | Fecha seleccionada afecta solo operación e incidencias del período. KPI, disponibilidad, prioridades, conductores y avisos siguen rotulados «Ahora» o con su corte propio |
| DA-D07 | Conservar códigos de conductores y ausencia de datos personales. No introducir nombres, DNI, contactos, expedientes ni coordenadas personales |
| DA-D08 | Reutilizar endpoints existentes. Única ampliación de API requerida en esta versión: `zoneId` y `operationalDate` en el resumen ADMIN, derivados del actor y reloj del servidor, sin cambios de permisos ni DDL |
| DA-D09 | Mantener actividad reciente y accesos rápidos como detalles secundarios accesibles debajo de la retícula. Su carga puede ser diferida hasta expandirlos |
| DA-D10 | Esta fase produce contratos y evidencia estática. No implementa componentes, modifica servicios, aplica migraciones, prepara fixtures ni inicia la fase 2 |

Prevalecen las reglas ya vigentes de [resumen por rol](L-07-resumen-API-v0.1.0.md), [reportes canónicos](L-07-reportes-API-v0.1.0.md) y [L-07](L-07.md). Este documento no reescribe esos contratos ni amplía las autorizaciones de GPS o identidad.

## 2. Contrato visual

Orden de lectura: **cabecera → atención prioritaria → cinco KPI → dos filas de tres paneles → detalles secundarios → pie**. Orden de paneles: flota, operación, ruta; incidencias, conductores, alertas y avisos. A anchos reducidos se conserva ese orden en el DOM.

La referencia es el raster visible de **1106 × 628 px**. Sus medidas previas son aproximaciones visuales; la fase 2 hará una comparación del recorte del main con el mismo ancho. No se fija una altura máxima de página: prioridades, categorías, texto ampliado o errores pueden aumentar la altura y generar desplazamiento vertical. No usar `transform: scale`, textos ocultos ni alturas rígidas para simular una coincidencia exacta.

| Propiedad local | Valor de contrato |
| --- | --- |
| Fondo / tarjeta / borde | `#f3f5f9` / `#ffffff` / `#e5eaf2` |
| Texto principal / secundario | `#111827` / `#52627a` |
| Enlaces y foco | `#1d4ed8`; acento decorativo azul `#3b82f6` |
| Verde / ámbar / rojo / violeta / gris | `#059669` / `#b45309` / `#dc2626` / `#7c3aed` / `#64748b` |
| Iconos | SVG locales, trazo consistente, contenedor pastel circular de 36 px; significado también textual |
| Radios | 14 px en tarjetas; controles y etiquetas con radio propio local |
| Espaciado | Base 4 px; margen interno del contenedor 12 px; hueco KPI 12 px; hueco entre paneles 18 px; padding de tarjeta 16 px |
| Tipografía | Heredar la familia local existente; saludo 24 px; KPI 26 px; títulos de panel 14 px; texto de apoyo y leyenda mínimo 12 px; interlineado ≥1,35 |
| Tamaño de bloques | KPI mínimo 104 px de alto; panel mínimo 232 px; alturas ampliables. A 1106 px, ancho aproximado de KPI 207 px y panel 349 px |
| Dona | SVG de 112 × 112 px, anillo de 12 px, cifra y descriptor centrales; leyenda con cantidad y porcentaje |
| Conductores | Dos filas visibles inicialmente, mínimo 48 px por fila; códigos multilínea si hace falta |
| Controles | Área de interacción mínima de 44 × 44 px; foco visible; nombre accesible; sin menús con acciones inexistentes |

Los tamaños de texto y controles adaptan la referencia para legibilidad; no son una afirmación de igualdad exacta con los píxeles de la imagen. Los tokens pertenecen al contenedor ADMIN; no se modifican `:root`, el shell ni los estilos de SUPER_ADMIN.

Retícula por **ancho útil del contenedor**: desde 1024 px, KPI 5 columnas y paneles 3; de 768 a 1023, KPI 3 y paneles 2; de 480 a 767, KPI 2 y paneles 1; por debajo de 480, ambos 1. A 320 px y con texto al 150 % debe conservarse lectura, navegación y acceso a todos los valores. Si la leyenda no cabe junto a la dona, pasa debajo. Los porcentajes no se comunican solo mediante colores.

La cabecera muestra fecha, «Hola, Administrador», organización de la sesión, estado de sesión, botón de exportación y selector de fecha. «Sesión activa» solo significa autenticación válida; el estado de conexión y la actualización de datos se identifican por separado. El pie contiene producto/año y enlaces válidos a Auditoría (`/audit`) y Términos ADMIN (`/admin-terms`); no inventa páginas de soporte o privacidad.

## 3. Contexto temporal y ampliación mínima de API

El `GET /api/v1/dashboard/admin` conserva todos sus campos y semántica. En la futura fase de integración añadirá:

```json
{
  "zoneId": "America/Lima",
  "operationalDate": "2026-10-26"
}
```

Son ejemplos de forma, no constantes. `zoneId` es la zona IANA de la organización del actor. `operationalDate` es la fecha de `generatedAt` en esa zona. Se calculan sobre el mismo instante ya usado en el resumen, sin selector de empresa, permisos nuevos, consulta global de organizaciones ni datos personales. La respuesta sigue siendo `no-store` y exclusiva del ADMIN. El endpoint SUPER_ADMIN no cambia.

La ampliación es **planificada, todavía no implementada**. Despliegue futuro: backend con los campos aditivos antes del frontend que los requiere; los consumidores antiguos pueden ignorarlos. Si faltan o son inválidos, mostrar «Fecha de la organización no disponible» y bloquear únicamente consultas/exportaciones dependientes de fecha; los contadores actuales válidos pueden seguir visibles. No inferir la zona del sistema operativo ni pedir acceso al endpoint de organizaciones de SUPER_ADMIN.

El selector acepta un día ISO y usa `from=to=día` en los reportes existentes. «Hoy» usa `operationalDate`; las fechas futuras no se ofrecen para este resumen de actividad observada. Las fechas pasadas significan **estado vigente de la cohorte de ese día al corte consultado**, no una reproducción del estado histórico de la flota. El intervalo diario es inicio inclusivo y siguiente día exclusivo, calculado en el servidor con la zona de la organización; no sumar siempre 24 horas en el navegador.

Al refrescar «Hoy» después de cambiar el día operativo se actualiza la selección. Si el usuario escogió una fecha pasada se conserva. Los paneles actuales no cambian al elegir fecha. No se admite un rango de varios días en este Dashboard; el módulo Reportes conserva su rango propio, hasta 90 días según su contrato.

## 4. Fuentes y cinco indicadores

Todos los paths siguientes llevan el prefijo `/api/v1`. La empresa procede de la sesión autenticada en el servidor. El cliente no envía `organizationId` ni un selector de tenant.

| ID y etiqueta | Fuente / definición exacta | Contexto y acción |
| --- | --- | --- |
| DA-K01 · Conductores disponibles | `GET /reports/availability`: suma de `drivers[].total` cuyo `status=DISPONIBLE`. Total registrado = suma de **todos** los estados recibidos válidos | Ahora; «de N registrados». `/drivers`. Disponibilidad registrada, no acreditación de habilitación |
| DA-K02 · Vehículos disponibles | Misma respuesta: suma en `vehicles` para `DISPONIBLE`; denominador = suma de todos los estados válidos | Ahora; «de N registrados». `/vehicles`. No sustituir denominador por `activeVehicles` |
| DA-K03 · Asignaciones futuras | `futureScheduledAssignments` de disponibilidad. Ofertas `SCHEDULED` posteriores al corte calculado por esa API | Ahora; «programadas». `/assignments`. No limitar al día seleccionado ni llamarlas servicios canónicos |
| DA-K04 · Servicios en curso | `servicesInProgress` de `GET /dashboard/admin`: servicios canónicos `scheduled_service.IN_SERVICE` | Ahora; «en ejecución». `/assignments`. No usar `assignmentsInService`, que cuenta ofertas |
| DA-K05 · Incidencias abiertas | `openIncidents` de disponibilidad: `OPEN` + `FOLLOW_UP` de la organización, sin filtro diario | Ahora; «pendientes de gestión». `/incidents`. No sumar otra vez el contador de prioridad de incidencias |

Se comparte una sola respuesta de disponibilidad para K01/K02/K03/K05 y flota. La ausencia de una categoría en una distribución válida y completa equivale a cero; una respuesta ausente, nula, fallida o malformada no. Los contadores son enteros no negativos representables de forma segura en el cliente. Estados duplicados, totales negativos o datos incompatibles se presentan como información no verificable, no se corrigen silenciosamente.

Las distribuciones actuales incluyen registros según su estado, sin un filtro adicional `active`. El Dashboard mantiene ese universo y lo explica mediante «Disponibilidad registrada; la asignación confirma la habilitación vigente». No calcula elegibilidad mediante un booleano local ni modifica estados para que coincidan con la imagen.

## 5. Tres gráficos y seis paneles

### DA-P01 — Estado de la flota

Fuente: la misma distribución de vehículos de K02. Cinco segmentos excluyentes: `DISPONIBLE` («Disponibles», verde), `EN_SERVICIO` («En servicio», azul), `ATENCION` («Atención segura», rojo), `MANTENIMIENTO` («En mantenimiento», ámbar), `INACTIVO` («Inactivos», gris). Mostrar los cinco renglones aunque alguno sea cero. Un estado nuevo válido se conserva como «Otros estados» y se avisa para revisar el contrato; no se pierde del total.

Centro: total de vehículos registrados en ese universo. Porcentaje por estado = `cantidad / total × 100`. Con total cero, anillo neutro y «0 vehículos · Sin registros»; porcentajes no aplicables mediante «—». Acción: `/vehicles`.

### DA-P02 — Resumen de operación

Fuente: `GET /reports/services?from=día&to=día`. Reutilizar `totals` y `coverage`, sin derivar el resultado de ofertas ni contar dos veces un servicio con reofertas. Cohorte por primera oferta, cierre válido y resultado al corte siguen íntegramente el contrato L-07.

Dona con dos segmentos: `completed` y `scheduled-completed`. Centro: `completionPercent` del servidor, mostrado con un decimal. Leyenda: completados, pendientes, cancelados, interrumpidos y relevados (`transferred`), con sus cantidades y porcentajes sobre `scheduled`. Se mantiene visible la cobertura de legados excluidos y servicios sin primera oferta cuando no sea cero.

`scheduled = completed + pending + cancelled + interrupted + transferred` debe cumplirse. Un porcentaje incoherente, suma inválida o más completados que programados significa respuesta incompatible. Con `scheduled=0`, `completionPercent=null`, anillo neutro y «Sin servicios programados»; nunca un cumplimiento del 0 %. Con programados positivos y cero completados, el 0 % sí es válido.

El detalle puede mostrar «En curso de esta cohorte» derivado de sus filas con `status=IN_SERVICE`, claramente subordinado a pendientes; no se añade como otra categoría sumable ni se confunde con K04, que es global actual. Acción: `/reports`.

### DA-P03 — Esquema de ruta

Fuentes: `GET /routes?limit=100&offset=0` y un único `GET /routes/{id}` para la ruta seleccionada. Respetar el orden del catálogo: nombre y UUID. Elegir por defecto la primera ruta activa con versión vigente en la página cargada; mantener la elección explícita del usuario. Para otras páginas se navega o se consulta el catálogo, sin descargar automáticamente todas las versiones de todas las rutas.

Se muestra **solo la versión cuyo ID coincide con `currentVersionId`**, su nombre, revisión y trazado de paradas registradas. El título visible es «Esquema de ruta» y la aclaración «Trazado registrado; no muestra ubicación de vehículos». El dibujo SVG es un esquema entre paradas, no una ruta vial calculada ni prueba de circulación.

Usar únicamente pares de coordenadas registrados y válidos. Se requieren al menos dos paradas adyacentes con coordenadas para un segmento; no unir a través de una parada intermedia sin coordenadas. No sustituir ausencias por 0,0; los ceros legítimos recibidos se someten a la validación normal de coordenadas. Sin geometría suficiente: «La ruta no tiene coordenadas suficientes», con acceso a `/routes`. Sin ruta activa en una página no exhaustiva: decir «en esta página», no afirmar ausencia global.

Los extremos y marcadores corresponden a paradas, nunca a conductores. No hay llamadas a proveedores cartográficos, controles de seguimiento ni iconos de vehículos simulados. «Ver ruta» abre un visor local ampliado de esa misma versión; «Ver rutas» enlaza `/routes`. No se añade una ruta de navegación `/map` inexistente. El visor admite teclado, cerrar con Escape y devolución del foco.

### DA-P04 — Incidencias por estado

Fuente: `GET /reports/incidents?from=día&to=día`, `totalsByStatus`. Segmentos: `OPEN` («Abiertas», rojo), `FOLLOW_UP` («En seguimiento», ámbar), `RESOLVED` («Resueltas», verde), `CLOSED_BY_DRIVER` («Cerradas por conductor», gris). Centro: suma de incidencias **reportadas en el día seleccionado**, en su estado actual al consultarlas. No se intenta reconstruir el estado que tuvieron al final de ese día.

Misma regla de porcentajes y cero denominador de flota. La diferencia con K05 es deliberada: una incidencia de ayer todavía abierta contribuye a K05 pero no a la cohorte de hoy. Un estado nuevo se conserva como «Otros estados» y no se representa como resuelto. Acción: `/incidents`.

### DA-P05 — Conductores

Fuente: `GET /drivers?page=0&size=2&sort=createdAt,desc`, sin filtros implícitos de actividad o disponibilidad. `createdAt` es un campo de orden permitido por el servidor; `id` no lo es actualmente y no se enviará como `sort`. El orden exacto entre dos registros con idéntico `createdAt` depende del servidor y no afecta indicadores ni asigna identidad.

Mostrar «Últimos registrados», código inequívoco, estado recibido y condición activo/inactivo. Los códigos son `CONDUCTOR-UUID`; si se abrevian visualmente, el completo sigue accesible y no se crea un alias ficticio CD-001. El estado operativo no acredita la documentación ni autorización. Cada fila ofrece «Ver conductores» con referencia accesible para localizar el código; no hay menú de activación, suspensión o cambio de disponibilidad en el Dashboard.

El total visible procede de `totalItems`, rotulado como total del listado; no se infiere de las dos filas y no reemplaza automáticamente denominadores de otra consulta con distinto corte. Error o tamaño de muestra nunca equivalen a cero conductores.

### DA-P06 — Alertas y avisos

La seguridad se representa en la franja de prioridades y su detalle, con sus ocho códigos en orden de servidor: `CRITICAL_INCIDENTS`, `SAFETY_ATTENTION`, `UPCOMING_UNACCEPTED`, `PENDING_REASSIGNMENT`, `EXPIRED_PENDING_OFFERS`, `EXPIRING_OFFERS`, `OPEN_INCIDENTS`, `ONBOARDING_REVIEW`. Las colas pueden solaparse; no se suman sus cantidades para inventar un total de casos.

Los avisos proceden de `GET /notices?failures=false&page=0&size=20`: bandeja personal del ADMIN. Se muestran como máximo dos avisos recientes y acceso a `/notices`. El indicador de no leídos se rotula «en esta página» porque no existe un total global de no leídos en ese contrato. No usar `failures=true` como si fuera la bandeja personal o la salud de toda la plataforma.

Estado favorable conjunto únicamente con las ocho prioridades consultadas correctamente, todas cero, y `totalItems=0` de la bandeja personal: «Sin prioridades ni avisos en esta consulta». Si hay avisos ya leídos, decir «Sin prioridades operativas pendientes · Hay avisos en tu bandeja». No afirmar «Todo en orden» global por inspeccionar solo los últimos veinte avisos. Consultar no marca lectura, responde ofertas ni cambia la atención de seguridad.

## 6. Exportación definida

El botón mantiene su posición y estilo de referencia. Al abrir el diálogo informa: **«Reporte de servicios del [día], zona [zoneId]. Incluye servicios y sus resultados; no incluye flota, conductores, incidencias, avisos ni el esquema de ruta»**.

Se reutilizan los contratos existentes: consulta de período, lista/selección de emisión, emisión expresa mediante `POST /reports/services/emissions`, lectura de emisión y descarga `GET /reports/services/emissions/{id}/export?format=csv|xlsx|pdf`. Cada archivo usa el contenido persistido de esa emisión, con versión, corte y SHA-256. No se recalcula al elegir otro formato.

Emitir exige pulsar «Emitir reporte». Una incertidumbre conserva la misma `operationKey` y período para consultar/reintentar; no se generan claves nuevas automáticamente. Descargar requiere seleccionar una emisión confirmada y elegir formato. Abrir el Dashboard, refrescar o abrir el diálogo no emite reportes. La emisión puede tener un corte posterior al panel observado: mostrar ese corte y no prometer identidad con todas las tarjetas actuales. Una exportación integral del Dashboard queda fuera de esta versión y no se simula.

## 7. Estado, conexión y límites de consulta

Estados de presentación por fuente: `loading`, `ready`, `empty`, `refreshing`, `stale`, `unavailable`, `forbidden`, `invalid`. `empty` exige respuesta correcta con universo cero. `refreshing` puede conservar el último dato del **mismo usuario, empresa y período**, indicado como anterior; al fallar pasa a `stale` con hora y mensaje. En la primera carga fallida no se dibuja un 0 ni una dona exitosa. Ante cierre/cambio de sesión se vacían los datos en memoria, sin persistencia del Dashboard en localStorage/sessionStorage.

Errores de autenticación siguen los interceptores y guardas actuales. Un 403 no se sortea mediante otro rol/endpoint; datos ajenos no se conservan como caché visible. La fecha de la respuesta debe coincidir con la selección; una respuesta antigua no sustituye un período posterior. Fuentes independientes pueden fallar sin borrar los paneles correctos. Los límites existentes de 10 000 filas y retención de reportes permanecen: informar «Resumen no disponible» y permitir la consulta pertinente; no fabricar un agregado completo a partir de filas truncadas.

Se requiere una suscripción de pantalla al canal operativo existente. La futura ampliación de su API de cliente añadirá un estado de conexión de solo lectura (`disconnected`, `connecting`, `connected`, `reconnecting`), conservando el comportamiento y firmas existentes. No se considera conectado solo porque `connect()` se haya invocado.

Se agrupan invalidaciones de eventos en ventanas de **300 ms**, con una consulta en vuelo por fuente y como máximo una invalidación pendiente por fuente. Cada **60 s** de pantalla visible se revisan fuentes actuales y reportes seleccionados; no el catálogo estático de rutas ni la tabla de auditoría oculta. Al ocultar la pestaña se pausa el sondeo; al volver, una sola ronda consolidada. No crear un socket por tarjeta. Al salir se cancelan suscripciones y temporizadores propios.

Presupuesto inicial objetivo, sin contar autenticación/refresh: **8 GET** si hay una ruta seleccionable (resumen, disponibilidad, servicios, incidencias, dos conductores, bandeja, catálogo de rutas y detalle de una ruta), más el POST normal de ticket WebSocket. Los dos reportes esperan al contexto temporal válido del resumen. Cambiar fecha provoca **2 GET**; cambiar ruta, **1 GET**. Auditoría diferida: un GET adicional solo al abrir el detalle. No hacer consultas de expediente ni una llamada por conductor. Este presupuesto es de diseño y se verificará en navegador, no es una medición ya realizada.

Las respuestas tienen cortes propios. Mostrar `generatedAt` del resumen/disponibilidad, `cutoffAt` del reporte canónico y hora de recepción etiquetada «Consultado» cuando el endpoint carezca de corte. No inventar un timestamp de servidor para incidencias, lista o avisos. No hay un único snapshot global ni una garantía de disponibilidad 24/7 por disponer de WebSocket.

## 8. Validación y puerta de salida de fase 1

| Criterio | Verificación de contrato requerida |
| --- | --- |
| DA-C01 | Exactamente cinco KPI y seis paneles identificados y tres donas |
| DA-C02 | Cada KPI tiene fuente, campo, universo, corte y destino existente |
| DA-C03 | Servicios en curso usan servicios canónicos; ofertas futuras siguen su propio significado |
| DA-C04 | Cinco estados de flota incluidos; identidad ordinaria por código |
| DA-C05 | Donas con fórmulas, ceros, nulos y redondeo definidos; sin sumar subconjuntos solapados |
| DA-C06 | Fecha y zona provienen del servidor; campos nuevos distinguidos de los ya implementados |
| DA-C07 | Mapa resuelto como esquema de versión de ruta, sin GPS personal |
| DA-C08 | Exportación acotada a emisión canónica expresa; sin escritura automática |
| DA-C09 | Prioridades antes de indicadores, error no favorable y avisos con alcance personal/paginado |
| DA-C10 | Responsivo, teclado, texto ampliado, contraste y lectura alternativa especificados |
| DA-C11 | Presupuesto de consultas, ciclo de vida y cortes independientes definidos |
| DA-C12 | Código, contratos L-07, migraciones, términos e implementación permanecen sin cambios en esta fase |

Las leyendas muestran porcentajes con un decimal mediante redondeo HALF_UP; el valor canónico original se conserva. No redistribuir redondeos para forzar 100,0 %. Las cantidades enteras son exactas y permiten comprobar el total. El centro de operación usa el porcentaje canónico redondeado a un decimal, no la suma de leyendas redondeadas. Los segmentos SVG usan razones sin redondear.

Objetivos de contraste de esta evolución: al menos 4,5:1 para textos normales frente a sus superficies y 3:1 para iconos de acción; los acentos pastel son decorativos. Son criterios de diseño por verificar en la fase visual, no una certificación de accesibilidad ya obtenida. La dona tiene nombre, descripción del universo y leyenda HTML; los controles son operables por teclado y respetan movimiento reducido.

La evidencia de esta fase verificará consistencia del anexo, aritmética de escenarios sintéticos, existencia de fuentes/rutas, fuentes sin modificar y hashes. Eso no equivale a pruebas del futuro componente. Fase 2 seguirá pendiente de su ejecución propia; no se registra aprobación humana ni resultados de runtime ficticios.

## 9. Impacto previsto de implementación

Fase 2: componente ADMIN aislado, estilos locales, tarjetas/dona y fixtures de presentación. Fase 3: adapter/facade, enlace de fuentes y la ampliación aditiva de contexto en `DashboardContract`, `DashboardService` y tipo cliente. Fase 4: controles, exportación y estado de conexión aditivo del servicio realtime. Mantener pruebas de SUPER_ADMIN, shell y guardas. No agregar DDL, cambios en Android, permisos nuevos o alteraciones de campos existentes por este contrato.

La validación estática y el manifiesto se archivan en [evidencia de fase 1](../evidencia/dashboard-admin-fase-1-2026-10-26/validacion-estatica.json). La evaluación inicial queda intacta como antecedente.
