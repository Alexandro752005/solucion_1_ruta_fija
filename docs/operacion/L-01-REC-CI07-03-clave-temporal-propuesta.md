# REC-CI07-03 — Sustitución explícita del material temporal del ensayo

Versión **0.1.0**, 2026-10-17, America/Lima. **PROPUESTA NO APROBADA, NO IMPLEMENTADA, NO EJECUTADA.** L-01 EN_CURSO. No es una operación del protocolo normal ni una autorización para una clave definitiva.

## Hecho nuevo y bloqueo

El operador aclara que solo identifica las contraseñas de acceso SUPER_ADMIN y ADMIN; la contraseña de cifrado del archivo temporal no está identificada. Esta aclaración sustituye su declaración previa de recordarla con certeza. Los mensajes genéricos de `check-key` no prueban corrupción ni una excepción criptográfica concreta.

El cliente existente no conserva esa contraseña ni ofrece restablecerla. El protocolo exige la firma con la privada correspondiente a la pública inscrita, además del código de emergencia y el OTP. Las contraseñas de acceso y el código de emergencia no proporcionan una operación de descifrado del archivo. No se probarán combinaciones ni se buscarán secretos en registros.

REC-CI07-02 v0.1.0 autorizó una única sustitución del código del fixture, conservando la pública original. Esa reparación ya ocurrió y se conserva. No autoriza sustituir la clave, repetir la reparación ni sobrescribir los archivos.

## Objeto exacto propuesto

Autorizar una preparación correctiva adicional del **mismo fixture sintético**, con un par Ed25519 temporal nuevo que el operador genera y custodia personalmente. Admitir esta sustitución auditada exclusivamente como preparación previa del siguiente segmento de ensayo local. Después deberá ejecutarse completa la recuperación normal con la copia nueva.

| Elemento | Límite fijo |
| --- | --- |
| Base / rol / esquema | `ruta_fija_test` / `rf_test` / V26 |
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| SUPER_ADMIN sintético | `088518c1-821c-4316-9f87-090f51ac44e0` |
| Organización / ADMIN | `1a759298-7831-4aaf-9299-c3920a9cc28a` / `92ebc07c-1319-4850-a734-f792cb299979` |
| Instalación / credencial | `f0cd888b-57df-4def-9d08-7882ffadebae` / singleton1 |
| Pública anterior, preservada como antecedente | SHA-256 `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c` |
| Reparación anterior | Auditoría `d791653a-6379-4f02-ac1d-1db020dde403`, contador1 |
| Altas históricas / cupos restantes | 1/2/0 y 0/2/20; no nuevas cuentas ni recuperación de cupo |

## Preparación personal y condiciones previas

1. Autorizar expresamente esta versión. Preparar y comprobar primero la modificación y sus pruebas con dobles/material ficticio; no leer privadas del operador ni cambiar el fixture durante esas pruebas.
2. Mantener marcador/protección del caso. Si hace falta cargar el arnés corregido, coordinar una reanudación personal explícita del mismo caso, conservando el marcador, historial y observaciones públicas. No liberar limpiezas ni reconstruir sesiones anteriores perdidas.
3. El operador usa los comandos actuales `keygen` y `check-key` para generar un nuevo par temporal en un destino diferente fuera del repositorio/aplicación, sin sobrescribir ni eliminar los archivos anteriores. Prepara su copia cifrada en la misma PC y comprueba personalmente ambos archivos con su contraseña de cifrado; ambas huellas públicas deben coincidir.
4. El operador custodia esa contraseña sin ponerla en argumentos, `.env`, chat o evidencia. No se presupone otro equipo, USB, nube o gestor. El agente no genera, descifra ni custodia las privadas.
5. Verificar base/rol/V26, RUN/IDs, marcador, lock propio del arnés, pública anterior, inscripción original, reparación REC-CI07-02 exactamente una, aceptación ADMIN e identidades existentes, ledger y ausencia de recuperación completada o reto vigente/prueba validada. Conservar el reto vencido.
6. Exigir autenticación y reautenticación humanas del SUPER_ADMIN sintético mediante el login normal y sus límites; registrar las sesiones observadas del nuevo segmento para comprobar después su revocación.
7. Incorporar exclusivamente al arnés test una operación de preparación con la nueva **pública**. Validar X.509 Ed25519 y exigir confirmación humana de su huella frente a principal/copia comprobados. No recibir ni archivar privada, contraseña de cifrado, ruta privada o código. Si hay discrepancia o cancelación, detener sin escribir.

## Única modificación autorizable del fixture

