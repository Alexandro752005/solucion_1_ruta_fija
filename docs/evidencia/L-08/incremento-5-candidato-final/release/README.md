# L-08 — empaquetado release de candidato local

Este incremento prepara una APK Android `release` firmada con una clave nueva de **candidato local**, distinta de Android Debug. No acredita custodia definitiva por el titular, no publica servicios, no instala una segunda app y no reemplaza la APK v4 que fue comprobada físicamente en el POCO.

La API incorporada por defecto es `https://api.rutafija.invalid/api/v1`. Es una dirección reservada deliberadamente sin servicio real. El candidato no depende del laboratorio HTTP `192.168.18.4:4311`; tampoco representa una conexión operativa al piloto. Debe recompilarse con el dominio HTTPS real autorizado cuando se cumplan los prerrequisitos del piloto y resolverse la firma definitiva antes de su instalación en campo.

## Receta y controles

Desde la raíz del proyecto:

```powershell
powershell.exe -NoProfile -File scripts/Build-RutaFijaL08ReleaseCandidate.ps1
powershell.exe -NoProfile -File scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute
powershell.exe -NoProfile -File scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute -VerifyRebuild
```

La primera orden solo valida y muestra el plan. `-Execute` ejecuta `flutter build apk --release --no-pub --build-number=5 --android-project-arg=rutaFijaL08Isolated=false --android-project-arg=rutaFijaLocalRelease=true --dart-define=RF_API_BASE_URL=https://api.rutafija.invalid/api/v1`. La receta permite parametrizar `-ApiBaseUrl` con HTTPS, puerto 443, ruta `/api/v1`, sin credenciales ni consulta. Rechaza HTTP, direcciones IP literales y destinos locales. No consulta DNS ni envía solicitudes a la API.

Gradle mantiene bloqueada la entrega ordinaria sin autorización explícita y configuración de firma candidata. Nunca sustituye una clave ausente por la clave debug. La clave RSA de 3072 bits y su contraseña aleatoria se mantienen fuera de los artefactos públicos, bajo `backend/.local/l08-local-release-key/`, con herencia de ACL desactivada y acceso exclusivo de usuario Windows actual y SYSTEM. Los secretos se suministran temporalmente al proceso de Gradle y se retiran del entorno al terminar. No se imprimen ni incorporan a propiedades del proyecto.

El primer build release agotó los 384 MB de metaspace durante el lint de una dependencia. La receta reserva 768 MB de metaspace únicamente en el proceso de empaquetado, mantiene el heap en 1024 MB y un worker, y restaura el entorno al finalizar. No omite lint ni cambia `gradle.properties` o versiones. Este uso de `GRADLE_OPTS` con `org.gradle.jvmargs` sigue la [configuración de JVM de Gradle](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties).

El empaquetador verifica paquete, etiqueta, número de versión, ausencia de `debuggable`, certificado distinto de debug mediante `apksigner`, y la URL HTTPS en la biblioteca AOT arm64 del APK. También confirma la ausencia de la URL del laboratorio físico. La APK no se instala. Su nueva firma es distinta de la de v4 y no permite una actualización directa sobre aquella instalación debug.

El JAR se reutiliza del candidato V50 ya preservado y comprobado en ejecución de solo lectura. Antes de copiarlo se exige su SHA-256 conocido y se compara cada entrada compilada/recurso con `backend/target/classes`. No se reejecuta Flyway ni se modifica la base. El esquema real es V50 con 51 migraciones exitosas contando V0 a V50; ese conteo no significa que exista una migración V51. El manifiesto final incorpora hashes de las fuentes actuales.

## Evidencia

- [Comprobación final de artefactos y reproducción](final-artifacts-check.json): APK release v5 de 55 667 319 bytes, dos binarios cotejados y recompilación bit a bit idéntica con el mismo certificado. APK SHA-256 `a4685115e712cfe0c5e24ad84efc4abaf1f4f679a6f95fd1ca0b0354d7b1d776`; JAR SHA-256 `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`.
- [Copias de fuentes de empaquetado y firma](source-snapshots.json): scripts, helper Java, Gradle y configuración de URL preservados por SHA-256.
- [Preflight del empaquetador](builder-preflight.json): modo Plan sin clave ni build; rechazo de HTTP de laboratorio, IP HTTPS literal y credenciales embebidas.
- [Revisión independiente del móvil](qa-mobile.json): controlador, UI y seis casos nuevos revisados por un agente distinto del autor; sin hallazgos bloqueantes para candidato local.
- [Incidencias de la herramienta y correcciones](incidencias-builder.json): intentos iniciales, agotamiento de metaspace y rechazo automático de una sonda opcional de ACL que no llegó a ejecutarse. La ACL efectiva de la clave real sí fue leída y comprobada; no se presenta aquella sonda como aprobada.
- `flutter-release-*.log`: resultado íntegro de cada compilación, sin secretos.
- `apk-metadata-*.txt` y `apk-signature-*.txt`: inspección del APK y verificación criptográfica ejecutadas.
- `release-verification-*.json`: resultado de firma, API incorporada, ACL, hashes y comparación del JAR.
- `rebuild-*.json`: reproducción con la misma clave y comparación binaria; una diferencia de bytes debe informarse, no ocultarse.
- [Artefactos y manifiesto final](../../../../../artifacts/L-08-entrega-final/manifest.json).

Las pruebas de GPS y widget del incremento verifican el código móvil del candidato. La ejecución física previa en POCO corresponde a v4; la APK release v5 no se presenta como físicamente instalada o validada. El costo de preparación es S/ 0 y no se ejecutan órdenes Git/GitHub.

## Firma técnica del acta

`scripts/Sign-RutaFijaL08Closure.ps1` no firma en modo Plan. Solo después de congelar el texto final, `-Execute` firma los bytes exactos de `docs/actas/L-08-cierre-funcional-global.md` con SHA256withRSA y la misma clave candidata del APK. Exporta exclusivamente la firma y el certificado público en una carpeta nueva `closure-signature-<UUID>/`. El comprobante vincula los hashes del acta, firma, certificado y helper Java compilado.

La verificación usa exclusivamente el certificado público, comprueba que su huella coincide con el firmante del APK y exige que modificar un solo byte del acta sea rechazado. Un segundo proceso puede repetirla sin leer la clave privada:

```powershell
powershell.exe -NoProfile -File scripts/Sign-RutaFijaL08Closure.ps1 -Execute -VerifyOnly -SignatureDirectory 'RUTA_ABSOLUTA_A_CLOSURE_SIGNATURE'
```

Se tipifica como **FIRMA TÉCNICA AUTOMATIZADA CANDIDATO LOCAL**. No atribuye identidad o voluntad jurídica al titular, ni sustituye su ceremonia de custodia para producción. Cualquier modificación posterior del acta invalida la firma y exige una nueva constancia fechada.
