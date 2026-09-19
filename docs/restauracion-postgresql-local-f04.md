# Restauración local PostgreSQL — F0.4

F0.4 probó la recuperabilidad del respaldo F0.3 usando exclusivamente las herramientas nativas de PostgreSQL 16. No se utilizó Docker, Compose ni Testcontainers.

## Aislamiento aplicado

| Rol | Base |
|---|---|
| Origen de desarrollo | `solucion_ruta_fija_1` |
| Recuperación temporal | `ruta_fija_recovery_20260919_f04` |

La base de recuperación se creó desde `template0`, con codificación UTF8 y un nombre distinto del origen. La restauración usó `pg_restore --exit-on-error --single-transaction` sobre el destino; no utilizó `--clean` ni `--create`.

## Resultado de comparación

| Indicador | Origen | Recuperación |
|---|---:|---:|
| Codificación | UTF8 | UTF8 |
| Tablas en `public` | 0 | 0 |
| Historial Flyway | Ausente | Ausente |
| Extensión `btree_gist` | Ausente | Ausente |
| Constraints en `public` | 0 | 0 |
| Tablas de auditoría | 0 | 0 |

El SHA-256 del archivo restaurado se mantuvo en `8F0B9DCEE89D685C747A18E7BC7E01BEC006309FC20CD945BE83F570AD9AC1C5`.

Los ceros son esperados: F0.3 respaldó el estado inicial vacío de la base nativa. Esta prueba valida el archivo y el procedimiento de recuperación aislado, pero no valida aún las migraciones V1–V5, `btree_gist`, constraints de V5 ni la auditoría append-only. La base temporal se conserva y no debe utilizarse como base de desarrollo.