8. En una transacción y con bloqueo de la fila/revalidación de condiciones, sustituir únicamente `public_key_x509` y `updated_at` de esa credencial. Insertar auditoría específica `L01_SYNTHETIC_RECOVERY_KEY_FIXTURE_REPLACED`, motivo `TEMPORARY_KEY_ENCRYPTION_PASSWORD_UNAVAILABLE`, referencia de aprobación, RUN, segmento, pública anterior/nueva mediante huellas, fecha del servidor y contador1. Impedir una segunda sustitución satisfactoria.
9. Conservar usuario, instalación, correo, contraseña, versión de sesión, código de emergencia vigente y su hash, inscripción original, retos, auditorías, aceptación, ID-01, cuentas y ledger. No repetir REC-CI07-02 ni reemitir códigos. Si el operador tampoco dispone del sustituto vigente, detener: esta propuesta no autoriza resolver esa falta.
10. Mantener los archivos originales y toda evidencia como antecedentes del recorrido interrumpido. La inscripción HTTP original corresponde a la clave anterior y nunca se presentará como inscripción HTTP de la nueva.

## Efecto exacto sobre el ensayo y su evidencia

La nueva pública se configura mediante **mantenimiento del fixture autorizado**, no mediante una nueva llamada `/enrollment`: la API normal rechaza una segunda inscripción y seguirá haciéndolo. La aprobación de esta propuesta deberá admitir explícitamente esa preparación sintética como excepción para este ensayo local. Se conserva la evidencia de inscripción normal original y su límite; no se acredita una segunda inscripción normal ni inscripción legítima del titular.

El arnés deberá distinguir `originalEnrollmentResponseCount=1`, `syntheticFixtureCodeRepairCount=1` y `syntheticFixtureKeyReplacementCount=1`, con sus auditorías/huellas/segmentos propios. No se fabricará otra respuesta de inscripción ni se trasladarán verificaciones históricas al segmento nuevo. La guarda de este nuevo segmento fijará la nueva huella autorizada; no se relajará a aceptar cualquier pública.

11. El operador ejecutará `recover` del cliente vigente usando la copia nueva: reto vinculado, firma Ed25519, sustituto vigente, OTP simulado sin reenvío, contraseña nueva, código final mostrado una vez y login recuperado. No se omitirá ninguno de esos controles.
12. Comprobar conservación de la **nueva pública durante esa recuperación**, consumo del reto y código, rotación final, revocación de las sesiones realmente observadas del segmento y uso manual de la copia. No afirmar conservación de la pública original a través de la preparación correctiva.
13. Archivar referencias y resultados saneados. `PASS_LOCAL_TEMPORARY_COPY_ONLY` solo será admisible tras el recorrido humano completo y las comprobaciones anteriores, identificando explícitamente esta preparación excepcional. Una sustitución aislada no produce PASS.

## Pruebas exigidas antes de ejecutar la preparación

Pruebas dirigidas con datos ficticios: autenticación/reautenticación, autorización exacta, pública/huella inválida, cancelación, base/caso/lock incorrectos, retos incompatibles, modificación concurrente, repetición, atomicidad de actualización/auditoría y preservación de cuentas/cupos/aceptación/código. Probar la distinción de segmentos y evidencia sin atribuirle una inscripción HTTP nueva. No ejecutar suites que limpien la base mientras el caso humano permanezca protegido. No ampliar privilegios ni migrar desarrollo/piloto.

## Límites y alternativa

No cambia Ed25519, endpoints, DTO, validaciones o política de sustitución de claves del funcionamiento normal. No habilita usar una clave nueva como prueba de control previo del titular real. Es una excepción de **preparación de prueba**, con autenticación legítima todavía disponible en el fixture. Sin autorización no se implementa ni ejecuta esa sustitución.

Alternativa: conservar el caso pendiente y retomarlo únicamente si el operador identifica por sus propios medios la contraseña original; no realizar nuevos intentos por rutina. Dos copias cifradas con contraseña desconocida no permiten completar el ensayo.

CI03 gate previo sigue pendiente; no otro ADMIN. SC-03/SC-07 conservan su aprobación condicional y no se aplican hasta acreditar ambos ensayos. No se pide aprobar otra vez su contenido original: esta decisión cubre la nueva excepción de preparación descrita. L-01 sigue EN_CURSO, piloto bloqueado, L-02 sin iniciar; custodia independiente y titular real pendientes. Sin SMTP adicional, despliegue, cuentas reales, nuevas bases, Git/GitHub o regeneración de final3. Términos publicados y cupos intactos.

## Decisión solicitada

Recomendación: autorizar **REC-CI07-03 v0.1.0**, incluida la preparación de la nueva pública por mantenimiento del fixture en lugar de una segunda inscripción HTTP, con los límites anteriores. Después se implementará y probará antes de pedir acciones personales. Hasta esa autorización, conservar el caso sin más intentos.

Texto propuesto: **«Autorizo REC-CI07-03 v0.1.0 exclusivamente para el mismo fixture sintético. Admito la sustitución auditada de la pública como preparación excepcional del nuevo segmento, conservando la inscripción original solo como antecedente y exigiendo después la recuperación manual normal completa con la copia nueva. No autorizo otra reparación del código, nuevas cuentas ni cambios al protocolo normal».**
