# L-01 — CI01 verificado, términos aprobados/publicados y operación del titular pendiente

Fecha: **2026-10-17, America/Lima**. L-01 **EN_CURSO**; L-02 **NO INICIADA**. Este corte prevalece para el estado actual sobre los anteriores, que se conservan como históricos. La [matriz canónica](matriz-cierre-L01-revision-2026-10-14.md) contiene el estado por criterio; este documento conserva sus evidencias.

## CI-L01-01 — VERIFICADO

### Fuente humana y correspondencia local

El titular comunicó ejecución satisfactoria de la continuación, con estas salidas saneadas. Esta es una transcripción de su mensaje, **no una transcripción automática inventada del proceso**:

```text
MANUAL_OTP_RUN_ID=fa616262-500e-4490-9292-7ee1927d2257
MANUAL_OTP_CASE_RUN_ID=3e9c0bf5-237b-4370-a87f-cea684e0b1d8
HTTP_CONSUMPTION=PASS_PENDING_REVIEW_NO_JWT_NO_COOKIE
CHALLENGE_CONSUMED_AND_OTP_CLEARED=true
OPERATIONAL_APPUSER_OR_DRIVER_CREATED=false
RESEND_ATTEMPTS=0
EXACT_FIXTURE_CLEANUP=PASS_DISABLED_AND_TOKENS_REVOKED
LEDGER_CREATION_PRESERVED=true
RESULT=PASS_PERSISTED_AND_CONSUMED_PENDING_REVIEW
Consumo observado: 2026-10-17T05:02:28.625877600Z
```

La utilidad nueva `ManualOtpEvidenceVerification` y su wrapper `scripts/Test-RutaFijaL01OtpEvidence.ps1` **solo leen** la base: JDBC read-only/repeatable-read, `rf_test`, destino validado `ruta_fija_test`; no Spring, Flyway, limpieza, HTTP o SMTP. Conservan sin modificar el arnés que ejecutó el titular: `ManualOtpDeliveryValidation.java` SHA **0B4B61B62870BC8FE163CF2476CCFBDB74502D44386C37D6AD6C7422223B4259** y su wrapper SHA **AF6961340DA6259382EB1540DEBF7C7D18BF03ADE05889C8901FC5049A52B1C0**.

Se contrastó el caso raíz por nombre/ADMIN sintéticos exactos y se unieron desafío, identidad, invitación y solicitud. Tanto desafío como solicitud conservan `manual-otp-fa616262-500e-4490-9292-7ee1927d2257`. La consulta identificó:

| Dato persistido | Resultado |
|---|---|
| Solicitud | `e0f3de05-5b8a-4f26-93d8-89e6ebc693dd`, PENDING_REVIEW, sin provisión ni aprobación personal |
| OTP emitido | `2026-10-17T05:01:07.237385Z` — 00:01:07 Lima |
| Consumo persistido | `2026-10-17T05:02:28.601315Z` — 00:02:28 Lima |
| Vigencia | Consumido aproximadamente 81,364 s después de emitir, dentro de 5 minutos |
| Marca comunicada | Observación del cliente unos **24,5626 ms** posterior al instante guardado; no se presenta como timestamp exacto de la fila |
| Atomicidad del resultado | Consumo, verificación de correo, uso de invitación y presentación de solicitud tienen el mismo instante de servidor |
| Identidad operativa | Sin AppUser del destinatario ni Driver; sin grupo/unidad/provisión |
| Limpieza | Organización INACTIVE, ADMIN inactivo, OTP retirado, capacidad expirada y sin accesos activos |
| Ledger | Una alta ADMIN propia; agregado ADMIN=1, sin otra alta para reanudar |
| Invitación anterior | Una invitación anterior revocada y sin consumir conservada; la nueva está consumida, sin forzar revocación incompatible |

La ventana actual contiene únicamente el envío de 00:01:07. El envío del primer intento, 21:15 del día anterior, ya estaba fuera de los 60 minutos al reanudar. Esa salida normal de la ventana no es un reinicio manual del presupuesto. La evidencia anterior conserva el instante y estado originales.

La utilidad devuelve **PASS_LOCAL_CORRESPONDENCE_AND_CLEANUP**. Su consulta final confirmó además el bloqueo asesor de limpieza libre. El reporte humano satisfactorio se combina con las comprobaciones del arnés ejecutado y la persistencia actual: no se declara entrega Gmail únicamente por una fila o por un número de pruebas.

### Evidencias preservadas y limpieza de la base

