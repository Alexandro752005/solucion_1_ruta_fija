# Protocolo de API de recuperación SUPER_ADMIN — L-01

Este documento describe el contrato backend probado para implementar un cliente de configuración y recuperación. No es una instrucción para compartir secretos ni una interfaz de usuario terminada.

## Inscripción inicial

Antes de cualquier incidente, el titular debe generar localmente un par Ed25519 y proteger la clave privada fuera del repositorio, del servidor y de `ruta-fija-native.env`, con custodia separada del código de emergencia. La clave pública se codifica como X.509 DER en Base64.

Con una sesión `SUPER_ADMIN` activa, correo verificado y reautenticación correcta, el cliente envía:

```http
POST /api/v1/auth/super-admin-recovery/enrollment
Authorization: Bearer <sesion-super-admin>
Content-Type: application/json
```

```json
{
  "publicKeyX509Base64": "<clave-publica-X509-Base64>",
  "currentPassword": "<contrasena-actual>"
}
```

La respuesta `201` incluye `installationId`, la huella SHA-256 de la clave pública y `emergencyCodeShownOnce`. Guardar ese código una sola vez y en un medio distinto al que custodia la clave privada. La API no permite volver a consultarlo ni reemplazar la clave inscrita.

## Recuperación

1. Solicitar un reto con `POST /api/v1/auth/super-admin-recovery/challenges` y `accountEmail`/`newEmail`. La respuesta `202` no revela si la cuenta existe. Para una cuenta elegible entrega `challengeId`, `installationId`, `accountReference`, `nonce`, `expiresAt` y `challengeToSignUtf8`.
2. Firmar exactamente los bytes UTF-8 de `challengeToSignUtf8` usando Ed25519 y la clave privada custodiada por el titular. Codificar la firma como Base64URL sin padding. No reconstruir el texto ni normalizarlo en el cliente.
3. Enviar `emergencyCode` y `signatureBase64Url` a `POST /api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof`. Un resultado `202` devuelve `verificationToken` y confirma el intento de enviar un OTP al nuevo correo.
4. Si se requiere un reenvío, usar `POST /api/v1/auth/super-admin-recovery/challenges/{challengeId}/email/resend` con el `verificationToken`. Se aplican los límites OTP existentes.
5. Enviar `verificationToken`, el OTP recibido como `code` de seis dígitos y `newPassword` a `POST /api/v1/auth/super-admin-recovery/challenges/{challengeId}/complete`.
6. La respuesta `200` incluye `newEmergencyCodeShownOnce`. Guardarlo en un medio separado de la clave privada. Después de la respuesta, el código anterior y todas las sesiones previas dejan de ser válidos; la clave pública permanece igual.

Todas las respuestas de recuperación llevan `Cache-Control: no-store`. No registrar cuerpo de solicitud/respuesta, OTP, código, firma ni clave privada. Tres pruebas de firma/código inválidas consumen el reto; el reto vence a los cinco minutos. La verificación de correo conserva el límite de tres envíos en sesenta minutos y el bloqueo después de dos OTP incorrectos.

## Decisiones operativas todavía necesarias

- Definir dónde y cómo se generará y custodiará la clave privada y su respaldo, de modo separado del código de emergencia. No se ha elegido un gestor, dispositivo ni responsable.
- Implementar la interfaz o herramienta cliente que genere/registre la clave pública, firme el reto localmente y complete la recuperación sin enviar la clave privada al servidor. Estas rutas backend todavía no están integradas en una interfaz.
- Probar el recorrido operativo y validar la entrega del OTP nuevo por SMTP al destinatario autorizado. Las pruebas automáticas del backend usan un doble de correo.

## Actualización local — cliente operativo disponible para ensayo — 2026-10-14

Se añadió `pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli`, una utilidad Java 21 que usa las rutas y DTO descritos en este protocolo. No modifica el contrato del backend:

