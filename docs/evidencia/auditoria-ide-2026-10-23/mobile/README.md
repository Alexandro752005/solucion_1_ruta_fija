# Auditoría IDE: Flutter y Android

2026-10-23, America/Lima. Trabajo local sin Git/GitHub, PostgreSQL, dispositivo ni compilación Android. Las pruebas conectadas opt-in L-01 a L-07 se desactivaron explícitamente solo en el proceso de pruebas.

## Origen de los diagnósticos

`flutter analyze --no-pub` desde la raíz reprodujo **1.716 diagnósticos: 1.633 errores y 83 avisos**. La clasificación completa encontró **cero diagnósticos en `mobile/` vivo**:

| Superficie archivada o generada | Diagnósticos |
|---|---:|
| `backend/target` | 360 |
| `docs/evidencia` | 649 |
| `memory/backup` | 707 |

Las copias están fuera del paquete Flutter activo: carecen de su configuración de paquetes o de las demás fuentes de su versión histórica. El inventario conserva rutas y hashes de 34 copias Dart archivadas/generadas; 29 archivos produjeron diagnósticos. También existe un `pubspec.yaml` histórico dentro de evidencia. No se corrigieron ni eliminaron esas copias.

La extensión instalada Dart-Code 3.144.0 define `dart.analysisExcludedFolders` como rutas relativas al workspace o absolutas, también aplicables al descubrimiento de proyectos. Se comprobó en su `package.json:1735` y en `out/dist/extension.js:20834`. Las exclusiones de búsquedas y watchers ya existentes no configuraban esa opción.

## Cambios aplicados

Con autorización del coordinador se creó `analysis_options.yaml` en la raíz, excluyendo exclusivamente `backend/target/**`, `docs/evidencia/**`, `memory/backup/**`, `backups/**`, `artifacts/**`, `.runtime/**` y `.worktrees/**`. El coordinador añadió las mismas siete rutas, sin glob, a `dart.analysisExcludedFolders` de VS Code. Se conservan las reglas de `mobile/analysis_options.yaml`; no se suprimió ninguna regla de diagnóstico del código vivo ni se excluyeron `mobile/lib`, `mobile/test` o el proveedor local parcheado.

La primera ejecución completa de pruebas detectó **un test obsoleto**, con 232 aprobadas, siete omitidas y un fallo. El caso de `mobile_driver_repository_test.dart` esperaba un cuerpo anterior a las pruebas de proveedor habilitado y primer plano. El cliente ya enviaba `locationServiceEnabled: true` y `foreground: true`; `MobileDriverService.java:174-175` exige ambos.

Se respaldó el test byte a byte antes de modificarlo. Solo se añadieron esos dos campos al mapa esperado, manteniendo la comparación exacta, los endpoints y las comprobaciones de identidad. El código de producción no cambió. [Respaldo y supersesión](source-supersession.json) documenta los 15 inventarios históricos que conservan su huella anterior; no se reescribió ninguno. La extensión `.dart.before` del respaldo conserva sus bytes sin añadir otra unidad Dart separada del paquete.

## Comandos y resultados

| Directorio | Comando | Resultado | Salida |
|---|---|---|---|
| Raíz, antes | `flutter analyze --no-pub` | Exit 1; 1.716 diagnósticos archivados/generados | [Log](flutter-analyze-workspace-root-before.log), [clasificación](workspace-root-diagnostics.json) |
| `mobile/`, antes | `flutter analyze --no-pub` | Exit 0; sin incidencias | [Log](flutter-analyze-mobile.log) |
| `mobile/`, antes | `flutter test --no-pub --reporter expanded` | Exit 1; 232 aprobadas, siete omitidas, un fallo | [Log](flutter-test-complete-before.log) |
| Raíz, después | `flutter analyze --no-pub` | Exit 0; sin incidencias | [Log](flutter-analyze-workspace-root-after.log) |
| `mobile/`, después | `flutter analyze --no-pub` | Exit 0; sin incidencias | [Log](flutter-analyze-mobile-after.log) |
| `mobile/`, después | `flutter test --no-pub --reporter expanded test/features/profile/mobile_driver_repository_test.dart` | Exit 0; tres aprobadas | [Log](flutter-test-driver-repository-after.log) |
| `mobile/`, después | `flutter test --no-pub --reporter expanded` | Exit 0; **233 aprobadas, siete omitidas, cero fallos** | [Log](flutter-test-complete-after.log) |

En ambas ejecuciones completas se establecieron `RF_L01_CONNECTED_CLIENTS` a `RF_L07_CONNECTED_CLIENTS` en `false` para ese proceso. Las siete pruebas omitidas no cuentan como aprobadas. Algunas pruebas unitarias usan un servidor HTTP de loopback propio; no se invocó un backend del proyecto ni PostgreSQL.

## Android y límites

Se revisaron las opciones Gradle, configuración de firma y manifiesto Android sin modificarlos. La configuración mantiene aislamiento de prueba opt-in, firma candidata explícita, permisos de ubicación de primer plano y bloqueo de release sin sus precondiciones. No se realizó otro build Gradle ni una nueva instalación: las fuentes Android y de producción no cambiaron. La evidencia física y los binarios firmados de L-08 conservan su alcance anterior.

El análisis CLI de raíz y paquete quedó limpio. La actualización final de los diagnósticos visibles del IDE corresponde a la comprobación del coordinador; el resultado CLI no sustituye una lectura de ese panel. Las limitaciones de API reservada del candidato, piloto real y condiciones físicas pendientes de L-08 no cambian con esta auditoría.

La comprobación del archivo congelado se ejecutó con lectura de cada hash, sin llamar a un verificador que pudiera reescribir la evidencia anterior. Su resultado queda en [frozen-l08-readonly-check.json](frozen-l08-readonly-check.json); el resumen de esta auditoría, comandos, hashes y límites está en [resultado-mobile-ide.json](resultado-mobile-ide.json).
