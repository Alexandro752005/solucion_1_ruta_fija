# L-02 — Evidencia final V28

Fecha local **2026-10-19**. Alcance **FUNCIONAL_LOCAL_PILOTO_BLOQUEADO**. Este directorio congela la ejecución final; no sobrescribe incrementos, fallos históricos ni L-01. [Matriz](../matriz-final-funcional-local-2026-10-19.md), [acta](../../../actas/L-02-cierre-funcional-local-2026-10-19.md).

## Backend

Desde `backend`, ejecución final dirigida:

```powershell
cmd.exe /d /c "mvn.cmd -q -Dtest=QualificationIntegrationTest,L02TemporalConcurrencyTest,EmailOtpChallengeTest,InvitationServiceBoundaryTest,InvitationCodeHasherTest,L02CleanupProtectionTest test > target/l02-final-acceptance-v28.log 2>&1"
```

Exit **0** comprobado. Los seis XML adjuntos suman **53 tests, 0 fallos, 0 errores, 0 omitidos**: 44 HTTP/estado, 2 carreras PostgreSQL, 6 OTP/invitaciones y 1 guardia de históricos. `QualificationIntegrationTest` usa backend Spring/filtros reales y PostgreSQL `ruta_fija_test`/`rf_test`, rollback; no `NativePostgresIntegrationTest` ni truncado. Las carreras son dos conexiones independientes, con retirada técnica exacta. Los XML no contienen propiedades de credenciales.

Empaquetado posterior `mvn.cmd --offline -q -DskipTests package`: exit **0**, log vacío por modo silencioso y éxito; [resultado](empaquetado.json). Solo se corrigieron cinco acentos en rótulos de error después de la suite, compilados por este empaquetado. No se repitió el envío OTP ni recuperación/aceptación humana.

## Clientes

Desde `frontend`: `npm.cmd run typecheck`, `npm.cmd run test:ci -- --include=src/app/features/qualification/driver-qualification.page.spec.ts --include=src/app/features/incidents/incidents-system.page.spec.ts`, `npm.cmd run build`. Todos exit **0**, **20/20**. Logs `frontend__*` adjuntos. Pruebas de componentes con dobles; navegador real acreditado aparte.

Desde `mobile`: `flutter analyze`, `flutter test --no-pub`, `flutter build apk --debug --no-pub`. Todos exit **0**; análisis sin incidencias, suite configurada **86 PASS y 2 optativas omitidas**. Logs `mobile__*` adjuntos. Las pruebas offline históricas excluidas de la configuración no se cuentan como verificadas. El caso conectado Flutter L-02 se ejecutó por separado.

## Integración, persistencia y preservación

- [Resultado conectado](../conectado-44b3d67a-69ce-4169-89b4-62ebe8a40160/resultado.json): navegador CRM real y repositorios Flutter → HTTP/Spring/PostgreSQL, correo simulado, un solo CONDUCTOR nuevo. Las sesiones del cliente de prueba no equivalen a participantes reales.
- [Migración V28](backend__target__l02-migration-v28.json): 27→28, exactamente una migración esperada pendiente; no se ignoraron discrepancias ni repararon checksums. V27 está archivada en incremento-1.
- [Postflight después de la suite](post-suite-solo-lectura.json): V28, Flyway válido, ledger **1/3/1**, 2 aceptaciones, hashes de L-01 preservados. Consulta con configuración privada existente, sin exponer valores ni usar otra base.
- [Caso cerrado](../conectado-44b3d67a-69ce-4169-89b4-62ebe8a40160/verificacion-cierre-V28.json): deshabilitación, revocación, historial/ledger retenidos, marcador/privado retirados y lock libre. `TEST_CLEANUP_RELEASED=true` no autoriza limpieza global de históricos.
- Inspección de recursos técnicos final: sin restos. Su archivo conserva el aviso no sensible de Commons Logging anterior a la línea JSON; no debe interpretarse todo el archivo como JSON puro.
- [Artefactos locales](../../../../artifacts/L-02-2026-10-19/manifest.json), [fuentes congeladas por huella](../fuentes-cierre.sha256), [manifiesto del cierre](../manifest-cierre.sha256). No despliegue ni snapshot final3 nuevo.

Algunos logs nativos conservan UTF-16 con BOM de PowerShell; los XML y JSON saneados de cierre son UTF-8. Leer según BOM, sin reescribir evidencia histórica. No hay secretos, contraseñas, OTP, tokens, claves privadas ni documentos originales en este archivo de evidencia.

No se acredita dispositivo físico, títulos reales, nueva intervención humana, aceptación de ADMIN legítimo ni independencia de custodia. Piloto bloqueado; L-03 sin iniciar.