- `keygen` crea Ed25519 y protege el material PKCS#8 en un contenedor PKCS#12 con la biblioteca estándar del JDK. En Windows restringe la ACL del archivo al propietario; en sistemas POSIX aplica `rw-------`. Rechaza rutas dentro del repositorio. La clave pública X.509 Base64 queda en un archivo hermano `.pub`.
- `enroll` solicita por entrada oculta el token de sesión y la contraseña actual, y envía solo la clave pública a `/enrollment`. Muestra una vez el código de emergencia devuelto.
- `recover` pide cuenta/correo nuevos, solicita el reto, muestra sus vínculos y vencimiento, requiere confirmación antes de firmar exactamente `challengeToSignUtf8` en UTF-8, verifica localmente la firma con la clave pública hermana y sigue `/proof` y `/complete`. Token, contraseña, código y OTP no se aceptan como argumentos ni se imprimen. El cuerpo de errores HTTP se descarta.
- Solo permite HTTP en loopback; para cualquier otro host requiere HTTPS. No sigue redirecciones y envía `Cache-Control: no-store`.

Validación automática nueva: `SuperAdminRecoveryCliTest`, 7 pruebas; incluye cifrado/lectura del contenedor en Windows, firma exacta, JSON escapado y recorrido completo contra un servidor HTTP local falso. Verifica que los secretos de entrada no aparecen en la salida capturada. No contactó al backend real, no usó una clave del titular y no generó ningún correo.

**Esto habilita un ensayo local con una clave de prueba, pero no cierra la operación.** El titular aún debe confirmar el medio de custodia y de respaldo cifrado, separado del código de emergencia. No ejecutar `keygen` para crear la clave real, no inscribirla ni usar el flujo real hasta confirmar esa custodia. Las instrucciones de ejecución y el resultado del preflight SMTP actual deben quedar en la evidencia L-01.

## Actualización de evidencia — 2026-10-15

El cliente también se ejecutó contra Tomcat real de perfil test, puerto aleatorio y PostgreSQL ruta_fija_test mediante SuperAdminRecoveryCliBackendIT. Pasó en la regresión completa de 130 IT; se repite en la regresión final de autorización. Cuenta, correos y claves son temporales, el correo es simulado y no se generó ni custodió la clave del titular. Esto supera el límite de API falsa del primer corte sin acreditar el ensayo humano.

### Ficha de ensayo del titular pendiente

Antes de la inscripción real, el titular debe escoger medios de custodia existentes y separados para la clave privada cifrada, su respaldo y el código de emergencia. La clave permanece fuera del repositorio, servidor y archivo env; no se comunica por chat, argumentos o registros. No se deduce que esos medios ya existan ni se propone un desembolso.

La evidencia del ensayo contendrá únicamente:

- Fecha, entorno e identificación no secreta de instalación/caso.
- Función del titular y confirmación de custodia separada, sin rutas o valores secretos.
- Resultado de inscripción previa, firma del reto, OTP al nuevo correo, cambio de contraseña y rotación del código mostrado una vez.
- Confirmación de que código y reto usados no se reutilizan, sesiones previas se revocan y la clave pública permanece igual.
- Incidencias y resultado; sin capturas de claves, OTP, contraseñas, códigos o tokens.

Los comandos interactivos disponibles se muestran mediante la utilidad SuperAdminRecoveryCli de Java 21 (keygen, enroll y recover). La clase usa biblioteca estándar y solicita los secretos con entrada oculta; no acepta secretos como argumentos ni imprime cuerpos de error. El titular ejecutará el cliente en su equipo y medios elegidos cuando el entorno y la custodia estén preparados. Esta actualización documental no ordena inscribir una clave real ni autoriza al agente a generarla o custodiarla.

La recepción SMTP confirmada correspondió a la prueba aislada del adaptador; no equivale al OTP de este ensayo. No se marca CI-L01-07 como terminado por las pruebas con material temporal.

## Paquete local e instrucciones para el titular — 2026-10-15

Se preparó `backend/target/ruta-fija-recovery-cli.jar`, únicamente con la clase cliente y sus clases internas; no contiene configuración del servidor, archivo env, cuenta, clave ni código de emergencia. SHA-256 del paquete de este corte: `91B85744C8B94839598317BDF29370778123CA1989DE84F27BA0768100DA88ED`.

Comprobaciones ejecutadas: listado del JAR; `jdeps -s` identifica únicamente `java.base` y `java.net.http`; el arranque sin terminal interactiva se negó a pedir entradas. No se ejecutó keygen, enroll o recover con material real. La integración automática de la clase con Tomcat real y material temporal sigue siendo una evidencia distinta del paquete y del ensayo humano.

