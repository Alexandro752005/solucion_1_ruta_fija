# F1.6 — Seguridad local y documentación nativa

F1.6 consolida la operación sin dependencia local de contenedores y protege la
instancia PostgreSQL 16 usada por Ruta Fija.

## Controles aplicados

- PostgreSQL debe escuchar solamente en 127.0.0.1 y ::1 por el puerto 5432.
- Las reglas HBA de red deben usar SCRAM y permitir únicamente loopback.
- rf_migrator, rf_app y rf_test conservan login sin privilegios de superusuario,
  creación de bases, creación de roles, replicación o bypass RLS.
- Los archivos privados de backend/.local, runtime y backups se excluyen de
  Git.
- README, manual, tareas VS Code y CI usan el camino nativo.

## Activar el loopback

Este paso requiere elevar una consola porque reinicia el servicio PostgreSQL de
Windows. Abra PowerShell o VS Code como Administrador en la raíz del proyecto:

~~~powershell
.\scripts\Set-RutaFijaPostgresqlLoopbackF16.ps1
~~~

El script usa ALTER SYSTEM para ajustar listen_addresses y luego reinicia
postgresql-x64-16. No cambia datos, tablas, usuarios de aplicación ni
migraciones.

Después, ejecute la auditoría de solo lectura:

~~~powershell
.\scripts\Test-RutaFijaNativeSecurityF16.ps1
~~~

La salida correcta termina con:

~~~text
F1_6_SECURITY_AUDIT=PASS listener=loopback hba=scram roles=least-privilege secrets=not-tracked ci=native
~~~

## Rollback controlado

Solo si fuera necesario revertir la escucha local, un administrador puede
ejecutar en PostgreSQL:

~~~sql
ALTER SYSTEM RESET listen_addresses;
~~~

Después debe reiniciar el servicio PostgreSQL. El rollback no elimina datos,
pero no debe usarse para publicar la base sin una revisión de red y seguridad.

## Tarea Visual Studio Code

La tarea Ruta Fija: aplicar PostgreSQL solo loopback (F1.6, administrador)
requiere que VS Code haya sido iniciado como Administrador. La tarea posterior
Ruta Fija: auditar seguridad local (F1.6) no modifica estado.
