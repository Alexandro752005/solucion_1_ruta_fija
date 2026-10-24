# CI07 — Contraseña de cifrado no identificada; ensayo detenido

## Aclaración vigente

El operador informa que solo identifica las contraseñas de acceso SUPER_ADMIN y ADMIN. No identifica la contraseña con que cifró el archivo temporal. Esta aclaración sustituye la declaración anterior de recordarla; se conservan los fallos y todas las evidencias previas. No se conoce ni solicita ningún valor secreto.

Los errores genéricos no permiten atribuir una excepción criptográfica concreta ni acreditar corrupción. La falta de una contraseña de cifrado identificada impide continuar de forma controlada la apertura y uso de la copia. No se solicitan más intentos, combinaciones o búsquedas de secretos.

## Contraste de código y protocolo

El cliente PKCS#12 carga el contenedor y su entrada con la contraseña elegida en `keygen`. No tiene restablecimiento mediante la contraseña de acceso ni mediante el código de emergencia. `recover` abre el archivo antes de crear un reto HTTP; `check-key` es exclusivamente local.

La inscripción normal es única y la recuperación exige la firma Ed25519 con la privada correspondiente a la pública previamente inscrita, el código y la verificación de correo. El [protocolo](recuperacion-superadmin-protocolo.md) no permite sustituir esa pública durante la recuperación. REC-CI07-02 autorizó reparar únicamente el código preservando la pública, por lo que no permite resolver este nuevo impedimento sustituyendo claves.

## Estado persistido comprobado

[Inspección SELECT archivada](ci07-cifrado-no-identificado-140c10a1-2026-10-17T164208Z.json), **2026-10-17T16:42:08.244527300Z**, `ruta_fija_test` / `rf_test` / V26:

- RUN_ID `140c10a1-be0f-4d08-a6d4-c796e2ed107a`; segmento `82bffcf1-6fab-41fd-a57b-3cbbbf18b123`.
- Una inscripción y la pública `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c` conservadas.
- Una reparación REC-CI07-02, auditoría `d791653a-6379-4f02-ac1d-1db020dde403`; sustituto sin consumo.
- Solo el reto viejo vencido, cero fallos de prueba, sin prueba validada, consumo o recuperación completada. Versión de sesión0.
- Aceptación ADMIN preservada, historial1/2/0 y cupos0/2/20; marcador del mismo caso y lock activos.
- Cero escrituras, HTTP, SMTP, privadas leídas o hashes de emergencia seleccionados por la inspección. No nuevas suites.

## Propuesta pendiente de decisión

[REC-CI07-03 v0.1.0](../../operacion/L-01-REC-CI07-03-clave-temporal-propuesta.md), SHA-256 **1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8**, preparada para revisión. No aprobada, implementada o ejecutada.

Propone nueva clave temporal generada y copia comprobada personalmente por el operador, sin sobrescribir archivos; una sustitución auditada de la pública del mismo fixture con autenticación/reautenticación, manteniendo cuenta, instalación, código vigente, aceptación, historial y cupos. Admite expresamente esa sustitución como preparación excepcional de prueba y no como una segunda inscripción HTTP: la original seguirá referida únicamente a la pública anterior. Después exige todo el recorrido normal de recuperación y conservación de la nueva pública durante él. No atribuye esa conservación al cambio previo de preparación.

Esta excepción necesita autorización nueva por exceder REC-CI07-02. Sin ella se conserva el caso pendiente; también puede retomarse si el operador identifica personalmente la contraseña original. No se implementó otro protocolo ni se ejecutó sustitución, rotación o generación de material.

## Estado de L-01

CI07 manual PENDING por apertura/uso de copia no acreditados. CI03 gate previo PENDING; no otro ADMIN autorizado. TEST_CLEANUP_RELEASED=false; SC-03/SC-07 continúan condicionales sin aplicar. L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. Aceptación legítima ADMIN y custodia/titular reales siguen pendientes para el piloto. CI-L01-01 no se repite; términos publicados, final3, protocolo, seguridad y cupos sin cambios.
