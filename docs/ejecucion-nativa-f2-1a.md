# F2.1A — Línea base recuperable pre-V6

Este control protege la base de desarrollo antes de que exista la migración de
roles V6. Genera un dump custom de PostgreSQL y prueba una restauración completa
en una base nueva. No usa Docker, no muestra contraseñas y no sobrescribe bases
ni dumps existentes.

## Requisitos

- PostgreSQL 16 local, accesible únicamente por 127.0.0.1:5432.
- Configuraciones privadas e ignoradas:
  backend/.local/ruta-fija-native.env y
  backend/.local/ruta-fija-bootstrap.env.
- API y CRM detenidos: los puertos 8080 y 4200 deben estar libres.
- Desarrollo en solucion_ruta_fija_1 con Flyway V1–V5 y sin el rol ADMIN
  persistido todavía.

## Ejecución

~~~powershell
.\scripts\Invoke-RutaFijaF21aBaseline.ps1
~~~

Si ya existe una recuperación de la misma fecha, indique un destino nuevo:

~~~powershell
.\scripts\Invoke-RutaFijaF21aBaseline.ps1 -RecoveryDatabase ruta_fija_recovery_YYYYMMDD_f21a_r2
~~~

## Qué comprueba

1. La fuente es exactamente la base de desarrollo local autorizada.
2. Las configuraciones privadas no están versionadas.
3. No hay sesiones activas de negocio ni runtime web antes del dump.
4. Flyway está en V1–V5, UTC, UTF8 y el constraint de rol aún representa el
   estado anterior a V6.
5. El dump se puede leer con pg_restore, tiene checksum SHA-256 y se restaura
   en una base nueva con UTC.
6. El manifiesto de la fuente no cambia durante el proceso y coincide con el
   manifiesto restaurado, incluidos conteos por rol, refresh tokens,
   group_coordinator y auditoría.

La recuperación queda preservada como evidencia. No se inicia el CRM contra
ella y eliminarla requiere una decisión explícita posterior.

Una ejecución aprobada termina así:

~~~text
F2_1A_BASELINE=PASS source=solucion_ruta_fija_1 recovery=...
flyway=V1-V5 ... restore=identical docker=0
~~~

