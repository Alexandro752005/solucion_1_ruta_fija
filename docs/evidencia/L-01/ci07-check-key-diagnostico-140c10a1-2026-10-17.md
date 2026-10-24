# CI07 — Diagnóstico local de apertura de la copia

## Hechos y límites

El operador comunicó un fallo genérico al ejecutar `check-key` y después aclaró que introdujo la contraseña de acceso SUPER_ADMIN. Confirmó posteriormente que recuerda con certeza la contraseña de cifrado elegida en `keygen`. No compartió su valor. Se indicó una única comprobación local con esa contraseña de cifrado; su resultado sigue pendiente.

El mensaje genérico no demuestra por sí solo que la contraseña sea incorrecta: el código vigente agrupa errores de lectura del contenedor, descifrado/carga PKCS#12 y comprobación del par privado/público. El diagnóstico no atribuye una excepción criptográfica específica que no fue capturada.

[Metadatos y límites de la inspección](ci07-check-key-fallido-140c10a1-2026-10-17T163759Z.json), corte **2026-10-17T16:37:59.3668730Z**:

- Principal y copia previstos por la guía existen y tienen477 bytes cada uno. Solo se consultaron metadatos del archivo privado, sin leer su contenido ni calcular su hash.
- Los dos archivos públicos coinciden con la huella inscrita `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c`.
- JAR canónico conserva SHA-256 `91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A`.
- La longitud coincidente no acredita igualdad o integridad del contenido cifrado. Los archivos públicos coincidentes tampoco prueban que las claves privadas puedan abrirse. La variable de la terminal B no fue inspeccionada.

La inspección JSON conserva el estado de consulta pendiente al corte. Las aclaraciones humanas posteriores sobre el uso de contraseña de acceso y el recuerdo de la contraseña de cifrado se registran aquí, sin modificar ese antecedente.

## Base y protección

`check-key` no utiliza API, base, correo ni código de emergencia. No se repitió una consulta de base por este comando exclusivamente local. La [última inspección JDBC](ci07-clave-local-140c10a1-2026-10-17T163414Z.json), a2026-10-17T16:34:14.586167200Z, conserva una sola reparación, reto anterior vencido sin prueba/consumo/completado, clave pública y aceptación ADMIN, ledger1/2/0, cupos0/2/20 y lock/marcador activos. Es evidencia de ese corte, no una nueva lectura de base a16:37.

## Continuación indicada

En B, en `PS ...>`, una vez `java -jar $trialJar check-key $trialCopy`; introducir únicamente la contraseña de cifrado que el operador confirmó recordar, mediante la entrada oculta existente. No se muestran caracteres ni asteriscos. Comunicar solo `LOCAL_ENCRYPTED_KEY_CHECK` y `PUBLIC_KEY_SHA256`, o error saneado. Detener ante fallo y esperar contraste antes de `recover`; no repetir reparación/inscripción/generación ni cambiar archivos.

Sin cambios de código o protocolo y sin nuevas suites. No hay PASS local nuevo ni recuperación manual acreditada. CI07 PENDING, CI03 gate previo PENDING, limpieza no liberada, SC-03/SC-07 sin aplicar, L-01 EN_CURSO y L-02 sin iniciar. CI-L01-01 no se repite. No envíos externos, cuentas adicionales, migraciones de desarrollo/piloto ni cambios de final3 o términos publicados.
