# Mantenimiento A1: ramas, dependencias y CI

Estado de referencia: 2026-10-02. Esta guía distingue el `main` publicado de cambios locales todavía no integrados. No afirma que la demo temporal ni G5 estén aprobados.

## Fase 1. Triage antes de borrar

El inventario público detectó 12 PR abiertos de `dependabot[bot]` (#1, #3–#8, #10–#14) con checks fallidos. También falló un run de backend en `main`; el frontend del mismo run pasó. Esto **no** prueba que cada actualización sea defectuosa: una falla heredada del CI puede contaminar todos los PR. Primero estabilice `main`, revise alertas de seguridad y conserve como excepción cualquier PR crítico que no tenga sustituto.

Desde PowerShell, después de instalar y autenticar GitHub CLI para cerrar PRs, ejecute un inventario de solo lectura:

```powershell
git fetch origin --prune
git status --short --branch
git ls-remote --heads origin 'refs/heads/dependabot/*'
gh pr list --repo Alexandro752005/solucion_1_ruta_fija --state open --limit 100
```

`gh` no es imprescindible para Git local, pero sí facilita cerrar PRs con un motivo antes de borrar sus ramas. Si no lo tiene, cierre cada PR desde GitHub y verifique su rama exacta en la interfaz. No borre `main`, ramas humanas ni todas las referencias con comodines. No use `git push --mirror`, `--force` ni `git branch -r | ...` para eliminaciones masivas.

Por cada PR de Dependabot declarado obsoleto:

```powershell
$repo = 'Alexandro752005/solucion_1_ruta_fija'
$pr = 14
$branch = 'dependabot/maven/backend/org.springdoc-springdoc-openapi-starter-webmvc-ui-3.1.1'
$expectedSha = '97a1835f6b664d2c21486c0e8483d47ce681dc78'

gh pr view $pr --repo $repo --json number,state,headRefName,baseRefName,author
git ls-remote --heads origin refs/heads/$branch
gh pr close $pr --repo $repo --comment 'Se cierra para regenerar la actualización sobre main estable; no se fusionó.'
git push --force-with-lease=refs/heads/${branch}:$expectedSha origin :refs/heads/$branch
git fetch origin --prune
```

Compare **a mano** el autor, base `main`, nombre de rama y SHA justo antes de ejecutar la eliminación. La opción `--force-with-lease` actúa como protección ante cambios concurrentes; si el bot actualizó la rama, se rechazará el borrado. El comando `gh pr close` cambia el estado del PR; el comando `git push` elimina solo la referencia remota indicada. Guarde el enlace del PR cerrado para la trazabilidad y verifique el resultado. Para los demás PRs repita el procedimiento con número, rama y SHA nuevos; nunca pegue una lista antigua en un bucle irreversible.

## Fase 2. Dependabot y revisión

`.github/dependabot.yml` programa revisiones semanales de Maven, npm, Pub y GitHub Actions. Agrupa versiones menores/parche por área y deja cada salto mayor como PR individual. Limita la cola para que sea revisable. La etiqueta `enhancement` existe en este repositorio; las antiguas etiquetas `dependencies`, `backend`, `frontend` y `ci` no existían y por eso no se aplicaban.

GitHub solicita revisión del propietario mediante `.github/CODEOWNERS` para manifiestos, lockfiles y workflows; Dependabot asigna además al responsable mediante `assignees`. No se usa la clave `reviewers` de `dependabot.yml`, cuya disponibilidad cambia entre ediciones de GitHub. Un propietario no debe aprobar su propio PR; si el repositorio incorpora colaboradores, añádalos como propietarios/revisores antes de exigir una aprobación de otra persona.

Después de integrar la configuración y cerrar PRs obsoletos, espere el siguiente ciclo o active un chequeo de Dependabot desde la página de dependencias. No fusione todos los PR nuevos en un solo lote: revise licencias, compatibilidad, lockfiles, changelogs, alertas de seguridad y CI por ecosistema.

## Fase 3. CI y actualizaciones mayores

La puerta mínima en PR es:

| Job | Puerta obligatoria | Evidencia |
| --- | --- | --- |
| Backend nativo | Java 21, PostgreSQL 16 efímero, `mvn verify`, Failsafe sin saltos | Unitarias + integración y esquema Flyway |
| Frontend | Node 24.16, `npm ci`, audit alto, typecheck, Vitest y build | Lockfile, tipos, pruebas y bundle |
| Móvil | Flutter 3.47.5, `flutter pub get`, analyze y test | API móvil y widgets sin compilar APK en cada PR |

El frontend aún no tiene ESLint ni el backend Checkstyle/Spotless; `typecheck` detecta tipos, no formato. Incorpore linters reales en PRs independientes después de corregir línea base, y entonces conviértalos en checks obligatorios. El APK debug/firmado debe validarse antes de una entrega, no necesariamente en cada PR para conservar tiempo de CI.

Orden recomendado para majors: actualice primero parches del major actual; congele una línea base verde; lea notas de migración oficiales y compatibilidades; migre Angular y su CLI/build/compiler junto con TypeScript soportado en un PR; migre Spring Boot y sus starters/Flyway/springdoc en otro; ejecute regresión CRM–API–móvil–BD y un ensayo de migración/rollback; integre solo con revisión y evidencia. Nunca mezcle Angular, Spring Boot y acciones CI en un mismo PR mayor.

El run de `main` observado el 2026-09-24 falló en `Verify backend`, no en instalación de PostgreSQL ni en frontend. No fue posible obtener sus logs detallados mediante API pública (HTTP 403). El CI no otorgaba a `rf_test` DML sobre tablas creadas después por `rf_migrator`; se añadieron privilegios por defecto en la **base efímera de CI** como corrección fundada en el código. Es una hipótesis fuerte, no un diagnóstico confirmado hasta ver el nuevo run verde. Si vuelve a fallar, inspeccione el primer error de Maven/Failsafe y corrija la causa antes de activar la protección de rama.

La auditoría npm del lockfile anterior detectó siete vulnerabilidades, incluidas dos críticas y dos altas. Este mantenimiento alinea las nueve dependencias Angular en 22.2.1, regenera el lockfile y exige `npm ci` estricto y `npm audit --audit-level=high` sin hallazgos. No es una migración a Angular 23 ni autoriza fusionar otros saltos mayores sin revisión.

## Fase 4. Documentación y entrega

El README distingue instalación real con PostgreSQL de compilación web sin base, explicita URLs, arquitectura, seguridad, pruebas, despliegue y limitaciones. `CONTRIBUTING.md` define ramas, Conventional Commits, PR y migraciones. Mantenga sincronizados el README, `docs/Uso del Sistema.md`, OpenAPI y evidencias al integrar funciones nuevas; no describa trabajo local no publicado como funcionalidad de `main`.

Antes de una demostración pública: respaldo restaurable, secretos fuera del repositorio, PostgreSQL privado, flujo ADMIN → CONDUCTOR → CRM → reporte/auditoría probado desde Android real, y un cierre planificado del acceso temporal. El túnel de demo no es hosting productivo ni corrige fallos de aceptación de asignaciones.

## Fase 5. Puerta de `main`

Integre solo una revisión con checks verdes, diff revisado y sin secretos. Configure en GitHub una regla de rama para `main` con PR, conversación resuelta, review y checks obligatorios; la configuración del repositorio no puede establecerse solo mediante archivos versionados. Cierre PRs obsoletos después de conservar trazabilidad y confirme que Dependabot abrió nuevos PRs agrupados. La limpieza de ramas no sustituye la estabilización del CI ni la validación manual del producto.
