# Evidencia F1.7 — recuperación post-migración

Fecha local de ejecución: 2026-09-21, Lima.

## Ejecución certificada

| Elemento | Evidencia |
| --- | --- |
| Fuente | solucion_ruta_fija_1 |
| Destino nuevo | ruta_fija_recovery_20260922_f17_r2 |
| Archivo | backups/f1-7/solucion_ruta_fija_1_20260922T034455Z_f17.dump |
| SHA-256 | 045f49fb4422a66d45c0375bf288b342387454dafc9b0e7b5f7a0a3a1bae525b |
| Resultado | F1_7_RECOVERY=PASS |

El timestamp del archivo y de la base está en UTC; corresponde a la noche local
del 2026-09-21.

## Comparación aprobada

- Flyway V1–V5 restaurado.
- 12 tablas de negocio con los mismos conteos que desarrollo.
- UTC y UTF8 conservados.
- Extensión btree_gist activa.
- Constraints V5 de conductor y vehículo presentes y semánticamente válidos.
- Trigger trg_audit_event_append_only restaurado.
- Fuente comprobada sin cambios antes y después del dump.

## Regresión posterior

La verificación nativa posterior aprobó:

~~~text
F1_4_NATIVE_VERIFY=PASS unit=21 integration=13 flyway=V1-V5 docker=0
F1_5_PROXY_SMOKE=PASS rest=login,me,refresh websocket=ready,replay-rejected docker=0
~~~

## Nota de trazabilidad

Un primer destino de recuperación sin sufijo quedó preservado durante la
elaboración del validador. PostgreSQL normalizó una representación textual de
los constraints durante pg_restore; no se certificó ese intento. La ejecución
con sufijo r2 validó los constraints por nombre y semántica, y es la única
evidencia F1.7 aprobada. Ninguna de las bases de recuperación se utiliza para
ejecutar el CRM.
