# Evidencia F1.6 — seguridad local nativa

Fecha: 2026-09-22, Lima.

## Resultado aprobado

~~~text
F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native
~~~

## Controles confirmados

- PostgreSQL 16 escucha únicamente por loopback.
- Las reglas HBA de red usan SCRAM.
- rf_migrator, rf_app y rf_test no poseen privilegios de superusuario,
  administración de roles, administración de bases, replicación ni bypass RLS.
- Los secretos locales y datos de runtime no están versionados.
- README, manual, tareas VS Code y CI siguen el camino nativo.

## Corrección del auditor

La primera ejecución detectó dos defectos de comparación en el script F1.6:
la representación textual de booleanos de PostgreSQL y una etiqueta con tildes
en Windows PowerShell. Se corrigieron en el auditor; los atributos reales de
los tres roles fueron consultados en modo lectura y ya cumplían la política.
No se modificaron roles, contraseñas, datos ni migraciones.
