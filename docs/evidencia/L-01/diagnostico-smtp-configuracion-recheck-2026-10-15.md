# Revisión segura de configuración SMTP — 2026-10-15

## Resultado

La discrepancia sigue presente. El usuario SMTP leído en memoria desde `backend/.local/ruta-fija-native.env` no coincide con el remitente permitido en `backend/src/main/resources/application-local.properties`. La lista permitida no se modificó. La ubicación y única variable que requieren corrección manual son:

`backend/.local/ruta-fija-native.env` → `SPRING_MAIL_USERNAME`

No se imprimieron valores privados y no se verificó la contraseña.

## Fuentes y carga

- El archivo privado es la fuente local destinada al perfil Runtime. Contiene una sola definición de `SPRING_MAIL_USERNAME` y una de `SPRING_MAIL_PASSWORD`.
- `scripts/Import-RutaFijaNativeEnvironment.ps1` lee el archivo, valida nombres permitidos, rechaza una variable duplicada y carga las variables al entorno Process. Con `-Scope Runtime` pasa las credenciales de correo; con `-Scope Test` construye el perfil `test` y no pasa `SPRING_MAIL_USERNAME` ni `SPRING_MAIL_PASSWORD`.
- `application-local.properties` usa placeholders Spring para tomar ambos valores del entorno. Esos placeholders no son credenciales duplicadas. El mismo archivo contiene el remitente y destinatario permitidos del piloto; ambos permanecen intactos.
- Al momento de este diagnóstico, las variables SMTP no están definidas en el proceso que ejecutó la inspección. No había backend local escuchando en el puerto 8080. La validación automatizada vigente ejecuta el perfil `test`, usa correo simulado y no es evidencia de configuración Gmail efectiva. No se identificó un proceso Runtime activo cuya fuente efectiva pueda atribuirse.

## Restricción de entrega

La prueba real permanece detenida hasta que se corrija la discrepancia y se repita un preflight local saneado. Esta revisión no abrió conexiones a Gmail ni a SMTP, no envió mensajes y no cambió configuración privada ni permitidos.
