# Plan autorizado para F1 — PostgreSQL nativo sin Docker

Este plan nace del cierre F0.5. No ejecuta todavía ninguno de sus cambios. Su objetivo es que Ruta Fija opere localmente con PostgreSQL 16 instalado en Windows, Java 21 y Angular, sin depender de Docker para iniciar, probar o detener el sistema.

## Convención de entornos

| Entorno | Nombre | Estado al cierre F0.5 | Regla |
|---|---|---|---|
| Desarrollo | `solucion_ruta_fija_1` | Existe; vacío antes de migraciones | Solo CRM/API local |
| Pruebas | `ruta_fija_test` | Reservado; aún no existe | Nunca apunta a desarrollo ni recibe datos de usuarios |
| Recuperación | `ruta_fija_recovery_YYYYMMDD_fNN` | Existe `ruta_fija_recovery_20260919_f04` | No ejecutar la aplicación aquí; conservar hasta autorizar limpieza |

## Secuencia propuesta

1. **F1.1A — Secretos y preflight nativo.** Definir una fuente local ignorada por Git para variables de proceso, sin reutilizar `.env` de Compose. Debe aportar datasource, JWT y política explícita de semillas. Reemplazar los `.bat` y tareas VS Code que invocan Docker por comprobaciones de Java, Node y PostgreSQL nativo. Esta subfase no inicia Spring Boot, Flyway, API ni frontend.
2. **F1.2 — Privilegios y bases aisladas.** Usar `soporte` solamente para bootstrap administrativo. Crear roles separados para migración (`rf_migrator`), aplicación (`rf_app`) y pruebas (`rf_test`), sin registrar contraseñas. Crear `ruta_fija_test` aislada y otorgar solo los permisos necesarios. Configurar Flyway con el migrador y la aplicación con DML, sin DDL.
3. **F1.3 — Primer esquema nativo.** Ejecutar Flyway V1–V5 desde una base de desarrollo vacía, verificar UTF8/UTC, `btree_gist`, constraints de exclusión y auditoría append-only. Hibernate debe conservar `ddl-auto=validate`.
4. **F1.1B — Arranque nativo real.** Tras F1.2 y F1.3, ampliar los iniciadores para levantar backend y frontend, registrar sus PID, comprobar health y ejecutar el cierre controlado.
5. **F1.4 — Pruebas sin Testcontainers.** Sustituir las tres pruebas de integración actuales basadas en `PostgreSQLContainer` por una estrategia protegida contra `ruta_fija_test`: perfil dedicado, URL bloqueada contra desarrollo, migración controlada y limpieza segura. El objetivo es que `mvn verify` sea nativo y no requiera Docker.
6. **F1.5 — Frontend y tiempo real.** Configurar el proxy de Angular para `/api` y `/ws` con soporte WebSocket, conservar mismo origen y comprobar REST, login, refresh y `/ws/operations` desde `http://localhost:4200`.
7. **F1.6 — Seguridad local y documentación.** Restringir PostgreSQL a loopback si sigue escuchando en todas las interfaces, actualizar README, manual de uso, tareas VS Code y CI para el camino nativo. Revisar que no haya secretos en Git.
8. **F1.7 — Nueva evidencia de recuperación.** Con V1–V5 aplicadas, generar un nuevo dump, restaurarlo en otra base de recuperación y comparar Flyway, tablas, `btree_gist`, constraints V5 y auditoría. Solo entonces se podrá cerrar la observación de integridad de G0.

## Retiro de Docker

Durante F1 no se utilizará Docker para operar ni verificar Ruta Fija. Los archivos heredados de Compose, Dockerfiles y configuraciones relacionadas no se eliminarán todavía: se retirarán en una fase posterior, con autorización explícita, cuando el arranque nativo, las pruebas de integración y la restauración post-migración estén aprobados. Así se evita perder una ruta de recuperación antes de contar con su reemplazo probado.

## Criterios para iniciar cambios funcionales posteriores

- `ruta_fija_test` existe y es distinta de desarrollo/recuperación.
- La aplicación arranca localmente sin secretos versionados ni Docker.
- Flyway V1–V5 y `ddl-auto=validate` pasan contra PostgreSQL nativo.
- `mvn verify`, Angular lint, pruebas y build completan sin contenedores.
- El proxy HTTP/WebSocket funciona desde el CRM.
- Un nuevo backup post-migración se restaura y compara correctamente.
