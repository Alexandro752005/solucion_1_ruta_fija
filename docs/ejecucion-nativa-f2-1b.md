# F2.1B — Ensayo aislado de Flyway V6

F2.1B prueba la candidata V6 de consolidación hacia ADMIN sin aplicar la
migración en solucion_ruta_fija_1 ni en ruta_fija_test.

## Ejecución

Con API y CRM detenidos:

~~~powershell
.\scripts\Invoke-RutaFijaF21bV6Rehearsal.ps1
~~~

Si ya existe una base de ensayo del mismo día, indique un sufijo nuevo:

~~~powershell
.\scripts\Invoke-RutaFijaF21bV6Rehearsal.ps1 -RecoveryDatabase ruta_fija_recovery_YYYYMMDD_f21b_r2
~~~

## Proceso protegido

1. Localiza el único dump aprobado de F2.1A dentro de backups/f2-1a.
2. Restaura el dump en una base nueva de recuperación F2.1B.
3. Comprueba que la copia V5 está vacía antes de insertar datos controlados.
4. Inserta seis cuentas: dos ADMINISTRADOR, dos COORDINADOR, un SUPER_ADMIN y
   un CONDUCTOR; además incluye tokens, una fila group_coordinator y auditoría
   histórica.
5. Ejecuta la candidata V6 mediante Flyway real, con el runner restringido a
   la recuperación temporal.
6. Valida cuentas, constraint, revocación selectiva de refresh tokens,
   conservación de group_coordinator y auditoría histórica.
7. Comprueba que desarrollo no cambió y que V6 continúa fuera del classpath
   operativo hasta F2.2.

La base de ensayo se preserva. El script no borra ni sobrescribe bases, dumps o
archivos existentes.

Una ejecución aprobada termina así:

~~~text
F2_1B_FLYWAY=PASS migration=V6 target=isolated-recovery
F2_1B_V6_REHEARSAL=PASS fixture=... backup=... sha256=...
~~~
