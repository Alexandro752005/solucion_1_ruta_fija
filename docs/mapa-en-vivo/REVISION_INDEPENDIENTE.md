# Revisión independiente del incremento

Revisor real: `/root/qa_independiente_mapa`, distinto de los tres autores. Alcance ejecutado: lectura actual de backend/CRM/Flutter y de las bibliotecas cartográficas instaladas; no ejecutó pruebas ni usó BD/API/dispositivo. El coordinador verifica aparte los comandos y resultados.

## Hallazgos y correcciones

| Severidad | Defecto detectado | Corrección observada por revisor |
|---|---|---|
| P1 | Captura previa al cierre fuera del mutex, permitía tick simultáneo | `beforeComplete` adquiere el mismo `_pending` durante consulta/captura; prueba con consulta bloqueada y tick intercalado |
| P2 | Selección restaurada por REST recentraba después de desplazamiento manual | Canvas condiciona auto pan; centrar explícito conserva su acción; prueba real Leaflet de drag/invalidation/restauración |
| P2 | Cancelación normal de tesela obsoleta se mostraba como fallo | Preservar `http.RequestAbortedException` sin aviso de error cartográfico |
| Evidencia débil | Latch anterior no probaba que la segunda conexión estuviera esperando | Test consulta `pg_stat_activity` y `pg_blocking_pids` para esperar el PID exacto de la primera transacción antes de revocar/completar |

El revisor volvió a leer las correcciones y no detectó defectos bloqueantes adicionales en ese alcance estático. No convirtió esa lectura en aprobación de integración o de pruebas no ejecutadas.

## Controles examinados

ADMIN en controller y servicio; tenant derivado de sesión; joins de objetos y pertenencias vigentes; consentimiento y servicio/tramo válidos; TTL/no-store; DTOs redactados; sin camino de subida desde mapa personal; invalidación y limpieza por sesión/visibilidad/permisos; reloj monotónico; UA y caché HTTP flutter_map conservados con cliente separado.

## Límite físico identificado

Invalidar epoch al salir de primer plano o revocar impide mostrar/enviar el resultado de una captura pendiente. Una solicitud nativa `getCurrentPosition` ya iniciada puede tardar hasta su timeout de quince segundos; el API de lectura puntual utilizado no acredita cancelación física instantánea del sensor. Se requiere aceptación Android real para observarlo. No se agrega servicio background ni se acepta un punto tardío después de invalidación; «detener» en pruebas automáticas significa detener futuros envíos y descartar resultados pendientes, no demostrar apagado físico instantáneo de hardware.

Las pruebas que confirman los arreglos se vinculan en el registro final con logs reales y sus fechas; este informe no sustituye esos resultados.

## Segunda revisión independiente de UX móvil y contraste

El autor del CRM `/root/mapa_visuales` revisó posteriormente móvil sin escribir sus archivos. Encontró y el autor corrigió: confusión de GPS apagado con permiso denegado, texto de retirada puntual que prometía actualización únicamente manual, y atribución fuera del área del mapa. Las pruebas correspondientes verifican ajustes correctos, texto de captura periódica y bounds de atribución dentro del mapa. El coordinador vio el PNG final con tipografía legible y cuadrícula sintética, incluyendo CTA de cierre.

La ejecución en Brave detectó ausencia de resultados automáticos concluyentes de contraste. El cálculo de los colores y fondos reales encontró cuatro pares de texto pequeño por debajo de 4.5:1; se oscurecieron exclusivamente esos cuatro colores, con ratios finales entre 5.49 y 6.05 y sin cambios en topbar/layout. Se ejecutaron pruebas dirigidas y build posteriores. Esta comprobación no se denomina certificación completa de accesibilidad.