- [Consulta inicial de correspondencia](ci-l01-01-correspondencia-2026-10-17.log), SHA **F8D773D92A5457C14E5891CB4951226231E98385F24708DA1A510A79DAE397F2**.
- [Consulta final posterior a la publicación](ci-l01-01-consulta-final-2026-10-17.log), SHA **BF29E95475701A7D6012FE23C449DCF16231CBCDEC66C6249A497C2E4DC6DE0A**.
- [Siete unitarias del verificador](ci-l01-01-verificador-unitarias-2026-10-17.log), SHA **C4AFA6B2146FC982D8E35FECF7EAD85BA24D28CE3D65A07732FE58F5F9201BDA**. Identificadores, cronología, ventana exacta y limpieza inválida se rechazan con datos ficticios, sin DB/SMTP.
- [Intento fallido y continuación preparada del 16](otp-recuperacion-selectiva-2026-10-16.md), conservados como históricos. Sus inspecciones se copiaron, sin cambiar bytes, a [inicial histórica](otp-manual-historico/inspeccion-inicial-2026-10-16.log) y [final histórica](otp-manual-historico/inspeccion-final-2026-10-16.log).

**Se levanta la suspensión por este caso OTP.** Pueden reanudarse las pruebas autorizadas que limpian **solo `ruta_fija_test`**, después de esta preservación fuera de `backend/target`. No se ejecutó limpieza global en esta interacción. La limpieza futura puede retirar los fixtures de prueba; no elimina estas evidencias ni convierte su ausencia posterior en un fallo retroactivo del ensayo acreditado.

Antes de cualquier próximo paso humano de aceptación/recuperación se deben volver a suspender esas limpiezas hasta terminar y archivar el caso. No se repite el envío SMTP de CI01. El verificador se puede volver a usar mientras las filas existan:

```powershell
.\scripts\Test-RutaFijaL01OtpEvidence.ps1 `
  -CaseRunId '3e9c0bf5-237b-4370-a87f-cea684e0b1d8' `
  -RunId 'fa616262-500e-4490-9292-7ee1927d2257' `
  -ObservedConsumedAtUtc '2026-10-17T05:02:28.625877600Z'
