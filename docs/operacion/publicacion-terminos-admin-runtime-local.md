# Publicación de términos ADMIN en el runtime local

Fecha local: 23/10/2026, America/Lima. Base: `solucion_ruta_fija_1`, PostgreSQL 16 local, esquema V1–V50. Alcance: habilitar la lectura y aceptación personal del documento por ADMIN en el CRM local.

## Resultado

Se publicó `ADMIN_TERMS` versión `1.0.0`, documento `156fbf6d-665b-43d5-b72d-008f7a4b2e5d`, el `2026-10-24T02:21:09.159221Z` (23/10/2026, 21:21:09 en Lima). Su contenido tiene 21.108 bytes UTF-8 y SHA-256 `20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417`.

La [fuente aprobada](../legal/publicado/terminos-admin-v1.0.0.md) y su [registro de aprobación](../legal/publicado/terminos-admin-v1.0.0.aprobacion.json) ya existían. Se registró ese mismo contenido, sin normalizar saltos de línea ni modificar archivos históricos. La aprobación previa de publicación local consta en la [publicación del 17/10](../legal/publicacion-admin-v1.0.0-2026-10-17.md); el propietario autorizó expresamente su incorporación al runtime en esta intervención. La fecha factual del texto permanece como parte de su versión inmutable; actualizar sus declaraciones requeriría otra versión.

La publicación anterior pertenecía a `ruta_fija_test`. El CRM nativo utiliza `solucion_ruta_fija_1`, donde `admin_terms_document` estaba vacío. Las migraciones crean la estructura; no publican términos ni registran aceptaciones por una persona.

Se agregó un evento `OWNER_AUTHORIZED_LOCAL_ADMIN_TERMS_PUBLICATION`, con actor de aplicación nulo para identificar correctamente una intervención técnica del propietario. No se atribuyó esa acción a una sesión ADMIN o SUPER_ADMIN. Se mantuvieron cero aceptaciones en esta intervención.

## Procedimiento reproducible

Requisitos: Python 3, PowerShell, herramientas de PostgreSQL 16 en su ubicación nativa y configuración privada `backend/.local/ruta-fija-native.env`. Los scripts no muestran contraseñas ni las incluyen como argumentos de procesos.

Consultar primero, sin publicar:

```powershell
.\scripts\Publish-RutaFijaAdminTermsLocal.ps1
```

Resultado comprobado: `ADMIN_TERMS_LOCAL_CHECK=PASS_PUBLISHED`, versión `1.0.0`, `V50=PASS`. Antes de la publicación devolvía `PENDING_PUBLICATION`.

Publicar únicamente cuando esté autorizada esta misma versión local:

```powershell
.\scripts\Publish-RutaFijaAdminTermsLocal.ps1 -Publish
```

El publicador limita el destino a `solucion_ruta_fija_1` en `127.0.0.1:5432`, con `rf_migrator`; valida esquema, contenido aprobado, aprobación y snapshot base. Genera y comprueba un respaldo custom antes de abrir la transacción de publicación. Inserta el documento y su auditoría en una transacción serializable. Un registro existente idéntico se conserva; cualquier conflicto de contenido o versión detiene el procedimiento. No ejecuta Flyway, cambios de permisos ni aceptaciones.

Se comparan huellas de las tablas antes y después. La base protegida `ruta_fija_test` se consulta en modo de solo lectura y debe permanecer idéntica. La repetición verificada devolvió `PASS; NEW_DOCUMENT=false`, sin cambios en las tablas de ninguna base. Los resultados y respaldos se guardan por ejecución, sin sobrescribir evidencias anteriores.

Si una transacción ya quedó confirmada pero la comprobación posterior detecta actividad concurrente, el script informa `COMMITTED_VERIFICATION_INCOMPLETE`. Corresponde revisar los resultados privados y consultar el estado; no borrar ni reemplazar el documento. Durante la primera ejecución coincidieron logins de SUPER_ADMIN y ADMIN. La comparación con el respaldo confirmó que las cuentas solo cambiaron `last_login_at` y `updated_at`; contraseñas, correos, roles y demás campos se conservaron. La auditoría y las sesiones explican los cambios concurrentes. La comprobación posterior de idempotencia quedó aprobada.

## Respaldo y evidencia

Respaldo preventivo anterior a insertar el documento:

- Ruta privada: `backend/.local/accesos/publicacion-terminos-20261023_212107-b05ba0a6/pre-publicacion-terminos.dump`.
- Tamaño: 647.346 bytes.
- SHA-256: `f2a7859122ea98555e6c36451857c74f6bbab69edfdd094683daf593c20d9802`.
- Archivo custom legible mediante `pg_restore --list`; no se afirma una nueva restauración de este respaldo.
- ACL heredada restringida al propietario local y SYSTEM. La carpeta está excluida por `backend/.local/` y los volcados por `*.dump`.

[Evidencias verificadas](../evidencia/operacion-local/publicacion-terminos-20261023_212246-4d9fc987/): `publicacion-inicial.json`, `revision-concurrencia.json`, `resultado.json` y `verificacion-http.json`. La última ejecución de publicación fue una comprobación de idempotencia y generó otro respaldo; su `inserted=false` no modifica el registro de la inserción inicial.

## Comprobación del CRM y siguiente acción personal

La prueba autenticada recorrió el proxy real del CRM `http://127.0.0.1:4300`, con la cuenta ADMIN existente. El código de aplicación, las migraciones y las reglas de autorización permanecieron sin cambios.

| Comprobación | Resultado |
|---|---|
| GET de términos sin autenticar | 401 |
| Login ADMIN | 200 |
| GET `/api/v1/admin/terms/current` con ADMIN | 200; contenido idéntico a la fuente aprobada, `synthetic=false` |
| Estado de aceptación personal | `accepted=false`; fecha de aceptación ausente/nula |
| Caché del documento | `Cache-Control: no-store` |
| Consulta de conductores antes de aceptar términos | 403, compuerta conservada |
| Ruta web `/admin-terms` | 200 |
| Cierre de la sesión de comprobación | 204 |

No se envió una petición a `/api/v1/admin/terms/acceptances`. Las 50 migraciones permanecieron válidas y las 105 tablas de la base protegida conservaron sus huellas durante la publicación y la comprobación de idempotencia. La API y el CRM siguieron activos; no fue necesario reiniciar.

El titular ADMIN debe iniciar sesión, abrir `http://127.0.0.1:4300/admin-terms`, leer el documento, marcar la aceptación expresa y pulsar **Aceptar esta versión**. Después podrá usar **Continuar al CRM**. Si mantiene abierta la pantalla anterior, puede pulsar **Volver a consultar**. La publicación local no constituye una aceptación personal ni una autorización de despliegue del piloto real.
