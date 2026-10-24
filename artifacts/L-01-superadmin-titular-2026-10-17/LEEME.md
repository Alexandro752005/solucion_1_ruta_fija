# Ruta Fija — Kit público del cliente de recuperación SUPER_ADMIN

Fecha: 2026-10-17, America/Lima. **CI-L01-07 PENDIENTE DEL TITULAR.**

Este kit contiene solo el cliente Java 21 y documentos. No contiene cuenta, configuración privada, clave, copia de clave, contraseña, token o código de emergencia. Prepararlo no genera ni inscribe material del titular, no inicia la API y no envía correo.

Disponibilidad confirmada de nuevo: **solo PC personal Windows**. Los medios independientes siguen pendientes. No ejecutar generación/inscripción/recuperación real mientras falten custodia y entorno legítimo. Otra carpeta del mismo equipo que hospeda la API no cumple la separación acordada.

## Comprobación que puedes hacer ahora sin secretos

1. Leer este documento y la plantilla de evidencia.
2. Consultar Java (`java -version`); el cliente requiere Java 21.
3. Comprobar el SHA-256 del JAR y los archivos del manifiesto después de copiar el kit. El kit es público; copiarlo no constituye una copia de la clave.

```powershell
Get-FileHash -LiteralPath '.\ruta-fija-recovery-cli.jar' -Algorithm SHA256
```

Huella esperada del JAR:

`91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A`

## Secuencia personal cuando estén disponibles los medios

1. Identificar los medios existentes para la privada cifrada fuera del servidor/repositorio, la copia cifrada independiente y el código en otro lugar. Confirmar solo tipos/disponibilidad, sin rutas privadas. No se autoriza gasto ni almacenamiento en la carpeta del kit dentro del proyecto.
2. Preparar cuenta legítima SUPER_ADMIN y entorno expresamente autorizado: correo verificado, identidad común ID-01 y reautenticación. Una cuenta de fixture o una clave temporal de pruebas no acredita estos requisitos. No aplicar migraciones a desarrollo/piloto.
3. El titular genera su par Ed25519 cifrado mediante `keygen`, con contraseña introducida oculta, y copia contenedor y pública a su medio independiente. No lo hace el agente. No usar transcripciones, capturas o redirecciones.
4. Ejecutar `check-key` en principal y copia, comparar huellas públicas, desconectar/reconectar el medio de copia y comprobar otra vez su lectura. Este comando no usa API/SMTP/código de emergencia. No certifica por sí solo la independencia física de los medios.
5. Inscribir la pública mediante `enroll` antes del incidente. Guardar el código de emergencia mostrado una sola vez directamente en su medio independiente, separado de privada/copia.
6. Para el ensayo autorizado, suspender antes cualquier prueba que limpie `ruta_fija_test`. Ejecutar `recover` usando la **copia comprobada**: código, reto contextual firmado, nuevo correo verificado y contraseña nueva. No hacer envíos fuera de la autorización vigente ni repetir automáticamente.
7. Guardar el código rotado una sola vez, conservar pública, comprobar revocación y consumo y completar la plantilla saneada. Reanudar limpiezas solo después de preservar la evidencia y terminar el proceso.

Los comandos disponibles, para cuando correspondan esas condiciones, son:

```powershell
# Rutas y origen se introducen solo localmente. No pegarlos en chat ni evidencias.
$taskOwnerKeyFile = Read-Host 'Archivo de privada cifrada fuera del servidor/repositorio'
$taskOwnerCopyFile = Read-Host 'Archivo de copia cifrada en medio independiente'
$taskOwnerApiBase = Read-Host 'Origen API autorizado, sin /api/v1'

# Solo el titular, con custodia ya preparada:
java -jar '.\ruta-fija-recovery-cli.jar' keygen $taskOwnerKeyFile
# Copia personal sin sobrescribir archivos anteriores; comprobar también el .pub:
java -jar '.\ruta-fija-recovery-cli.jar' check-key $taskOwnerKeyFile
java -jar '.\ruta-fija-recovery-cli.jar' check-key $taskOwnerCopyFile
# Después de verificar los resultados anteriores:
java -jar '.\ruta-fija-recovery-cli.jar' enroll $taskOwnerApiBase $taskOwnerKeyFile
# En un ensayo autorizado posterior, usando la copia:
java -jar '.\ruta-fija-recovery-cli.jar' recover $taskOwnerApiBase $taskOwnerCopyFile
```

Detener ante cualquier salida distinta de cero; los comandos no son una secuencia para pegar y ejecutar sin comprobar cada paso. El protocolo del proyecto detalla la copia sin sobrescritura. HTTP solo se permite para loopback; otro host exige HTTPS. No se publica un túnel ni se despliega mediante este kit.

## Evidencia y límites

El cliente actual ya tiene pruebas con cuenta/claves/copia temporales y backend real de test, con correo simulado. Esas evidencias se conservan en el proyecto; no se convierten en un ensayo del titular. La confirmación SMTP/OTP de CI-L01-01 tampoco inscribe esta clave ni acredita su custodia.

La plantilla solicita únicamente resultados y huella pública. No registrar OTP, firmas, claves, contraseñas, códigos, tokens, rutas privadas o cuerpos HTTP. Si los medios siguen pendientes, CI-L01-07 permanece pendiente y L-01 abierto.
