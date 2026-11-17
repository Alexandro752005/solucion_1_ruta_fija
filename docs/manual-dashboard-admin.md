# Manual del Dashboard ADMIN — Ruta Fija

Versión de entrega local: **26/10/2026**, America/Lima. Este manual corresponde a **Resumen → `/dashboard`**, con perfil ADMIN. La evolución está regida por [DA-01](contratos/DA-01-dashboard-admin-v0.1.0.md) y su [acta de cierre](actas/dashboard-admin-evolucion-cierre-local-2026-10-26.md).

## Inicio y acceso

Desde la raíz del proyecto, en PowerShell:

```powershell
.\iniciar_ruta_fija.bat
.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test
```

El inicio administra los procesos del proyecto, comprueba la base configurada y levanta API **8080**, CRM **4300** y el puente móvil según su configuración existente. Reutiliza PostgreSQL si ya estaba funcionando; no aplica migraciones para solventar un esquema rechazado. En el entorno de esta entrega, la base nativa tiene **V1–V51**, sin migraciones fallidas. No utilizar la antigua expectativa V50 de documentación histórica para degradarla.

1. Abrir **http://127.0.0.1:4300** y entrar con una cuenta ADMIN habilitada de la organización.
2. Completar los pasos de identidad, correo y términos que soliciten las guardas existentes. El Dashboard no los omite ni acepta términos automáticamente.
3. Abrir **Resumen** en la navegación. SUPER_ADMIN conserva su resumen de plataforma; CONDUCTOR utiliza su acceso móvil y no obtiene este Dashboard administrativo.

Las credenciales se consultan exclusivamente en el archivo privado del propietario, `backend/.local/accesos/CREDENCIALES_ACTIVAS.md`; no se incluyen en este manual ni en el paquete de entrega. Para salir, cerrar sesión y ejecutar:

```powershell
.\finalizar_ruta_fija.bat
```

Ese cierre verifica la propiedad de los procesos y conserva PostgreSQL compartido cuando no existe una concesión de propiedad del proyecto. Los ejecutables no se modificaron por esta evolución. La guía general sigue en [Uso del Sistema](Uso%20del%20Sistema.md).

## Leer los cinco indicadores

| Indicador | Significado |
| --- | --- |
| Conductores disponibles | Conductores en estado `DISPONIBLE`; el denominador incluye todos los estados registrados de disponibilidad |
| Vehículos disponibles | Vehículos en estado `DISPONIBLE` respecto del universo completo registrado |
| Asignaciones futuras | Ofertas/asignaciones futuras programadas; no se presenta como cantidad de servicios únicos |
| Servicios en curso | Servicios canónicos en curso del resumen autorizado; no utiliza el contador heredado de ofertas en servicio |
| Incidencias abiertas | Incidencias abiertas actuales de la organización, incluso si fueron reportadas otro día |

Los cinco indicadores describen el estado actual y conservan su corte. Elegir una fecha anterior no convierte estas cifras en datos históricos. La disponibilidad no demuestra por sí sola habilitación documental ni autoriza conducción.

Antes de las tarjetas aparece **Atención prioritaria**. Conserva las ocho colas y su orden de seguridad, con acceso a sus módulos. No se suman colas que pueden contener el mismo caso. Un error o dato anterior se identifica expresamente y no equivale a «sin prioridades».

## Leer los seis paneles

| Panel | Uso y límites |
| --- | --- |
| Estado de la flota | Dona y leyenda de disponibilidad registrada: disponibles, en servicio, atención segura, mantenimiento e inactivos; incluye cantidad y porcentaje |
| Resumen de operación | Servicios canónicos de la cohorte del día seleccionado, con completados, pendientes, cancelados, interrumpidos y relevados. Sin servicios programados, el cumplimiento es **—**, no un éxito de 0 % o 100 % |
| Esquema de ruta | Trazado de una ruta registrada y su revisión, con paradas; no contiene posiciones personales GPS ni vehículos animados |
| Incidencias por estado | Incidencias reportadas el día seleccionado y su estado consultado: abiertas, seguimiento, resueltas y cerradas por conductor; no sustituye el KPI de todas las abiertas |
| Conductores | Dos registros iniciales, con códigos `CONDUCTOR-…`, estado y acceso al módulo; el código completo está disponible en su referencia. No muestra nombres, DNI ni contactos |
| Alertas y avisos | Bandeja personal del ADMIN y prioridades. Muestra hasta dos avisos de la página consultada, con referencia y acceso a la bandeja. Consultar no confirma su lectura |

Los gráficos tienen leyendas textuales. Un universo vacío mantiene un anillo neutro y un texto explícito; una fuente fallida muestra **No disponible**. Un número confirmado de cero se distingue de un dato ausente. No se excluyen categorías desfavorables para aumentar porcentajes.

**Actividad reciente** y **Accesos rápidos** están debajo de los paneles. La actividad se consulta al solicitarla; no se descarga una auditoría oculta en cada sondeo. Los enlaces abren módulos reales y conservan sus permisos.

