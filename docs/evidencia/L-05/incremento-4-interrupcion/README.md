# L-05 — Incremento4: interrupción y atención segura

2026-10-20. Corte funcional local de SRV-INT-L05-05; **L-05 EN_CURSO**, piloto bloqueado y L-06 no iniciada. [API](../../../contratos/L-05-interrupcion-API-v0.1.0.md), [matriz](../matriz-L05.md), [resultado](resultado.json), [casos](casos.json), [preservación](preservacion.json).

## Implementado y comprobado

- Interrupción propia online y comunicada/comprobada por ADMIN, con autoría real y horario servidor. Se cierra el tramo sin completar el servicio ni generar declaración de ruta finalizada; incidencia vinculada al tramo, incluso cuando se informa después.
- Estados `INTERRUPTED` y `ATENCION`, GPS actual detenido y puntos históricos sujetos al mismo máximo de siete días. Driver y unidad bloqueados hasta revisión ADMIN separada. Pérdida de habilitación no impide comunicar el riesgo propio.
- Unidad retirada permanece bloqueada; revisión `READY` no la reactiva. Retorno de mantenimiento explícito independiente, conserva bloqueo hasta el día siguiente y requiere después revisión/activación separadas. No se eluden MNT-01A/MNT-02A, documentos ni declaración propia de disponibilidad.
- Cancelación expresa de continuidad conserva interrupción y atención. Corrección temporal separada de un caso cancelado conserva originales, sin inventar cumplimiento.
- Aislamiento con la misma referencia de servicio/atención frente a ADMIN ajeno, roles y versiones, replay exacto y payload cambiado, inmutabilidad SQL y rechazo de liberación por cambios ordinarios.
- CRM y Flutter con confirmaciones explícitas, estados diferenciados y consulta por key ante respuesta incierta; sin reenvío de mutación por refresh ni cola offline. Consultar el terminal detiene GPS de su tramo; consultar un terminal antiguo no detiene otro servicio.

## Evidencia y límites

124 casos backend distintos seleccionados contra Spring y PostgreSQL reales; 16 de atención, 15 de tiempos, 20 de GPS, 10 de ejecución, 10 mantenimiento, 14 ofertas, 20 servicios, 8 disponibilidad, 1 retorno, 9 grupos y 1 carrera de autorización. Fixtures transaccionales rollback; MockMvc aplica filtros/controladores reales, **no es recorrido TCP del navegador/app**. No `NativePostgresIntegrationTest`, limpieza global, alta persistente o correo externo.

59 casos Flutter y 36 CRM pasaron; análisis Flutter sin incidencias, typecheck y compilación CRM, JAR y APK debug. Las pruebas de componentes y sensores dobles no acreditan hardware, batería ni interacción humana. Artefactos sin instalar ni desplegar.

Se conservan resultados iniciales fallidos: selección Maven demasiado larga para Windows, una aserción de campo nulo omitido en JSON, fixture JPA sin flush antes de comprobar por JDBC, respuesta sintética móvil incompleta y falta de un frame del widget tras editar. Se corrigieron fixtures/aserciones sin relajar controles ni borrar pruebas. Dos avisos de estilo Dart se corrigieron con llaves. Logs backend potencialmente verbosos se archivan como resúmenes saneados y huellas, sin propiedades, parámetros ni cuerpos de autenticación.

V40 aditiva solo test, ledger1/3/2 y aceptaciones2 con huellas intactas; casos cerrados, final3, documento ADMIN publicado y sellos incrementales anteriores conservados. No se reconstruyó ejecución histórica.

## Pendiente del lote

Relevo aceptado y asumido atómicamente, varios tramos/pausas y sus correcciones, carreras de negocio, aviso de inicio previsto perdido y recorrido conectado autorizado. El control de atención no acredita esos criterios. Cierre solo con la matriz completa; físico L-08.
