# REC-CI07-02 — Ejecución personal sobre el mismo fixture

Versión1.0.0, 2026-10-17. **Autorizada e implementada; reparación real del fixture todavía NO ejecutada.** [Propuesta0.1.0 aprobada](L-01-REC-CI07-02-reparacion-sintetica-propuesta.md), SHA B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2; [autorización y diagnóstico positivo de B](L-01-REC-CI07-02-autorizacion-2026-10-17.json), SHA EF4E1B5B5C03C448F77B4F2F9B744BC5E710DF75A36902D2FCE91B97EBE9CFC4.

Solo `ruta_fija_test`/`rf_test`/V26 y RUN_ID **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. Ledger1/2/0, cupos0/2/20, cuenta SUPER_ADMIN088518c1 y pública5b727e9a… preservados. No hay otro ADMIN autorizado. [Evidencia técnica y sus límites](../evidencia/L-01/REC-CI07-02-implementacion-2026-10-17.md).

## 1. Reanudar el servidor: intervención actual

Conservar B y sus variables/archivos temporales. En una terminal PowerShell interactiva que servirá como A, desde la raíz del proyecto, sin transcripción, grabación o redirección:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'
```

No ejecutar `-Serve` solo. Este comando reutiliza cuenta/organización/clave de firma del runtime existentes y crea un segmento de observación nuevo del mismo caso; no prepara otra identidad ni sustituye contraseñas. No debe pedir crear una contraseña de arranque. Si la pide o aparece error, detener y comunicar solo el motivo saneado.

Mantener A abierta cuando aparezca `Ensayo L01>`. Abrir **http://127.0.0.1:4311/login** para comprobar que carga, sin iniciar sesión todavía. Comunicar solo RUN_ID y si carga el login. **Esperar la comprobación de marcador/lock antes del paso2.** El marcador se conserva incluso si el proceso se interrumpe; no eliminarlo para reanudar pruebas.

## 2. Reparación del fixture: solo después de comprobar protección

En A, únicamente en el prompt `Ensayo L01>`, escribir una vez:

```text
reparar-codigo
```

El comando exige dos entradas ocultas distintas:

1. «Autenticación: contraseña actual del SUPER_ADMIN sintético…». Introducir la contraseña de ese SUPER_ADMIN que funcionó en el CRM, no la de ADMIN o la de cifrado de la clave.
2. «Reautenticación: repite su contraseña actual…». Volver a introducirla. Son dos logins normales con sus límites y auditoría; los tokens no se imprimen. Las sesiones nuevas son la referencia que se comprobará después de recuperar.

Si falla una entrada, detener. No repetir autenticación automáticamente.

Después aparecerá **una vez** el encabezado:

```text
CODIGO SUSTITUTO DEL FIXTURE SINTETICO — mostrar una sola vez, guardar separado:
```

El operador debe leer y conservar únicamente de forma local la línea siguiente de43 caracteres, separada de los archivos de clave. Se trata del sustituto propuesto para el fixture; aún no está activo en ese instante.

Cuando aparezca «Repite AHORA los43 caracteres del sustituto…», introducir exactamente ese valor en la entrada oculta y Enter. No hay caracteres ni asteriscos. **Enter vacío, cancelación o diferencia detienen antes de guardar el hash.** No repetir el comando para obtener otro valor si falla: conservar el resultado para diagnóstico.

Solo después de la coincidencia, una sesión vigente y la revalidación transaccional de las guardas, se cambia el hash y se inserta la auditoría. Los bloqueos de cupos son los mismos de V22/V23; no se amplían permisos del ledger ni se cambia su contenido.

Resultado esperado, todavía no obtenido:

```text
L01_FIXTURE_REPAIR=PASS_PRECONDITION_ONLY
SYNTHETIC_FIXTURE_CODE_REPAIR_COUNT=1
RECOVERY_SEGMENT_ID=<referencia pública>
```

Comunicar solo esas tres salidas. La reparación no es un PASS de CI07. El código solo se considera activo tras ese resultado. El arnés impide otra reparación exitosa por su auditoría persistida y el bloqueo de la credencial; si hubiera una interrupción después del commit, revisar esa auditoría antes de cualquier continuación.

## 3. Recuperación manual normal: después de contrastar la reparación

Usar el cliente/JAR actual y la copia temporal ya comprobada. No reinscribir o regenerar claves. El código para este recorrido será **el sustituto confirmado**, no el código original cuya entrega no se acreditó.

```powershell
java -jar $trialJar recover 'http://127.0.0.1:4311' $trialCopy
```

Leer previamente toda la secuencia del apartado5 de la [guía de ensayos](L-01-ensayos-humanos-sinteticos.md): correos `sa.human.140c10a1@rutafija.test` → `sa.recovered.140c10a1@rutafija.test`, contraseña de cifrado de la copia, revisar reto y firmar, sustituto oculto, Enter sin reenvío, `correo` en A para OTP simulado, OTP en B y nueva contraseña/confirmación. El reto dura cinco minutos. Preservar el código final rotado que el cliente mostrará una vez, sin capturarlo en evidencias/chat.

En una nueva ventana privada, login con correo nuevo/contraseña nueva y `/platform-admins`. Después, únicamente si se realizaron las acciones, `copia-ok`, `recuperacion-ok` y `verificar` en A. Se comprueban consumo, rotación, pública y sesiones previas observadas del **segmento reanudado**; no se reconstruyen los tokens perdidos del proceso anterior. La referencia del código previo proviene del fixture reparado o de la prueba normal exitosa del mismo segmento, solo en memoria.

Comunicar únicamente MANUAL_RECOVERY_RESULT, EVIDENCE_FILE y EVIDENCE_SHA256. Se espera PASS_LOCAL_TEMPORARY_COPY_ONLY solo tras el recorrido real completo y su archivo; todavía PENDING. Si falla, conservar caso sin reintento o correo automático.

## 4. Estado y límites que siguen vigentes

CI03 conserva aceptación sintética y gate previo pendiente; no otro ADMIN ni alteración de esa aceptación. Aun si CI07 pasa, no escribir `salir` ni forzar liberación mientras CI03 siga pendiente. TEST_CLEANUP_RELEASED=false hasta la salida permitida con ambos PASS archivados. SC03/SC07 aprobados condicionalmente, todavía sin aplicar. L-01 EN_CURSO, piloto bloqueado y L-02 sin iniciar. Copia temporal mismaPC no acredita custodia independiente ni titular real; CI01 no se repite, sin SMTP externo, Git/GitHub, despliegue, migraciones de desarrollo/piloto o regeneración de final3.
