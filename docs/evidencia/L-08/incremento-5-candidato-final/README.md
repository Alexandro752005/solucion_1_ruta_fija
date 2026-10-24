# L-08 incremento 5 — cierre del candidato local

2026-10-23 America/Lima. **GO PARA CANDIDATO LOCAL DE ENTREGA; NO_GO_PILOTO_REAL.** Contrato 0.9.0. [Autorización](autorizacion-alcance-local.json), [acta](../../../actas/L-08-cierre-funcional-global.md), [matriz](../matriz-L08.md) e [informe](../../../../INFORME_FINAL_SUSTENTACION_RUTA_FIJA_V3_1_L08.md).

La aprobación local se separa de las condiciones de campo del contrato 0.8.0. Los recursos, actos personales y ensayos físicos pendientes conservan ese estado; la documentación o simulación no los acredita.

| Superficie | Evidencia | Resultado |
|---|---|---|
| Higiene | [Preservación](hygiene/preservacion.json), [retiro selectivo](hygiene/postflight-salida.txt), [residuos](hygiene/limpieza-conservadora.json) | 1.288 entradas reconciliadas, cuatro documentos históricos en respaldo exacto; 260 residuos archivados; ledger 1/3/8 y dos aceptaciones |
| Java | [Original](hygiene/participacion-java-original.txt), [corregida](hygiene/participacion-java-corregida.txt), [inventario](hygiene/participacion-java-producto.json) | Original 40,50 % mezclaba pruebas Dart; producto 51,83 %. Sin umbral académico inventado |
| Móvil | [Resultado](mobile/resultado-mobile.json), [procedimiento](mobile/README.md), [QA independiente](release/qa-mobile.json) | 56 pruebas aprobadas, incluidas seis nuevas; analyze correcto. Falta de fix simulada, sin observación física |
| Continuidad | [Índice OPS](ops/README.md), [resumen definitivo](ops/ops-summary-final.json) | AES-256/GCM; dos tablas y 101 filas; RTO local 415,026 s; detección PG 2,117 s y conjunto HTTP/PG 8,413 s |
| Release | [Receta](release/README.md), [manifiesto](../../../../artifacts/L-08-entrega-final/manifest.json) | v5 firmada sin debug; API HTTPS `.invalid` sin servicio; reproducción idéntica byte a byte; no instalada |

## Comandos y secuencia

Higiene: `python scripts/audit_l08_local.py preserve`, `python scripts/audit_l08_local.py archive` y `scripts/medir-cumplimiento-java.ps1`. Las salidas, copia Java de solo lectura, medidor original e intentos iniciales se conservan en `hygiene/`.

Móvil: `flutter test --no-pub --reporter expanded test/features/location/l08_prolonged_no_fix_test.dart` (seis aprobadas), `flutter test --no-pub --reporter expanded test/features/location` (56 aprobadas, incluidas las seis) y `flutter analyze --no-pub` (salida 0). Logs y fuentes copiadas en `mobile/`.

Continuidad: `scripts/Test-RutaFijaL08Continuity.ps1 -Execute`. Los modos Plan y los casos fallidos y corregidos quedan en `ops/`. Se conserva el reloj desde el primer inicio, incluyendo la intervención. HTTP sintético y PostgreSQL 16 real en un clúster nuevo 55441; sin escrituras en 5432. El resumen definitivo distingue la primera detección, solo de PostgreSQL, de la detección corregida tras retirar el proceso hijo HTTP.

Release: `scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute` y `-VerifyRebuild`. Metadatos y salida de `apksigner verify --verbose --print-certs` archivados. Se reutiliza el JAR verificado y se contrastan clases y recursos; no se ejecuta una nueva migración ni una regresión integrada completa.

## Incidencias y límites

El primer postflight suponía V51 y se detuvo sin escribir; se corrigió a V50 con 51 migraciones. La primera preservación detectó cuatro documentos mutables de coordinación de L-07 distintos; sus bytes originales se reconciliaron con respaldos exactos, sin alterar manifiestos. Se corrigió la métrica Java para comparar código de producción de las tres tecnologías. Los fallos de lanzamiento, memoria y permisos se conservan en sus áreas.

Las condiciones de campo siguen pendientes: falta física de señal GPS, independencia y custodia del respaldo/clave, calendario y RPO operativos, recuperación representativa, monitor y alertas con guardia 24/7, identidad, privacidad, ADMIN, titular e infraestructura reales. Ver [protocolo](../../../legal/protocolo-transicion-piloto-real.md). La firma del acta es técnica y automatizada, sin atribución humana. La release candidata no actualiza directamente la debug v4 instalada.

El manifiesto nuevo `manifest-cierre.sha256` archiva este incremento, fuentes finales, documentos coordinados, binarios y residuos conservados. Su comprobante es `verificacion-cierre.json`. No se regeneran manifiestos históricos. Claves, contraseñas y datos privados quedan excluidos.
