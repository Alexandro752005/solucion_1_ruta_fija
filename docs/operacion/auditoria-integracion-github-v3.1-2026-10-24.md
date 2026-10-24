# Auditoría e integración de la candidata v3.1

Fecha: 24/10/2026, America/Lima. Repositorio: `Alexandro752005/solucion_1_ruta_fija`. Base: `main`. Candidata: `release/v3.1-candidato-entrega`.

## Corte y auditoría

El corte candidato previo es `82c4d853d1f1de2937633a0a2ed1a2abfd976925`; el corte de `main` consultado es `3617354`. El árbol local estaba limpio y la divergencia era de seis commits candidatos y cinco commits de mantenimiento en `main`.

Se inspeccionaron **4 429 archivos versionados**, 153 561 545 bytes y 4 217 blobs distintos del corte candidato. Se contrastó el contenido con las ocho credenciales/secretos locales distintos detectables en la configuración privada y con patrones de claves privadas y tokens. **No se detectaron coincidencias**. Este resultado describe el alcance del escaneo; no es una garantía universal sobre cualquier secreto desconocido o todo el historial externo al corte revisado.

No se encontraron `.env` privados, directorios `.local`, runtime, logs temporales, `node_modules`, binarios de compilación, APK, dumps ni archivos de sistema que requiriesen `git rm --cached`. Se conserva `.env.example`, que contiene marcadores de sustitución. La coincidencia `docs/evidencia/L-01/cierre-funcional-local-2026-10-17/fuentes/backend/target/l01-closure-readonly/ManualL01LocalClosureReadOnly.java` es una **fuente Java de evidencia archivada**, no una salida compilada ni una caché activa. Se preserva junto con sus manifiestos.

El mayor archivo versionado es un informe XML de aceptación de aproximadamente 7,4 MB; los informes y fuentes de evidencia explican gran parte del volumen del PR. No se eliminan evidencias selladas para reducir artificialmente el conteo de archivos.

Se refuerza `.gitignore` para `*.tmp`, `*.rfdb` y carpetas privadas o generadas a cualquier profundidad: `.local`, `node_modules`, `target`, `.dart_tool`, `build`, `.gradle` y `.pub-cache`. Las exclusiones previas de secretos, respaldos y distribuciones se mantienen.

## Resolución de la divergencia

```powershell
git fetch origin
git merge origin/main -X ours --no-commit --no-ff -m "merge: resolver divergencia con main priorizando la entrega candidata v3.1"
```

La integración automática terminó con código **0**, sin rutas sin resolver. Se detuvo antes del commit para revisar el resultado. Se conserva una referencia local de recuperación en `refs/backup/v31-before-main-20261024`.

`-X ours` solo prioriza los bloques conflictivos; los cambios no conflictivos también se revisaron. Se conservaron expresamente el README, el POM, el wrapper Maven y los archivos `package.json`/lockfile de la candidata para mantener la documentación operativa vigente y las versiones ya verificadas. Las propuestas de `main` de Maven 3.10.0, JaCoCo 0.8.15 y jsdom 30.1.1 quedan fuera de esta integración; no se degrada Angular 22.2.3 ni se cambia el SDK Flutter 3.47.5/Dart 3.13.4.

Se incorporan los metadatos de colaboración y mantenimiento de `main`, incluidos CODEOWNERS, CONTRIBUTING, Dependabot y el workflow de CI. Las comprobaciones de GitHub se registran por separado del resultado histórico de las pruebas locales; la capacidad de fusionar no sustituye una ejecución de pruebas satisfactoria.

## Alcance de entrega

La fuente actual tiene **51 migraciones versionadas V1 a V51**. La evidencia histórica L-08 V50 conserva 50 versionadas más la repetible específica de pruebas; no se confunden ambos inventarios. Esta integración no migra bases, no ejecuta limpieza global y no inicia pruebas sobre la fixture histórica local.

Siguen vigentes **GO PARA CANDIDATO LOCAL DE ENTREGA / NO_GO_PILOTO_REAL**. Los términos ADMIN registran aceptación autenticada y hash SHA-256; no se atribuye una firma criptográfica personal al administrador. La firma técnica del acta y la firma Android tienen alcances distintos. El piloto requiere las compuertas del [protocolo vinculante](../legal/protocolo-transicion-piloto-real.md).

La descripción del Pull Request referencia pruebas y actas fechadas; no presenta sus conteos como una nueva revalidación de todos los lotes. Las credenciales, binarios y respaldos permanecen en el archivo local privado, fuera del contenido del PR.

## Primera comprobación en GitHub

El PR #30 quedó automáticamente fusionable (`mergeable=true`, estado `clean`). El primer job Linux del backend detectó `./mvnw: Permission denied`, código 126, antes de compilar: el wrapper conservaba modo Git `100644`. Se corrige a `100755` mediante `git update-index --chmod=+x backend/mvnw`, sin modificar el contenido del script ni dependencias. Los resultados de CI se vuelven a consultar sobre el nuevo commit; este fallo inicial se conserva como antecedente.

La siguiente ejecución aprobó frontend y móvil, pero el comando Maven genérico lanzó también suites históricas/manuales: 1 631 casos, un fallo y 1 295 errores, principalmente por tablas ausentes en la base recién creada y requisitos propios de fixtures. No se presenta esa ejecución como una regresión aprobada ni se alteran pruebas para hacerla pasar.

El workflow se adapta al alcance reproducible de la candidata: reutiliza la misma allowlist de **50 clases unitarias seguras** del runner local y ejecuta las **tres suites de integración dedicadas** de regularización, concurrencia y rectificación. Provisiona sus tres bases exactas únicamente en el runner efímero de GitHub, con contraseñas aleatorias enmascaradas y roles separados. No cambia migraciones ni compuertas, no usa la fixture histórica local y no ejecuta su limpieza global. Cada reporte seleccionado debe existir, contener pruebas y tener cero errores, fallos u omisiones. El resto de los arneses históricos/manuales conserva su evidencia previa y queda explícitamente fuera de este check, pendiente de su preparación específica.
