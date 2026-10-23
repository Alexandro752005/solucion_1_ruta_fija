# L07.2 — API de servicios y emisiones v0.1.0

2026-10-21. Implementación por verificar. REP-L07-01 y REP-RET-L07-03 aprobadas en el registro L07. Mantener los reportes de ofertas existentes, identificados como tales; no usarlos como KPI de servicio.

## Consulta y emisión

ADMIN: `/api/v1/reports/services`. CONDUCTOR con sesión MOBILE: `/api/v1/mobile/reports/services`. GET con `from/to` inclusivos, máximo90 días, devuelve vista vigente. POST `/emissions` con `operationKey` UUID, `from/to` crea una emisión del mismo alcance; repetir clave/contenido devuelve la misma, otra carga con esa clave409. GET `/emissions?from&to` lista versiones propias de esa serie; GET `/emissions/{id}` lee el contenido persistido; GET `/emissions/{id}/export?format=csv|xlsx|pdf` usa exclusivamente ese contenido. No selector de organización/conductor ni privilegios operativos SUPER_ADMIN. ADMIN lee sus emisiones de organización; CONDUCTOR exclusivamente las que emitió para sí. Objetos ajenos404. Todas las respuestas no-store.

Servicio contado una vez, cohorte por `scheduled_at` de su primera oferta ordenada por `created_at,id`, nunca la cabecera reprogramada. Numerador exige cierre COMPLETED de un tramo iniciado y registrado hasta el corte; aceptación/cancelación/interrupción no completan. En alcance propio se cuentan las participaciones aceptadas o ejecutadas del solicitante, y solo su cierre COMPLETED aporta al numerador; un relevo TRANSFERRED se muestra separado. Ninguna fila incluye tiempos/recursos de otros participantes en el alcance propio. Cero denominador devuelve porcentaje nulo. Ofertas legadas sin servicio canónico y servicios sin primera oferta se declaran excluidos/no acreditados; no se inventa historia.

Cada fila distingue fecha de cohorte, programación vigente, cierre declarado/registrado, tiempos originales/efectivos, resultado terminal y referencias de corrección. El KPI acredita terminación del servicio/participación, no corroboración GPS del terminal. La versión nueva incluye cambios efectivos y conserva la anterior. Emisión serializada con los bloqueos de programación de la organización; clave y serie únicas en PostgreSQL. La consulta usa snapshot transaccional, no diferentes cortes por formato.

IDs/códigos, estados, grupos/recursos necesarios y tiempos; sin nombres, DNI, contactos, textos libres de expediente/incidencia ni coordenadas. CSV protege fórmulas. Formatos incluyen referencia/versión/corte/huella del contenido y el mismo resumen/filas; no recalculan al descargar. No se almacenan archivos derivados en el servidor. Contenido, relaciones de emisor e idempotencia retirables a90 días del fin efectivo del piloto por procedimiento restringido; núcleo inmutable mínimo conserva ID/serie/versión/huella/corte y constancia de retiro. Descargas externas no se declaran eliminadas.

V45 aditiva, solo ruta_fija_test/rf_migrator tras preflight; retorno compatible desactiva el uso de las rutas nuevas y conserva tablas/emisiones, sin SQL inverso ni reescritura de migraciones. Ningún inicio efectivo del piloto se registra por ejecutar estas pruebas.

## Contraste local posterior — 2026-10-21

[Acta L07](../actas/L-07-cierre-funcional-local-2026-10-21.md) y [matriz final](../evidencia/L-07/matriz-L07.md):verificación funcional local con sus límites. Las notas iniciales de implementación pendiente son históricas. No implica ensayo físico,habilitación del piloto o ejecución de purgas/copia reales.
Las descargas autorizadas y renderizadas registran REPORT_EXPORTED con actor,organización,emisión,formato y huella. Preparación servidor no equivale a guardado del cliente;lecturas simples/rechazos no registran exportación satisfactoria. Esta adición final se verificó con17 casos de reportes en PostgreSQL/MockMvc.
