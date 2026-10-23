# Integridad del snapshot restaurado: contraste de solo lectura

Caso `c748e5ab-c20a-43d6-9c17-af5d0a703dab`, observado el 2026-10-22T22:11:16.599176100Z. Destino autorizado: `ruta_fija_test_l08_locale@127.0.0.1:55438`, UTF8 y Spanish_Peru.1252. Las dos restauraciones y sus fallos iniciales se conservan.

**Resultado: PASS_READ_ONLY_RECONCILED_SQL_SNAPSHOT_INTEGRITY_NOT_SERVICE_RTO.** Se comparó el conjunto completo con las huellas originales del snapshot V48; no se omitieron tablas ni categorías de metadatos. No se escribió en las bases, no se accedió a5432 y no se repitió la restauración.

## Diferencias explicadas y comprobadas

| Categoría | Diferencia observada | Comprobación aplicada |
|---|---|---|
| CHECK y predicados de índices | `pg_restore` reconstruye casts de arrays literales en otra representación SQL. | Transformación reversible limitada a los casts literales revisados:74 restricciones y7 índices. Se conservan valores, orden, operadores y todos los demás tokens. La comparación exige las huellas originales completas. |
| Dos CHECK de V46 reemplazados posteriormente en V49 | El origen actual V50 no permite inferir su representación anterior. | Solo cuatro combinaciones de representación de los dos CHECK, conservando todo su contenido; una coincide con la huella original V48. No se sustituyen por las reglas actuales. |
| ACL de `retention_execution_authority` | El propietario tiene permisos implícitos; la restauración deja `relacl` nulo en esta tabla. | Se comprueban explícitamente los siete privilegios para `rf_migrator`, y su ausencia para `rf_test` y `rf_app`; solo se expande el ACL implícito de esa tabla para comparar con el manifiesto. |
| Orden de agregación de restricciones | La expresión original concatena nombres PostgreSQL con colación `C`; reconstruirla como `text[]` heredaba Spanish_Peru.1252. | `pg_collation_for` confirma `C` en la expresión original. La agregación de restricciones reconstruidas usa expresamente esa colación. No se cambia el orden de otras categorías ni el manifiesto. |

Las98 tablas y todas las categorías de esquema coinciden después de estas comprobaciones explícitas. Se revalidaron SHA-256 y autenticación GCM del archivo original `6e914b7a79bf5c705d52aebc8e1c2b768f0d61f695265738e330bad2ef727dee`.

## Evidencias y trazabilidad

- [Resultado JSON](reconciliacion-snapshot-collation.json), SHA-256 `ee57c693b21a6b145d70e6236c2ac7937fcb38d7df1b8fd138a80c9e038134a5`.
- [Contraste estático V50/V48](diferencias-esquema-staticas.json), conservando las adiciones posteriores separadas del snapshot.
- [Fuente, clase ejecutada, lanzador y registro](prueba-integridad-lectura/manifest.sha256), SHA-256 del manifiesto `97d88cedc9f6d5caa3388d0a249a87f3e32991ea32585cc6968aa679855bd3cc`.
- Fuente mantenida en `backend/src/test/java/pe/rutafija/support/L08RestoredConstraintCollationProof.java`. Es un diagnóstico específico de este caso y no sustituye silenciosamente el comparador general ni sus manifiestos anteriores.

## Límites

Este resultado acredita integridad SQL del snapshot restaurado. **No acredita arranque y operación empresarial de la aplicación, RTO completo ≤4h, RPO ≤1h, respaldo en medio independiente ni preparación del piloto.** Los errores y tiempos de los intentos anteriores permanecen registrados; no se reinicia el cronómetro para convertirlos en un cumplimiento de RTO.

El destino está en V48 y el candidato actual en V50. No se migra el destino por inferencia ni se valida compatibilidad limitándose a responder a un endpoint de salud. Falta concretar y ejecutar una comprobación compatible del servicio y medir un ensayo completo con sus condiciones autorizadas. La matriz CI-L08-05 sigue PARCIAL.

## Comprobación posterior de arranque y salud del binario preservado

[Evidencia de ejecución](arranque-v48-solo-lectura.json), SHA-256 `f0e4a81df61e564917ed8ef225e6500965633b0aac7fc7001647e05a22a7e1b9`: **PASS_PRESERVED_V48_API_READ_ONLY_ON_RESTORED_SNAPSHOT_NOT_BUSINESS_RTO**.

Se extrajo el JAR preservado L07 V48, SHA-256 `23a949ace14161e7e3d7110be31836cc03e26c1df59549fa78e303738d5abaa1`, y se comprobó la procedencia exclusiva de sus clases de aplicación. No se mezclaron clases V50. Spring arrancó con validación Hibernate, sin migraciones, seed o tareas programadas; ambos gates activos y correo sustituido por un doble que rechaza cualquier entrega.

La API local4313 respondió HTTP200 `UP` en readiness con componentes `readinessState` y `db`, y en liveness. Pool y sesión `rf_test` de solo lectura; todas las huellas de filas y esquema iguales antes/después. Cero envíos, contexto cerrado y puerto liberado. [Fuentes y registro](prueba-arranque-v48/manifest.sha256).

Los dos primeros diagnósticos del arnés se conservan: el cast `inet_server_addr()::text` incluía la máscara del tipo inet y no coincidía con el literal del host. El diagnóstico individual identificó exactamente esa comprobación; se corrigió a `host(inet_server_addr())`, manteniendo el host exigido127.0.0.1. No fue una restauración repetida ni un fallo de integridad de los datos.

Esto acredita arranque/lectura del binario preservado contra su snapshot. **La operación empresarial completa y el RTO/RPO siguen sin acreditarse.** El arranque duró dentro del intervalo22:25:01.987Z–22:25:12.923Z; ese intervalo no sustituye el cronometraje completo desde la detección del incidente.
