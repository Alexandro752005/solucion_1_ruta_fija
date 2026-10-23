# REC-CI07-02 — Reparación sintética persistida y contrastada

## Resultado y correspondencia

El operador comunicó `L01_FIXTURE_REPAIR=PASS_PRECONDITION_ONLY`, contador1 y segmento `82bffcf1-6fab-41fd-a57b-3cbbbf18b123`. La inspección SELECT de **2026-10-17T16:11:48.111015900Z**, exclusivamente en `ruta_fija_test` / `rf_test` / V26, acredita la correspondencia con el mismo caso.

| Referencia | Valor comprobado |
| --- | --- |
| RUN_ID | `140c10a1-be0f-4d08-a6d4-c796e2ed107a` |
| Cuenta SUPER_ADMIN sintética | `088518c1-821c-4316-9f87-090f51ac44e0` |
| Instalación | `f0cd888b-57df-4def-9d08-7882ffadebae` |
| Auditoría única de reparación | `d791653a-6379-4f02-ac1d-1db020dde403` |
| Fecha/hora del servidor registrada en el estado del arnés | `2026-10-17T16:10:02.365128Z` |
| Propuesta aplicada | REC-CI07-02, SHA-256 `B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2` |
| Huella pública conservada | `5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c` |

La fila de auditoría, el segmento y los IDs coinciden con el estado público del arnés. El contador es1, la confirmación oculta antes de escribir está registrada como verdadera y la entrega original continúa expresamente sin acreditar. El lector JDBC de Timestamp representa milisegundos en su salida; la precisión adicional de la marca pública procede del resultado del arnés. No se presentan como dos eventos distintos.

## Evidencia archivada

- [Contraste de persistencia de solo lectura](REC-CI07-02-reparacion-verificada-2026-10-17/contraste-persistencia-solo-lectura.json), SHA-256 **33BF7FEF8AF9C7F1099205B7A9654A17F45A442DACC9AF5D7788CE4915F81FDE**.
- [Fuente del lector utilizado](REC-CI07-02-reparacion-verificada-2026-10-17/ManualL01EnrollmentReadOnly.java) y [manifiesto](REC-CI07-02-reparacion-verificada-2026-10-17/manifest.sha256).
- [Reanudación y protección anteriores](REC-CI07-02-reanudacion-2026-10-17.md); [implementación y pruebas previas](REC-CI07-02-implementacion-2026-10-17.md).

El lector usa transacción de solo lectura, aislamiento REPEATABLE_READ y rollback. Solo selecciona referencias, conteos, huella pública y campos explícitos de auditoría; no selecciona valores ni hashes de códigos de emergencia. Compilar este lector no ejecutó suites ni preparó otro fixture.

## Controles conservados al corte

- Historial1 SUPER_ADMIN / 2 ADMIN / 0 CONDUCTOR y cupos restantes0 / 2 / 20; dos vínculos de identidad.
- Una sola inscripción, misma cuenta, instalación y clave pública; versión de sesión0, todavía sin recuperación completada.
- Una aceptación ADMIN de términos1.0.0 y huella aprobada `20FFD9525CB19EC51387BF11859CF34100A5117B00DE8F428BFEB8B9A4DC6417`, conservadas.
- Un reto anterior vencido, cero fallos de prueba, sin prueba validada ni consumo. No se borró ni reinició.
- Marcador del mismo caso y lock de limpieza activos; `TEST_CLEANUP_RELEASED=false`.

La reparación fue ejecutada personalmente mediante el comando autorizado; no fue una recuperación completada ni acredita por sí sola el uso de la copia, el consumo del sustituto, la rotación posterior o la revocación de sesiones. Esas comprobaciones corresponden al recorrido normal pendiente.

## Continuación personal y límites

Seguir el apartado3 de la [guía vigente](../../operacion/L-01-REC-CI07-02-ejecucion.md), con el cliente existente y `$trialCopy`. Introducir el sustituto confirmado únicamente en la entrada oculta del protocolo normal; OTP simulado, sin reenvío. Después de recuperar y comprobar el login con la contraseña nueva, confirmar las acciones reales con `copia-ok`, `recuperacion-ok`, `verificar` y comunicar solo resultados y referencias públicas. No repetir reparación ni inscripción.

CI07 manual **PENDING**, CI03 **PENDING** por gate previo no acreditado. No hay otro ADMIN autorizado. SC-03/SC-07 continúan condicionales sin aplicar; L-01 **EN_CURSO**, piloto bloqueado y L-02 sin iniciar. Dos archivos en la misma PC no acreditan custodia independiente. CI-L01-01 permanece VERIFICADO sin repetir SMTP. No se ejecutaron envíos externos, limpiezas ni escrituras de base por el agente; términos publicados, final3 y protocolo Ed25519 sin cambios.