```

## CI-L01-03 — APROBADO Y PUBLICADO; ACEPTACIÓN REAL PENDIENTE

El titular respondió expresamente: **«Apruebo ese contenido exacto para publicación local»**, a la pregunta que identificaba ADMIN1.0.0 y SHA **20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417**. No se vuelve a pedir esa aprobación ni se modifica el contenido aprobado.

Se crearon sin sobrescritura y con atributo de solo lectura:

- [Documento publicado inmutable](../../legal/publicado/terminos-admin-v1.0.0.md), mismos bytes/huella que el candidato.
- [Registro de aprobación](../../legal/publicado/terminos-admin-v1.0.0.aprobacion.json), SHA **EA0348497927CE671A2A9CE91144226C4D835C38C9B2BC75697C3218132774B1**. Registra la respuesta, fecha local y momento de registro sin inventar identidad de una cuenta aprobadora o retrofechar un evento.
- [Manifiesto de publicación](../../legal/publicado/manifest-admin-1.0.0.sha256), SHA **94987E48D73F0CC0355EF566941A9356D6A95BE27691492B15897408434F1DE5**.

El candidato y su ficha anteriores conservan bytes y huellas como fuentes de revisión; esta evidencia actualiza su estado posterior. La privacidad/identificación separada continúa como borrador incompleto, condición antes de nuevos datos reales; aprobar reglas de uso no aprueba ese aviso.

### Corrección técnica y registro de la versión

Se detectó que `AdminTermsDocument` aplicaba `strip()` al texto almacenado mientras calculaba la huella del argumento original. Un documento con salto final podía quedar incoherente. Se conserva ahora el contenido exacto y solo se rechaza nulo/blanco; los identificadores mantienen su normalización previa. No se cambió ni un byte del documento aprobado.

El publicador local `ApprovedAdminTermsPublication`/`scripts/Publish-RutaFijaAdminTermsTest.ps1` valida aprobación, versión, huella, longitud y bytes; solo admite la versión aprobada y destino `ruta_fija_test`/`rf_test`. Inserta únicamente el documento, nunca cuentas ni aceptaciones. Rechaza una colisión de versión con contenido diferente y conserva una publicación idéntica existente. No crea un endpoint de publicación ni aplica Flyway.

Se registró **ADMIN_TERMS 1.0.0**, documento **f590e228-5d2e-47e4-b144-b0df1198bd49**, `synthetic=false`, a **2026-10-17T05:16:43.071422Z** (00:16:43 Lima). Se contrastaron texto, SHA calculado por PostgreSQL y versión vigente en otra conexión de solo lectura. **Aceptaciones presentes=0; aceptaciones creadas por el comando=0**.

- [Registro real local](terminos-publicacion-registro-2026-10-17.log), SHA **05D8530FE1D2F9D1FBF602209481AC386DFBF3A58C577219BE525D952DAC36A3**.
- [Consulta posterior de solo lectura](terminos-publicacion-consulta-2026-10-17.log), SHA **3B7F0A56AD0CD73FA82B706303F034D9338A1E45CEF2127F183C7C74760E3098**.
- [Pruebas dirigidas](terminos-publicacion-pruebas-2026-10-17.log), SHA **5B9FFC6D14EE09F06B42701585ACDFB20E04D000AE26B5C24A702142F2345685**: **20 unitarias +1 IT correctas**, sin fallos/errores/omitidas. Incluyen las siete unitarias del verificador de CI01, no se suman como casos nuevos independientes. La IT usa un documento sintético, comprueba CRLF/Unicode/saltos finales y los rechazos UPDATE/DELETE por los triggers; todo se revierte, sin aceptación, migración o limpieza global.

No se repitió una regresión completa con limpieza de datos. Las evidencias válidas anteriores de gate, activación, concurrencia y clientes conectados se reutilizan con sus límites; este corte no afirma aceptación humana ni despliegue.

### Pasos que faltan para completar CI03

1. **Aprobación/publicación ya realizadas:** no volver a aprobar ni editar1.0.0. Cualquier cambio requiere otra versión y aprobación.
2. Preparar un **ADMIN legítimo elegible**, con su organización, revisión restringida de identidad y correo verificado según el flujo autorizado. No se ha acreditado aquí un ADMIN real listo para ese ensayo. Completar antes las condiciones de información/privacidad que correspondan a nuevos datos reales. No cambiar destinatarios SMTP o emitir correo adicional por esta guía.
3. Suspender limpiezas durante ese paso. Para un ADMIN existente, el CRM utiliza `/admin-terms`; para un alta pendiente utiliza `/admin-activation`, con contraseña propia y verificación previas, antes de completar habilitación. La cuenta actuará por sí misma.
4. Mostrar el documento completo1.0.0, comprobar huella vigente y casilla inicialmente desmarcada; el ADMIN decide y registra su aceptación expresa. Login, publicación o aprobación del titular no son aceptación del ADMIN.
5. Contrastar registro ligado a ADMIN, organización, documento, versión, SHA y hora del servidor, y guardar evidencia saneada sin tokens/PII. Solo entonces evaluar CI03 como verificado.

Comprobación de publicación sin escritura:

```powershell
.\scripts\Publish-RutaFijaAdminTermsTest.ps1
```

Si una limpieza autorizada de test elimina la fila, el documento/registro aprobados siguen fuera de la base; se puede registrar nuevamente esa misma versión local, sin crear aceptaciones:

```powershell
# Solo ruta_fija_test. Conserva una versión existente idéntica y rechaza cualquier contenido distinto:
.\scripts\Publish-RutaFijaAdminTermsTest.ps1 -Publish
```

## CI-L01-07 — PENDIENTE, KIT PÚBLICO PREPARADO

El titular confirmó de nuevo: **«siguen pendientes, por ahora solo tengo mi PC windows»**. Siguen sin acreditar medio de custodia independiente, clave/inscripción, copia comprobada, código separado y ensayo personal. No se genera ni custodia su clave por el agente.

Se preparó [kit ZIP público](../../../artifacts/ruta-fija-superadmin-titular-2026-10-17.zip), SHA **7739912D5BB0732DA59E583A27E501ABD111EF287718CEB1BDABB053E82AC2AF**. Su contenido se comprobó por lista explícita: cliente JAR, LEEME, plantilla pendiente y manifiesto; no env/configuración, cuentas, claves o códigos. El JAR mantiene SHA **91913FF7C11CABFAA01881FCBFB24BBF5ED347564C0140D73A757BC96D38E24A** y solo cinco clases CLI/manifest; no se ejecutaron `keygen`, `enroll` o `recover` reales. Preparar el kit no constituye custodia.

[Guía del kit](../../../artifacts/L-01-superadmin-titular-2026-10-17/LEEME.md) y [plantilla sin ejecutar](../../../artifacts/L-01-superadmin-titular-2026-10-17/ensayo-titular-plantilla.md). Secuencia pendiente: medios separados disponibles → preparación personal → `check-key` de principal/copia y reconexión → inscripción legítima previa → ensayo con copia, verificación del nuevo correo, contraseña nueva, revocación/consumo y código rotado. El [protocolo canónico](recuperacion-superadmin-protocolo.md) mantiene los comandos y controles. Otra carpeta en la PC servidora no cumple separación. No se añade un gasto ni se presume otro equipo.

## Alcance conservado

Ningún envío externo del agente, limpieza global, migración de desarrollo/piloto, despliegue, clave real o aceptación delegada en esta interacción. `final3` ZIP/manifiesto siguen con sus huellas originales. Solo CI01 queda **VERIFICADO** por este ensayo; **CI03 y CI07** mantienen L-01 EN_CURSO. No se solicita cambio de lote ni se inicia L-02.

Las huellas de este corte y sus fuentes/artefactos públicos se reúnen en `cierre-parcial-2026-10-17.sha256`, fuera de target. Ese manifiesto no contiene credenciales, OTP o claves.
