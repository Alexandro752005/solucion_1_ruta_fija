> **Estado vigente al 2026-10-17: L-01 VERIFICADA — FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** [Acta](../actas/L-01-cierre-funcional-local-2026-10-17.md) y [matriz final](../evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Los cortes anteriores siguientes se conservan como antecedentes. Piloto bloqueado; L-02 sin iniciar y sin autorizar.

# REC-CI07-03 — Ejecución personal del mismo caso sintético

Versión1.0.7, 2026-10-17, America/Lima. **Recuperación manual local PASS contrastada; L-01 pendiente de CI03.** [Propuesta0.1.0 aprobada](L-01-REC-CI07-03-clave-temporal-propuesta.md), SHA `1B0886BAC2CB077DEB9C0539D7CE16BE3349723EC74496A49A7531DDF20D38B8`; [autorización](L-01-REC-CI07-03-autorizacion-2026-10-17.json), SHA `434560CD1674A008405E5B1AA425CC5E8E6C7DDC17475A997EA2B7414C32BBA6`.

**Etapa vigente:** **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado en `observacion-1792287275662.json`, SHA **E0A986D10E09F8FBB289061753B3E36759B0CAE24E53731EDE4BBA8640D5FFFB**, contrastado en test/rf_test/V26 aUTC2026-10-18T01:40:55Z (17 de octubre en Lima). Misma cuenta/segmento, reto/OTP consumidos, capacidad retirada, pública conservada, versión0→1, revocación y login comprobados. **No repetir ninguno de los recorridos siguientes.** Falta gate previo CI03; mantener A y protección, SC03/07 sin aplicar, L01 EN_CURSO. [Resultado y revisión de doce criterios](../evidencia/L-01/CI07-manual-PASS-2026-10-17.md). Se observaron2 OTP simulados (1reenvío), dentro de3/60min y registrado como desviación de la instrucción de no reenviar; no se registra cero.

Solo `ruta_fija_test`/`rf_test`/V26, RUN_ID `140c10a1-be0f-4d08-a6d4-c796e2ed107a`. Misma cuenta/organización/instalación/ADMIN, ledger1/2/0 y cupos0/2/20. REC-CI07-02 y su sustituto se conservan. Esta preparación no es otra inscripción HTTP; la original queda ligada a la pública anterior. No acredita recuperación ni custodia independiente.

**Leer y ejecutar únicamente la etapa indicada por A-COORD. No pegar una secuencia de comandos interactivos completa:** esperar siempre a que termine cada comando y vuelva `PS ...>`. Ningún secreto se introduce en `Ensayo L01>` como comando. No grabar ni redirigir las terminales. Solo el operador genera/abre las claves.

## 1. Recargar el arnés: procedimiento anterior, ya contrastado

Conservar B y los archivos originales. En A, en `Ensayo L01>`:

1. Escribir una vez `verificar`; esperar a que archive y vuelva el prompt.
2. Escribir una vez `salir`. Para esta recarga coordinada, el código vigente archiva otra vez y conserva el marcador porque ambos ensayos son PENDING. Debe mostrar `TEST_CLEANUP_RELEASED=false` y regresar a PowerShell. Si muestra true o falla, detener.
3. En A ya en `PS ...>`, desde la raíz, ejecutar una vez:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'
```

Debe reutilizar la cuenta sin contraseña nueva de arranque, mostrar el mismo RUN_ID e incluir `preparar-clave` en los comandos. Mantener A abierta; comprobar que carga http://127.0.0.1:4311/login sin login. Comunicar solo RUN_ID, si se conservó TEST_CLEANUP_RELEASED=false, comando presente y login cargado. **Esperar comprobación de marcador/lock antes de continuar.** No borrar marcadores ni liberar bloqueos manualmente.

## 2. Nuevo destino temporal: preparación completada, no repetir

En B, solo en `PS ...>`, desde la raíz, manteniendo A abierta en `Ensayo L01>`. El bloque siguiente no solicita secretos y no llama a Java/API. Los puntos y coma son necesarios: conservan separadas las instrucciones incluso si el pegado elimina los saltos de línea. Pegar **solo este bloque** una vez; no incluir los prompts ni el siguiente comando Java.

```powershell
$trialSetupReady = $false;
try {
    $trialJar = (Resolve-Path -LiteralPath '.\artifacts\L-01-superadmin-titular-2026-10-17\ruta-fija-recovery-cli.jar' -ErrorAction Stop).Path;
    if ((Get-FileHash -LiteralPath $trialJar -Algorithm SHA256 -ErrorAction Stop).Hash -ne '91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A') { throw 'Cliente distinto.'; };
    $trialNewKeyRoot = Join-Path $env:LOCALAPPDATA 'RutaFijaEnsayoTemporal\140c10a1-be0f-4d08-a6d4-c796e2ed107a\REC-CI07-03';
    if (Test-Path -LiteralPath $trialNewKeyRoot -ErrorAction Stop) { throw 'Destino ocupado.'; };
    New-Item -ItemType Directory -Path $trialNewKeyRoot -ErrorAction Stop | Out-Null;
    $trialNewSid = [Security.Principal.WindowsIdentity]::GetCurrent().User;
    $trialNewAcl = [Security.AccessControl.DirectorySecurity]::new();
    $trialNewAcl.SetOwner($trialNewSid);
    $trialNewAcl.SetAccessRuleProtection($true, $false);
    $trialNewInheritance = [Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [Security.AccessControl.InheritanceFlags]::ObjectInherit;
    $trialNewRule = [Security.AccessControl.FileSystemAccessRule]::new($trialNewSid, [Security.AccessControl.FileSystemRights]::FullControl, $trialNewInheritance, [Security.AccessControl.PropagationFlags]::None, [Security.AccessControl.AccessControlType]::Allow);
    $trialNewAcl.AddAccessRule($trialNewRule);
    Set-Acl -LiteralPath $trialNewKeyRoot -AclObject $trialNewAcl -ErrorAction Stop;
    $trialNewPrimary = Join-Path $trialNewKeyRoot 'principal-temporal.p12';
    $trialNewCopy = Join-Path $trialNewKeyRoot 'copia-temporal.p12';
    $trialSetupReady = $true;
    Write-Host 'L01_TEMPORARY_KEY_WORKSPACE=READY_NO_KEYS_CREATED';
} catch { Write-Host 'L01_TEMPORARY_KEY_WORKSPACE=STOPPED_KEEP_STATE'; };
```

No modifica `$trialPrimary`/`$trialCopy` originales ni sus archivos. Solo un resultado `L01_TEMPORARY_KEY_WORKSPACE=READY_NO_KEYS_CREATED` confirma la preparación completa del directorio. Si muestra `STOPPED_KEEP_STATE` o un error, detenerse sin repetir, borrar ni recrear el destino; una creación parcial no se limpia automáticamente. **La preparación ya fue comunicada y contrastada; conservar estas instrucciones como antecedente sin volver a ejecutarlas.** El bloque no es una nueva modalidad de ensayo y no modifica el lanzador.

## 3. Generar, copiar y comprobar: un comando interactivo cada vez

Generación ya comunicada como satisfactoria; conservar el comando como antecedente y no repetirlo:

```powershell
java -jar $trialJar keygen $trialNewPrimary
```

Elegir y confirmar personalmente una **contraseña de CIFRADO** de prueba, mínimo12 caracteres; se recomienda ASCII para evitar diferencias de teclado. Esa contraseña abrirá principal y copia; no se obtiene del login. Conservarla mediante los medios personales disponibles, fuera de los archivos cifrados y sin chat, argumentos, `.env` o evidencia. No se presupone gestor ni medio independiente. Si falla, detener. No generar la clave definitiva.

El operador ya comunicó éxito, conservación de la contraseña y lectura satisfactoria del principal y de la copia con huellas coincidentes. Las entradas ocultas no muestran caracteres ni asteriscos. Se conservan los comandos siguientes como antecedente, sin repetirlos.

**Antecedente, no repetir:** la copia ya existe según metadatos. Se conserva el bloque no interactivo indicado para su creación. Las dos llamadas usan `overwrite=false`; ante un destino ocupado o copia parcial, detenerse sin borrar/repetir:

```powershell
try {
    if ((Test-Path -LiteralPath $trialNewCopy -ErrorAction Stop) -or (Test-Path -LiteralPath ($trialNewCopy + '.pub') -ErrorAction Stop)) { throw 'Destino ocupado.'; };
    [IO.File]::Copy($trialNewPrimary, $trialNewCopy, $false);
    [IO.File]::Copy(($trialNewPrimary + '.pub'), ($trialNewCopy + '.pub'), $false);
    Write-Host 'L01_TEMPORARY_COPY=CREATED_LOCAL_ONLY';
} catch { Write-Host 'L01_TEMPORARY_COPY=STOPPED_KEEP_STATE'; };
```

**Comprobación del principal ya comunicada como PASS, no repetir:** se conserva el comando ejecutado con la contraseña de CIFRADO nueva:

```powershell
java -jar $trialJar check-key $trialNewPrimary
```

Se comunicó PASS/huella del principal, sin la línea de consola de creación de copia. No se reconstruye esa línea: la copia y su pública fueron comprobadas por metadatos. Una lectura del principal no acredita lectura de la copia.

**Comprobación de copia ya comunicada como PASS, no repetir:** se conserva el segundo comando ejecutado con el mismo cifrado nuevo:

```powershell
java -jar $trialJar check-key $trialNewCopy
```

Los dos resultados comunicados son `LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE` con la misma pública nueva, contrastada con los archivos `.pub`. El agente no descifró las privadas. Dos archivos en esta PC no son respaldo independiente.

## 4. Preparación pública completada: conservar como antecedente, no repetir

**Preparación ya ejecutada y contrastada:** los pasos siguientes documentan el procedimiento realizado bajo REC-CI07-03. Su confirmación humana incluyó disponibilidad del sustituto vigente. Conservarlo localmente para `recover`; no existe autorización para otra reparación del código. Ante pérdida, detenerse y avisar sin valores.

Para consultar únicamente el archivo público, en B ya en PowerShell:

```powershell
Get-Content -LiteralPath ($trialNewPrimary + '.pub')
```

Solo ese archivo `.pub`; no abrir ni pegar el `.p12`. Su línea X.509 Base64 es pública y se pegará directamente en A, no se necesita compartirla por chat.

En A, en `Ensayo L01>`, ejecutar una vez `preparar-clave`. Solicita en este orden:

1. Contraseña de **ACCESO SUPER_ADMIN**, oculta, para autenticación normal.
2. La misma contraseña de **ACCESO SUPER_ADMIN**, oculta, para reautenticación normal. Las dos entradas corresponden al acceso al CRM y no muestran caracteres ni asteriscos.
3. La línea pública del archivo nuevo `.pub`, visible. No contraseña ni código.
4. `PUBLIC_KEY_SHA256` nueva de64 caracteres, coincidente en ambos checks: `5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433`.
5. Revisar huellas anterior/nueva. Solo si comprobó principal/copia y **todavía conserva el sustituto vigente de REC-CI07-02**, escribir exactamente `CLAVE_COPIA_Y_CODIGO_CONFIRMADOS`. No introducir aquí ese código ni ninguna contraseña. Si no lo conserva, cancelar/detener: no está autorizada otra reparación del código.

La transacción vuelve a validar las guardas, cambia solo pública/timestamp e inserta una auditoría; no toca código, cuenta, instalación o cupos. Ante rechazo, no repetir.

Resultado comunicado y contrastado:

```text
L01_FIXTURE_KEY_PREPARATION=PASS_PRECONDITION_ONLY
SYNTHETIC_FIXTURE_KEY_REPLACEMENT_COUNT=1
RECOVERY_SEGMENT_ID=e140a035-8211-4037-82e6-388329f1cfc8
PUBLIC_KEY_SHA256=5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433
```

Correspondencia de persistencia verificada: auditoría58b926a7… y pública actual coinciden con la aprobación, caso y segmento. Las guardas de preparación ya la rechazan como repetida. Una sustitución no es un PASS de CI07.

## 5. Recuperación normal completada: instrucciones como antecedente, no repetir

**Recorrido ya ejecutado y contrastado:** la secuencia siguiente documenta las instrucciones dadas; no debe volver a ejecutarse. El sustituto REC02 ya fue consumido y rotado durante esta recuperación. Se mantiene la copia temporal y el código nuevo bajo control personal, sin exponerlos. Procedimiento indicado para aquel recorrido:

```powershell
java -jar $trialJar recover 'http://127.0.0.1:4311' $trialNewCopy
```

1. Correo actual `sa.human.140c10a1@rutafija.test`; nuevo `sa.recovered.140c10a1@rutafija.test`.
2. Contraseña de **CIFRADO nueva** para abrir la copia nueva: la misma que pasó ambos checks. Entrada invisible sin caracteres ni asteriscos.
3. Revisar instalación `f0cd888b-57df-4def-9d08-7882ffadebae`, correo nuevo, reto exacto y vencimiento. `accountReference` es aleatorio por reto: contrastar que coincide entre resumen y bytes mostrados; no equipararlo al UUID interno de la cuenta. S solo si corresponde y continúa vigente.
4. Introducir oculto el **sustituto vigente de43 caracteres** conservado desde REC-CI07-02. La preparación de clave no lo cambió.
5. Enter sin S ante reenvío. En A, en `Ensayo L01>`, `correo` una vez para leer OTP simulado asociado al correo nuevo; introducirlo oculto en B. No hay correo externo en este ensayo.
6. Establecer/confirmar contraseña de acceso SUPER_ADMIN nueva,12–72 caracteres ASCII para este ensayo, distinta de la anterior. El cliente muestra una sola vez el código final rotado: conservarlo localmente separado de las claves, sin evidencia/chat. Si no aparece o cualquier paso falla, detenerse sin otra recuperación, reenvío o reparación por rutina.
7. Nueva ventana privada: http://127.0.0.1:4311/login con correo/contraseña nuevos; comprobar `/platform-admins`.
8. Solo tras usar realmente la copia, completar recuperación, conservar el código nuevo y comprobar login/acceso: en A, `copia-ok`, luego `recuperacion-ok`, luego `verificar`, uno cada vez. No escribir declaraciones de acciones todavía no realizadas.

Comunicar solo `MANUAL_RECOVERY_RESULT`, `EVIDENCE_FILE` y `EVIDENCE_SHA256`. Se exige clave nueva conservada **durante la recuperación**, código/reto consumidos, rotación, login y rechazo de sesiones observadas del nuevo segmento, más correspondencia de la auditoría de preparación. No se afirma conservación de la pública original a través del mantenimiento. Ante fallo, detener sin reenvíos ni repeticiones.

## 6. Archivo, protección y criterios restantes

**PASS_LOCAL_TEMPORARY_COPY_ONLY contrastado**, con límites de copia temporal misma PC. La inscripción HTTP original permanece1 y ligada a la pública anterior; sustitución de fixture1 por su auditoría separada y recuperación normal1. La entrega original del primer código de inscripción no se acredita retroactivamente. La copia y custodia del titular real siguen pendientes.

CI03 gate previo sigue pendiente, sin otro ADMIN autorizado. No escribir `salir` al finalizar CI07 para intentar liberar limpiezas; la única salida anticipada aquí indicada es la recarga coordinada del apartado1 con marcador conservado y resultado false. Ambos PASS archivados/contrastados siguen siendo necesarios para aplicar SC-03/SC-07. No cierre, piloto, custodia independiente o titular real por inferencia. L-01 EN_CURSO y L-02 sin iniciar. CI01 no se repite; términos, final3 y protocolo normal intactos. Sin SMTP externo, Git/GitHub, despliegue o migraciones de desarrollo/piloto.


## Cierre funcional local acreditado — 2026-10-17

**L-01 VERIFICADA; tipo FUNCIONAL_LOCAL_PILOTO_BLOQUEADO.** Se aplican SC-03/SC-07 y la [precision aprobada SC-03 1.1.0](../contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md), [registro de aplicacion](../contratos/SC-L01-LOCAL-01-aplicacion-v1.1.0-2026-10-17.json). El contrato anterior y la aprobacion condicionada quedan conservados como antecedentes; no se cambian silenciosamente ni se solicita otra aprobacion equivalente.

- CI03: aceptacion humana sintetica original intacta (c496b63c), gate anterior omitido en ese ensayo conservado como historia. [Complemento automatizado separado](../evidencia/L-01/REC-CI03-01-automatizado-2026-10-17/README.md): capacidad de activacion valida, POST complete409 RESOURCE_CONFLICT especificamente por terminos pendientes, aceptacion automatizada de un unico ADMIN adicional y mismo POST200 COMPLETED; login/grupos200. Resultado compuesto **PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE**; no se inventa PASS_MANUAL_SYNTHETIC_ONLY ni se atribuye al titular la aceptacion automatizada.
- CI07: **PASS_LOCAL_TEMPORARY_COPY_ONLY** archivado y contrastado, sin repetir recuperacion. Copia en la misma PC; no independencia de custodia. CI01 permanece VERIFICADO sin nuevo SMTP.
- Los doce criterios se revisaron con evidencia, entorno y limites en la [matriz final](../evidencia/L-01/matriz-final-funcional-local-2026-10-17.md). Se reutilizan regresiones validas y se ejecutaron27 unitarias dirigidas del arnes y el complemento real HTTP/PG, separados de la evidencia humana. Ningun criterio obligatorio del alcance local queda pendiente.
- Archivo previo a salida del arnes: **TEST_CLEANUP_RELEASED=true**, marcador ausente/lock libre, servidores cerrados. Limpieza selectiva del ADMIN adicional por API normal, inactivo y sesiones revocadas403 FORBIDDEN_ROLE; aceptacion e historial conservados. Sin limpieza global, estados forzados o reinicio de cupos. Ledger1/2/0 pasa a **1/3/0**, cupos **0/1/20**, aceptaciones2; ADMIN manual, SUPER_ADMIN y caso CI01 intactos. Las futuras pruebas autorizadas que limpian solo ruta_fija_test pueden reanudarse tras el archivo.
- Requisitos originales pendientes **antes del piloto**: aceptacion propia de ADMIN legitimo; preparacion, medios/custodia independiente e inscripcion/ensayo del titular real; privacidad/identificacion y condiciones de conservacion; infraestructura/base/HTTPS/copias/restauracion y preparacion de participantes/recursos/titulos. No se registran como satisfechos por equivalencia. Solo una PC Windows confirmada; no se presupone medio adicional.
- Terminos1.0.0 SHA20FFD952... y final3 ZIP38065106.../manifiestoE78D7002... comprobados sin cambios. Sin SMTP adicional, claves definitivas, migraciones de desarrollo/piloto, despliegue o Git/GitHub. S/0 y sin offline. **L-02 NO INICIADA / NO AUTORIZADA; OK VERDE separado.**

El [acta](../actas/L-01-cierre-funcional-local-2026-10-17.md) contiene el alcance, referencias, limites y bloqueos. Las observaciones historicas PENDING del ensayo humano anterior al complemento no representan un pendiente actual del alcance local tras aplicar la adenda.
