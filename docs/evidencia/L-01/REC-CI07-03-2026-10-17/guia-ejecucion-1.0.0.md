# REC-CI07-03 — Ejecución personal del mismo caso sintético

Versión1.0.0, 2026-10-17, America/Lima. **Autorizada e implementada; sustitución del fixture NO ejecutada.** [Propuesta0.1.0 aprobada](L-01-REC-CI07-03-clave-temporal-propuesta.md), SHA `1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8`; [autorización](L-01-REC-CI07-03-autorizacion-2026-10-17.json), SHA `434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6`.

Solo `ruta_fija_test`/`rf_test`/V26, RUN_ID `140c10a1-be0f-4d08-a6d4-c796e2ed107a`. Misma cuenta/organización/instalación/ADMIN, ledger1/2/0 y cupos0/2/20. REC-CI07-02 y su sustituto se conservan. Esta preparación no es otra inscripción HTTP; la original queda ligada a la pública anterior. No acredita recuperación ni custodia independiente.

**Leer y ejecutar únicamente la etapa indicada por A-COORD. No pegar una secuencia de comandos interactivos completa:** esperar siempre a que termine cada comando y vuelva `PS ...>`. Ningún secreto se introduce en `Ensayo L01>` como comando. No grabar ni redirigir las terminales. Solo el operador genera/abre las claves.

## 1. Recargar el arnés: etapa actual

Conservar B y los archivos originales. En A, en `Ensayo L01>`:

