# L-01 — Diagnóstico y continuación selectiva del bootstrap humano

Fecha: 2026-10-17. Caso **140c10a1-be0f-4d08-a6d4-c796e2ed107a**. L-01 **EN_CURSO**; CI03/CI07 humanos no acreditados; SC-03/SC-07 aprobados condicionalmente y **sin aplicar**.

## 1. Incidente e historial conservados

El titular reportó que la primera ejecución se interrumpió al capturar/confirmar la contraseña y produjo `L01_HUMAN_TRIAL=REFUSED_DETAILS_REDACTED`. Una segunda ejecución de `-Serve` produjo ese resultado más `L01_HUMAN_REASON=ANOTHER_HUMAN_CASE_PENDING`. Comunicó que sospechaba un error de escritura; **esa sospecha no identifica por sí sola la causa**. No se pidió ni se leyó la contraseña del titular.

El arnés anterior escribía el marcador antes de la captura, pero guardaba el estado del caso solo después del bootstrap. Si faltaban STATE/SECRET, el siguiente `-Serve` intentaba un RUN_ID nuevo y chocaba con el marcador anterior. **Esta es la causa comprobada del segundo rechazo y de la falta de continuación disponible.**

El primer rechazo no conservó una causa individual: agrupaba confirmación/longitud y ocultaba los `IllegalArgumentException`, sin diario previo a la captura. También son posibles otras fallas antes de la persistencia. Resultado saneado: **`FIRST_REJECTION_CAUSE=NOT_RECORDED_CANNOT_RECONSTRUCT`**. No se declara «confirmación distinta» o «formato inválido» retrospectivamente.

## 2. Estado comprobado, sin escrituras de base

Inspección de solo lectura en **ruta_fija_test / rf_test / V26**, final a **2026-10-17T07:27:23.721351700Z**:

| Elemento | Resultado |
|---|---|
| RUN_ID del marcador | `140c10a1-be0f-4d08-a6d4-c796e2ed107a`, el original |
| SHA del marcador | **83A91866767A8EFC56CBF7ED553191D53610F5A264E0E9A6C0D86CBBE79FF205**, idéntico antes/después |
| Proceso del ensayo | Terminado; sin listeners4311/8091. Java PID4612 no es el lanzador humano; no se intervino. |
| Lock asesor de sesión | Libre tras terminar el proceso; no se interpreta como permiso de limpieza |
| Protección persistente | **Activa:** `standardCleanupRefusedByMarker=true`, por el código de guarda existente, sin ejecutar limpieza |
| Cuenta SUPER_ADMIN / alta histórica | **0 / 0** |
| Cuentas y organización del caso | **0 / 0**, buscando solo las referencias sintéticas del mismo RUN_ID |
| Identidad común / clave HMAC inscrita / recuperación | **0 / 0 / 0** |
| Contraseña persistida del caso | **No:** no existe cuenta; tampoco archivo privado de firma JWT del arnés |
| STATE / SECRET | Ambos ausentes; el diagnóstico no los crea |
| Ledger global | SUPER_ADMIN0, ADMIN1, CONDUCTOR0; cupos disponibles1/3/20, conservados |
| Términos | Documento aprobado1.0.0/huella20FFD952… intactos; aceptaciones0 |
| Elegibilidad | **`ELIGIBLE_SAME_CASE_NO_PREPARED_ACCOUNT`** |

No se alteró el caso OTP finalizado, el ledger ni otras filas. No se guardó una contraseña, creó cuenta, emitió correo o inició backend durante el diagnóstico. El marcador permanece en su ubicación original y se archivó solo una copia pública de su UUID.

## 3. Corrección mínima del mismo arnés, exclusivamente en test

Se conservan `ManualL01HumanTrial`, `ManualL01HumanTrialServer` y su wrapper; no se construye otro mecanismo ni se cambia el protocolo de recuperación.

- **`-InspectRunId <GUID>`:** inspección JDBC read-only del caso exacto, con ausencia de cuenta/organización/cupo SUPER_ADMIN/identidad/credencial, comprobación de marcador y protección. No muestra secretos o hashes de contraseñas.
- **`-Serve -ResumeRunId <GUID>`:** continuación explícita. Exige el mismo marcador/RUN_ID y estado coherente; si aún no hay cuenta/credencial, comprueba nuevamente ausencia persistida y disponibilidad histórica. Conserva el RUN_ID y el marcador; no los sustituye ni borra.
- Antes de pedir contraseña guarda **atómicamente metadatos públicos** y diario de eventos/códigos del mismo caso. Un error de entrada vuelve a dejarlo pendiente y reanudable; no registra valor, hash, longitud o contenido introducidos.
- Si ya existe cuenta, identidad/cupo o credencial, **no permite recapturar/sobrescribir su contraseña por inferencia**. La rama de cuenta preparada mantiene su credencial vigente; un estado inconsistente exige revisión selectiva.
- Distingue errores futuros: `PASSWORD_INPUT_CANCELLED`, `PASSWORD_LENGTH_INVALID`, `PASSWORD_UTF8_LIMIT_EXCEEDED`, `PASSWORD_CONFIRMATION_MISMATCH`. La causa antigua desconocida sigue como tal; no se reescribe el historial.
- Conserva los gates, correo doble, aislamiento, trigger de cupos, lock y marcador. No hay cambios de código normal, migración, aceptación automática, limpieza o desactivación de preflight.

