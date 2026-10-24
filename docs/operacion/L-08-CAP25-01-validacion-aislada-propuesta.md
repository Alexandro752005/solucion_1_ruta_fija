# L08-CAP25-01 — Carga25 y concurrencia en una instancia sintética aislada

Versión0.1.0, 2026-10-22. **PROPUESTA; no ejecutada ni autorizada por el perfil de carga.** Referencia `860c1cdb-b062-469a-9a3f-156ea23bb656`.

## Motivo y límites de autorización

El perfil CAP-L08-01 ya está aprobado:1 SUPER_ADMIN,4 ADMIN y20 CONDUCTOR, hasta cuatro organizaciones operativas. La autorización de ese perfil no permite crear el dataset persistente. El contrato L-08 y la propuesta de restauración exigen identificar y autorizar su destino antes de escribir.

El test compartido conserva ledger1/3/7. Sus cuentas retiradas y casos L01…08 no se reactivarán ni eliminarán para generar carga. La carrera integral de retiro también necesita que sus recursos y planes sean visibles para conexiones independientes; una transacción que siempre se revierte no proporciona esa visibilidad.

## Destino concreto propuesto

| Elemento | Valor |
|---|---|
| Instancia nativa nueva | Solo `127.0.0.1:55439`; sin servicio Windows o acceso remoto |
| Base nueva | `ruta_fija_test` dentro de esa instancia, no la de5432 |
| Directorio privado | `backend/.local/l08-cap25-860c1cdb-b062-469a-9a3f-156ea23bb656/cluster/data` |
| Backend de ensayo | Solo `127.0.0.1:4314`, con transporte de correo simulado |
| Migraciones | Copia del esquema vigente mediante migrador propio de esa instancia, Flyway validado; sin restaurar cuentas de casos anteriores |
| Roles de BD | Migrador y runtime separados, sin reutilizar contraseñas de5432; secretos temporales protegidos localmente, sin argumentos o evidencia |
| Dataset máximo | Un SUPER_ADMIN, cuatro ADMIN y veinte CONDUCTOR sintéticos; cuatro organizaciones operativas |

El puerto55439 no estaba escuchando al preparar esta propuesta; se volverá a comprobar antes de crear. El lanzador deberá negarse ante destino/puerto ocupado, manifiesto ajeno, ruta con enlace, esquema inesperado o falta de la autorización exacta. La validación habitual de `NativePostgresTestDatabase` seguirá admitiendo únicamente5432; el arnés de este caso tendrá comprobaciones independientes y cerradas a55439. No se añadirá un fallback o un puerto configurable al cargador habitual.

## Trabajo autorizado por la eventual confirmación

1. Preparar y arrancar exclusivamente esa instancia y base. Ninguna conexión de escritura a5432,55438, desarrollo o piloto. No restaurar ni modificar los destinos de REST-01/02.
2. Construir el dataset por los flujos de prueba previstos y mecanismos normales de alta, activación, términos, identidad sintética, incorporación, revisión, aprobación y habilitación. Mantener seguridad, cupos históricos1/4/20, aislamiento, documentos y estados. Las aceptaciones automáticas serán exclusivamente sintéticas y registrarán su origen.
3. Crear recursos sintéticos suficientes y fechas compatibles, sin reutilizar cuentas cerradas ni inventar excepciones a disponibilidad, mantenimiento, documentos o solapamientos. Conservar ledger e historial del dataset; ningún reinicio de cupos, eliminación o creación sucesiva para recuperar una prueba fallida.
4. Medir creación/aceptación válidas con los25 actores del perfil. Separar calentamiento, errores, negativas de seguridad, muestras y percentiles; registrar condiciones del equipo, conexiones, concurrencia efectiva y p95 hasta respuesta y commit durable comprobado por otra conexión. No contabilizar un HTTP200 simulado ni una transacción revertida como persistencia durable. Correo y GPS serán simulados y se indicará expresamente; no acredita25 personas o25 equipos reales.
5. Ejecutar carreras de oferta/aceptación y de retiro mediante conexiones reales y recursos exclusivos nuevos de este dataset, conservando planes, tombstones, auditoría y pruebas negativas de aislamiento. Repetir la misma referencia ante fallos recuperables, sin fabricar otro caso para sustituirlo.
6. Al terminar, archivar resultados y huellas saneadas; retirar selectivamente las cuentas y recursos del dataset, conservar todos los contadores/antecedentes y detener únicamente sus procesos demostrablemente propios. No borrar automáticamente la instancia. Un fallo conserva datos y bloqueos hasta diagnóstico.

## Exclusiones y condiciones restantes

No autoriza correo externo, ubicación física, otros participantes o cuentas reales, claves definitivas, despliegue, acceso público, medios/servicios con coste, modificación de final3 ni Git/GitHub. No acredita batería/TalkBack, respaldo independiente, monitoreo fuera del host, cobertura24/7, RPO o RTO completo. No cambia criterios de aceptación ni garantiza aprobar la carga por el hecho de ejecutarla.

La confirmación solicitada será únicamente **«Autorizar L08-CAP25-01 con esos límites»**. Hasta entonces se permite preparar código y pruebas puras del arnés, pero no crear la instancia, base o dataset.
