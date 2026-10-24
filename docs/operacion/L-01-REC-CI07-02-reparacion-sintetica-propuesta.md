# REC-CI07-02 — Propuesta de reparación selectiva del fixture de recuperación

Versión **0.1.0**, 2026-10-17, America/Lima. **PROPUESTA: NO APROBADA, NO IMPLEMENTADA, NO EJECUTADA.** L-01 EN_CURSO. Su autorización se solicita por separado de SC-03/SC-07 y de cualquier OK VERDE de otro lote.

## Motivo y límite de autorización actual

La inscripción del SUPER_ADMIN sintético quedó persistida, pero el operador informa que el código de emergencia no apareció en su terminal. La entrega original sigue sin acreditar. El JAR y Java21.0.10 coinciden con los previstos; una reproducción técnica con datos ficticios muestra ambas rutas de salida, sin demostrar la visibilidad de aquella ejecución. No hay causa exacta probada.

El protocolo vigente exige código y firma; conserva solo hash y bloquea otra inscripción sobre la credencial existente. No permite recuperar aquel valor ni emitir otro mediante una sesión ordinaria. La instrucción del operador prohíbe rotar credenciales por inferencia y limita los casos a la guía. **La autorización actual no cubre esta reparación.** No se ejecutará como un intento normal de recuperación.

## Objeto exacto propuesto

Extender el mismo arnés de pruebas con una reparación previa de su fixture, explícita y de una sola ejecución, para poder retomar después el protocolo normal. No se propone cambiar Ed25519, rutas/DTO del protocolo ni seguridad del funcionamiento normal.

| Elemento fijo | Valor |
|---|---|
| Base / rol / esquema | `ruta_fija_test` / `rf_test` / V26 |
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| SUPER_ADMIN sintético | `088518c1-821c-4316-9f87-090f51ac44e0` |
| Organización del caso | `1a759298-7831-4aaf-9299-c3920a9cc28a` |
| Credencial / instalación | Singleton existente, `f0cd888b-57df-4def-9d08-7882ffadebae` |
| Huella pública conservada | `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c` |

### Condiciones previas

1. Comprobar en la terminal B del operador la salida del adaptador del JAR existente mediante marcadores completamente ficticios, sin API/base/correo, contraseñas o claves. La reproducción técnica local ya satisfecha no sustituye esta comprobación de B.
2. Autorizar expresamente **REC-CI07-02 v0.1.0**. El resultado positivo del diagnóstico por sí solo no autoriza reparar.
3. Reanudar únicamente el servidor del mismo caso mediante el parámetro existente `-Serve -ResumeRunId`, con guardas reforzadas si hacen falta para preservar sus observaciones. Se reutiliza la clave de firma del runtime mediante el arnés; no se muestra ni se lee por herramientas del agente. No se vuelve a preparar SUPER_ADMIN, ADMIN, organización o clave del titular.
4. Verificar base/rol/V26, marcador exacto, lock adquirido por el arnés, identidades sintéticas, pertenencia del caso, ID-01 ya revisado, credencial/instalación/huella invariables y ausencia de recuperaciones completadas. Conservar los retos vencidos; si hay una prueba validada o un reto incompatible vigente, detener.
5. El operador autentica y reautentica al SUPER_ADMIN sintético con su contraseña actual mediante entradas protegidas. Se conservan los límites de autenticación y se captura una sesión de referencia para comprobar su revocación en la recuperación posterior.

### Única escritura correctiva autorizable

6. Incorporar al mismo arnés un comando de reparación del fixture, disponible exclusivamente bajo las guardas anteriores y limitado a una ejecución satisfactoria de este caso. Ningún endpoint o comando de esta reparación se incorpora al funcionamiento normal.
7. Generar dentro del proceso un código de reemplazo aleatorio del formato vigente. Mostrarlo una sola vez en consola local. **Antes de escribirlo en la base**, exigir que el operador lo repita en una entrada oculta; si no coincide o cancela, no sustituir la credencial y detener. El agente no introduce ni captura ese valor.
8. Tras esa confirmación, bloquear de nuevo la fila, revalidar las precondiciones y sustituir únicamente el hash del código de emergencia usando el codificador vigente, en una transacción auditada. Mantener usuario, contraseña, correo, pública, instalación, aceptación ADMIN, identidad y cupos. El código anterior queda invalidado por esta reparación explícita.
9. Registrar motivo `EMERGENCY_CODE_DELIVERY_NOT_ACCREDITED`, fecha del servidor, IDs, huella pública y `syntheticFixtureCodeRepairCount=1`. No archivar códigos, hashes secretos, tokens o rutas privadas. Conservar el evento de inscripción original y el incidente de entrega, sin presentarlos como corregidos retrospectivamente. Impedir una segunda sustitución por rutina o repetición automática.

### Recorrido y evidencia posteriores

10. Reconstituir y distinguir la referencia pública de inscripción y las nuevas observaciones de sesión previas a la recuperación. Los secretos y observaciones volátiles del proceso anterior no se reconstruyen como evidencia histórica; la evidencia debe identificar el segmento reanudado y las sesiones efectivamente comprobadas.
11. El operador ejecuta el cliente vigente con su copia temporal y el protocolo normal: reto nuevo, firma Ed25519, código del fixture reparado, OTP **simulado** sin reenvío, contraseña nueva, rotación final y login. Se comprueban pública conservada, consumo, rechazo del código previo y revocación de las sesiones observadas antes de esta recuperación.
12. Registrar `PASS_LOCAL_TEMPORARY_COPY_ONLY` solo si el recorrido y archivo exigidos pasan. La reparación no produce ese resultado, no acredita entrega original, titular real o custodia independiente. Conservar todas las evidencias anteriores y distinguir preparación correctiva de comprobaciones normales.

## Conservación de los demás límites

- Ledger histórico1/2/0 y cupos0/2/20: preservados. Se mantienen las cuentas, roles y organizaciones existentes.
- ADMIN y aceptación publicados: preservados. El gate previo de CI03 sigue pendiente; REC-CI03-01 sigue sin autorización.
- Términos ADMIN1.0.0, su huella, ID-01, aislamiento, OTP y permisos normales: sin reducción ni equivalencias nuevas.
- Entorno solo sintético/test, correo simulado; sin Gmail adicional, participantes reales, material definitivo, desarrollo/piloto, despliegue, Git/GitHub o regeneración de final3.
- Marcador protege las limpiezas mientras el proceso está detenido; al reanudar se añade su lock. Archivo antes de cualquier salida permitida; no forzar liberación ante pendientes.
- SC-L01-LOCAL-01 conserva su aprobación condicional y exige ambos PASS contrastados. Esta propuesta no aplica la adenda, no cierra L-01 y no inicia L-02.

## Decisión solicitada

**Recomendación:** autorizar esta reparación exclusiva del fixture, condicionada al diagnóstico positivo de salida en B y a las guardas/confirmación humana descritas. Permitiría continuar con el mismo caso sin atribuir una entrega no acreditada al pasado.

Alternativa: conservar el caso pendiente sin sustituir el código. La recuperación no podrá completarse con el factor actualmente no disponible. No se ofrece omitirlo ni modificar el protocolo por equivalencia.

Texto de autorización propuesto: **«Autorizo REC-CI07-02 v0.1.0 exclusivamente para el fixture sintético indicado, condicionado al diagnóstico positivo en B y a sus guardas y confirmación humana previa a la escritura. Mantengo todos los demás límites y el gate CI03 pendiente».**
