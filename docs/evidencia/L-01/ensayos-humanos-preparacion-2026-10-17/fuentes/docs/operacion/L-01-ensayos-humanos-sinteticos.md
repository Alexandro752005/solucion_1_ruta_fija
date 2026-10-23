# L-01 — Ensayos humanos sintéticos de ADMIN y recuperación

Fecha: 2026-10-17. **Preparados; todavía no ejecutados por el titular.** L-01 EN_CURSO.

## Alcance y entorno comprobados

Este recorrido representa dos identidades ficticias mediante las facilidades de pruebas existentes. El operador humano conserva su rol real previsto SUPER_ADMIN; no se le atribuye una segunda identidad real ni aceptación operativa como ADMIN. La cuenta, documentos de identidad y correo del ensayo son sintéticos. Los términos que se leen sí son el documento aprobado ADMIN 1.0.0, sin modificar sus bytes.

Destino único: PostgreSQL local **ruta_fija_test**, usuario **rf_test**, esquema existente **V26**, sin Flyway ni modificación de esquema. Inspección de solo lectura a 2026-10-17T06:15:43.590935600Z:

| Rol | Altas históricas | Cupos disponibles |
|---|---:|---:|
| SUPER_ADMIN | 0 | 1 |
| ADMIN | 1 | 3 |
| CONDUCTOR | 0 | 20 |

No hay ADMIN sintético pendiente elegible para reutilizar. El ADMIN histórico pertenece al caso OTP finalizado y no se reutiliza ni se modifica. El ensayo nuevo necesita una alta SUPER_ADMIN sintética y una alta ADMIN por OP-ADMIN-01; los triggers conservan su ledger. Al continuar el mismo caso no se vuelve a crear SUPER_ADMIN y una credencial ADMIN pendiente se renueva por el flujo existente, sin otra alta. Si cambia el estado o se agotan cupos, detener y revisar selectivamente; no borrar ni truncar.

Se mantienen activos los gates ID-01 y términos, los permisos, OTP y los estados normales. Solo el adaptador de correo se sustituye por uno en memoria para las tres direcciones sintéticas de este caso. No inicializa ni usa el transporte Gmail; sus credenciales no se inyectan en el proceso de ensayo. La configuración privada existente se carga para resolver el destino de pruebas. Los schedulers quedan deshabilitados en este proceso aislado para preservar datos ajenos. No hay endpoints nuevos en la aplicación normal.

## 1. Arranque personal: terminal A