El titular puede copiar este JAR a su equipo de custodia, con Java 21. El equipo/medio donde se guarde la clave real debe estar fuera del servidor y del repositorio, como exige la decisión aprobada; una carpeta diferente en el mismo servidor no acredita esa separación. Deben comprobarse custodia y recuperación de la copia cifrada antes de dar por satisfecho ese requisito.

Los comandos siguientes son una guía para la ejecución interactiva del titular **cuando el entorno y la custodia estén preparados**; no se ejecutaron aquí. Sustituir los marcadores localmente. Ningún argumento contiene contraseña, token, OTP, clave privada en claro o código de emergencia. No usar transcripción de PowerShell, capturas de pantalla ni redirección de salida durante el ensayo.

```powershell
# En el equipo del titular, después de copiar y comprobar el paquete:
Get-FileHash -Algorithm SHA256 -LiteralPath '.\ruta-fija-recovery-cli.jar'

# Generación solo por el titular en el medio separado elegido:
java -jar '.\ruta-fija-recovery-cli.jar' keygen '<RUTA_PRIVADA_CIFRADA_FUERA_DEL_SERVIDOR_Y_REPOSITORIO>'

# API_BASE es solo origen, p. ej. http://127.0.0.1:PUERTO para un ensayo local;
# no añadir /api/v1. Cualquier otro host requiere HTTPS.
java -jar '.\ruta-fija-recovery-cli.jar' enroll '<API_BASE>' '<RUTA_PRIVADA_CIFRADA>'
java -jar '.\ruta-fija-recovery-cli.jar' recover '<API_BASE>' '<RUTA_PRIVADA_CIFRADA>'
```

`enroll` pide sesión SUPER_ADMIN y contraseña mediante consola protegida. `recover` solicita las entradas necesarias y confirmación del contexto antes de firmar. Los códigos de emergencia se muestran una sola vez por requisito del flujo; deben guardarse directamente en su medio separado sin volcarlos a registros. No compartir por chat los valores o rutas privadas.

Un ensayo con correo real solo se realiza bajo el destinatario y las condiciones autorizados, sin reintentos automáticos. El paquete no inicia servicios ni publica HTTPS. Esta guía no autoriza migrar otra base ni sustituye la preparación pendiente del entorno de ensayo del titular.

### Precondición de identidad incorporada en V26

La configuración legítima del titular incluye ahora ID-01: identidad común revisada mediante la vista restringida de SUPER_ADMIN, con reautenticación y declaración expresa. Esta comprobación conserva solo HMAC y referencia; no es la inscripción de clave pública ni sustituye sus secretos independientes. No se comunica el DNI real por chat. La inscripción real de recuperación debe respetar esa elegibilidad antes de operar; los fixtures históricos de test con el gate aislado no acreditan la identidad del titular.

## Continuación del titular — disponibilidad confirmada y comprobación de la copia

El titular confirmó en esta interacción: PC personal Windows disponible; medio independiente de copia todavía sin confirmar; clave real sin preparar/inscribir; copia no creada/comprobada y código sin custodia independiente. **CI-L01-07 permanece PENDIENTE.** No volver a pedir una elección de algoritmo o diseño: Ed25519, privada cifrada fuera de servidor/repositorio, copia cifrada separada y código en otro lugar ya están acordados.

### Qué puede preparar ahora y qué necesita un medio adicional

1. Con la PC Windows se pueden revisar el cliente, su huella y las instrucciones, sin generar material real ni conectar a la API. Si esa misma PC hospeda la API, otra carpeta de su disco no cumple «fuera del servidor». No se ha acreditado que exista otro equipo/medio de custodia. No se exige ni autoriza un gasto.
2. Antes de generar la clave real, el titular identifica medios que ya tenga disponibles para la privada cifrada, una copia independiente y el código separado. Dos rutas del mismo disco no acreditan independencia; ni `.env`, repositorio, correo/chat o evidencias sirven para custodiar estos secretos. Si faltan medios, detener ese tramo y mantenerlo pendiente.
3. La futura inscripción necesita cuenta legítima SUPER_ADMIN, correo verificado, ID-01 y reautenticación en un entorno autorizado. Este documento no crea esa cuenta ni autoriza migrar desarrollo/piloto, publicar acceso remoto o introducir nuevos datos reales sin sus condiciones de privacidad. Las pruebas temporales del agente no inscriben al titular.

