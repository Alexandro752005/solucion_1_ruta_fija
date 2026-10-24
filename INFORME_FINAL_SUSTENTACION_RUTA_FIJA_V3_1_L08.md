# Informe final de sustentación académica y técnica — Ruta Fija V3.1

2026-10-23, America/Lima. Lotes L-00 a L-08: **VERIFICADAS en su alcance funcional local**. L-08: **FUNCIONAL_LOCAL_APROBADA_CANDIDATO_FINAL**. Decisión: **GO PARA CANDIDATO LOCAL DE ENTREGA; NO_GO_PILOTO_REAL.**

## Objetivo y arquitectura

Ruta Fija coordina acceso, incorporación, organizaciones, unidades, programación y asignación, inicio y cierre del servicio, ubicación autorizada, incidencias, comunicados, reportes y auditoría. Conserva un monolito modular con API compartida y controles de rol, organización y objeto en el servidor. Los roles activos son SUPER_ADMIN, ADMIN y CONDUCTOR.

Se mantiene Java 21 / Spring Boot 3.5.16, Angular 22, Flutter 3.47.5 y PostgreSQL 16. La base está en Flyway V50, con 51 migraciones exitosas contando V0 a V50; no existe una V51 en este corte. El trabajo tiene costo S/ 0 y trazabilidad local mediante SHA-256, sin Git/GitHub ni publicación.

La ubicación se captura durante un servicio iniciado, con consentimiento, permiso y la app en primer plano. No hay operación ni cola offline: una acción se confirma tras la respuesta del servidor y una respuesta incierta se resuelve consultando el estado existente. Si faltan capturas durante más de 120 segundos, el servicio continúa y aparece un aviso de señal degradada en el siguiente ciclo de 30 segundos.

## Resultados y comandos comprobables

| Verificación | Comando/entrada ejecutada | Resultado y procedencia |
|---|---|---|
| Puertos y procesos | `Get-NetTCPConnection -State Listen`; `Get-Process` | Inicial: únicamente 5432 escuchaba entre los siete puertos solicitados. Sin detener procesos ajenos |
| Inmutables | `python scripts/audit_l08_local.py preserve` | 1.288 entradas reconciliadas; cuatro documentos mutables históricos en respaldo exacto; final3 y términos íntegros |
| Limpieza conservadora | `python scripts/audit_l08_local.py archive` | 260 archivos, 10.583.186 bytes archivados con mapa y hashes; sin borrado global |
| Base y locks | Java 21 `L08PhysicalFinalPostflight`, copia con salida al nuevo incremento | Solo SELECT: ledger 1/3/8, dos aceptaciones, 85 puntos históricos, lock libre y caso retirado |
| Participación Java | `scripts/medir-cumplimiento-java.ps1` | Producto: Java 25.110, TypeScript 8.529, Dart 14.811 líneas; Java 51,83 %. Resultado original 40,50 % preservado |
| GPS simulado | `flutter test --no-pub --reporter expanded test/features/location` | 56 pruebas aprobadas, incluidas seis nuevas; controlador y UI reales con proveedor y reloj sintéticos |
| Análisis móvil | `flutter analyze --no-pub` | Sin incidencias, salida 0 |
| Continuidad | `scripts/Test-RutaFijaL08Continuity.ps1 -Execute` | PostgreSQL 16 nuevo en 55441; dos tablas y 101 filas restauradas; archivo alterado y clave errónea rechazados; RTO SQL local 415,026 s |
| Sondas | `scripts/Watch-RutaFijaL08Health.ps1 -Action Probe -Execute ...` | HTTP sintético y PostgreSQL real aislado: caída PG detectada en 2,117 s; conjunto HTTP/PG en 8,413 s. Cota de diseño: 105 s bajo precondiciones |
| Firma y empaquetado | `scripts/Build-RutaFijaL08ReleaseCandidate.ps1 -Execute` y `-VerifyRebuild` | Release v5 firmada, sin debug; reproducción con certificado estable y APK idéntica byte a byte; sin instalación |
| Firma del acta | `scripts/Sign-RutaFijaL08Closure.ps1 -Execute` y verificación pública independiente | SHA256withRSA con clave candidata; verificación pública y rechazo de cambio de un byte exigidos antes del sellado |
| Sellado | `python scripts/seal_l08_candidate.py seal` y `verify` | Manifiesto nuevo, verificación de archivos y comprobante final; no modifica manifiestos históricos |

