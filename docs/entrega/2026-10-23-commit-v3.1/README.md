# Commit de entrega local V3.1

La petición actual autoriza preparar Git localmente y sustituye la restricción histórica de trabajo sin Git para esta tarea. No autoriza publicar, hacer push, contactar GitHub, migrar bases o activar un piloto.

El título solicitado se conserva exactamente:

```text
release(core): cierre funcional candidato local de entrega v3.1 (L-00 a L-08 verificado)
```

[commit-body.txt](commit-body.txt) contiene el cuerpo exhaustivo, listo para `git commit -m`. [COMMIT_MESSAGE.txt](COMMIT_MESSAGE.txt) reúne título y cuerpo en UTF-8 para revisión. [Runtime y evaluación](../../runtime-y-evaluacion-solucion-1-v3.1.md) explica las capacidades, los flujos y las condiciones pendientes.

Orden exacta para Windows PowerShell desde la raíz del repositorio:

```powershell
git add .
git commit -m "release(core): cierre funcional candidato local de entrega v3.1 (L-00 a L-08 verificado)" -m (Get-Content -Raw -Encoding UTF8 "docs/entrega/2026-10-23-commit-v3.1/commit-body.txt")
```

Se conserva la configuración existente de fines de línea del proyecto; `.gitattributes` preserva expresamente los bytes de las actas, términos publicados, evidencias certificadas, respaldos de esta entrega y los dos launchers PowerShell contrastados por hash. El [script equivalente](comando-commit.ps1) exige la raíz del repositorio correcto y comprueba que staging termine correctamente antes de intentar el commit; no se ejecuta durante la generación de estos archivos.

Las rutas largas de respaldos requieren Git para Windows con `core.longpaths=true`. Durante la preparación se aplicó `git config --local core.longpaths true` únicamente a este repositorio; no se cambió la configuración global. El primer intento de staging rechazado y la comprobación de bytes del índice se conservan como evidencia de preparación.

## Correcciones respecto del borrador solicitado

- Angular actual es22.2.3, no22.1.4.
- El inventario es V1…V50 de runtime más una repetible exclusiva de pruebas; no se debe exigir esa repetible a desarrollo. Se corrigió la comprobación compartida entre los dos launchers.
- GPS corresponde a capturas con UI visible, no a la activación de un servicio Android persistente con notificación continua.
- La respuesta de asignación usa un identificador de evento en JSON. `X-Idempotent-Replay` llega en la respuesta del servidor.
- Aceptación autenticada de términos con hash no se describe como firma criptográfica personal. El ledger1/3/8 es el de altas históricas por rol.
- La cobertura local se respalda con nueve lotes y pruebas fechadas; no se afirma el100% de una rúbrica desconocida ni ausencia de deuda. Desarrollo permaneceV12, la release Android apunta a `.invalid` y PR-01…06 siguen pendientes.

## Protección y alcance

El staging incorpora fuentes backend/CRM/móvil, las50migraciones versionadas y la repetible de pruebas en sus carpetas, scripts, actas, contratos, protocolo y documentación/evidencias saneadas. Las plantillas de configuración se conservan.

Se excluyen entornos privados, claves Ed25519/RSA, keystores, almacenes DPAPI, archivos de caso `.private*`, runtime, logs, dumps, cachés, memoria de respaldo y binarios/ZIP de distribución. Las huellas y certificados públicos pueden versionarse. No se borran archivos privados del disco ni se reescriben actas o manifiestos históricos para preparar Git.

Las fuentes vivas del manual y de ambos launchers evolucionan después de la auditoría anterior. Sus respaldos exactos y el registro de sustitución de esta carpeta permiten identificar los bytes históricos cuando un manifiesto previo los referencia. Los binarios, logs y backups excluidos siguen en el archivo local; el repositorio no contiene por sí solo toda la evidencia física de aceptación.

Validaciones nuevas:12casos sintéticos de esquema y42comprobaciones de regresión de launchers. Los731PASS de aplicación y7pruebas Flutter conectadas omitidas corresponden a la auditoría previa; no se presentan como suites vueltas a ejecutar para este cambio de documentación/compuerta. No se inició API, Angular, PostgreSQL ni un túnel durante esta preparación.

La revisión de formato de Git detecta CRLF del launcher Windows preservado y líneas vacías finales preexistentes, incluidas migraciones cuyo checksum no debe cambiar. Se registran en [staging-format-observations.json](staging-format-observations.json); no se desactivaron reglas ni se reformatearon SQL certificados para ocultarlas. La comprobación de seguridad del staging y la coincidencia de bytes revisados se evalúan por separado en [staging-verification.json](staging-verification.json).