Desde la raíz del proyecto, en una terminal Windows interactiva **sin transcripción, grabación ni redirección**:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve
```

El primer comando solo inspecciona; no crea cuentas. El segundo repite las guardas y toma el bloqueo de limpiezas antes de preparar el caso. La primera vez solicita, con entrada invisible sin caracteres ni asteriscos, una contraseña de prueba SUPER_ADMIN y su confirmación. Utiliza 12 a 64 caracteres ASCII para este ensayo; no reutilices una contraseña real.

El fixture crea únicamente SUPER_ADMIN sintético con correo previamente verificado como precondición de prueba y una organización sintética activa. No crea ADMIN ni acepta términos. La revisión de identidad ID-01 la ejecutarás en el CRM.

Anota solo `L01_HUMAN_RUN_ID`. La consola muestra las tres direcciones ficticias y el ID de la organización. Mantén esta terminal abierta durante ambos ensayos.

- CRM y API del recorrido: **http://127.0.0.1:4311**.
- Backend interno: 127.0.0.1:8091; no usar directamente para estos pasos, para conservar la observación del recorrido.
- Puertos ocupados, estado inesperado o error: detener sin iniciar otro caso. No usar el lanzador SMTP de CI01.

El lock y `backend/.local/l01-human-trial.active` impiden la limpieza estándar de test. El marcador persiste ante una interrupción; **no lo borres para forzar pruebas**. No ejecutar otros arneses de limpieza o migración durante tu intervención.

## 2. CI03: SUPER_ADMIN de prueba → ADMIN inactivo

1. Abre **http://127.0.0.1:4311/login** en una ventana normal. Usa el correo `sa.human.…@rutafija.test` que imprimió el lanzador y la contraseña que acabas de establecer.
2. Abre **http://127.0.0.1:4311/platform-admins** → **Revisar mi identidad como titular**.
3. Representa la ficha ficticia de SUPER_ADMIN: DNI **90000001**, referencia **SYNTHETIC owner visual review**, contraseña actual y confirmaciones de revisión/declaración. Estas confirmaciones representan el original ficticio dentro del ensayo; no acreditan el documento o identidad reales del operador. No introduzcas tu DNI real.
4. Pulsa **Confirmar operación**. Debe confirmarse la declaración y habilitarse la preparación de ADMIN.
5. Pulsa **Preparar alta ADMIN** y completa:

| Campo de la vista restringida | Valor del ensayo |
|---|---|
| Organización activa | `SYNTHETIC HUMAN TRIAL …`, con ID mostrado en terminal A |
| Correo para el alta | `admin.human.…@rutafija.test` mostrado en terminal A |
| Nombre para el alta | `SYNTHETIC HUMAN ADMIN` |
| DNI comprobado | `40000001`, exclusivamente ficticio |
| Método | Revisión presencial |
| Referencia | `SYNTHETIC admin visual review` |
| Motivo | `SYNTHETIC human activation rehearsal` |
| Confirmaciones | Representación expresa de la ficha ficticia revisada, sin copias reales |

6. Pulsa **Confirmar operación** una vez. Debe aparecer la cuenta **inactiva**, con identidad comprobada, estado de activación pendiente y una credencial de 24 horas presentada una vez. No elijas reemplazo ni modifiques el ADMIN del caso OTP. La creación consume un cupo aunque no se active.
7. Conserva la credencial solo para introducirla directamente en el siguiente paso; no la incluyas en capturas, archivos o chat. SUPER_ADMIN no establece la contraseña del ADMIN.

## 3. CI03: activación, correo simulado y aceptación personal

1. En una ventana privada separada abre **http://127.0.0.1:4311/admin-activation**.
2. Introduce la credencial temporal y **establece personalmente** una contraseña ADMIN de prueba y su confirmación. Pulsa **Registrar contraseña**. No cierres ni recargues esta página: el acceso opaco de activación dura 30 minutos y reside en memoria.
3. Pulsa **Enviar código de verificación** una vez. En terminal A escribe **`correo`**. El adaptador presenta el OTP sintético vigente una sola vez en esa consola, sin guardarlo en evidencia. Introdúcelo en la página y pulsa **Verificar correo** dentro de sus cinco minutos. No reenvíes por rutina: se mantienen tres envíos por 60 minutos y el bloqueo por verificaciones fallidas.
4. **Antes de aceptar**, en terminal A escribe **`gate`**. Debe resultar:

```text
PRE_ACCEPTANCE_GATE=PASS (activación HTTP 409; operación sin sesión HTTP 401)
```

Este chequeo intenta completar la activación sin aceptación y consulta una operación usando solo la capacidad de activación. No crea aceptación, no envía correo y debe conservar la cuenta inactiva. Si no pasa, detén el ensayo antes de aceptar y comunica solo el resultado saneado. El rechazo 401 no se presenta como prueba de un gate de términos 403 para un ADMIN ya activo; ese caso tiene evidencia automática previa separada.

5. Pulsa **Consultar documento vigente**. Comprueba versión **1.0.0**, lee el texto completo y confirma que la casilla está inicialmente desmarcada. El documento publicado conserva SHA-256:

```text
20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417
```

6. **Tú** marcas la casilla y pulsas **Registrar aceptación**. Después de «Aceptación registrada», pulsa **Completar activación**. Son acciones separadas; la aplicación no acepta automáticamente al iniciar sesión.
7. En la misma ventana privada vuelve a `/login`, inicia sesión como ese ADMIN con su contraseña y abre `/invitations` solo para comprobar acceso, **sin emitir invitaciones**. Si queda una sesión anterior, cierra sesión primero. En terminal A escribe **`admin-ok`** únicamente si realizaste lectura, aceptación y activación.
8. Escribe **`verificar`** en terminal A. El arnés consulta la fila persistida y contrasta cuenta/organización/documento/versión/huella/hora, secuencia temporal, identidad y auditoría; además exige la observación HTTP, el gate previo y tu confirmación. El resultado esperado es **`PASS_MANUAL_SYNTHETIC_ONLY`**. Guarda solo ruta de evidencia, huella y resultados saneados.

Esto es **aceptación manual persistida de un ADMIN sintético**, no aceptación operativa de una persona ADMIN legítima. CI-L01-03 original continúa pendiente de esa segunda condición.

## 4. CI07: clave y copia temporales, terminal B

No ejecutes aquí material definitivo del titular. El JAR y protocolo existentes conservan Ed25519/PKCS#12. La copia de este ensayo queda en la **misma PC**, lo que permite comprobar lectura/uso pero no independencia.

En otra terminal interactiva, desde la raíz del proyecto:

```powershell
$trialJar = (Resolve-Path -LiteralPath '.\artifacts\L-01-superadmin-titular-2026-10-17\ruta-fija-recovery-cli.jar').Path
if ((Get-FileHash -LiteralPath $trialJar -Algorithm SHA256).Hash -ne '91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A') {
    throw 'Detener: paquete distinto del cliente revisado.'
}
$trialRunText = Read-Host 'L01_HUMAN_RUN_ID mostrado en terminal A'
$trialRun = [Guid]::Parse($trialRunText)
$trialKeyRoot = Join-Path $env:LOCALAPPDATA ('RutaFijaEnsayoTemporal\' + $trialRun.ToString())
if (Test-Path -LiteralPath $trialKeyRoot) { throw 'Destino ya ocupado: no sobrescribir claves.' }
New-Item -ItemType Directory -Path $trialKeyRoot -ErrorAction Stop | Out-Null
$trialPrincipalSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$trialDirectoryAcl = [Security.AccessControl.DirectorySecurity]::new()
$trialDirectoryAcl.SetOwner($trialPrincipalSid)
$trialDirectoryAcl.SetAccessRuleProtection($true, $false)
$trialInheritance = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit
$trialRule = [Security.AccessControl.FileSystemAccessRule]::new($trialPrincipalSid, [Security.AccessControl.FileSystemRights]::FullControl, $trialInheritance, [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow)
$trialDirectoryAcl.AddAccessRule($trialRule)
Set-Acl -LiteralPath $trialKeyRoot -AclObject $trialDirectoryAcl
$trialPrimary = Join-Path $trialKeyRoot 'principal-temporal.p12'
$trialCopy = Join-Path $trialKeyRoot 'copia-temporal.p12'

java -jar $trialJar keygen $trialPrimary
if ($LASTEXITCODE -ne 0) { throw 'Detener: clave temporal no preparada.' }
Copy-Item -LiteralPath $trialPrimary -Destination $trialCopy -ErrorAction Stop
Copy-Item -LiteralPath ($trialPrimary + '.pub') -Destination ($trialCopy + '.pub') -ErrorAction Stop
java -jar $trialJar check-key $trialPrimary
if ($LASTEXITCODE -ne 0) { throw 'Detener: lectura de principal fallida.' }
java -jar $trialJar check-key $trialCopy
if ($LASTEXITCODE -ne 0) { throw 'Detener: lectura de copia fallida.' }
```

La carpeta se crea fuera del repositorio y de los directorios de la aplicación, restringida al usuario de Windows. El cliente también rechaza guardar una clave dentro del repositorio. La contraseña de cifrado se introduce oculta (mínimo 12 caracteres) y se confirma; no va en argumentos ni variables de comandos. Las dos comprobaciones deben mostrar éxito y la misma huella **pública**. No compartas rutas privadas ni contenido de los archivos.

## 5. CI07: inscripción y recuperación usando la copia

1. Conserva terminal A abierta. En ella escribe **`sesion-cli`**, introduce oculta la contraseña inicial del SUPER_ADMIN sintético y copia el token mostrado solo hacia la entrada oculta de `enroll`. Es una sesión obtenida por el login normal, no un permiso especial. No la registres ni compartas.
2. En terminal B:

```powershell
java -jar $trialJar enroll 'http://127.0.0.1:4311' $trialPrimary
if ($LASTEXITCODE -ne 0) { throw 'Detener: inscripción no completada; no repetir automáticamente.' }
```

Introduce contraseña del archivo, token y contraseña actual SUPER_ADMIN en las entradas ocultas. La API exige autenticación, ID-01 y reautenticación. Comprueba que la huella pública inscrita coincide con `check-key`. El código de emergencia se presenta una vez: úsalo solo durante este ensayo mediante entrada oculta; no lo guardes junto a la clave ni en evidencias. Su presentación temporal en consola no acredita custodia independiente real.

3. En terminal B ejecuta la recuperación **con la copia**:

```powershell
java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy
if ($LASTEXITCODE -ne 0) { throw 'Detener: recuperación no completada; conservar el estado para revisión.' }
```

El cliente solicita, en este orden:

- Correo inicial `sa.human.…@rutafija.test` y correo nuevo `sa.recovered.…@rutafija.test` mostrados en terminal A.
- Contraseña del archivo de copia, oculta.
- Revisión del reto exacto: instalación, cuenta, nuevo correo, vencimiento y bytes UTF-8. Confirma con **S** solo si coinciden. El reto dura cinco minutos; no generes uno nuevo por rutina.
- Código de emergencia anterior, oculto.
- Ante «¿Solicitar un único reenvío…?», pulsa **Enter**, sin S. El mensaje ya está en el correo simulado; no hace falta reenviar.
- En terminal A escribe **`correo`**, lee el OTP sintético para el correo nuevo y escríbelo solo en la entrada oculta del cliente.
- Establece personalmente una contraseña nueva de prueba y confírmala, ambas ocultas. El cliente presenta una sola vez el código rotado. No pegues ninguna de esas salidas en chat.

4. Cierra la ventana privada ADMIN. En una nueva ventana privada inicia sesión en `/login` con el correo SUPER_ADMIN recuperado y la contraseña nueva. Debe conservar el mismo ID y permitir `/platform-admins` según sus permisos. La observación comprueba el login; los accesos y refresh previos se verifican automáticamente como rechazados, sin revelar sus valores.
5. En terminal A escribe **`copia-ok`** solo después de comprobar ambos archivos con `check-key` y usar efectivamente `$trialCopy` en `recover`. Escribe **`recuperacion-ok`** solo después de completar personalmente el recorrido y el login nuevo. Estas declaraciones se distinguen de las comprobaciones del servidor.
6. Escribe **`verificar`**. Resultado esperado: **`PASS_LOCAL_TEMPORARY_COPY_ONLY`**. Se contrastan reto consumido, auditoría, hash del código anterior frente al vigente, código rotado, pública conservada, versión de sesión y rechazo HTTP de todas las sesiones previas observadas. Los secretos solo estuvieron en memoria del proceso para esas comprobaciones; no se archivan.

La lectura y uso de dos archivos en la misma PC **no** satisface la custodia elegida, ni protege ante pérdida del equipo. CI-L01-07 original continúa pendiente de medios y configuración legítimos.

## 6. Archivo y liberación de limpiezas

`verificar` crea archivos nuevos, sin sobrescribir, en `docs/evidencia/L-01/ensayos-humanos-<RUN_ID>/`, con SHA-256 propio. Solo guarda IDs, resultados, huella pública, marcas de servidor, estados, conteos y controles; no OTP, códigos, tokens, firmas, contraseña, clave privada ni su ruta. El documento aprobado `synthetic=false` no convierte la cuenta sintética en una persona real.

Cuando **ambos** resultados técnicos sean PASS, escribe **`salir`** en terminal A. Vuelve a archivar antes de liberar el lock y retira únicamente el marcador propio del ensayo; no borra filas ni recupera cupos. Debe imprimir:

```text
HUMAN_TRIAL_EVIDENCE_ARCHIVED=true; TEST_CLEANUP_RELEASED=true
```

Si hay un pendiente, interrupción o error de observación, el marcador mantiene las limpiezas suspendidas; no lo elimines ni crees otro caso. Conserva el estado para revisión selectiva. No reanudes limpiezas tras un simple cierre de ventana.

Comunica únicamente `L01_HUMAN_RUN_ID`, `MANUAL_ADMIN_RESULT`, `MANUAL_RECOVERY_RESULT`, `EVIDENCE_FILE`, `EVIDENCE_SHA256` y `TEST_CLEANUP_RELEASED`. Se contrastará la evidencia local antes de actualizar resultados de los ensayos. No se pide otra aprobación de términos ni autorización para L-02.

## Custodia real: faltantes y alternativa S/0 condicionada

Solo está confirmada la PC Windows. Faltan un lugar de custodia fuera del servidor para la clave cifrada definitiva, una copia cifrada en un medio independiente realmente disponible y comprobado, custodia del código en otro lugar, inscripción legítima previa y ensayo del titular con esa copia. La clave de este documento es exclusivamente temporal.

Una alternativa sin contratar servicios, **solo si se consiguen disponibles sin gasto**, es usar un segundo equipo propio o prestado con autorización que no aloje la API para custodiar/usar la clave cifrada, más un medio extraíble independiente para su copia, y un soporte del código guardado por separado. No se presume que ninguno exista. Comprobar lectura/uso de la copia y separación física seguiría siendo obligatorio. Si todo permanece en la única PC servidora, no hay una alternativa de carpetas que satisfaga la independencia elegida.
