# L-04 — Retorno compatible local

2026-10-19. Solo instalación de pruebas autorizada. [Evidencia conectada](../evidencia/L-04/conectado-bbd70822-8f52-45a6-b617-9b896ff90a2c/resultado.json), [gate específico](../evidencia/L-04/incremento-3-servicios/regresion/TEST-pe.rutafija.qualification.QualificationReturnGateIntegrationTest.xml).

1. Conservar V35 y sus datos. No SQL inverso, limpieza global ni restauración de un binario antiguo que permita ADMIN_DIRECT nuevo.
2. Detener emisión/reasignación nuevas mediante `app.qualification.operations-enabled=false`, usando las mismas clases compiladas, Flyway deshabilitado y JPA validate en `ruta_fija_test`. Mantener correo simulado. No trasladar este procedimiento a desarrollo/piloto por inferencia.
3. Conservar consulta autenticada propia, historia, recibos y períodos. La deshabilitación impide nueva elegibilidad y ofertas; no acredita títulos ni acepta por CONDUCTOR. La retirada administrativa motivada de compromisos preinicio sigue siendo una acción explícita; no termina automáticamente una prestación física.
4. Una reoferta fallida después de retirar la anterior debe revertir la transacción completa. El ensayo HTTP comprobó igualdad del servicio/ofertas antes y después del409. El cuerpo específico del409 no se archivó: la prueba nativa complementaria verifica el blocker `OPERATIONAL_FEATURE_DISABLED` y la denegación de habilitación con ese flag.
5. Corregir la causa y revalidar antes de habilitar otra vez. Consultar operaciones inciertas; no reenviar por rutina, renovar plazos ni copiar aceptaciones.
6. Preservar contadores, términos y final3. Las limpiezas solo son retiro exacto o rollback. Una liberación de lock de fixture no habilita TRUNCATE.

El retorno se ensayó con dos contextos del backend cargando las mismas clases compiladas y base real, no mediante despliegue o ejecución de un JAR antiguo. JAR/CRM/APK se empaquetaron localmente; no se publicó infraestructura ni se validó Android físico. Piloto bloqueado y L-05 exige OK VERDE separado.
