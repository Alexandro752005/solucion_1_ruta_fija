# Evidencia F2.1B — ensayo aislado de V6

Fecha local: 2026-09-22, Lima.

## Ejecución certificada

| Elemento | Evidencia |
| --- | --- |
| Fuente restaurada | backup F2.1A de solucion_ruta_fija_1 |
| SHA-256 del backup | de413296e4ce9bd41e2e37a478af8672dd1560a76062bda714808ef696542eaf |
| Destino certificado | ruta_fija_recovery_20260922_f21b_r2 |
| Flyway previo | V1–V5 |
| Flyway posterior | V1–V6 |
| SHA-256 de candidata V6 | 9be06c6d0267e055e4db75c16aa15774f6dad7cbdfc0c69d0282f1aac6f8b622 |
| Resultado | F2_1B_FLYWAY=PASS y F2_1B_V6_REHEARSAL=PASS |

## Fixture y resultado

| Regla | Antes de V6 | Después de V6 |
| --- | --- | --- |
| ADMINISTRADOR | 2 | 0 |
| COORDINADOR | 2 | 0 |
| ADMIN | 0 | 4 |
| SUPER_ADMIN | 1 | 1 |
| CONDUCTOR | 1 | 1 |
| Total de cuentas | 6 | 6 |
| Refresh activos de cuentas migradas | 4 | 0 |
| Refresh activos de SUPER_ADMIN/CONDUCTOR | 2 | 2 |
| group_coordinator | 1 | 1 |
| Auditoría histórica con COORDINADOR | Conservada | Conservada |

También se intentó insertar un COORDINADOR después de V6: PostgreSQL lo
rechazó mediante el constraint final. La fila histórica previamente revocada
conservó su timestamp de revocación.

## Integridad y alcance

- Desarrollo continuó exactamente en V1–V5; no se cambió ningún rol real.
- La candidata V6 está aislada en scripts/flyway/f2-1b y no se carga por el
  backend todavía.
- El primer destino sin sufijo,
  ruta_fija_recovery_20260922_f21b, se preservó como intento no certificado:
  Flyway aplicó V6, pero el verificador tenía un formato de fecha incorrecto.
  No afectó desarrollo ni contiene datos reales. El destino _r2 es la única
  evidencia certificada.
- group_coordinator no se eliminó ni se modificó; su retiro de autorización y
  escrituras pertenece a F2.2.