Los comandos completos, fallos y correcciones están en el [incremento 5](docs/evidencia/L-08/incremento-5-candidato-final/README.md). Las seis pruebas nuevas están incluidas en las 56, no se suman otra vez. Las suites completas de backend y CRM, la carga y la accesibilidad física usan evidencia histórica preservada; no se presentan como nuevas ejecuciones.

El primer build release se detuvo por falta de memoria Metaspace durante lint. El segundo y su reproducción pasaron tras aumentar la memoria únicamente para ese proceso, sin omitir lint ni cambiar dependencias. En continuidad se conservaron los fallos de lanzamiento y el proceso hijo detectado al revisar la salida; la prueba corregida y su postflight quedaron anexados. El reloj de restauración incluye los fallos e intervención desde el primer inicio.

## Evidencia histórica integrada con límites

- **CAP25:** 25 actores en cuatro organizaciones, 100 creaciones y 100 aceptaciones; p95 hasta persistencia durable de 343,0684 y 461,8702 ms, respectivamente. Una sola PC y loopback.
- **REST03:** 105 tablas, esquema, permisos y operación API comprobados; intervalo local máximo de 318,731 s. No demuestra pérdida total del equipo ni medio independiente.
- **Escritorio:** 12 combinaciones, 66 páginas y 804 comprobaciones, con zoom nativo al 100, 200 y 300 %.
- **FIS02:** POCO con Android 13 y APK debug v4; login, GPS, cierre y logout reales. Batería de 30 a 28 % en 959 s sin alimentación. TalkBack y escala al 150 % confirmados por el usuario, contraste corregido, 85 puntos históricos conservados y ausencia de envíos tras cierre. La pérdida física de señal GPS sigue sin observarse.

Cada corte conserva sus manifiestos originales. Las pruebas físicas de v4 no se atribuyen a la release v5.

## Entregables y reproducción

[Acta](docs/actas/L-08-cierre-funcional-global.md), [matriz](docs/evidencia/L-08/matriz-L08.md), [contrato 0.9.0](docs/contratos/L-08.md), [manifiesto de binarios](artifacts/L-08-entrega-final/manifest.json), [respaldo](docs/operacion/L-08-respaldo-independiente.md), [monitoreo](docs/operacion/L-08-monitoreo-operativo.md) y [transición al piloto](docs/legal/protocolo-transicion-piloto-real.md).

- APK release candidata SHA-256: `a4685115e712cfe0c5e24ad84efc4abaf1f4f679a6f95fd1ca0b0354d7b1d776`.
- JAR V50 SHA-256: `eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f`.
- Certificado Android SHA-256: `181da8d8cf0c18d56a424d0ed22fb9424a2bdf904c8e0dff7395a512c658bb45`.

La APK incorpora `https://api.rutafija.invalid/api/v1` como destino reservado **sin servicio real**. Para uso conectado hay que configurar y probar la API HTTPS autorizada y resolver la custodia y distribución definitivas. No se instala sobre v4 debug porque las firmas difieren. El POCO conserva una sola app instalada, v4, con su API privada de laboratorio.

## Brechas de campo expresamente conservadas

La aprobación local conserva las condiciones originales de campo: pérdida física de señal GPS; medio y clave independientes recuperables tras perder el sistema operativo; respaldo horario, verificación diaria y RPO sostenidos; monitor independiente, alertas entregadas y guardia 24/7; recuperación total hasta clientes; dominio, certificados y custodia definitiva; privacidad, ADMIN legítimo, ceremonia USB del titular, participantes, unidades y títulos reales. Estos requisitos están en el protocolo vinculante y en la columna de campo de la matriz. No se simulan aceptaciones personales ni se certifica cumplimiento legal integral.

La participación Java es **51,83 %** del código de producto: 406 archivos y 25.110 líneas no vacías. La métrica compara Java principal, TypeScript sin pruebas y Flutter `lib`, excluyendo pruebas y archivos generados. Demuestra predominio por esta medida; no certifica un umbral académico no proporcionado. No se añadió código artificial para elevarla.

## Integridad y responsabilidad

Final3 SHA-256: `3806510606a1565f23b8c526a13cc1049bc79141babdb8e1316f042488b3d11b`. Términos ADMIN 1.0.0 SHA-256: `20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417`.

No se ejecutaron TRUNCATE, altas persistentes, migraciones ni escrituras en 5432. El seguimiento, backlog y memorias del proyecto conservan sus antecedentes y reflejan GO local / NO_GO piloto. El acta recibe una firma técnica automatizada mediante la clave candidata, sin atribuir una firma personal o jurídica al titular.
