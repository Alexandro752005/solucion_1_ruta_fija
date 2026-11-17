# Reversión funcional del Dashboard ADMIN

Entrega local: **26/10/2026**. Procedimiento para este mismo workspace, con dependencias existentes y fuentes que coincidan con el manifiesto. El [script](../../scripts/Restore-RutaFijaDashboardAdmin.ps1) valida hashes antes de escribir y solo sustituye tres archivos de entrada del Resumen.

La reversión recupera el Resumen ADMIN anterior, manteniendo los componentes nuevos sin invocar y la API aditiva compatible. No degrada PostgreSQL/Flyway, no cambia credenciales, términos, permisos, móvil ni reportes y no borra historial. No pretende restaurar una versión completa de todo el proyecto.

## Comprobar sin alterar

Desde la raíz:

```powershell
.\scripts\Restore-RutaFijaDashboardAdmin.ps1 -Action Validate
.\scripts\Restore-RutaFijaDashboardAdmin.ps1 -Action Plan
```

La salida esperada es `PASS_DELIVERY_HASHES_AND_RESTORE_PLAN`, `writes: 0`. Se verifica el ZIP contra `manifest-SHA256.json`, el inventario de cada entrada y los hashes de compatibilidad. SHA-256 acredita integridad frente a este manifiesto local; no es una firma criptográfica de un tercero.

Archivos permitidos:

- `frontend/src/app/features/dashboard/dashboard.page.ts`
- `frontend/src/app/features/dashboard/dashboard.page.html`
- `frontend/src/app/features/dashboard/dashboard.page.spec.ts`

Un archivo modificado después de la entrega, un vínculo que redirija el destino, un ZIP alterado o una incompatibilidad de fuentes bloquea la operación. Conservar los cambios propios y revisar la divergencia; no forzar ni modificar el manifiesto para ocultarla.

## Volver al Resumen anterior

Cerrar sesiones y utilizar los ejecutables oficiales para detener el runtime. El script rechaza la escritura si 8080/4300 siguen escuchando o permanece el estado administrado.

```powershell
.\finalizar_ruta_fija.bat
.\scripts\Restore-RutaFijaDashboardAdmin.ps1 -Action RestorePrevious
Push-Location frontend
try {
    npm.cmd run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'TYPECHECK_FAILED' }
    npm.cmd run test:ci -- --include=src/app/features/dashboard/dashboard.page.spec.ts
    if ($LASTEXITCODE -ne 0) { throw 'PREVIOUS_DASHBOARD_TEST_FAILED' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'BUILD_FAILED' }
} finally { Pop-Location }
.\iniciar_ruta_fija.bat
.\scripts\Invoke-RutaFijaNativeRuntime.ps1 -Action Test
```

Si una comprobación falla, no iniciar hasta revisarla. Antes de escribir se guardan los tres archivos actuales en una carpeta nueva `backend/.local/accesos/dashboard-admin-restore-<fecha>-<id>/`. Un error de escritura revierte los archivos escritos a esos bytes de origen. No se ejecutan borrados recursivos.

## Recuperar el Dashboard entregado

```powershell
.\finalizar_ruta_fija.bat
.\scripts\Restore-RutaFijaDashboardAdmin.ps1 -Action RestoreDelivered
Push-Location frontend
try {
    npm.cmd run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'TYPECHECK_FAILED' }
    npm.cmd run test:ci
    if ($LASTEXITCODE -ne 0) { throw 'FRONTEND_TEST_FAILED' }
    npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'BUILD_FAILED' }
} finally { Pop-Location }
.\iniciar_ruta_fija.bat
```

## Ensayo registrado

Se reconstruyeron las tres fuentes anteriores a la fase 3, contrastadas con sus hashes originales, dentro de una **copia privada aislada del frontend**. Aprobaron tipos, las **3 pruebas** del Resumen anterior y la compilación de producción. No se sustituyeron las fuentes activas ni se reinstalaron dependencias.

La vista anterior se abrió además en un navegador separado con sus assets reconstruidos y la API local actual, en lectura. Las capturas se rotulan **anterior reconstruida**; no son fotografías históricas. El ensayo del script de restauración utiliza una copia aislada y prueba ambos sentidos. La comprobación en el workspace activo usa exclusivamente `Validate` y `Plan`.

Evidencia: [rehearsal](../evidencia/dashboard-admin-fase-6-2026-10-26/rollback-rehearsal.json), [validación de entrega](../evidencia/dashboard-admin-fase-6-2026-10-26/validation.json) y [comparación](../evidencia/dashboard-admin-fase-6-2026-10-26/comparison-validation.json).
