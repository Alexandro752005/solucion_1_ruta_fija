# REC-CI07-02 — Reanudación y protección comprobadas

## Corte y alcance

Corte de solo lectura: **2026-10-17T16:04:33.653016700Z**. El operador confirmó el mismo RUN_ID y que carga el login. Reanudó personalmente el servidor mediante el parámetro existente `-Serve -ResumeRunId`; el agente no inició el servidor ni introdujo secretos.

- RUN_ID: `140c10a1-be0f-4d08-a6d4-c796e2ed107a`.
- Entorno comprobado: `ruta_fija_test`, rol `rf_test`, esquema `V26`.
- Nuevo segmento de observación: `82bffcf1-6fab-41fd-a57b-3cbbbf18b123`.
- Listeners `127.0.0.1:4311` y `127.0.0.1:8091`, ambos del proceso `18632`.
- Marcador del mismo caso y advisory lock de limpieza presentes; la guarda estándar rechaza limpiezas. `TEST_CLEANUP_RELEASED=false`.

## Evidencia y comprobaciones

[Inspección saneada](REC-CI07-02-reanudacion-2026-10-17/proteccion-y-caso-solo-lectura.json), SHA-256 **B4F61E07DA643891DA13BF5A14270F9394441458D0765F54B6E421DEEE453F5B**. [Manifiesto](REC-CI07-02-reanudacion-2026-10-17/manifest.sha256).

| Comprobación | Resultado al corte |
| --- | --- |
| Historial de creaciones SUPER_ADMIN / ADMIN / CONDUCTOR | 1 / 2 / 0; cupos restantes 0 / 2 / 20 |
| Caso, cuentas, organización e identidades revisadas | Coinciden con el fixture autorizado |
| Inscripción de recuperación | Una respuesta observada; credencial y huella pública coinciden |
| Términos publicados y aceptación ADMIN existente | Conservados; documento 1.0.0 con huella aprobada |
| Recuperación completada, reto vigente o reparación previa | Ninguno |
| Autorización REC-CI07-02 y diagnóstico humano de salida | Comprobados por las guardas y huellas inmutables |
| Estado de observación | Sin marca de observación incompleta; nuevo segmento conserva la clave pública y establece nuevas referencias de sesión |
| Resultado ADMIN / recuperación manual | PENDING / PENDING |

La inspección usa el modo existente `-InspectRunId`, con transacción de solo lectura y rollback. El campo `harnessOwnsCleanupLock=false` dentro de las guardas del inspector es esperado: esa instancia desconectada del servidor recibe PID de sesión `0`. **No indica ausencia del lock**: `cleanupLockHeld=true` y `standardCleanupRefusedByMarker=true` están comprobados. El comando interactivo exige además que su propia sesión del servidor sea la titular del lock antes de reparar.

No hubo escrituras en la base, peticiones HTTP ni envíos externos durante esta inspección; tampoco se leyeron archivos de claves privadas ni valores o hashes de códigos de emergencia. No se ejecutaron nuevas suites, reparaciones ni recuperaciones por el agente. La existencia del archivo privado de firma del runtime se comprobó únicamente como metadato mediante el inspector existente.

## Siguiente intervención personal

Seguir el apartado 2 de la [guía autorizada](../../operacion/L-01-REC-CI07-02-ejecucion.md): en A, en `Ensayo L01>`, ejecutar una vez `reparar-codigo`; completar autenticación y reautenticación ocultas, conservar localmente el sustituto mostrado una vez y repetirlo oculto antes de guardar su hash. Comunicar solo `L01_FIXTURE_REPAIR`, `SYNTHETIC_FIXTURE_CODE_REPAIR_COUNT` y `RECOVERY_SEGMENT_ID`. Ante fallo, detener sin reintentar. La recuperación normal se hará después de contrastar esa reparación; aún no está acreditada.

## Límites y estado

Este corte no acredita entrega del código original ni reparación efectiva, recuperación, uso humano de la copia o custodia independiente. CI03 conserva la aceptación sintética y el gate previo pendiente; no se autorizó otro ADMIN. SC-03/SC-07 siguen aprobados condicionalmente sin aplicar. L-01 **EN_CURSO**, piloto bloqueado, L-02 sin iniciar. CI-L01-01 permanece VERIFICADO sin repetir SMTP. No se modifica final3, el cliente de recuperación ni los términos publicados.