### Límites efectivos de contraseña

El bootstrap anterior y el actual admiten **12…72 posiciones de entrada**, con límite adicional de **72 bytes UTF-8**, acorde con el encoder utilizado. Para ASCII: **12…72 caracteres**. Unicode puede alcanzar el límite de bytes antes de72. La instrucción previa12…64 era una recomendación más restrictiva, no el máximo del arnés; no demuestra la causa del primer rechazo. Se corrigieron la guía y el aviso de consola sin debilitar el límite.

La contraseña solo será capturada personalmente por el titular. Al validar y preparar por primera vez la cuenta del mismo caso se consumirá la alta SUPER_ADMIN prevista; no se sobrescribe una cuenta previa ni se recupera cupo.

## 4. Comprobaciones ejecutadas y límites

- **19 unitarias dirigidas PASS**, cero fallos/errores/omitidas: incluye las10 anteriores y9 casos nuevos de marcador/UUID, cuenta/cupo/organización/clave existentes, estado ajeno, límite ADMIN, historial, reemplazo atómico, ASCII12/64/72, rechazo11/73, UTF-8 y razones diferenciadas. No sumar las ejecuciones intermedias ni presentarlas como ensayos humanos.
- Inspección real del caso antes/después: ausencia persistida y ledger iguales, guardas de limpieza efectivas y marcador sin cambios. **Solo lectura**, sin Spring/Flyway/SMTP/limpieza.
- Parámetros `-Serve -ResumeRunId` reconocidos; ejecución no interactiva rechazada **antes de capturar o crear datos**. AST PowerShell del wrapper y cinco bloques de la guía correctos; wrapper con BOM UTF-8 para Windows PowerShell.
- Fuente/guía anteriores permanecen en el archivo de preparación. Los manifiestos históricos, JAR vigente, términos y final3 conservan sus huellas.

La continuación interactiva todavía no se ejecutó por el titular. Estas comprobaciones acreditan su preparación/guardas, **no** la creación humana, aceptación o recuperación posteriores. No se ejecutaron suites con limpieza ni una regresión completa.

### Archivos saneados

- [Inspección inicial](bootstrap-recuperacion-140c10a1-2026-10-17/inspeccion-inicial.log), SHA **C76A816BAFF0BA4E48C41D709BC837ABB07BE4215AB40EAF4BE1980F3AB58E6C**.
- [Inspección final](bootstrap-recuperacion-140c10a1-2026-10-17/inspeccion-final.log), SHA **6165DC79FEA2C01F04E1C12FD5302119563D897D5D5A0F0D78997F2745F1DDFC**.
- [19 unitarias](bootstrap-recuperacion-140c10a1-2026-10-17/unitarias-19.log), SHA **FCFBD835727AF4F8F8316B7859CC56A59688C20B271BF678489FBC845F1EFE83**.
- [Copia pública del marcador](bootstrap-recuperacion-140c10a1-2026-10-17/marcador-original-publico.txt), misma huella83A91866…; el marcador operativo no se modifica.

## 5. Continuación exacta que realiza el titular

Desde la raíz, en terminal Windows interactiva sin transcripción/grabación/redirección:

```powershell
.\scripts\Invoke-RutaFijaL01HumanTrial.ps1 -Serve -ResumeRunId '140c10a1-be0f-4d08-a6d4-c796e2ed107a'
```

No requiere volver a compilar: las clases están preparadas. Introducir y confirmar una contraseña de prueba de12…72 caracteres ASCII; **no aparecerán caracteres ni asteriscos**. Mantener terminal A abierta y confirmar solo RUN_ID y disponibilidad de `http://127.0.0.1:4311/login`. Si se rechaza, comunicar únicamente el código saneado y conservar el caso; no iniciar otro ni borrar el marcador. La [guía vigente](../../operacion/L-01-ensayos-humanos-sinteticos.md) mantiene las siguientes etapas sin aceptar o manejar secretos por el operador.

Limpiezas siguen suspendidas hasta terminar/archivar y liberar por el recorrido previsto. CI01 no se repite. L-01 EN_CURSO, ambas PASS humanas pendientes, adenda SC no aplicada, piloto bloqueado y L-02 sin iniciar.
