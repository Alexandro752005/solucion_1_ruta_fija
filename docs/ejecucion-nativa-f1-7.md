# F1.7 — Backup y recuperación post-migración

F1.7 genera evidencia real de que el esquema nativo migrado puede respaldarse y
restaurarse sin usar servicios externos ni alterar desarrollo.

## Ejecución

Detenga el CRM y la API antes de iniciar:

~~~powershell
.\finalizar_ruta_fija.bat
.\scripts\Invoke-RutaFijaRecoveryEvidenceF17.ps1
~~~

El script crea:

- Un dump custom de pg_dump en backups/f1-7, carpeta ignorada por Git.
- Una base nueva con formato ruta_fija_recovery_YYYYMMDD_f17.
- Una restauración mediante pg_restore ejecutada por rf_migrator.

No borra, renombra ni sobrescribe ninguna base o dump existente. Si necesita
una segunda evidencia el mismo día, asigne un sufijo seguro:

~~~powershell
.\scripts\Invoke-RutaFijaRecoveryEvidenceF17.ps1 -RecoveryDatabase ruta_fija_recovery_YYYYMMDD_f17_r2
~~~

## Validaciones

Antes de crear el destino, F1.7 exige:

- API y CRM detenidos.
- Configuración privada ignorada por Git.
- Fuente exactamente en solucion_ruta_fija_1.
- Sin sesiones activas o transacciones pendientes en desarrollo.
- Catálogo fuente con Flyway V1–V5, 12 tablas, UTC, UTF8, btree_gist,
  constraints de exclusión V5 y trigger append-only.

Después de restaurar, compara fuente y destino para los mismos elementos,
incluidos los conteos de todas las tablas de negocio. Los constraints se
validan por nombre y semántica V5; PostgreSQL puede normalizar su representación
textual al ejecutar pg_restore.

Una ejecución certificada termina con:

~~~text
F1_7_RECOVERY=PASS database=ruta_fija_recovery_YYYYMMDD_f17 backup=backups\f1-7\... sha256=...
flyway=V1-V5 tables=12 btree_gist=activo constraints_v5=iguales audit=append-only
~~~

La base de recuperación se conserva para auditoría. No se inicia la API contra
ella y su eliminación requiere una decisión explícita posterior.