## Fecha, cortes y conexión

El selector permite consultar días hasta el día operativo confirmado por el servidor. Solo cambia las cohortes diarias de operación e incidencias y el período del reporte. La zona organizacional y el corte aparecen en pantalla. Si el resumen no confirma el contexto, se deshabilita la fecha y no se inventa una zona; las horas de respaldo se identifican como UTC.

Cada fuente conserva su instante de consulta. **Dato anterior / actualización pendiente** advierte que la última actualización falló; no garantiza vigencia. La etiqueta **Sesión activa** describe autenticación. El canal operativo muestra por separado desconectado, conectando, conectado o reconectando.

Con la pestaña visible hay un sondeo de respaldo de **60 s** y agrupación de eventos en **300 ms**. Al ocultarla se pausa ese sondeo y al volver se consolida una ronda. Salir del módulo cancela suscripciones y temporizadores. No recargar repetidamente para forzar datos: revisar el estado y el corte de la fuente.

## Consultar una ruta

1. En **Esquema de ruta**, elegir una ruta activa de la página del catálogo. Las flechas recorren páginas; una página sin opciones no significa que todo el catálogo esté vacío.
2. Pulsar **Ver ruta** para abrir el visor.
3. Utilizar ampliar, reducir, desplazar y restablecer; el rango es **1×–4×**. Consultar también la lista textual de paradas.
4. Cerrar con **×** o **Escape**. El foco vuelve al botón de apertura.
5. **Ver rutas** abre el módulo de gestión existente.

Una parada sin coordenadas interrumpe el segmento: no se unen puntos a través del hueco ni se generan coordenadas 0,0. El catálogo nativo estaba vacío al cierre; los controles de geometría se comprobaron en un visor aislado con datos sintéticos identificados. Esa prueba no acredita rutas operativas reales.

## Exportar servicios

1. Elegir el día y pulsar **Exportar resumen**. Se abre **Exportar reporte de servicios** y se consultan las versiones disponibles.
2. Seleccionar una emisión confirmada. Comprobar referencia, versión, período, zona, corte y SHA-256 canónico.
3. Pulsar **CSV**, **Excel** o **PDF**. Los tres formatos representan el contenido persistido de esa misma emisión; sus bytes y hashes de archivo son diferentes.
4. Si necesita un corte nuevo, pulsar expresamente **Emitir reporte**. Abrir el diálogo, refrescar o recibir eventos no crea emisiones.
5. Si la respuesta se pierde, conservar la referencia visible y pulsar **Consultar o recuperar con la misma operación**. No empezar otra operación para resolver la incertidumbre.

La operación pendiente conserva su clave y su período al cerrar/reabrir el diálogo o cambiar la fecha dentro de la misma pantalla. No se guarda en el almacenamiento del navegador: resolverla antes de recargar completamente, abandonar la pantalla o cambiar de sesión. Un cambio de actor/empresa elimina el contenido de la sesión anterior.

La exportación corresponde exclusivamente a **servicios del período**. No exporta flota, conductores, incidencias, bandeja ni mapa. Las dos emisiones locales de prueba de fase 4 se conservan; el cierre de fases 5 y 6 no crea una tercera.

## Incidencias de uso

| Situación | Procedimiento |
| --- | --- |
| No disponible en un panel | Revisar el aviso de esa fuente y su corte; las otras fuentes siguen siendo independientes. No interpretar como cero |
| Canal desconectado | Mantener la pestaña visible y observar la recuperación/sondeo; sesión y canal tienen estados distintos |
| Sesión caducada | Iniciar sesión de nuevo. Un fallo definitivo de renovación retira los datos de la pantalla |
| Acceso prohibido | Revisar el rol y las guardas con el responsable; no cambiar permisos ni consultar otra empresa como atajo |
| Sin rutas registradas | Registrar y revisar rutas mediante su módulo autorizado; no rellenar la tarjeta con posiciones supuestas |
| Emisión pendiente | Consultar/recuperar la misma operación antes de abandonar la pantalla |
| Problema al iniciar | Ejecutar la comprobación ligera del runtime y revisar sus registros privados. No aplicar migraciones ni borrar datos para eludir la compuerta |

El Dashboard fue probado entre **320 y 1440 px** y con ampliación CSS al **150 %**. Existe una observación previa en la cabecera general del CRM a 320 px; el Dashboard no introdujo ese desbordamiento. La evidencia de contraste y teclado es automatizada y no sustituye una evaluación humana completa con lector de pantalla.

## Entrega y reversión

Consultar el [acta final](actas/dashboard-admin-evolucion-cierre-local-2026-10-26.md), las [capturas comparativas](evidencia/dashboard-admin-fase-6-2026-10-26/comparison-validation.json) y la [guía de reversión](operacion/dashboard-admin-reversion.md). El paquete local y su manifiesto están en `artifacts/dashboard-admin-entrega-final-2026-10-26/`.

El cierre corresponde a la evolución local del main ADMIN. Conserva los requisitos previos del piloto real y no certifica un nuevo ensayo físico Android.
