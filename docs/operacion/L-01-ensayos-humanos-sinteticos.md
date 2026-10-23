> **Estado vigente al 2026-10-17: L-01 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../actas/L-01-cierre-funcional-local-2026-10-17.md) y [matriz final](../evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Los cortes anteriores siguientes se conservan como antecedentes. Piloto bloqueado; L-02 sin iniciar y sin autorizar.

# L-01 — Ensayos humanos sintéticos de ADMIN y recuperación

Fecha: 2026-10-17. **Caso140c10a1: ADMIN activado/aceptación persistida y gate previo pendiente; CI07 inscrito, entrega original no acreditada y recuperación pendiente.** El operador confirmó la salida ficticia en B y **autorizó REC-CI07-02 v0.1.0**. Reparación del fixture implementada/compilada,35 unitarias y preflight PostgreSQL PASS sin ejecutar sustitución; pendiente intervención personal. Seguir la [guía de reparación y reanudación del mismo caso](L-01-REC-CI07-02-ejecucion.md). Al último corte el servidor está detenido y el marcador protege las limpiezas. **No repetir `enroll` ni iniciar otro caso; esperar protección antes de `reparar-codigo`.** L-01 EN_CURSO, SC-03/SC-07 condicionales sin aplicar. [Corte vigente](../evidencia/L-01/REC-CI07-02-implementacion-2026-10-17.md); los bloques previos son antecedentes.

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

**Si existe un caso pendiente, no ejecutar `-Serve` solo.** El caso actual `140c10a1-be0f-4d08-a6d4-c796e2ed107a` se interrumpió antes de preparar una cuenta o guardar contraseña; seguir la continuación del apartado siguiente. Los comandos de alta inicial de este bloque corresponden únicamente a un entorno sin caso pendiente:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve
```

El primer comando solo inspecciona; no crea cuentas. El segundo repite las guardas y toma el bloqueo de limpiezas antes de preparar el caso. La primera vez solicita, con entrada invisible sin caracteres ni asteriscos, una contraseña de prueba SUPER_ADMIN y su confirmación. El límite efectivo del arnés es **12 a 72 posiciones de entrada y máximo72 bytes UTF-8**; utiliza **12 a 72 caracteres ASCII** para el ensayo. La recomendación anterior12…64 era un subconjunto válido, no el máximo implementado. No reutilices una contraseña real.

El fixture crea únicamente SUPER_ADMIN sintético con correo previamente verificado como precondición de prueba y una organización sintética activa. No crea ADMIN ni acepta términos. La revisión de identidad ID-01 la ejecutarás en el CRM.

Anota solo `L01_HUMAN_RUN_ID`. La consola muestra las tres direcciones ficticias y el ID de la organización. Mantén esta terminal abierta durante ambos ensayos.

- CRM y API del recorrido: **http://127.0.0.1:4311**.
- Backend interno: 127.0.0.1:8091; no usar directamente para estos pasos, para conservar la observación del recorrido.
- Puertos ocupados, estado inesperado o error: detener sin iniciar otro caso. No usar el lanzador SMTP de CI01.

El lock y `backend/.local/l01-human-trial.active` impiden la limpieza estándar de test. El marcador persiste ante una interrupción; **no lo borres para forzar pruebas**. No ejecutar otros arneses de limpieza o migración durante tu intervención.

### Continuación del arranque interrumpido del mismo caso

La inspección del caso confirma: cero cuentas/organizaciones propias, cero altas SUPER_ADMIN, sin estado preparado o archivo de clave de firma del runtime. La protección por marcador sigue activa aunque el proceso anterior terminó y liberó su lock de sesión. La primera versión no registró la causa exacta del rechazo inicial; no se deduce que fuera una confirmación distinta. El segundo `ANOTHER_HUMAN_CASE_PENDING` se debió al marcador anterior frente al nuevo RUN_ID que intentaba generar `-Serve` sin continuación.

Con el arnés corregido y ya compilado, desde la raíz:

```powershell
# Solo lectura; debe informar ELIGIBLE_SAME_CASE_NO_PREPARED_ACCOUNT:
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -InspectRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'

# Continuación explícita: mantiene RUN_ID/marcador, sin recuperar cupos:
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'
```

Solo el titular realiza la nueva captura oculta y confirmación. La continuación vuelve a comprobar la base/rol/esquema, el marcador exacto, la ausencia de cuenta/contraseña preparada y los cupos; si algo cambió, se detiene. No sustituye contraseñas de una cuenta existente. Antes de pedir la entrada guarda atómicamente metadatos públicos del mismo caso; los rechazos futuros registran solo un código saneado, sin contraseñas, hashes, longitud ni contenido. El marcador no se borra ni se reemplaza para continuar.

Resultados de entrada distinguibles: `PASSWORD_INPUT_CANCELLED`, `PASSWORD_LENGTH_INVALID`, `PASSWORD_UTF8_LIMIT_EXCEEDED`, `PASSWORD_CONFIRMATION_MISMATCH`. Si vuelve a fallar, conserva el caso y comunica solo el código. No relanzar sin diagnóstico. Cuando aparezca el mismo `L01_HUMAN_RUN_ID` y cargue `/login`, confirmar protección y pasar a la etapa siguiente; los restantes pasos del recorrido no cambian.

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

### Si se ejecutó `gate` después de aceptar

No se puede recuperar la comprobación previa sobre una aceptación ya registrada. En el caso actual, el titular confirmó ese orden: el ADMIN está activo/COMPLETED, con aceptación1.0.0 y login observados, pero sin `preAcceptanceGate` registrado. El comando rechaza su precondición antes de intentar HTTP409/401; `PASO_NO_COMPLETADO` no borra el avance.

No repetir correo, gate o alta, no eliminar aceptación ni reabrir por SQL, no detener/relanzar el servidor para reiniciar el caso. Mantener terminal A y protección. Si se realizó personalmente lectura/aceptación, `admin-ok` seguido de `verificar` archiva el avance; el resultado sigue PENDING por la comprobación omitida. No escribir ni inferir un PASS. Véase el diagnóstico enlazado para la evidencia y la continuación pendiente; cualquier ADMIN adicional consumiría otro cupo histórico y no está creado ni autorizado por esta nota.

**Actualización vigente:** declaración `admin-ok` y archivo1792227077200 ya contrastados; no repetirlos por rutina. El titular **no autoriza REC-CI03-01 por ahora**: no crear otro ADMIN. [Revisión de registros del mismo caso](../evidencia/L-01/revision-registros-gate-140c10a1-2026-10-17.md): dos LOGIN_FAILED previos por INVALID_CREDENTIALS no prueban el gate de términos; no se encontró traza específica suficiente en el ámbito inspeccionado. Conservar únicamente esa comprobación pendiente de CI03, sin cambiar SC-03, y continuar CI07 con los apartados4/5. La clave temporal la genera personalmente el operador mediante el cliente vigente, manteniendo abierto el servidor del ensayo.

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

### Antecedente401 y continuación vigente tras inscripción verificada

[Diagnóstico401 del mismo caso](../evidencia/L-01/ci07-enrollment-401-140c10a1-2026-10-17.md), conservado como antecedente: no había clave inscrita en ese corte. Después de la comprobación ficticia de entrada y del login de consola exitoso09:37Z, el operador completó personalmente la inscripción. La [inspección posterior09:44Z](../evidencia/L-01/ci07-inscripcion-verificada-140c10a1-2026-10-17.md) confirma una credencial del SUPER_ADMIN sintético, auditoría09:40:48.051Z y huella pública persistida igual al observadorHTTP; el operador confirma coincidencia con principal y copia. La causa exacta del401 original sigue sin acreditarse. No repetir `keygen`, `sesion-cli` o `enroll` por rutina; conservar terminales y variables existentes.

Continuación personal: apartado5, paso3, `recover` con `$trialCopy`. Correos del caso: `sa.human.140c10a1@rutafija.test` → `sa.recovered.140c10a1@rutafija.test`; instalación `f0cd888b-57df-4def-9d08-7882ffadebae`. Leer primero toda la secuencia antes de solicitar el reto de cinco minutos. Contraseñas/códigos solo en prompts ocultos. OTP exclusivamente simulado mediante `correo`, sin reenvío; después login con contraseña nueva y declaraciones/archivo. La inscripción no es por sí sola un PASS de recuperación. ADMIN sigue PENDING por el gate previo: no escribir `salir` ni liberar limpiezas ante un PASS aislado de CI07.

### Si no se identifica el código antes de firmar

El cliente está programado para mostrar el código en B al terminar `enroll`, después de la huella pública, bajo **«Código de emergencia (mostrar una sola vez; guardar aparte de la clave privada):»**, en la línea siguiente de43 caracteres. **La visibilidad de esa salida en este caso no está acreditada; el operador informa que no aparece.** No es contraseña, token ni OTP. Antes de solicitar un reto, comprobar personalmente que ese factor esté disponible en el lugar local separado elegido; no compartirlo ni enviarlo a evidencias.

En el caso140c10a1 el operador se detuvo en «¿Firmar y continuar?» por no tenerlo identificado. [Consulta posterior09:56Z](../evidencia/L-01/ci07-reto-sin-codigo-140c10a1-2026-10-17.md): reto original09:50Z vencido09:55Z, cero intentos fallidos y sin prueba validada/consumo/completado. No hay PASS de recuperación. Se solicitó escribir **N y Enter** en B para salir antes de firmar o enviar `/proof`; la cancelación del cliente aún requiere confirmación humana. No existe escritura de cancelación ni se borra el reto vencido.

Localizar el valor únicamente en el historial visible del `enroll` exitoso de esa terminal. Si está disponible y el cliente ya salió, se podrá retomar el flujo vigente con el mismo caso/clave/copia y un reto nuevo, conservando el vencido, los contadores y límites; no ejecutar aún otro `recover` por rutina. Si no está disponible, mantener CI07 pendiente: la API conserva hash, no ofrece consulta del código ni reinscripción sobre la credencial existente. No extraer secretos de memoria del arnés, reemitir/rotar por inferencia, sobrescribir claves o alterar SQL para sustituir el factor. Comunicar solo disponibilidad y salida a `PS ...>`, nunca el código. Mantener A y la protección contra limpiezas.

**Aclaración posterior del operador:** no aparece el código en ninguna línea visible. No repetirle la búsqueda ni tratarlo como entrega confirmada/pérdida posterior. [Diagnóstico de entrega no acreditada](../evidencia/L-01/ci07-codigo-no-visible-140c10a1-2026-10-17.md): binario canónico con impresión y una prueba con terminal simulada PASS, sin verificar la consola Windows real. Se solicitaron solo SHA del `$trialJar` y `java -version` en la misma B, cuando esté en `PS …>`; sin nuevo `enroll` o `recover`. El caso y la credencial se conservan, recuperación manual pendiente. No emitir un reemplazo por inferencia.

### Diagnóstico vigente y propuesta de reparación del fixture

El operador confirmó el JAR91913FF7… y Java21.0.10+8-LTS-217. La comprobación local del mismo adaptador binario en ConPTY, con ese build y solo valores ficticios, muestra `printf`, `println` y el valor completo; no demuestra la salida de B anterior ni su causa. Para comprobar B, ejecutar desde `PS …>`:

```powershell
java --class-path $trialJar .\backend\target\l01-console-output-readonly\SafeConsoleOutputCheck.java
```

Solo imprime marcadores y letras X identificadas como ficticias; no solicita contraseña, código o archivo de clave ni usa API/base/SMTP. Informar únicamente si se ven los marcadores PRINTF y PRINTLN y el valor ficticio, o el error saneado. No es un caso nuevo ni un ensayo de recuperación.

El servidor del caso ya no está ejecutándose y no retiene el lock de sesión. El marcador persiste y conserva el bloqueo estándar de limpiezas; **no borrarlo ni deducir TEST_CLEANUP_RELEASED**. La reanudación preparada existente puede reutilizar el estado/clave de firma del runtime bajo guardas, pero no reconstruye sus secretos y observaciones volátiles perdidos.

Se propone [REC-CI07-02 v0.1.0](L-01-REC-CI07-02-reparacion-sintetica-propuesta.md): reparación previa exclusiva del mismo fixture sintético, guardada/auditada una sola vez, con autenticación/reautenticación humanas, pública/contadores invariables y código sustituto repetido oculto por el operador antes de escribir su hash. **Requiere aprobación expresa y diagnóstico positivo de B; todavía no está implementada ni ejecutada.** No debilita el protocolo normal, no acredita entrega original y no produce PASS por sí misma. Sin aprobación, conservar el caso pendiente.

**Actualización autorizada:** [REC-CI07-02](L-01-REC-CI07-02-autorizacion-2026-10-17.json) fue aprobada tras el diagnóstico positivo del operador. Su implementación está lista y probada, pero no se sustituyó todavía el código real del fixture. La [guía de ejecución](L-01-REC-CI07-02-ejecucion.md) exige reanudar mismoRUN, comprobar marcador/lock y luego autenticación/reautenticación y repetición oculta antes de la única escritura. Después continúa el protocolo normal con el sustituto confirmado. No inferir PASS, entrega original o custodia independiente de esa reparación. Los párrafos de propuesta anteriores describen sus cortes históricos.

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


## REC-CI07-02 — Reparación personal persistida, recuperación pendiente

Corte **2026-10-17T16:11:48.111015900Z**: [contraste archivado](../evidencia/L-01/REC-CI07-02-reparacion-verificada-2026-10-17.md), SHA del JSON **33BF7FEF8AF9C7F1099205B7A9654A17F45A442DACC9AF5D7788CE4915F81FDE**. Operador comunicó PASS_PRECONDITION_ONLY, contador1 y segmento **82bffcf1-6fab-41fd-a57b-3cbbbf18b123**; SELECT test/rf_test/V26 confirma auditoría única **d791653a-6379-4f02-ac1d-1db020dde403**, fecha servidor **2026-10-17T16:10:02.365128Z**, propuesta aprobada y confirmación oculta antes de escribir. La reparación fue personal mediante el arnes, no una rotación inferida por el agente. Una inscripción, misma cuenta/instalación/pública, aceptación ADMIN y términos publicados conservados; ledger1/2/0 y cupos0/2/20 intactos. Reto viejo vencido sin fallos/consumo; sessionVersion0, cero recuperaciones completas, marcador y lock activos. TEST_CLEANUP_RELEASED=false.

Continuación personal ya indicada: en B `java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy`; mismo correo inicial y nuevo sintéticos, revisar reto5min, sustituto confirmado oculto, Enter sin reenvío, `correo` en A para OTP simulado, contraseña nueva/confirmación y conservar código final una vez. Login recuperado en ventana privada y /platform-admins; solo tras realizarlo, `copia-ok`, `recuperacion-ok`, `verificar`. Comunicar solo MANUAL_RECOVERY_RESULT/EVIDENCE_FILE/EVIDENCE_SHA256, detener ante fallo sin repetir o reenviar; no `salir`. Guía REC-CI07-02-ejecucion v1.0.1, pasos1/2 ya realizados.

Reparación sola no acredita CI07 ni entrega original. Recuperación manual PENDING, CI03 gate previo PENDING sin otro ADMIN autorizado. SC03/07 condicionales sin aplicar; L01 EN_CURSO, piloto bloqueado, L02 sin iniciar. Copia misma PC no acredita custodia independiente. CI01 VERIFICADO sin repetir, sin SMTP externo, Git/GitHub, limpieza global, migraciones de desarrollo/piloto o cambios de final3. Solo consultas y compilación del lector; no nuevas suites ni secretos en evidencia.


## CI07 - PASS manual local contrastado; unico gate local pendiente CI03 (2026-10-17 Lima)

[Informe y revision de los doce criterios](../evidencia/L-01/CI07-manual-PASS-2026-10-17.md). **PASS_LOCAL_TEMPORARY_COPY_ONLY verificado**: archivo humano1792287275662, SHA **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**, mismoRUN140c10a1 y segmento e140a035. Predicado vigente del arnes true; native SELECT test/rf_test/V26 UTC2026-10-18T01:40:55Z confirma reto fa9aa2ae consumido01:30:47.240995Z, OTP/capacidad retirados, misma cuenta, publica5569... conservada, sesion0a1 y login. Observador archivado rechaza3access+3refresh; auditoria revoca5refresh,14previos figuran revocados. Comparaciones de codigo anterior/nuevo proceden del observador ya archivado, sin consultar secretos. Archivo publica milisegundos, nativo conserva995microsegundos adicionales; no se altera archivo. **Hubo2OTP simulados y1reenvio**, dentro3/60: se documenta desviacion de instruccion sin reenviar, sin afirmar cero ni repetir el ensayo.

Ledger1/2/0, cupos0/2/20, aceptacion1, reparacionREC02=1, sustitucionREC03=1 e inscripcionHTTP original1 conservados. Doce hashes de archivos (final3/terminos/JAR/aceptacion/archivo nuevo y6logs previos) coinciden; no se repiten suites. **CI07 es ensayo tecnico local VERIFICADO, no custodia real cumplida.** Los doce criterios se revisan en el informe enlazado. **CI03 sigue PENDING solo por gate previo**, sin otro ADMIN autorizado. Condicion SC07 acreditada, SC03 no; adenda conjunta sin aplicar, originales reales pendientes de piloto. **TEST_CLEANUP_RELEASED=false**, marcador/lock y suspension de limpiezas conservados; mantener A y no repetir recovery/salir. L01 EN_CURSO, piloto bloqueado/L02 sin iniciar. Siguiente accion: via expresamente autorizada para gate; despues adenda/archivo/salida/revisionfinal, sin reaprobacion de lo ya autorizado. Consolidado SHA **C50BB7B7864491D6841B1616445137AF37A11A6F0365A3CA5B856D059254769E**, nativo SHA **A746503800344BB3EBA2C699A665156E4FF1C9931549092156F3C431B913F2EC**.


## Cierre funcional local acreditado — 2026-10-17

**L-01 VERIFICADA; tipo FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Se aplican SC-03/SC-07 y la [precision aprobada SC-03 1.1.0](../contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md), [registro de aplicacion](../contratos/SC-L01-LOCAL-01-aplicacion-v1.1.0-2026-10-17.json). El contrato anterior y la aprobacion condicionada quedan conservados como antecedentes; no se cambian silenciosamente ni se solicita otra aprobacion equivalente.

- CI03: aceptacion humana sintetica original intacta (c496b63c), gate anterior omitido en ese ensayo conservado como historia. [Complemento automatizado separado](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/README.md): capacidad de activacion valida, POST complete409 RESOURCE_CONFLICT especificamente por terminos pendientes, aceptacion automatizada de un unico ADMIN adicional y mismo POST200 COMPLETED; login/grupos200. Resultado compuesto **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**; no se inventa PASS_MANUAL_SYNTHETIC_ONLY ni se atribuye al titular la aceptacion automatizada.
- CI07: **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado y contrastado, sin repetir recuperacion. Copia en la misma PC; no independencia de custodia. CI01 permanece VERIFICADO sin nuevo SMTP.
- Los doce criterios se revisaron con evidencia, entorno y limites en la [matriz final](../evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Se reutilizan regresiones validas y se ejecutaron27 unitarias dirigidas del arnes y el complemento real HTTP/PG, separados de la evidencia humana. Ningun criterio obligatorio del alcance local queda pendiente.
- Archivo previo a salida del arnes: **TEST_CLEANUP_RELEASED=true**, marcador ausente/lock libre, servidores cerrados. Limpieza selectiva del ADMIN adicional por API normal, inactivo y sesiones revocadas403 FORBIDDEN_ROLE; aceptacion e historial conservados. Sin limpieza global, estados forzados o reinicio de cupos. Ledger1/2/0 pasa a **1/3/0**, cupos **0/1/20**, aceptaciones2; ADMIN manual, SUPER_ADMIN y caso CI01 intactos. Las futuras pruebas autorizadas que limpian solo ruta_fija_test pueden reanudarse tras el archivo.
- Requisitos originales pendientes **antes del piloto**: aceptacion propia de ADMIN legitimo; preparacion, medios/custodia independiente e inscripcion/ensayo del titular real; privacidad/identificacion y condiciones de conservacion; infraestructura/base/HTTPS/copias/restauracion y preparacion de participantes/recursos/titulos. No se registran como satisfechos por equivalencia. Solo una PC Windows confirmada; no se presupone medio adicional.
- Terminos1.0.0 SHA20FFD952... y final3 ZIP38065106.../manifiestoE78D7002... comprobados sin cambios. Sin SMTP adicional, claves definitivas, migraciones de desarrollo/piloto, despliegue o Git/GitHub. S/0 y sin offline. **L-02 NO INICIADA / NO AUTORIZADA; OK VERDE separado.**

El [acta](../actas/L-01-cierre-funcional-local-2026-10-17.md) contiene el alcance, referencias, limites y bloqueos. Las observaciones historicas PENDING del ensayo humano anterior al complemento no representan un pendiente actual del alcance local tras aplicar la adenda.
