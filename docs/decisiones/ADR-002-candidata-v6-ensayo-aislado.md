# ADR-002 — Ensayar V6 fuera del classpath operativo

- Estado: implementado en F2.2; evidencia de ensayo conservada.
- Fecha: 2026-09-22.

## Contexto

La migración V6 elimina ADMINISTRADOR y COORDINADOR como valores admitidos por
la base. Durante F2.1B el backend, las pruebas de integración y Angular aún
usaban esos valores hasta F2.2 y F2.3 respectivamente.

Publicar V6 inmediatamente en backend/src/main/resources/db/migration haría que
Flyway la aplicara en ruta_fija_test antes de que el enum y las pruebas fueran
compatibles. El resultado sería una rama transitoriamente rota y una causa de
fallo difícil de aislar.

## Decisión

La SQL exacta de V6 se mantiene temporalmente en:

~~~text
scripts/flyway/f2-1b/V6__unify_administrative_roles_to_admin.sql
~~~

F2.1B la ejecuta con Flyway real sobre una recuperación V5 aislada y datos
legacy deterministas. El runner solo acepta bases con nombre
ruta_fija_recovery_YYYYMMDD_f21b y nunca puede apuntar a desarrollo o pruebas.

En F2.2 se movió el mismo contenido, sin cambiar su checksum, a la carpeta
operativa de Flyway junto con el cambio de UserRole, seguridad, servicios y
pruebas. Ese fue el único momento en que V6 se aplicó a
solucion_ruta_fija_1.

## Consecuencias

- La base de desarrollo permaneció en V1–V5 durante F2.1B.
- Las pruebas nativas existentes continúan ejecutables.
- La candidata V6 está versionada, es revisable y ya fue probada por Flyway.
- V6 quedó desplegada en desarrollo en F2.2; F2.3 sigue pendiente de adaptar
  la interfaz Angular al contrato ADMIN.
