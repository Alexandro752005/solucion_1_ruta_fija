# L-08 — Casos concretos pendientes de autorización de entorno

2026-10-22 America/Lima. **PROPUESTA**, no ejecución ni habilitación de piloto. El OK VERDE permite continuar la misión; no restituye cupos ni reabre casos retirados. Fuente: contrato L-08 0.4.0 y restricciones históricas de base/cuentas. El inventario de solo lectura confirmó ruta_fija_test/rf_test, ledger1/3/6, un ADMIN sintético activo con identidad/correo/términos comprobados y cero hitos de piloto.

## L08-REST-01 — Restauración en instancia local nueva

- Origen exclusivo `ruta_fija_test@127.0.0.1:5432`, leído mediante migrador de pruebas desde la configuración privada existente. Sin desarrollo ni piloto.
- Caso `c748e5ab-c20a-43d6-9c17-af5d0a703dab`. Preparación y comandos: [lanzador](../../scripts/Invoke-RutaFijaL08DatabaseRehearsal.ps1), modo `Plan` por defecto, sin conexiones o escrituras.
- Respaldo custom PostgreSQL cifrado AES-256-GCM; autenticación explícita antes de usar bytes. Clave temporal de prueba protegida con DPAPI del usuario Windows y ACL restringida; nunca en argumentos, registros o evidencias. Límite64MiB de contenido verificado en memoria. **No es custodia ni respaldo independiente**.
- Destino nuevo: **otra instancia PostgreSQL nativa**, solo127.0.0.1:55438, base también `ruta_fija_test`, directorio `backend/.local/l08-restore-<caso>/cluster/data`. Sin servicio Windows, contratación, Internet, sustitución de5432 o restauración `--clean`.
- Se niega si el puerto está ocupado, el directorio/caso está preparado o el destino no está vacío. Se conserva el caso ante fallo; no se borra ni reinicia para repetirlo.
- Roles temporales propios de esa instancia, sin reutilizar accesos de desarrollo. La lectura de configuración del migrador se concede solo durante la comprobación de ese destino; se retira después. `rf_test` mantiene separación de migrador.
- Restauración `pg_restore --exit-on-error --single-transaction --no-owner` por entrada autenticada, sin dump en claro. Compara huellas de todas las filas, constraints, índices y triggers contra el snapshot consistente del origen. Después faltan comprobaciones de aplicación/permisos para acreditar recuperación del servicio; no atribuir RTO4h por éxito SQL solamente.
- Se detiene exclusivamente el clúster propio y se conserva su directorio y contador restaurado. Ningún cupo, aceptación o evidencia del origen se modifica. Una copia restaurada no se convierte en nuevo piloto ni autoriza iniciar personas reales.

La lectura/copia local del test está comprendida en la misión. **La escritura en esta instancia nueva requiere confirmar L08-REST-01** antes de ejecutar `RestoreIsolated -Execute -AuthorizeNewIsolatedCluster`.

## L08-CON-01 — Recorrido operativo físico separado

- Exclusivamente ruta_fija_test/rf_test de5432, caso nuevo con RUN propio, mismo SUPER_ADMIN/ADMIN sintéticos elegibles y organización de pruebas; sin reabrir fixtures L-01…07.
- Máximo **un CONDUCTOR sintético nuevo**, por incorporación, verificación con correo simulado, revisión/activación y vínculo normales. El histórico pasa **6→7/20**, sin reponer cupos. No altas ADMIN/SUPER_ADMIN.
- Recursos sintéticos propios del caso: grupo/unidad/títulos de prueba, servicio/oferta y terminales identificados. Cada control de estado, documentos, términos, rol y empresa permanece activo. Las credenciales de prueba se manejan localmente, sin chat o argumentos.
- APK de pruebas separado, corregido y con huella nueva; conservar el APK y la app anterior. Login válido/inválido, sesión/cierre y operación normal, luego permisos/consentimiento personalmente otorgados por el usuario para GPS solo durante servicio y primer plano.
- No registrar coordenadas en evidencia ordinaria. Solo resultados de precisión/edad/distancia en forma saneada; ubicación real conserva el acceso y retiro≤7d. Sin falsificar GPS físico, sin cola offline, sin rastreo en segundo plano. Ninguna prueba autoriza circulación insegura.
- TalkBack y decisiones de consentimiento se realizan personalmente; automatización no equivale a lectura humana. Batería se mide con condiciones reales registradas, sin fingir drenaje con USB cargando. Si falta umbral aprobado, el resultado es medición y no aceptación numérica inventada.
- Protección de caso y lock antes de escribir; ninguna limpieza global. Al terminar, retiro selectivo/deshabilitación/revocación, preservando ledger, aceptaciones, ofertas, historial y pruebas previas.

**Requiere confirmar L08-CON-01**: el perfil25 y las autorizaciones anteriores por lote no crean permiso para una nueva cuenta persistente. La autorización no certifica los resultados ni habilita el piloto.

## CAP25 — Dependencia distinta

Con el ledger acumulado y los casos retirados no se fabricarán25 participantes reactivando casos o reiniciando contadores. La carga25 requiere un dataset/instancia de validación independiente, creación normal con cupos propios1/4/20 y su autorización concreta antes de ejecutarse. Esta propuesta **no solicita ni incluye todavía ese dataset**: primero se completa el mecanismo de restauración/caso físico y se prepara el recorrido de carga con medición hasta commit durable.

## Medios y condiciones reales todavía pendientes

La PC y el Poco no demuestran por sí solos un medio de respaldo independiente, monitor fuera del host, alertas entregadas o cobertura humana/escalamiento24/7. Tampoco sustituyen aceptación de ADMIN legítimo, preparación/ensayo/custodia del titular real, privacidad final, infraestructura HTTPS o títulos reales. Deben acreditarse antes de activar el piloto; estas autorizaciones de prueba no los declaran cumplidos.
