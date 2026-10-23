# L-05 — Retorno compatible local

2026-10-20. [Contrato](../contratos/L-05.md), [caso conectado autorizado](L-05-caso-conectado-autorizacion.md), [resultado](../evidencia/L-05/conectado-844b3bf6-f2fa-4666-bd0e-b1f7a1188696/resultado.json).

1. Conservar V42, sus guardas, datos, tramos originales, recibos e historial. No ejecutar migraciones inversas ni usar un binario antiguo que permita el inicio ADMIN o evada la aceptación del CONDUCTOR.
2. Para el retorno funcional ensayado, cargar las mismas clases compiladas con `app.qualification.operations-enabled=false`, Flyway deshabilitado y JPA validate, exclusivamente en `ruta_fija_test`. Correo simulado, tareas programadas deshabilitadas y sin despliegue.
3. Comprobar que la evaluación de un conductor preparado indique `OPERATIONAL_FEATURE_DISABLED` y que una oferta nueva sea rechazada HTTP409 por ese motivo específico. Una colisión de idempotencia no acredita este control.
4. Mantener consulta propia autenticada y cierre seguro por el conductor del servicio ya iniciado. El ensayo conservó un servicio realmente `IN_SERVICE`, consultó su oferta, completó por HTTP y contrastó el estado `COMPLETED` y su tramo original cerrado bajo el flag deshabilitado. No inicia ni completa ADMIN por el conductor.
5. Ante respuesta incierta, consultar estado/recibo del mismo caso. No crear otra oferta, reiniciar cupos o rehabilitar cuentas por rutina. Revisar la causa y las condiciones antes de habilitar operaciones nuevamente; nunca inferir que esto habilita el piloto.
6. Archivar evidencia antes del retiro exacto. Deshabilitar solo los participantes y recursos autorizados del caso, revocar sesiones y conservar ledger, aceptaciones, originales e historial. Una liberación de protección del caso no autoriza limpieza global.

## Resultado y límites

El campo `returnResult` del resultado conectado acredita el rechazo específico y la consulta/cierre propios. Se usaron dos contextos de Spring sobre las mismas clases y PostgreSQL real, sin cambiar el esquema ni ejecutar otro JAR. El primer intento rechazó por una clave de creación previamente utilizada; se conserva como antecedente fallido y no se presenta como prueba del flag. La continuación del mismo caso eliminó esa interferencia y verificó el blocker específico.

Esto acredita compatibilidad funcional local. No acredita restauración de infraestructura, despliegue, Android físico ni continuidad de red real del piloto. Permanecen los bloqueos operativos del piloto y el OK VERDE separado para L-06.
