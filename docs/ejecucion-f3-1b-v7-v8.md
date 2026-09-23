# F3.1B — Migraciones V7/V8 para contrato móvil

## Alcance

Esta fase materializa exclusivamente el diseño de F3.1A en PostgreSQL 16
nativo. V7 añade los modos `ADMIN_DIRECT` y `MOBILE_CONFIRMATION` y los campos
de respuesta futura de `assignment`. V8 crea `driver_current_location`, una
sola ubicación vigente por conductor y tenant, sin historial de recorridos.

No implementa sesión móvil, rutas `/mobile`, aceptación o rechazo de conductor,
pantallas Angular adicionales, Flutter, notificaciones ni GPS en segundo plano.
El CRM mantiene la creación directa existente; no puede fingir una respuesta de
conductor.

## Precondiciones

- PostgreSQL 16 local en `127.0.0.1:5432`; no se usa Docker.
- Configuraciones privadas nativa y bootstrap existentes e ignoradas por Git.
- Desarrollo en V1–V6 antes de la aplicación protegida.
- API y CRM detenidos; los puertos 8080 y 4200 deben estar libres.
- No haya cambiado la información de desarrollo desde el ensayo.

## Secuencia obligatoria para una base existente en V6

Desde la raíz del repositorio, primero haga el ensayo aislado:

~~~powershell
.\scripts\Invoke-RutaFijaF31bV7V8Rehearsal.ps1
~~~

El comando crea un `pg_dump` pre-V7/V8 dentro de `backups/f3-1b/`, restaura una
base nueva con el patrón `ruta_fija_recovery_YYYYMMDD_f31b`, verifica el
fixture de V6 y guarda un manifiesto con hashes de las migraciones. No modifica
la base de desarrollo.

Solo si el ensayo termina en `F3_1B_REHEARSAL=PASS`, aplique las mismas copias
certificadas:

~~~powershell
.\scripts\Invoke-RutaFijaF31bV7V8Migration.ps1
~~~

La aplicación compara el manifiesto, hash, lista de archivos y huella de datos
antes de invocar Flyway. Después concede a `rf_app` únicamente DML sobre
`driver_current_location`, sin permiso `CREATE` en el esquema. El comando es
de un solo uso: si la base ya está en V8, se detiene para impedir una segunda
aplicación.

Finalmente, antes de incorporar F3.2, valide el contrato de F3.1B:

~~~powershell
.\scripts\Test-RutaFijaF31bMobileSchema.ps1
.\verificar_ruta_fija.bat
~~~

La auditoría admite que `ruta_fija_test` esté vacía, en V6 previa a las pruebas
o en V8. Una migración repetible exitosa de privilegios de pruebas no se
confunde con una versión Flyway faltante.

> Esta auditoría es una evidencia histórica de la frontera pre-sesión: por
> diseño rechaza cualquier ruta `/mobile/`. Una vez implementada F3.3 no debe
> usarse como control actual; ejecute
> `Test-RutaFijaF33MobileOperations.ps1`, que valida V1-V10, rutas propias,
> idempotencia y privacidad de ubicación.

## Resultado esperado

~~~text
F3_1B_SCHEMA_AUDIT=PASS flyway=V1-V8 response_modes=ADMIN_DIRECT,MOBILE_CONFIRMATION location=current_only privileges=DML_without_DDL mobile_endpoints=0 docker=0
F3_1B_NATIVE_VERIFY=PASS flyway=V1-V8 docker=0
~~~

Para una instalación nueva y vacía del árbol actual,
`Invoke-RutaFijaFlywayF13.ps1` aplica V1–V10. Después ejecute
`Grant-RutaFijaApplicationPrivilegesF13.ps1` y
`Test-RutaFijaF33MobileOperations.ps1`. El ensayo protegido F3.1B se conserva
para una base histórica exactamente en V6; no use los auditores históricos
como control actual.

## Recuperación y límites

El dump y la base de recuperación son evidencia privada local y están excluidos
de Git. No se borran ni se reutilizan automáticamente. Si se requiere volver a
V6, se restaura primero el dump en una base de recuperación nueva y se valida
allí; no se sobrescribe desarrollo mediante un atajo. V7/V8 no eliminan datos
históricos de las tablas existentes.