1. Escribir una vez `verificar`; esperar a que archive y vuelva el prompt.
2. Escribir una vez `salir`. Para esta recarga coordinada, el código vigente archiva otra vez y conserva el marcador porque ambos ensayos son PENDING. Debe mostrar `TEST_CLEANUP_RELEASED=false` y regresar a PowerShell. Si muestra true o falla, detener.
3. En A ya en `PS ...>`, desde la raíz, ejecutar una vez:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'
```

Debe reutilizar la cuenta sin contraseña nueva de arranque, mostrar el mismo RUN_ID e incluir `preparar-clave` en los comandos. Mantener A abierta; comprobar que carga http://127.0.0.1:4311/login sin login. Comunicar solo RUN_ID, si se conservó TEST_CLEANUP_RELEASED=false, comando presente y login cargado. **Esperar comprobación de marcador/lock antes de continuar.** No borrar marcadores ni liberar bloqueos manualmente.

## 2. Nuevo destino temporal: después de comprobar la protección

En B, solo en `PS ...>`, desde la raíz. El bloque siguiente no solicita secretos y no llama a Java/API:

```powershell
$trialJar = (Resolve-Path -LiteralPath '.\artifacts\L-01-superadmin-titular-2026-10-17\ruta-fija-recovery-cli.jar').Path
if ((Get-FileHash -LiteralPath $trialJar -Algorithm SHA256).Hash -ne '91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A') { throw 'Cliente distinto: detener.' }
$trialNewKeyRoot = Join-Path $env:LOCALAPPDATA 'RutaFijaEnsayoTemporal\140c10a1-be0f-4d08-a6d4-c796e2ed107a\REC-CI07-03'
if (Test-Path -LiteralPath $trialNewKeyRoot) { throw 'Destino ya ocupado: conservar y revisar, no sobrescribir.' }
New-Item -ItemType Directory -Path $trialNewKeyRoot -ErrorAction Stop | Out-Null
$trialNewSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$trialNewAcl = [Security.AccessControl.DirectorySecurity]::new()
$trialNewAcl.SetOwner($trialNewSid)
$trialNewAcl.SetAccessRuleProtection($true, $false)
$trialNewInheritance = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
$trialNewRule = [Security.AccessControl.FileSystemAccessRule]::new($trialNewSid, [Security.AccessControl.FileSystemRights]::FullControl, $trialNewInheritance, [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow)
$trialNewAcl.AddAccessRule($trialNewRule)
Set-Acl -LiteralPath $trialNewKeyRoot -AclObject $trialNewAcl
$trialNewPrimary = Join-Path $trialNewKeyRoot 'principal-temporal.p12'
$trialNewCopy = Join-Path $trialNewKeyRoot 'copia-temporal.p12'
```

No modifica `$trialPrimary`/`$trialCopy` originales ni sus archivos. Ante error, detener sin borrar/recrear el destino.

## 3. Generar, copiar y comprobar: un comando interactivo cada vez

Primero, solo este comando:

```powershell
java -jar $trialJar keygen $trialNewPrimary
```

Elegir y confirmar personalmente una **contraseña de CIFRADO** de prueba, mínimo12 caracteres; se recomienda ASCII para evitar diferencias de teclado. Esa contraseña abrirá principal y copia; no se obtiene del login. Conservarla mediante los medios personales disponibles, fuera de los archivos cifrados y sin chat, argumentos, `.env` o evidencia. No se presupone gestor ni medio independiente. Si falla, detener. No generar la clave definitiva.

Solo tras éxito y regreso a `PS ...>`, copiar sin sobrescribir:

```powershell
Copy-Item -LiteralPath $trialNewPrimary -Destination $trialNewCopy -ErrorAction Stop
Copy-Item -LiteralPath ($trialNewPrimary + '.pub') -Destination ($trialNewCopy + '.pub') -ErrorAction Stop
```

Después ejecutar **solo el primero**, introducir la contraseña de CIFRADO y esperar a `PS ...>`:

```powershell
java -jar $trialJar check-key $trialNewPrimary
```

Si pasó, ejecutar **solo el segundo** y volver a usar esa contraseña de CIFRADO:

```powershell
java -jar $trialJar check-key $trialNewCopy
```

Ambos deben imprimir `LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE` y la misma `PUBLIC_KEY_SHA256`, distinta de la anterior5b727e9a… Comunicar solo los dos resultados y la huella pública. **Esperar contraste antes de preparar la pública.** Si falla, detener sin otro intento por rutina. Las entradas ocultas no muestran caracteres ni asteriscos. Dos archivos en esta PC no son respaldo independiente.

## 4. Preparar la nueva pública: después de contrastar ambos checks

Para consultar únicamente el archivo público, en B ya en PowerShell:

```powershell
Get-Content -LiteralPath ($trialNewPrimary + '.pub')
```

Solo ese archivo `.pub`; no abrir ni pegar el `.p12`. Su línea X.509 Base64 es pública y se pegará directamente en A, no se necesita compartirla por chat.

En A, en `Ensayo L01>`, ejecutar una vez `preparar-clave`. Solicita en este orden:

1. Contraseña de **ACCESO SUPER_ADMIN**, oculta, para autenticación normal.
2. La misma contraseña de **ACCESO SUPER_ADMIN**, oculta, para reautenticación normal.
3. La línea pública del archivo nuevo `.pub`, visible. No contraseña ni código.
4. `PUBLIC_KEY_SHA256` nueva de64 caracteres, coincidente en ambos checks.
5. Revisar huellas anterior/nueva. Solo si comprobó principal/copia y **todavía conserva el sustituto vigente de REC-CI07-02**, escribir exactamente `CLAVE_COPIA_Y_CODIGO_CONFIRMADOS`. No introducir aquí ese código ni ninguna contraseña. Si no lo conserva, cancelar/detener: no está autorizada otra reparación del código.

La transacción vuelve a validar las guardas, cambia solo pública/timestamp e inserta una auditoría; no toca código, cuenta, instalación o cupos. Ante rechazo, no repetir.

Resultado esperado, aún no obtenido:

```text
L01_FIXTURE_KEY_PREPARATION=PASS_PRECONDITION_ONLY
SYNTHETIC_FIXTURE_KEY_REPLACEMENT_COUNT=1
RECOVERY_SEGMENT_ID=<referencia pública>
PUBLIC_KEY_SHA256=<huella pública nueva>
```

Comunicar solo esas salidas. **Esperar contraste de persistencia antes de recuperar.** No `enroll`, `reparar-codigo` ni otra preparación. Una sustitución no es un PASS de CI07.

## 5. Recuperación normal: después del contraste de la preparación

Leer toda la secuencia antes de crear el reto de cinco minutos. En B, en PowerShell, una vez:

```powershell
java -jar $trialJar recover 'http://127.0.0.1:4311' $trialNewCopy
```

1. Correo actual `sa.human.140c10a1@rutafija.test`; nuevo `sa.recovered.140c10a1@rutafija.test`.
2. Contraseña de **CIFRADO nueva** para abrir la copia nueva.
3. Revisar instalación `f0cd888b-57df-4def-9d08-7882ffadebae`, referencia de cuenta, correo nuevo, reto exacto y vencimiento. S solo si corresponde.
4. Introducir oculto el **sustituto vigente de43 caracteres** conservado desde REC-CI07-02. La preparación de clave no lo cambió.
5. Enter sin S ante reenvío. En A, `correo` una vez para leer OTP simulado del nuevo correo; introducirlo oculto en B.
6. Establecer/confirmar contraseña de acceso SUPER_ADMIN nueva,12–72 caracteres ASCII. El cliente muestra una sola vez el código final rotado: conservarlo separado de las claves, sin evidencia/chat.
7. Nueva ventana privada: http://127.0.0.1:4311/login con correo/contraseña nuevos; comprobar `/platform-admins`.
8. Solo tras realizar las acciones: en A, `copia-ok`, luego `recuperacion-ok`, luego `verificar`, uno cada vez.

Comunicar solo `MANUAL_RECOVERY_RESULT`, `EVIDENCE_FILE` y `EVIDENCE_SHA256`. Se exige clave nueva conservada **durante la recuperación**, código/reto consumidos, rotación, login y rechazo de sesiones observadas del nuevo segmento, más correspondencia de la auditoría de preparación. No se afirma conservación de la pública original a través del mantenimiento. Ante fallo, detener sin reenvíos ni repeticiones.

## 6. Archivo, protección y criterios restantes

Esperado `PASS_LOCAL_TEMPORARY_COPY_ONLY` solo después del recorrido humano y evidencia contrastada; todavía PENDING. La inscripción HTTP original permanece1 y ligada a la pública anterior; la sustitución de fixture es otro contador/auditoría, no otra respuesta HTTP.

CI03 gate previo sigue pendiente, sin otro ADMIN autorizado. No escribir `salir` al finalizar CI07 para intentar liberar limpiezas; la única salida anticipada aquí indicada es la recarga coordinada del apartado1 con marcador conservado y resultado false. Ambos PASS archivados/contrastados siguen siendo necesarios para aplicar SC-03/SC-07. No cierre, piloto, custodia independiente o titular real por inferencia. L-01 EN_CURSO y L-02 sin iniciar. CI01 no se repite; términos, final3 y protocolo normal intactos. Sin SMTP externo, Git/GitHub, despliegue o migraciones de desarrollo/piloto.
