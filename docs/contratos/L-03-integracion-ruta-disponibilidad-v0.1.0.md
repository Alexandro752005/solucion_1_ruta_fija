# L-03 — Integración de ruta y disponibilidad

Estado: implementación verificada en alcance funcional local, con piloto bloqueado. Complementa el contrato L-03 y el catálogo v0.1.0; no altera el registro fuente ni autoriza L-04. [Matriz final](../evidencia/L-03/matriz-final-funcional-local-2026-10-19.md).

## Ruta capturada y cambio explícito

- Toda nueva alta HTTP de asignación exige `routeVersionId`: versión vigente de ruta activa del tenant. Origen/terminal deben corresponder a sus paradas. Se guarda la referencia y un recibo histórico. Las asignaciones anteriores conservan `LEGACY_UNRECORDED`; no se inventa una ruta retrospectiva.
- Revisar/retirar el catálogo conserva las versiones ya capturadas. Un PATCH administrativo no puede sustituir origen/terminal de una ruta capturada. El modo de respuesta de cada asignación continúa inmutable.
- `POST /assignments/{id}/route-replacement`: ADMIN, versión esperada, versión nueva, motivo sin PII y clave idempotente. Antes del inicio y del horario previsto, cancela la programación anterior y crea una sustituta `MOBILE_CONFIRMATION` con los mismos recursos/intervalo y nueva aceptación pendiente. Conserva la aceptación anterior, su ruta y el enlace entre programaciones. Ambos cambios y el recibo son una transacción; un fallo no debe confirmar una cancelación parcial. El plazo nuevo es el menor entre diez minutos y el instante inmediatamente anterior al inicio.
- `GET /assignment-route-operations/{key}` resuelve una respuesta perdida sin repetir la mutación. Clave reutilizada con otro contenido: conflicto. La creación también compara su huella; un registro histórico sin huella exige consulta, no equivalencia inventada.
- CRM muestra versión/paradas/historia y permite el cambio expreso. App consulta únicamente la ruta de su asignación; no edita catálogo. Retirar una ruta no elimina el servicio registrado.

## Declaración y corroboración

`Ruta finalizada` exige participación propia, aceptación auténtica e inicio. Guarda actor, versión, hora del servidor y resultado de terminal. Un cierre ADMIN no se etiqueta como declaración CONDUCTOR. Para cesar un servicio ya iniciado, la consulta/identidad móvil propia sigue siendo necesaria; la pérdida posterior de elegibilidad no concede nuevos servicios ni impide por sí sola registrar el cese. Los demás comandos mantienen sus gates operativos.

Se comprueba automáticamente el punto efímero propio disponible contra el terminal de la versión capturada: radio 200 m, antigüedad máxima 120 s, precisión horizontal máxima 100 m. Límites inclusivos; punto futuro, inválido, ausente o terminal sin configurar no corrobora. `DISCREPANCY`, `CORROBORATED` y razones `NOT_CORROBORATED_*` se distinguen del hecho declarado. No se captura GPS nuevo ni se acredita todo el trayecto. Seguimiento/retención de puntos pertenece a L-05; calibración/dispositivo físico, L-08.

V32 es aditiva: recibos de sustitución/declaración inmutables, guardas de tenant/participación y bloqueo de sobrescritura de versión capturada. Los registros previos siguen sin declaraciones inventadas. Aplicada solo a `ruta_fija_test`, con ledger y evidencias anteriores preservados.

## Disponibilidad propia

V31 dejó dos columnas inicialmente nulas: declaración y fecha del servidor. Activar, reservar, iniciar, terminar, suspender, desconectar y cerrar sesión no fabrican una declaración. `GET /mobile/availability` distingue declaración, estado calculado y posibilidad de cambio; los gates móviles existentes siguen vigentes.

Solo CONDUCTOR envía su intención explícita en línea. Puede declarar DISPONIBLE, DESCANSO o NO_DISPONIBLE; nunca RESERVADO/EN_SERVICIO. Declarar DISPONIBLE requiere elegibilidad; el alta de un servicio exige esa declaración previa. Cambiar a descanso/no disponible no cancela compromisos aceptados y se rechaza mientras existan. ADMIN debe usar suspensión motivada y auditada: el endpoint legado de disponibilidad ya no suplanta al conductor.

Se conserva el ledger histórico 1/3/1. Los fixtures nuevos de HTTP/PG son sintéticos y transaccionales con rollback; no hay nuevas cuentas comprometidas ni recuperación de cupos.

## Límites de evidencia

Los resultados HTTP/PG de MockMvc no acreditan navegador, proceso TCP independiente, GPS físico ni participantes reales. Nueve casos de ruta/asignación y ocho de declaración correctos en el [archivo final](../evidencia/L-03/aceptacion-V32/README.md), además de cuatro de geometría y pruebas CRM/Flutter. El caso conectado acredita navegador CRM y rechazo móvil por rol sobre TCP; no acredita consulta móvil positiva TCP. Modelos/widgets Flutter y backend/PG cubren esta consulta por separado.

Se relee y bloquea el conductor después del lock de organización antes de aceptar o declarar; dos regresiones adversas comprueban estados de ocupación almacenados en caché. Las seis carreras reales de base del lote se enumeran separadamente: no se presenta esa simulación de caché como dos clientes concurrentes. La comprobación real de logout verifica revocación persistida y petición posterior en contexto de persistencia nuevo; limpiar la caché del fixture no modifica el flujo normal ni las credenciales.
