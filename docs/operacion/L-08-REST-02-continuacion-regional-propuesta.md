# L08-REST-02 — Continuación del mismo respaldo con configuración regional compatible

Versión0.1.0, 2026-10-22. **PROPUESTA; sin ejecución autorizada todavía.**

El caso L08-REST-01 c748e5ab-c20a-43d6-9c17-af5d0a703dab se conserva. El respaldo cifrado V48 tiene SHA-256 `6e914b7a79bf5c705d52aebc8e1c2b768f0d61f695265738e330bad2ef727dee`. El origen5432 usa UTF8 y `Spanish_Peru.1252` para LC_COLLATE/LC_CTYPE; la base restaurada55438 usa `C`. No se declara recuperación íntegra: después de corregir una comparación de números JSON quedan diferencias de huella en ACL, índices y constraints, cuyo origen aún debe contrastarse tras igualar la configuración regional. No se atribuyen todas esas diferencias a la región sin comprobarlo.

## Cambio concreto solicitado

Crear **solo `ruta_fija_test_l08_locale@127.0.0.1:55438`**, en la instancia aislada existente y mismo directorio/caso; `TEMPLATE template0`, UTF8, proveedor libc, LC_COLLATE y LC_CTYPE `Spanish_Peru.1252`. Mantener intacta la restauración anterior `ruta_fija_test@55438`, el origen compartido5432, desarrollo y piloto. No borrar ni recrear bases para repetir.

Restaurar el mismo archivo autenticado AES-GCM, con los roles/credenciales temporales ya existentes y sin otro respaldo/envío/alta. Guardas: destino nuevo ausente, puerto/instancia/directorio exactos, exclusión de otros casos activos, lock del lanzador, timeout, transacción única, rechazo de nueva ejecución si ya está preparado, comparación completa de filas/esquema/ACL. Mantener el caso si falla; no reintentar por rutina.

El [lanzador preparado](../../scripts/Invoke-RutaFijaL08LocaleRestore.ps1) ejecuta solo Plan si no recibe switches. Tras autorización explícita, el comando sería `./scripts/Invoke-RutaFijaL08LocaleRestore.ps1 -ExecuteLocale -AuthorizeL08Rest02`. Su éxito SQL no acredita todavía recuperación del servicio/RTO4h, RPO horario, respaldo independiente ni piloto. La versión restaurada seguirá siendo V48 del snapshot, mientras el test compartido actual está en V50; no se migrará este nuevo destino por inferencia.

PostgreSQL documenta que la configuración regional afecta ordenación e índices, y permite crear una base con región distinta usando template0: [CREATE DATABASE16](https://www.postgresql.org/docs/16/sql-createdatabase.html). También exige preparar usuarios/permisos y distingue restauración transaccional: [SQL Dump16](https://www.postgresql.org/docs/16/backup-dump.html). Esta propuesta conserva toda diferencia pendiente como no verificada.
