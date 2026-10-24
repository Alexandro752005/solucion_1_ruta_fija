# Acta de cierre L-07 — Control, reportes, auditoría e inicio

**2026-10-21, America/Lima. VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.**

Alcance funcional local del [contrato0.4.0](../contratos/L-07.md) y registro v3.1 contrastado. Se aplican el OK VERDE posterior a L06 y las decisiones REP-L07-01,AUD-L07-02,L07-CON-01 y REP-RET-L07-03 ya aprobadas. No se modifican reglas o aprobaciones de otros lotes. Contrato anterior0.3.0 y cortes sellados preservados.

## Matriz y alcance

[CI-L07-01…10](../evidencia/L-07/matriz-L07.md) documentan evidencia,entorno y límites por criterio. [Archivo](../evidencia/L-07/cierre/README.md), [reglas](../evidencia/L-07/cierre/cobertura-reglas.json), [pruebas](../evidencia/L-07/cierre/pruebas-finales.json), [fuentes](../evidencia/L-07/cierre/fuentes.sha256) y [manifest](../evidencia/L-07/manifest-cierre.sha256).

KPI por cohorte original verificable,completados/cancelados/interrumpidos/pendientes separados y corroboración terminal distinta del cierre;legados identificados sin inventar historia. Emisiones inmutables/correcciones/CSV-XLSX-PDF del mismo corte y contenido;alcance propio y por organización. Auditoría segura por rol/empresa/plataforma,denegaciones sin secretos,diferencias enmascaradas y motivos/evidencias en su historia restringida. Resúmenes priorizados;inicio móvil separa compromiso de oferta;preferencias propias mantienen avisos obligatorios y escala accesible,sin editar identidad directamente.

Retención de auditoría seis meses calendario Lima,excepción exacta máximo30d no renovable y procedimiento restringido por selección/huella/revalidación. Volumen de1000 eventos sintéticos retirado en rollback y1001 rechazado;contenido de reportes90d tras fin efectivo con referencia mínima/tombstone. No se inició el piloto ni se purgó auditoría anterior. Otras finalidades mantienen sus plazos y herramientas todavía pendientes antes de datos reales;GPS no se extiende a seis meses. Copias comprobadas como política/inventario sintético,no respaldo/restauración reales.

## Comprobaciones y caso conectado

190 casos backend distintos reutilizados:157 Spring/MockMvc/PostgreSQL rollback y33 unitarios,no190 pruebas TCP.22 CRM y42 Flutter con dobles;JAR final,CRM producción,APK debug y análisis Flutter correctos. Última ejecución dirigida26 casos:17reportes,8metadatos y1negativa ADMIN ante cierre tras vencimiento. [Fallos/correcciones](../evidencia/L-07/cierre/historial-diagnostico.json) conservados;no se suma repetición ni se declara aprobado un conjunto que falló.

RUN **6b496135-b86b-4c5b-938f-a60ddcfe32db**. [Original](../evidencia/L-07/conectado-6b496135-b86b-4c5b-938f-a60ddcfe32db/resultado.json), [contraste](../evidencia/L-07/cierre/recorrido-conectado-contrastado.json), [postflight independiente](../evidencia/L-07/cierre/postflight-conectado.json). Trece etapas,cuatro CRM/Edge y tres Flutter/widget/repositorio con HTTP/TCP/backend/PG reales. Automatización sintética;correo/GPS simulados,selector Android doble. No nueva intervención humana,persona real o dispositivo físico.

Alta/revisión/activación normales del único CONDUCTOR permitido;inicio con compromiso aceptado y oferta independiente,preferencias persistidas,resumen ADMIN/plataforma,auditoría de plataforma y reporte propio. Respuesta504 simulada tras commit recuperada con idéntica clave;versiones anteriores conservadas tras ejecución/corrección y seis archivos CRM descargados/huellados. Carrera de emisión200/200 con un único registro idempotente y mismo cuerpo;denegaciones403/404 por rol/propiedad. [Retorno compatible](../operacion/L-07-retorno-compatible.md):misma implementación/esquema del recorrido,operación desactivada,perfil/preferencias200 y reportes404;sin SQL inverso/binario histórico/restauración.

El conectado precedió la adición final de auditoría de descargas. Ese cambio se verificó después con17 casos de reportes en backend/PostgreSQL/MockMvc,con descarga propia/ADMIN,rechazos,formato e identidad de snapshot. No se presenta como un segundo conectado. REPORT_EXPORTED acredita preparación autorizada del servidor,no recepción/guardado del usuario. No cambió el código de producción CRM/Flutter ni su contrato HTTP. Trece pares acción/entidad del caso comprobados persistidos con organización,hora y resultado;catálogo estático y evidencia heredada separados de esos resultados.

## Retiro e integridad

- Una alta CONDUCTOR autorizada,no ADMIN/SUPER_ADMIN persistentes nuevos. Cuenta,conductor,vehículo,grupo,ruta/tipo retirados;período y sesiones revocados,compromiso pendiente cancelado normalmente,cero compromisos vivos/tramos abiertos. Completado,corrección,emisiones/preferencias,historial y cupos conservados.
- Archivo saneado previo al retiro;dos diagnósticos privados exactos y secreto del fixture retirados. Marcador ausente,lock libre y **TEST_CLEANUP_RELEASED=true** contrastados independientemente. Solo pueden reanudarse pruebas autorizadas con rollback/retiro exacto;no limpieza global.
- Ledger **1SUPER_ADMIN/3ADMIN/6CONDUCTOR**,cupos históricos de pruebas **0/1/14**,aceptaciones **2**. Sin reinicio/devolución de cupos,aceptaciones borradas o reapertura de L01…06.
- Solo ruta_fija_test/rf_test;V45…V48 previamente aplicadas con rf_migrator y49 migraciones válidas. Revisión final sin migración. [Validación](../evidencia/L-07/cierre/postflight-V48-validacion.json), [12 protegidos/final3,187 congelados L06 y sellos L07](../evidencia/L-07/cierre/preservacion.json) correctos. Cuatro documentos globales evolucionan con respaldo;actas/términos/evidencias cerradas intactos.

## Condiciones pendientes del piloto

Privacidad/identificación/finalidad,protección y ejecución de retiros de otras categorías;aceptación propia de ADMIN legítimo;clave definitiva/custodia independiente y ensayo del titular;infraestructura/base piloto/HTTPS/copia cifrada/restauración;participantes/unidades/títulos reales y aplicabilidad documental. Canal externo efectivo/proyecto/cuota/dispositivo/entrega de Q074 siguen pendientes. Validación Android física,GPS/batería/carga/p95,accesibilidad y RTO/RPO conservan sus criterios de L08. Ninguno se acredita por dobles,inventario sintético o este cierre local.

S/0,sin offline/colas,RENIEC/MTC,QR,SMTP/push externo,Git/GitHub,despliegue,migraciones de desarrollo/piloto o nuevo snapshot. **Final3 intacto**. [Concordancia](../evidencia/L-07/cierre/concordancia.json) verificada. No quedan criterios obligatorios del alcance funcional local pendientes. **Piloto bloqueado. L08 NO INICIADA / NO AUTORIZADA;requiere OK VERDE separado.**
