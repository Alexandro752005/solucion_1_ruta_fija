# Contribuir a Ruta Fija

Gracias por mejorar el proyecto. La prioridad es conservar la integridad de los datos y la compatibilidad entre CRM, API y móvil. Toda contribución debe poder revisarse, probarse y revertirse.

## Preparar el trabajo

1. Lea el [README](README.md), el [manual operativo](docs/Uso%20del%20Sistema.md) y la decisión técnica relacionada con su cambio.
2. Sincronice `main` y cree una rama corta. No desarrolle sobre `main` ni mezcle una migración de datos con un rediseño visual no relacionado.
3. Use datos de prueba y la base aislada `ruta_fija_test`; las pruebas nunca deben escribir en `solucion_ruta_fija_1`.

Nombres admitidos: `feat/<tema>`, `fix/<tema>`, `docs/<tema>`, `test/<tema>`, `refactor/<tema>`, `chore/<tema>` y `release/<version>`. Use minúsculas y guiones, por ejemplo `fix/asignacion-reserva-conductor`. Las ramas de Dependabot conservan su prefijo automático `dependabot/`.

## Commits

Use Conventional Commits: `tipo(alcance): descripción breve en imperativo`. Tipos habituales: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `build`, `ci` y `revert`.

```text
feat(mobile): mostrar confirmación de asignación
fix(ci): otorgar DML al rol de integración
docs(readme): aclarar ejecución sin base de datos
```

Si cambia un contrato API, migración o regla operativa, explique el impacto y el plan de compatibilidad en el cuerpo del commit y del PR. No incluya credenciales, tokens, `.env`, backups, APK, `node_modules` ni `target`.

## Pull Requests

- Base: `main`; título en formato Conventional Commits; un propósito verificable por PR.
- Descripción: problema, solución, archivos/contratos afectados, riesgo, migración y rollback, evidencia de pruebas y capturas solo cuando aporten valor.
- Para BD: migración Flyway incremental, respaldo antes de aplicar, verificación de permisos `rf_migrator`/`rf_app`/`rf_test` y plan de recuperación. Nunca reescriba una migración ya aplicada.
- Para seguridad: pruebe rol autorizado y denegado, aislamiento entre organizaciones y ausencia de secretos/datos personales en logs.
- Para Angular o Flutter: muestre estados de carga/error, accesibilidad básica y prueba de integración con la API si cambia el flujo.
- Para dependencias mayores: un PR por marco o cambio acoplado; registre guía de migración, changelog, lockfile y regresión completa. No integre solo porque Dependabot abrió el PR.

Checklist antes de solicitar revisión:

```text
[ ] El diff es acotado y no contiene secretos ni artefactos generados.
[ ] Backend: mvnw verify con PostgreSQL de pruebas aislado, si aplica.
[ ] Frontend: npm ci, npm run typecheck, npm run test:ci, npm run build, si aplica.
[ ] Móvil: flutter pub get, flutter analyze, flutter test; APK si cambia Android.
[ ] Documentación, OpenAPI y migraciones reflejan el cambio.
[ ] CI verde; un revisor humano validó riesgos y evidencia.
```

El job web actual llama `typecheck`; no debe presentarse como lint de estilo. La adopción de ESLint y un verificador Java de estilo requiere un PR propio con configuración, corrección de línea base y un umbral que no oculte hallazgos.

## Reglas de integración

Configure una regla de protección de `main` en GitHub: PR obligatorio, revisión de propietario de código, resolución de conversaciones y checks de backend, frontend y móvil. No permita force-push ni borrado de `main`. Integre mediante squash si el PR contiene commits de trabajo; conserve el título Conventional Commit. Una excepción de emergencia exige justificación y PR de seguimiento.

Dependabot propone cambios, pero no tiene permiso para aprobarse o integrarse automáticamente. Las actualizaciones menores/parche se agrupan por ecosistema y las mayores se evalúan por separado. Consulte el [procedimiento de mantenimiento](docs/mantenimiento-a1.md).