### Operaciones que realiza personalmente el titular

Cuando los medios y el entorno estén preparados, usar una consola interactiva sin transcripción, redirecciones ni capturas de secretos. Los marcadores se sustituyen solo localmente. La contraseña del contenedor se introduce oculta; los comandos no la reciben como argumento.

```powershell
# En el equipo de custodia; el JAR no inicia servicios ni envía correo por sí solo:
Get-FileHash -Algorithm SHA256 -LiteralPath '.\ruta-fija-recovery-cli.jar'

# Indicar rutas localmente sin pegarlas en chat/evidencias:
$taskOwnerKeyFile = Read-Host 'Archivo nuevo de clave cifrada en el medio elegido fuera del servidor/repositorio'
$taskOwnerCopyFile = Read-Host 'Archivo nuevo de copia cifrada en el medio independiente elegido'

# Solo el titular genera el par real cuando la custodia está preparada:
java -jar '.\ruta-fija-recovery-cli.jar' keygen $taskOwnerKeyFile
if ($LASTEXITCODE -ne 0) { throw 'Detener: generación no completada.' }

# No sobrescribir un archivo previo ni copiar el código de emergencia aquí:
if ((Test-Path -LiteralPath $taskOwnerCopyFile) -or (Test-Path -LiteralPath ($taskOwnerCopyFile + '.pub'))) {
    throw 'Detener: destino de copia ya ocupado.'
}
Copy-Item -LiteralPath $taskOwnerKeyFile -Destination $taskOwnerCopyFile
Copy-Item -LiteralPath ($taskOwnerKeyFile + '.pub') -Destination ($taskOwnerCopyFile + '.pub')

# Comprobación local de descifrado y correspondencia Ed25519; NO API/SMTP/código:
java -jar '.\ruta-fija-recovery-cli.jar' check-key $taskOwnerKeyFile
if ($LASTEXITCODE -ne 0) { throw 'Detener: clave principal no comprobada.' }
java -jar '.\ruta-fija-recovery-cli.jar' check-key $taskOwnerCopyFile
if ($LASTEXITCODE -ne 0) { throw 'Detener: copia no comprobada.' }
```

`check-key` reutiliza el contenedor y verificación de pareja Ed25519 del cliente vigente: descifra, firma/verifica una comprobación local y muestra únicamente éxito y huella pública SHA-256. No cambia el protocolo de recuperación, no pide código de emergencia ni token de sesión y no envía red. Las dos huellas públicas deben coincidir. El titular debe comprobar también la protección y disponibilidad del medio de copia; el comando no certifica separación física, cifrado del dispositivo o sus permisos. Desconectar/volver a conectar el medio independiente y repetir únicamente su comprobación local acredita lectura disponible, sin compartir secretos.

Después, el titular ejecuta `enroll` contra la cuenta/entorno legítimos y guarda directamente el código mostrado una vez en su lugar independiente, no junto a la clave/copia. Contrastar la huella pública devuelta por la inscripción con las comprobaciones anteriores. Si no coinciden, detener sin intentar recuperación.

El ensayo autorizado se ejecuta con `recover` **usando el archivo de copia comprobado**, conservando el mismo par público. Requiere el código, confirmación del contexto exacto, OTP al correo nuevo permitido y contraseña nueva; captura oculta y sin reenvíos automáticos. Guardar el código rotado una sola vez en el medio independiente y retirar el anterior de su uso. No consumir el código solo para probar un script ni ejecutar mientras haya otras pruebas limpiando la base de test.

### Evidencia saneada que falta

Comunicar solo tipos de medios y disponibilidad comprobada, fecha/entorno/caso, huella **pública** inscrita, éxito de `check-key` principal/copia, uso de la copia en el ensayo, cambio de correo/contraseña, rechazo de sesiones previas, código/reto consumidos, rotación única y clave pública conservada. No compartir rutas privadas, código, contraseña, OTP, tokens, firmas o contenido de claves.

Los controles de reutilización/caducidad/contexto/concurrencia se prueban automáticamente con claves temporales y correo doble. La comprobación del titular se acredita solo cuando la realiza personalmente y se documenta su resultado; un medio pendiente no se convierte en una copia existente por preparar estos comandos.

### Paquete vigente con comprobación local de copia

JAR actualizado `backend/target/ruta-fija-recovery-cli.jar`: SHA-256 **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A**. Esta huella sustituye solo la del paquete CLI anterior, conservado en target; no cambia final3 ni la clave pública inscrita de ninguna cuenta. Solo contiene las cinco clases CLI y manifiesto; dependencias java.base/java.net.http. El arranque sin consola se rechaza con salida2, sin leer clave o abrir API.

Comprobaciones ejecutadas del cliente actual: ocho unitarias CLI (incluyen lectura/pareja correcta de copia temporal y negativas de contraseña/pública); IT con Tomcat/PostgreSQL reales recupera usando una copia cifrada **temporal**, cambia correo/contraseña y verifica access/refresh previos rechazados, código/reto consumidos, presentación de dos códigos distintos una vez cada uno y pública conservada. Tres IT adicionales contrastan contexto, caducidad exacta y concurrencia. Correo siempre doble. [Evidencia completa](incremento-Chiclayo-2026-10-15.md). Nada de ello declara preparados los medios o la clave real del titular.
## Disponibilidad y kit actuales — 2026-10-17

El titular volvió a confirmar «siguen pendientes, por ahora solo tengo mi PC windows». Continúan pendientes medios independientes, clave/inscripción, copia comprobada, código separado y ensayo humano. No repetir elección de algoritmo ni asumir que otra carpeta de la PC servidora resuelve la custodia.

Se preparó un [kit público ZIP](../../../artifacts/ruta-fija-superadmin-titular-2026-10-17.zip), SHA **7739912D5BB0732DA59E583A27E501ABD111EF287718CEB1BDABB053E82AC2AF**, con JAR91913FF7… intacto, [guía](../../../artifacts/L-01-superadmin-titular-2026-10-17/LEEME.md), plantilla PENDIENTE y manifiesto. La lista de archivos fue comprobada; no contiene secretos/configuración ni genera material real. Su preparación no acredita custodia. Los pasos personales siguen condicionados a medios/entorno legítimos y autorización de cualquier entrega.

CI01 SMTP/OTP ya quedó verificado por su caso y evidencia independiente; eso no verifica CI07. Se levantó la suspensión de limpiezas test por aquel OTP terminado. Volver a suspenderlas antes de un ensayo del titular y preservar su evidencia antes de reanudarlas. **CI07 PENDIENTE; L-01 EN_CURSO.**

## Ensayo técnico humano temporal autorizado — 2026-10-17

El titular autoriza ahora ejecutar personalmente `keygen`, `check-key`, `enroll` y `recover` con **cuenta/clave/copia exclusivamente temporales** y correo simulado en ruta_fija_test. No prepara ni inscribe su clave definitiva. La [guía exacta Windows](../../operacion/L-01-ensayos-humanos-sinteticos.md) utiliza el JAR91913FF7… vigente, sin cambiar Ed25519 o el protocolo. Precondiciones: fixture SUPER_ADMIN sintético, login normal, ID-01 por vista restringida y reautenticación. API de ensayo por proxy loopback4311; no Gmail.

Lanzador comprobado con10 unitarias y arranque real sin fixture; **ensayo manual todavía pendiente**. El operador generará y protegerá personalmente archivos fuera del repositorio/directorios de aplicación; la copia temporal puede estar en esa misma PC solo para probar lectura y uso. Se archivan referencias/huellas públicas/estados, no secretos. Lock más marcador persistente impiden limpiezas hasta archivo/liberación conforme a la guía.

Solo PC Windows confirmada: el resultado técnico con dos archivos locales **no acredita** custodia fuera del servidor, respaldo independiente ni separación real del código. La [propuesta de alcance](../../contratos/L-01-propuesta-validacion-funcional-local.md) queda sin aprobar; CI07 original permanece pendiente.
